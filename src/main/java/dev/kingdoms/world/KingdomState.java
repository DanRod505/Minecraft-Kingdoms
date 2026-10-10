package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kingdoms.orders.Area;
import dev.kingdoms.orders.Order;
import dev.kingdoms.orders.OrderStatus;
import dev.kingdoms.orders.OrderType;
import dev.kingdoms.territory.ChunkKey;
import dev.kingdoms.territory.ClaimMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every kingdom, settlement, citizen and claim in a world, plus the rules that change them.
 * Server-only and free of Minecraft classes: callers pass dimensions as ids and positions as ints.
 * Each mutating method either applies completely or throws {@link KingdomException} without changes.
 */
public final class KingdomState {
    public static final MapCodec<KingdomState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Kingdom.CODEC.listOf().fieldOf("kingdoms").forGetter(s -> sorted(s.kingdoms.values(), Kingdom::id)),
        Settlement.CODEC.listOf().fieldOf("settlements").forGetter(s -> sorted(s.settlements.values(), Settlement::id)),
        Citizen.CODEC.listOf().fieldOf("citizens").forGetter(s -> sorted(s.citizens.values(), Citizen::id)),
        ClaimMap.CODEC.fieldOf("claims").forGetter(s -> s.claims),
        Order.CODEC.listOf().fieldOf("orders").forGetter(s -> sorted(s.orders.values(), Order::id))
    ).apply(i, KingdomState::new));
    public static final Codec<KingdomState> CODEC = MAP_CODEC.codec();

    private static final List<String> FOUNDER_NAMES = List.of(
        "Ada", "Bento", "Clara", "Davi", "Elena", "Fausto", "Gilda", "Heitor", "Iris", "Joaquim",
        "Lia", "Mateus", "Nina", "Otto", "Paula", "Rui", "Sara", "Tomas", "Ursula", "Vitor");

    private final Map<UUID, Kingdom> kingdoms = new HashMap<>();
    private final Map<UUID, Settlement> settlements = new HashMap<>();
    private final Map<UUID, Citizen> citizens = new HashMap<>();
    private final Map<UUID, Order> orders = new HashMap<>();
    private final ClaimMap claims;
    private Supplier<UUID> ids = UUID::randomUUID;

    public KingdomState() {
        this(List.of(), List.of(), List.of(), new ClaimMap(), List.of());
    }

    private KingdomState(List<Kingdom> kingdoms, List<Settlement> settlements, List<Citizen> citizens, ClaimMap claims, List<Order> orders) {
        orders.forEach(o -> this.orders.put(o.id(), o));
        kingdoms.forEach(k -> this.kingdoms.put(k.id(), k));
        settlements.forEach(s -> this.settlements.put(s.id(), s));
        citizens.forEach(c -> this.citizens.put(c.id(), c));
        this.claims = claims;
    }

    /** For tests: makes generated ids deterministic. */
    public void setIdSource(Supplier<UUID> ids) {
        this.ids = ids;
    }

    // ---- queries ----

    public List<Kingdom> kingdoms() {
        return sorted(kingdoms.values(), k -> k.name().toLowerCase(Locale.ROOT));
    }

    public Optional<Kingdom> kingdom(UUID id) {
        return Optional.ofNullable(kingdoms.get(id));
    }

    public Optional<Kingdom> kingdomOf(UUID ruler) {
        return kingdoms.values().stream().filter(k -> k.ruler().equals(ruler)).findFirst();
    }

    public Optional<Kingdom> kingdomNamed(String name) {
        var key = normalizeName(name).toLowerCase(Locale.ROOT);
        return kingdoms.values().stream().filter(k -> k.name().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    public Optional<Settlement> settlement(UUID id) {
        return Optional.ofNullable(settlements.get(id));
    }

    public List<Settlement> settlementsOf(UUID kingdom) {
        return sorted(settlements.values().stream().filter(s -> s.kingdom().equals(kingdom)).toList(), Settlement::name);
    }

    public List<Citizen> citizensOf(UUID kingdom) {
        return sorted(citizens.values().stream().filter(c -> c.kingdom().equals(kingdom)).toList(), Citizen::name);
    }

    public Optional<Kingdom> ownerAt(String dimension, int chunkX, int chunkZ) {
        return claims.owner(dimension, ChunkKey.of(chunkX, chunkZ)).flatMap(this::kingdom);
    }

    public int claimCount(UUID kingdom) {
        return claims.count(kingdom);
    }

    // ---- rules ----

    /** Founds a kingdom with its capital camp at the given block, its founders and the claims around it. */
    public Kingdom found(UUID ruler, String rawName, String dimension, int x, int y, int z, long gameTime, KingdomRules rules) {
        var name = checkFound(ruler, rawName, dimension, x, z);
        int cx = x >> 4, cz = z >> 4;

        var kingdomId = ids.get();
        var capital = new Settlement(ids.get(), kingdomId, name, dimension, x, y, z, Settlement.Stage.CAMP);
        var kingdom = new Kingdom(kingdomId, name, ruler, Emblem.DEFAULT, capital.id(), 0, gameTime, Optional.empty());
        kingdoms.put(kingdomId, kingdom);
        settlements.put(capital.id(), capital);

        var random = new Random(kingdomId.getLeastSignificantBits() ^ kingdomId.getMostSignificantBits());
        var names = new ArrayList<>(FOUNDER_NAMES);
        for (int n = 0; n < rules.founderCount(); n++) {
            var founderName = names.isEmpty() ? "Colonist " + (n + 1) : names.remove(random.nextInt(names.size()));
            var citizen = new Citizen(ids.get(), founderName, kingdomId, capital.id(), Citizen.Profession.NONE, Optional.empty(), false);
            citizens.put(citizen.id(), citizen);
        }

        // Spiral outwards from the centre so the limit keeps the closest chunks; never take land already owned.
        int radius = rules.initialClaimRadius();
        var ring = new ArrayList<long[]>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                ring.add(new long[] { Math.max(Math.abs(dx), Math.abs(dz)), ChunkKey.of(cx + dx, cz + dz) });
        ring.sort(Comparator.comparingLong(a -> a[0]));
        int taken = 0;
        for (var entry : ring) {
            if (taken >= rules.maxClaims())
                break;
            if (claims.owner(dimension, entry[1]).isEmpty()) {
                claims.set(dimension, entry[1], kingdomId);
                taken++;
            }
        }
        return kingdom;
    }

    /** Checks every founding rule without changing anything; returns the normalized name. */
    public String checkFound(UUID ruler, String rawName, String dimension, int x, int z) {
        if (kingdomOf(ruler).isPresent())
            throw new KingdomException("kingdoms.error.already_ruler");
        var name = checkName(rawName, null);
        var centre = ChunkKey.of(x >> 4, z >> 4);
        if (claims.owner(dimension, centre).isPresent())
            throw new KingdomException("kingdoms.error.land_claimed", ownerName(dimension, centre));
        return name;
    }

    public Kingdom rename(UUID kingdomId, String rawName) {
        var kingdom = require(kingdomId);
        var renamed = kingdom.withName(checkName(rawName, kingdomId));
        kingdoms.put(kingdomId, renamed);
        return renamed;
    }

    public Kingdom setEmblem(UUID kingdomId, Emblem emblem) {
        var updated = require(kingdomId).withEmblem(emblem);
        kingdoms.put(kingdomId, updated);
        return updated;
    }

    public Kingdom setTreasury(UUID kingdomId, long amount) {
        if (amount < 0)
            throw new KingdomException("kingdoms.error.treasury_negative");
        var updated = require(kingdomId).withTreasury(amount);
        kingdoms.put(kingdomId, updated);
        return updated;
    }

    public Kingdom addTreasury(UUID kingdomId, long delta) {
        var kingdom = require(kingdomId);
        long result;
        try {
            result = Math.addExact(kingdom.treasury(), delta);
        } catch (ArithmeticException e) {
            throw new KingdomException("kingdoms.error.treasury_overflow");
        }
        return setTreasury(kingdomId, result);
    }

    /** Claims a chunk. It must be free, touch land the kingdom already holds, and fit the limit. */
    public void claim(UUID kingdomId, String dimension, int chunkX, int chunkZ, KingdomRules rules) {
        require(kingdomId);
        var key = ChunkKey.of(chunkX, chunkZ);
        var owner = claims.owner(dimension, key);
        if (owner.isPresent()) {
            if (owner.get().equals(kingdomId))
                throw new KingdomException("kingdoms.error.already_claimed");
            throw new KingdomException("kingdoms.error.land_claimed", ownerName(dimension, key));
        }
        if (claims.count(kingdomId) >= rules.maxClaims())
            throw new KingdomException("kingdoms.error.claim_limit", rules.maxClaims());
        boolean adjacent = false;
        for (var neighbour : ChunkKey.neighbours(key))
            adjacent |= claims.owner(dimension, neighbour).filter(kingdomId::equals).isPresent();
        if (!adjacent)
            throw new KingdomException("kingdoms.error.not_adjacent");
        claims.set(dimension, key, kingdomId);
    }

    /** Releases a chunk. The chunk holding the capital can't be released. */
    public void unclaim(UUID kingdomId, String dimension, int chunkX, int chunkZ) {
        var kingdom = require(kingdomId);
        var key = ChunkKey.of(chunkX, chunkZ);
        if (claims.owner(dimension, key).filter(kingdomId::equals).isEmpty())
            throw new KingdomException("kingdoms.error.not_owned");
        var capital = settlements.get(kingdom.capital());
        if (capital != null && capital.dimension().equals(dimension) && ChunkKey.of(capital.x() >> 4, capital.z() >> 4) == key)
            throw new KingdomException("kingdoms.error.capital_chunk");
        claims.remove(dimension, key);
    }

    public void delete(UUID kingdomId) {
        require(kingdomId);
        kingdoms.remove(kingdomId);
        settlements.values().removeIf(s -> s.kingdom().equals(kingdomId));
        citizens.values().removeIf(c -> c.kingdom().equals(kingdomId));
        orders.values().removeIf(o -> o.kingdom().equals(kingdomId));
        claims.removeKingdom(kingdomId);
    }

    // ---- citizens and orders (Phase 2) ----

    public Optional<Citizen> citizen(UUID id) {
        return Optional.ofNullable(citizens.get(id));
    }

    public List<Citizen> citizens() {
        return sorted(citizens.values(), Citizen::id);
    }

    public Optional<Citizen> citizenNamed(UUID kingdomId, String name) {
        var key = normalizeName(name).toLowerCase(Locale.ROOT);
        return citizensOf(kingdomId).stream().filter(c -> c.name().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    public Optional<Order> order(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** Orders of a kingdom, open ones first, then by priority (high first) and age. */
    public List<Order> ordersOf(UUID kingdomId) {
        var list = new ArrayList<>(orders.values().stream().filter(o -> o.kingdom().equals(kingdomId)).toList());
        list.sort(Comparator.comparing((Order o) -> !o.status().open())
            .thenComparing(Comparator.comparingInt(Order::priority).reversed())
            .thenComparingLong(Order::createdAt)
            .thenComparing(Order::id));
        return list;
    }

    /** Finds an order of the kingdom by the start of its id, as shown to players. */
    public Optional<Order> orderByPrefix(UUID kingdomId, String prefix) {
        var p = prefix.toLowerCase(Locale.ROOT);
        var matches = orders.values().stream().filter(o -> o.kingdom().equals(kingdomId) && o.id().toString().startsWith(p)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public List<Citizen> workersOf(UUID orderId) {
        return sorted(citizens.values().stream().filter(c -> c.order().filter(orderId::equals).isPresent()).toList(), Citizen::name);
    }

    public void setStockpile(UUID kingdomId, BlockLocation location) {
        var kingdom = require(kingdomId);
        if (claims.owner(location.dimension(), ChunkKey.of(location.x() >> 4, location.z() >> 4)).filter(kingdomId::equals).isEmpty())
            throw new KingdomException("kingdoms.error.outside_territory");
        kingdoms.put(kingdomId, kingdom.withStockpile(Optional.of(location)));
    }

    public Citizen assignProfession(UUID kingdomId, UUID citizenId, Citizen.Profession profession) {
        var citizen = requireCitizen(kingdomId, citizenId);
        var updated = citizen.withProfession(profession);
        citizens.put(citizenId, updated);
        return updated;
    }

    /**
     * Creates an order over an area. The whole area must lie in the kingdom's own territory (rule 20.9:
     * workers never touch other people's land) and the kingdom needs a stockpile to deliver to.
     */
    public Order createOrder(UUID kingdomId, OrderType type, Area area, int priority, Optional<String> material, long gameTime, KingdomRules rules) {
        var kingdom = require(kingdomId);
        if (priority < Order.MIN_PRIORITY || priority > Order.MAX_PRIORITY)
            throw new KingdomException("kingdoms.error.priority_range", Order.MIN_PRIORITY, Order.MAX_PRIORITY);
        if (area.volume() > rules.maxOrderVolume())
            throw new KingdomException("kingdoms.error.area_too_large", area.volume(), rules.maxOrderVolume());
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++)
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++)
                if (claims.owner(area.dimension(), ChunkKey.of(cx, cz)).filter(kingdomId::equals).isEmpty())
                    throw new KingdomException("kingdoms.error.outside_territory");
        if (kingdom.stockpile().isEmpty())
            throw new KingdomException("kingdoms.error.no_stockpile");
        if (type == OrderType.BUILD && material.isEmpty())
            throw new KingdomException("kingdoms.error.no_material");
        var order = new Order(ids.get(), kingdomId, type, area, priority, OrderStatus.ACTIVE, Optional.empty(),
            type == OrderType.BUILD ? material : Optional.empty(), gameTime, 0);
        orders.put(order.id(), order);
        return order;
    }

    /** Pausing, resuming or cancelling. Workers of a paused or cancelled order are released at once. */
    public Order setOrderStatus(UUID kingdomId, UUID orderId, OrderStatus status) {
        var order = requireOrder(kingdomId, orderId);
        if (!order.status().open())
            throw new KingdomException("kingdoms.error.order_closed");
        var updated = order.withStatus(status);
        orders.put(orderId, updated);
        if (status != OrderStatus.ACTIVE)
            releaseWorkers(orderId);
        return updated;
    }

    public Order setOrderPriority(UUID kingdomId, UUID orderId, int priority) {
        if (priority < Order.MIN_PRIORITY || priority > Order.MAX_PRIORITY)
            throw new KingdomException("kingdoms.error.priority_range", Order.MIN_PRIORITY, Order.MAX_PRIORITY);
        var updated = requireOrder(kingdomId, orderId).withPriority(priority);
        orders.put(orderId, updated);
        return updated;
    }

    /**
     * Gives an idle citizen the best active order for its profession: highest priority, then oldest.
     * Orders that are blocked are still offered, so a worker can find out the block is gone.
     */
    public Optional<Order> pickOrder(UUID citizenId) {
        var citizen = citizens.get(citizenId);
        if (citizen == null || citizen.profession() == Citizen.Profession.NONE)
            return Optional.empty();
        var best = ordersOf(citizen.kingdom()).stream()
            .filter(o -> o.status() == OrderStatus.ACTIVE && o.type().profession() == citizen.profession())
            .findFirst();
        citizens.put(citizenId, citizen.withOrder(best.map(Order::id)));
        return best;
    }

    /** Whether a better order than the current one exists, so the worker should replan (GDD 3.5). */
    public boolean shouldReplan(UUID citizenId) {
        var citizen = citizens.get(citizenId);
        if (citizen == null || citizen.order().isEmpty())
            return true;
        var current = orders.get(citizen.order().get());
        if (current == null || current.status() != OrderStatus.ACTIVE)
            return true;
        return ordersOf(citizen.kingdom()).stream()
            .filter(o -> o.status() == OrderStatus.ACTIVE && o.type().profession() == citizen.profession())
            .findFirst().map(o -> o.priority() > current.priority()).orElse(false);
    }

    public void releaseCitizen(UUID citizenId) {
        var citizen = citizens.get(citizenId);
        if (citizen != null)
            citizens.put(citizenId, citizen.withOrder(Optional.empty()));
    }

    public void recordProgress(UUID orderId, int amount) {
        var order = orders.get(orderId);
        if (order != null)
            orders.put(orderId, order.withProgress(order.progress() + amount));
    }

    /** Stores why no work happens. Returns true when the reason changed, so callers only log changes. */
    public boolean markBlocked(UUID orderId, String reason) {
        var order = orders.get(orderId);
        if (order == null || order.blocked().filter(reason::equals).isPresent())
            return false;
        orders.put(orderId, order.withBlocked(Optional.of(reason)));
        return true;
    }

    public void completeOrder(UUID orderId) {
        var order = orders.get(orderId);
        if (order == null || !order.status().open())
            return;
        orders.put(orderId, order.withStatus(OrderStatus.DONE));
        releaseWorkers(orderId);
    }

    public void setMaterialized(UUID citizenId, boolean materialized) {
        var citizen = citizens.get(citizenId);
        if (citizen != null)
            citizens.put(citizenId, citizen.withMaterialized(materialized));
    }

    /** A citizen died: it leaves the kingdom for good. */
    public void removeCitizen(UUID citizenId) {
        citizens.remove(citizenId);
    }

    private void releaseWorkers(UUID orderId) {
        for (var c : List.copyOf(citizens.values()))
            if (c.order().filter(orderId::equals).isPresent())
                citizens.put(c.id(), c.withOrder(Optional.empty()));
    }

    /**
     * Lists broken references (GDD 14.10). Never repairs anything by itself: loading a save must not
     * silently delete data, so an admin decides what to do with each problem.
     */
    public List<String> validate() {
        var problems = new ArrayList<String>();
        var rulers = new HashSet<UUID>();
        for (var k : kingdoms()) {
            if (!rulers.add(k.ruler()))
                problems.add("Ruler " + k.ruler() + " rules more than one kingdom (" + k.name() + ")");
            var capital = settlements.get(k.capital());
            if (capital == null)
                problems.add("Kingdom " + k.name() + " has a missing capital " + k.capital());
            else if (!capital.kingdom().equals(k.id()))
                problems.add("Kingdom " + k.name() + " has a capital owned by another kingdom");
            else if (claims.owner(capital.dimension(), ChunkKey.of(capital.x() >> 4, capital.z() >> 4)).filter(k.id()::equals).isEmpty())
                problems.add("Kingdom " + k.name() + " does not own the chunk of its capital");
            if (k.treasury() < 0)
                problems.add("Kingdom " + k.name() + " has a negative treasury");
        }
        for (var s : sorted(settlements.values(), Settlement::id))
            if (!kingdoms.containsKey(s.kingdom()))
                problems.add("Settlement " + s.name() + " (" + s.id() + ") belongs to missing kingdom " + s.kingdom());
        for (var c : sorted(citizens.values(), Citizen::id)) {
            if (!kingdoms.containsKey(c.kingdom()))
                problems.add("Citizen " + c.name() + " (" + c.id() + ") belongs to missing kingdom " + c.kingdom());
            var home = settlements.get(c.settlement());
            if (home == null)
                problems.add("Citizen " + c.name() + " (" + c.id() + ") lives in missing settlement " + c.settlement());
            else if (!home.kingdom().equals(c.kingdom()))
                problems.add("Citizen " + c.name() + " (" + c.id() + ") lives in a settlement of another kingdom");
        }
        for (var o : sorted(orders.values(), Order::id))
            if (!kingdoms.containsKey(o.kingdom()))
                problems.add("Order " + o.shortId() + " belongs to missing kingdom " + o.kingdom());
        for (var c : sorted(citizens.values(), Citizen::id))
            c.order().ifPresent(id -> {
                var o = orders.get(id);
                if (o == null)
                    problems.add("Citizen " + c.name() + " (" + c.id() + ") works on missing order " + id);
                else if (!o.kingdom().equals(c.kingdom()))
                    problems.add("Citizen " + c.name() + " (" + c.id() + ") works on an order of another kingdom");
            });
        for (var claim : claims.all())
            if (!kingdoms.containsKey(claim.kingdom()))
                problems.add("Chunk " + ChunkKey.x(claim.chunk()) + "," + ChunkKey.z(claim.chunk()) + " in " + claim.dimension()
                    + " is claimed by missing kingdom " + claim.kingdom());
        return problems;
    }

    // ---- helpers ----

    private Kingdom require(UUID kingdomId) {
        var kingdom = kingdoms.get(kingdomId);
        if (kingdom == null)
            throw new KingdomException("kingdoms.error.no_kingdom");
        return kingdom;
    }

    private Citizen requireCitizen(UUID kingdomId, UUID citizenId) {
        var citizen = citizens.get(citizenId);
        if (citizen == null || !citizen.kingdom().equals(kingdomId))
            throw new KingdomException("kingdoms.error.unknown_citizen");
        return citizen;
    }

    private Order requireOrder(UUID kingdomId, UUID orderId) {
        var order = orders.get(orderId);
        if (order == null || !order.kingdom().equals(kingdomId))
            throw new KingdomException("kingdoms.error.unknown_order");
        return order;
    }

    private String ownerName(String dimension, long chunk) {
        return claims.owner(dimension, chunk).flatMap(this::kingdom).map(Kingdom::name).orElse("?");
    }

    public static String normalizeName(String raw) {
        return raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    }

    private String checkName(String raw, UUID self) {
        var name = normalizeName(raw);
        if (name.length() < KingdomRules.NAME_MIN || name.length() > KingdomRules.NAME_MAX)
            throw new KingdomException("kingdoms.error.name_length", KingdomRules.NAME_MIN, KingdomRules.NAME_MAX);
        if (!name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '\''))
            throw new KingdomException("kingdoms.error.name_chars");
        var existing = kingdomNamed(name);
        if (existing.isPresent() && !existing.get().id().equals(self))
            throw new KingdomException("kingdoms.error.name_taken", name);
        return name;
    }

    private static <T, K extends Comparable<? super K>> List<T> sorted(java.util.Collection<T> values, java.util.function.Function<T, K> key) {
        var list = new ArrayList<>(values);
        list.sort(Comparator.comparing(key));
        return list;
    }
}
