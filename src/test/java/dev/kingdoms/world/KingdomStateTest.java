package dev.kingdoms.world;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final KingdomRules RULES = new KingdomRules(5, 1, 12, 4096);

    private final UUID alice = new UUID(0, 1);
    private final UUID bob = new UUID(0, 2);
    private KingdomState state;

    @BeforeEach
    void setUp() {
        state = new KingdomState();
        long[] next = { 100 };
        state.setIdSource(() -> new UUID(1, next[0]++));
    }

    private Kingdom foundAlice() {
        return state.found(alice, "  Avalon   Norte ", OVERWORLD, 8, 64, 8, 1000, RULES);
    }

    private static void assertRefused(String key, Runnable action) {
        var e = assertThrows(KingdomException.class, action::run);
        assertEquals(key, e.key());
    }

    @Test
    void foundingCreatesCapitalFoundersAndTerritory() {
        var k = foundAlice();
        assertEquals("Avalon Norte", k.name());
        assertEquals(alice, k.ruler());
        var capital = state.settlement(k.capital()).orElseThrow();
        assertEquals(Settlement.Stage.CAMP, capital.stage());
        assertEquals(5, state.citizensOf(k.id()).size());
        assertTrue(state.citizensOf(k.id()).stream().allMatch(c -> c.settlement().equals(capital.id())));
        assertEquals(9, state.claimCount(k.id()));
        assertEquals(k, state.ownerAt(OVERWORLD, 0, 0).orElseThrow());
        assertEquals(k, state.ownerAt(OVERWORLD, -1, 1).orElseThrow());
        assertTrue(state.ownerAt(OVERWORLD, 2, 0).isEmpty());
        assertTrue(state.ownerAt("minecraft:the_nether", 0, 0).isEmpty());
        assertTrue(state.validate().isEmpty());
    }

    @Test
    void founderNamesAreUniqueWithinKingdom() {
        var k = foundAlice();
        var names = state.citizensOf(k.id()).stream().map(Citizen::name).distinct().count();
        assertEquals(5, names);
    }

    @Test
    void foundingRefusesSecondKingdomBadNamesAndOwnedLand() {
        foundAlice();
        assertRefused("kingdoms.error.already_ruler", () -> state.found(alice, "Outro", OVERWORLD, 500, 64, 500, 0, RULES));
        assertRefused("kingdoms.error.name_taken", () -> state.found(bob, "avalon norte", OVERWORLD, 500, 64, 500, 0, RULES));
        assertRefused("kingdoms.error.name_length", () -> state.found(bob, "ab", OVERWORLD, 500, 64, 500, 0, RULES));
        assertRefused("kingdoms.error.name_chars", () -> state.found(bob, "Bad<Name>", OVERWORLD, 500, 64, 500, 0, RULES));
        assertRefused("kingdoms.error.land_claimed", () -> state.found(bob, "Camelot", OVERWORLD, 20, 64, 20, 0, RULES));
        assertEquals(1, state.kingdoms().size());
    }

    @Test
    void neighbourFoundingNeverTakesOwnedChunks() {
        var a = foundAlice();
        // Bob's centre chunk (2,0) is free but his 3x3 ring overlaps Alice's column x=1.
        var b = state.found(bob, "Camelot", OVERWORLD, 40, 64, 8, 0, RULES);
        assertEquals(a, state.ownerAt(OVERWORLD, 1, 0).orElseThrow());
        assertEquals(b, state.ownerAt(OVERWORLD, 2, 0).orElseThrow());
        assertEquals(6, state.claimCount(b.id()));
        assertTrue(state.validate().isEmpty());
    }

    @Test
    void claimingRequiresAdjacencyFreeLandAndLimit() {
        var k = foundAlice();
        state.claim(k.id(), OVERWORLD, 2, 0, RULES);
        assertRefused("kingdoms.error.already_claimed", () -> state.claim(k.id(), OVERWORLD, 2, 0, RULES));
        assertRefused("kingdoms.error.not_adjacent", () -> state.claim(k.id(), OVERWORLD, 5, 5, RULES));
        assertRefused("kingdoms.error.not_adjacent", () -> state.claim(k.id(), "minecraft:the_nether", 2, 1, RULES));
        state.claim(k.id(), OVERWORLD, 3, 0, RULES);
        state.claim(k.id(), OVERWORLD, 4, 0, RULES);
        assertEquals(12, state.claimCount(k.id()));
        assertRefused("kingdoms.error.claim_limit", () -> state.claim(k.id(), OVERWORLD, 5, 0, RULES));
    }

    @Test
    void claimingNeverOverwritesAnotherKingdom() {
        var a = foundAlice();
        var b = state.found(bob, "Camelot", OVERWORLD, 64, 64, 8, 0, RULES);
        assertRefused("kingdoms.error.land_claimed", () -> state.claim(b.id(), OVERWORLD, 1, 0, RULES));
        assertEquals(a, state.ownerAt(OVERWORLD, 1, 0).orElseThrow());
    }

    @Test
    void unclaimKeepsCapitalChunk() {
        var k = foundAlice();
        state.unclaim(k.id(), OVERWORLD, 1, 1);
        assertTrue(state.ownerAt(OVERWORLD, 1, 1).isEmpty());
        assertRefused("kingdoms.error.capital_chunk", () -> state.unclaim(k.id(), OVERWORLD, 0, 0));
        assertRefused("kingdoms.error.not_owned", () -> state.unclaim(k.id(), OVERWORLD, 1, 1));
    }

    @Test
    void treasuryNeverGoesNegativeOrOverflows() {
        var k = foundAlice();
        assertEquals(150, state.addTreasury(k.id(), 150).treasury());
        assertRefused("kingdoms.error.treasury_negative", () -> state.addTreasury(k.id(), -151));
        assertRefused("kingdoms.error.treasury_overflow", () -> state.addTreasury(k.id(), Long.MAX_VALUE));
        assertEquals(150, state.kingdom(k.id()).orElseThrow().treasury());
    }

    @Test
    void renameAndEmblem() {
        var k = foundAlice();
        assertEquals("avalon norte", state.rename(k.id(), "avalon norte").name());
        var emblem = new Emblem(HeraldicColor.RED, HeraldicColor.WHITE, Emblem.Symbol.LION);
        assertEquals(emblem, state.setEmblem(k.id(), emblem).emblem());
    }

    @Test
    void deleteRemovesEverything() {
        var k = foundAlice();
        state.delete(k.id());
        assertTrue(state.kingdoms().isEmpty());
        assertTrue(state.citizensOf(k.id()).isEmpty());
        assertTrue(state.ownerAt(OVERWORLD, 0, 0).isEmpty());
        assertTrue(state.validate().isEmpty());
    }

    @Test
    void codecRoundTripKeepsIdsTerritoryCitizensAndTreasury() {
        var k = foundAlice();
        state.addTreasury(k.id(), 42);
        state.claim(k.id(), OVERWORLD, 2, 0, RULES);
        var json = KingdomState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var loaded = KingdomState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(state.kingdoms(), loaded.kingdoms());
        assertEquals(state.citizensOf(k.id()), loaded.citizensOf(k.id()));
        assertEquals(state.settlementsOf(k.id()), loaded.settlementsOf(k.id()));
        assertEquals(10, loaded.claimCount(k.id()));
        assertEquals(json, KingdomState.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow());
    }

    @Test
    void validateReportsBrokenReferences() {
        var k = foundAlice();
        var json = KingdomState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        json.getAsJsonArray("settlements").remove(0);
        var broken = KingdomState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        var problems = broken.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("missing capital")), problems::toString);
        assertTrue(problems.stream().anyMatch(p -> p.contains("missing settlement")), problems::toString);
        assertEquals(1, broken.kingdoms().size(), "validation must not delete data");
        assertEquals(k.id(), broken.kingdoms().getFirst().id());
    }
}
