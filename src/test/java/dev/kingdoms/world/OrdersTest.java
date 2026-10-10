package dev.kingdoms.world;

import com.mojang.serialization.JsonOps;
import dev.kingdoms.orders.Area;
import dev.kingdoms.orders.Order;
import dev.kingdoms.orders.OrderStatus;
import dev.kingdoms.orders.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdersTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final KingdomRules RULES = new KingdomRules(5, 1, 12, 4096);
    private static final Area FIELD = Area.of(OVERWORLD, 0, 60, 0, 9, 64, 9);

    private final UUID alice = new UUID(0, 1);
    private final UUID bob = new UUID(0, 2);
    private KingdomState state;
    private Kingdom kingdom;

    @BeforeEach
    void setUp() {
        state = new KingdomState();
        long[] next = { 100 };
        state.setIdSource(() -> new UUID(1, next[0]++));
        kingdom = state.found(alice, "Avalon", OVERWORLD, 8, 64, 8, 1000, RULES);
    }

    private static void assertRefused(String key, Runnable action) {
        var e = assertThrows(KingdomException.class, action::run);
        assertEquals(key, e.key());
    }

    private void stockpile() {
        state.setStockpile(kingdom.id(), new BlockLocation(OVERWORLD, 2, 64, 2));
    }

    private Citizen worker(Citizen.Profession profession) {
        var c = state.citizensOf(kingdom.id()).stream().filter(x -> x.profession() == Citizen.Profession.NONE).findFirst().orElseThrow();
        return state.assignProfession(kingdom.id(), c.id(), profession);
    }

    private Order order(OrderType type, int priority, long time) {
        return state.createOrder(kingdom.id(), type, FIELD, priority, Optional.empty(), time, RULES);
    }

    @Test
    void ordersNeedStockpileOwnTerritoryAndSaneSize() {
        assertRefused("kingdoms.error.no_stockpile", () -> order(OrderType.MINE, 3, 0));
        stockpile();
        assertRefused("kingdoms.error.outside_territory",
            () -> state.createOrder(kingdom.id(), OrderType.MINE, Area.of(OVERWORLD, 0, 60, 0, 40, 60, 0), 3, Optional.empty(), 0, RULES));
        assertRefused("kingdoms.error.outside_territory",
            () -> state.createOrder(kingdom.id(), OrderType.MINE, Area.of("minecraft:the_nether", 0, 60, 0, 1, 60, 1), 3, Optional.empty(), 0, RULES));
        assertRefused("kingdoms.error.area_too_large",
            () -> state.createOrder(kingdom.id(), OrderType.MINE, Area.of(OVERWORLD, -16, 0, -16, 31, 60, 31), 3, Optional.empty(), 0, RULES));
        assertRefused("kingdoms.error.priority_range", () -> order(OrderType.MINE, 6, 0));
        assertRefused("kingdoms.error.no_material", () -> order(OrderType.BUILD, 3, 0));
        assertRefused("kingdoms.error.outside_territory",
            () -> state.setStockpile(kingdom.id(), new BlockLocation(OVERWORLD, 100, 64, 100)));
        var o = order(OrderType.MINE, 3, 0);
        assertEquals(OrderStatus.ACTIVE, o.status());
        assertEquals(500, FIELD.volume());
    }

    @Test
    void neighbourCannotOrderWorkOnOurLand() {
        var other = state.found(bob, "Brega", OVERWORLD, 100, 64, 100, 1000, RULES);
        state.setStockpile(other.id(), new BlockLocation(OVERWORLD, 100, 64, 100));
        assertRefused("kingdoms.error.outside_territory",
            () -> state.createOrder(other.id(), OrderType.MINE, FIELD, 3, Optional.empty(), 0, RULES));
    }

    @Test
    void workersTakeHighestPriorityThenOldestOfTheirProfession() {
        stockpile();
        var miner = worker(Citizen.Profession.MINER);
        var farmer = worker(Citizen.Profession.FARMER);
        var old = order(OrderType.MINE, 3, 10);
        var newer = order(OrderType.MINE, 3, 20);
        var urgent = order(OrderType.MINE, 5, 30);
        var farm = order(OrderType.FARM, 1, 5);
        assertEquals(urgent.id(), state.pickOrder(miner.id()).orElseThrow().id());
        assertEquals(farm.id(), state.pickOrder(farmer.id()).orElseThrow().id());
        state.setOrderStatus(kingdom.id(), urgent.id(), OrderStatus.CANCELLED);
        assertTrue(state.shouldReplan(miner.id()));
        assertEquals(old.id(), state.pickOrder(miner.id()).orElseThrow().id());
        assertFalse(state.shouldReplan(miner.id()));
        state.setOrderPriority(kingdom.id(), newer.id(), 4);
        assertTrue(state.shouldReplan(miner.id()), "a more urgent order makes the worker replan");
        var idle = state.citizensOf(kingdom.id()).stream().filter(c -> c.profession() == Citizen.Profession.NONE).findFirst().orElseThrow();
        assertTrue(state.pickOrder(idle.id()).isEmpty());
    }

    @Test
    void pausingAndCancellingReleaseWorkers() {
        stockpile();
        var miner = worker(Citizen.Profession.MINER);
        var o = order(OrderType.MINE, 3, 0);
        state.pickOrder(miner.id());
        assertEquals(1, state.workersOf(o.id()).size());
        state.setOrderStatus(kingdom.id(), o.id(), OrderStatus.PAUSED);
        assertTrue(state.workersOf(o.id()).isEmpty());
        assertTrue(state.pickOrder(miner.id()).isEmpty(), "paused orders take no workers");
        state.setOrderStatus(kingdom.id(), o.id(), OrderStatus.ACTIVE);
        assertEquals(o.id(), state.pickOrder(miner.id()).orElseThrow().id());
        state.setOrderStatus(kingdom.id(), o.id(), OrderStatus.CANCELLED);
        assertTrue(state.citizen(miner.id()).orElseThrow().order().isEmpty());
        assertRefused("kingdoms.error.order_closed", () -> state.setOrderStatus(kingdom.id(), o.id(), OrderStatus.ACTIVE));
    }

    @Test
    void changingProfessionDropsTheOrder() {
        stockpile();
        var miner = worker(Citizen.Profession.MINER);
        order(OrderType.MINE, 3, 0);
        state.pickOrder(miner.id());
        var farmer = state.assignProfession(kingdom.id(), miner.id(), Citizen.Profession.FARMER);
        assertTrue(farmer.order().isEmpty());
    }

    @Test
    void blockedReasonIsKeptUntilProgress() {
        stockpile();
        var build = state.createOrder(kingdom.id(), OrderType.BUILD, FIELD, 3, Optional.of("minecraft:cobblestone"), 0, RULES);
        assertTrue(state.markBlocked(build.id(), "missing_materials"));
        assertFalse(state.markBlocked(build.id(), "missing_materials"), "same reason is not reported twice");
        assertEquals(Optional.of("missing_materials"), state.order(build.id()).orElseThrow().blocked());
        state.recordProgress(build.id(), 2);
        assertTrue(state.order(build.id()).orElseThrow().blocked().isEmpty());
        assertEquals(2, state.order(build.id()).orElseThrow().progress());
        state.completeOrder(build.id());
        assertEquals(OrderStatus.DONE, state.order(build.id()).orElseThrow().status());
    }

    @Test
    void ordersSurviveCodecRoundTrip() {
        stockpile();
        var miner = worker(Citizen.Profession.MINER);
        var o = order(OrderType.MINE, 4, 7);
        state.pickOrder(miner.id());
        state.markBlocked(o.id(), "unreachable");
        state.setMaterialized(miner.id(), true);
        var json = KingdomState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var loaded = KingdomState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(state.order(o.id()), loaded.order(o.id()));
        assertEquals(state.citizen(miner.id()), loaded.citizen(miner.id()));
        assertEquals(kingdom.id(), loaded.kingdom(kingdom.id()).orElseThrow().id());
        assertTrue(loaded.kingdom(kingdom.id()).orElseThrow().stockpile().isPresent());
        assertTrue(loaded.validate().isEmpty());
    }

    @Test
    void deadCitizenLeavesKingdomAndDeletedKingdomTakesOrders() {
        stockpile();
        var miner = worker(Citizen.Profession.MINER);
        var o = order(OrderType.MINE, 3, 0);
        state.removeCitizen(miner.id());
        assertEquals(4, state.citizensOf(kingdom.id()).size());
        state.delete(kingdom.id());
        assertTrue(state.order(o.id()).isEmpty());
    }
}
