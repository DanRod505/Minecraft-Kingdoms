package dev.kingdoms.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
        ClaimMap.CODEC.fieldOf("claims").forGetter(s -> s.claims)
    ).apply(i, KingdomState::new));
    public static final Codec<KingdomState> CODEC = MAP_CODEC.codec();

    private static final List<String> FOUNDER_NAMES = List.of(
        "Ada", "Bento", "Clara", "Davi", "Elena", "Fausto", "Gilda", "Heitor", "Iris", "Joaquim",
        "Lia", "Mateus", "Nina", "Otto", "Paula", "Rui", "Sara", "Tomas", "Ursula", "Vitor");

    private final Map<UUID, Kingdom> kingdoms = new HashMap<>();
    private final Map<UUID, Settlement> settlements = new HashMap<>();
    private final Map<UUID, Citizen> citizens = new HashMap<>();
    private final ClaimMap claims;
    private Supplier<UUID> ids = UUID::randomUUID;

    public KingdomState() {
        this(List.of(), List.of(), List.of(), new ClaimMap());
    }

    private KingdomState(List<Kingdom> kingdoms, List<Settlement> settlements, List<Citizen> citizens, ClaimMap claims) {
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
        var kingdom = new Kingdom(kingdomId, name, ruler, Emblem.DEFAULT, capital.id(), 0, gameTime);
        kingdoms.put(kingdomId, kingdom);
        settlements.put(capital.id(), capital);

        var random = new Random(kingdomId.getLeastSignificantBits() ^ kingdomId.getMostSignificantBits());
        var names = new ArrayList<>(FOUNDER_NAMES);
        for (int n = 0; n < rules.founderCount(); n++) {
            var founderName = names.isEmpty() ? "Colonist " + (n + 1) : names.remove(random.nextInt(names.size()));
            var citizen = new Citizen(ids.get(), founderName, kingdomId, capital.id(), Citizen.Profession.NONE);
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
        claims.removeKingdom(kingdomId);
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
