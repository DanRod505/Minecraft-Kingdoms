package dev.kingdoms.territory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kingdoms.world.Codecs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chunk ownership per dimension (GDD 7.2). Pure data: rules about who may claim what live in
 * {@link dev.kingdoms.world.KingdomState}. Saved grouped by dimension and kingdom as compact long lists.
 */
public final class ClaimMap {
    private record Group(String dimension, UUID kingdom, List<Long> chunks) {
        static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(Group::dimension),
            Codecs.UUID_STRING.fieldOf("kingdom").forGetter(Group::kingdom),
            Codec.LONG.listOf().fieldOf("chunks").forGetter(Group::chunks)
        ).apply(i, Group::new));
    }

    public static final Codec<ClaimMap> CODEC = Group.CODEC.listOf().xmap(ClaimMap::fromGroups, ClaimMap::toGroups);

    private final Map<String, Map<Long, UUID>> owners = new HashMap<>();

    public Optional<UUID> owner(String dimension, long chunk) {
        var map = owners.get(dimension);
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(chunk));
    }

    public void set(String dimension, long chunk, UUID kingdom) {
        owners.computeIfAbsent(dimension, d -> new HashMap<>()).put(chunk, kingdom);
    }

    public boolean remove(String dimension, long chunk) {
        var map = owners.get(dimension);
        return map != null && map.remove(chunk) != null;
    }

    public void removeKingdom(UUID kingdom) {
        owners.values().forEach(map -> map.values().removeIf(kingdom::equals));
    }

    public int count(UUID kingdom) {
        int total = 0;
        for (var map : owners.values())
            for (var owner : map.values())
                if (owner.equals(kingdom))
                    total++;
        return total;
    }

    /** Every claim as (dimension, chunk, kingdom), for validation and debugging. */
    public List<Claim> all() {
        var list = new ArrayList<Claim>();
        owners.forEach((dim, map) -> map.forEach((chunk, kingdom) -> list.add(new Claim(dim, chunk, kingdom))));
        list.sort(Comparator.comparing(Claim::dimension).thenComparingLong(Claim::chunk));
        return list;
    }

    public record Claim(String dimension, long chunk, UUID kingdom) {}

    private static ClaimMap fromGroups(List<Group> groups) {
        var map = new ClaimMap();
        for (var group : groups)
            for (var chunk : group.chunks())
                map.set(group.dimension(), chunk, group.kingdom());
        return map;
    }

    // Sorted output keeps the saved file identical for identical state.
    private List<Group> toGroups() {
        var grouped = new HashMap<String, Map<UUID, List<Long>>>();
        for (var claim : all())
            grouped.computeIfAbsent(claim.dimension(), d -> new HashMap<>())
                .computeIfAbsent(claim.kingdom(), k -> new ArrayList<>())
                .add(claim.chunk());
        var groups = new ArrayList<Group>();
        grouped.forEach((dim, byKingdom) -> byKingdom.forEach((kingdom, chunks) -> groups.add(new Group(dim, kingdom, chunks))));
        groups.sort(Comparator.comparing(Group::dimension).thenComparing(Group::kingdom));
        return groups;
    }
}
