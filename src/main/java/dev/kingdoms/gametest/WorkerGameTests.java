package dev.kingdoms.gametest;

import dev.kingdoms.core.KingdomsConfig;
import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.entity.Containers;
import dev.kingdoms.entity.KingdomsEntities;
import dev.kingdoms.orders.Area;
import dev.kingdoms.orders.Order;
import dev.kingdoms.orders.OrderStatus;
import dev.kingdoms.orders.OrderType;
import dev.kingdoms.world.BlockLocation;
import dev.kingdoms.world.Citizen;
import dev.kingdoms.world.Kingdom;
import dev.kingdoms.world.KingdomService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.kingdoms.gametest.KingdomsGameTests.TEST_FUNCTIONS;

/**
 * Phase 2 acceptance: real NPCs doing each job in a 12x6x12 test area with a stone floor. Every test
 * counts items at both ends, so a job that loses or duplicates anything fails (GDD 12.4). Each test
 * has its own test environment, which puts it in its own batch: kingdoms claim whole chunks, so two
 * kingdoms built side by side at the same time would collide.
 */
public final class WorkerGameTests {
    public static final RegistryObject<Consumer<GameTestHelper>> FARMER = TEST_FUNCTIONS.register("farmer_harvests_and_replants", () -> WorkerGameTests::farmer);
    public static final RegistryObject<Consumer<GameTestHelper>> LUMBERJACK = TEST_FUNCTIONS.register("lumberjack_fells_tree", () -> WorkerGameTests::lumberjack);
    public static final RegistryObject<Consumer<GameTestHelper>> MINER = TEST_FUNCTIONS.register("miner_digs_area", () -> WorkerGameTests::miner);
    public static final RegistryObject<Consumer<GameTestHelper>> CARRIER = TEST_FUNCTIONS.register("carrier_hauls_items", () -> WorkerGameTests::carrier);
    public static final RegistryObject<Consumer<GameTestHelper>> BUILDER = TEST_FUNCTIONS.register("builder_waits_for_material_then_builds", () -> WorkerGameTests::builder);
    public static final RegistryObject<Consumer<GameTestHelper>> PAUSE = TEST_FUNCTIONS.register("pause_stops_and_resume_continues", () -> WorkerGameTests::pause);
    public static final RegistryObject<Consumer<GameTestHelper>> MATERIALIZE = TEST_FUNCTIONS.register("citizens_become_npcs", () -> WorkerGameTests::materialize);

    private static final int SIZE = 12;
    private static final BlockPos STOCKPILE = new BlockPos(1, 1, 1);
    private static final BlockPos WORKER_START = new BlockPos(3, 1, 3);

    private WorkerGameTests() {}

    public static void init() {}

    /** A kingdom with a stockpile chest and one NPC worker of the given profession. */
    private record Fixture(GameTestHelper helper, Kingdom kingdom, CitizenEntity worker) {
        ServerLevel level() {
            return helper.getLevel();
        }

        Container stockpile() {
            return Containers.at(level(), helper.absolutePos(STOCKPILE));
        }

        int stored(Item item) {
            return Containers.count(stockpile(), item);
        }

        Order order(OrderType type, BlockPos from, BlockPos to, Optional<String> material) {
            var a = helper.absolutePos(from);
            var b = helper.absolutePos(to);
            var area = Area.of(KingdomService.dimension(level()), a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
            return KingdomService.change(level().getServer(), s -> s.createOrder(kingdom.id(), type, area, Order.DEFAULT_PRIORITY, material,
                level().getGameTime(), KingdomsConfig.rules()));
        }

        Order current(Order order) {
            return KingdomService.state(level().getServer()).order(order.id()).orElseThrow();
        }

        void cleanUp() {
            var server = level().getServer();
            var citizens = KingdomService.state(server).citizensOf(kingdom.id());
            KingdomService.change(server, s -> {
                if (s.kingdom(kingdom.id()).isPresent())
                    s.delete(kingdom.id());
                return null;
            });
            for (var c : citizens) {
                var npc = level().getEntity(c.id());
                if (npc != null)
                    npc.discard();
            }
        }
    }

    private static Fixture setUp(GameTestHelper helper, Citizen.Profession profession) {
        for (int x = 0; x < SIZE; x++)
            for (int z = 0; z < SIZE; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        helper.setBlock(STOCKPILE, Blocks.CHEST);

        var level = helper.getLevel();
        var server = level.getServer();
        var capital = helper.absolutePos(STOCKPILE);
        var dimension = KingdomService.dimension(level);
        var ruler = UUID.randomUUID();
        var kingdom = KingdomService.change(server, s -> s.found(ruler, "Test " + ruler.toString().substring(0, 8), dimension,
            capital.getX(), capital.getY(), capital.getZ(), level.getGameTime(), KingdomsConfig.rules()));
        KingdomService.change(server, s -> {
            s.setStockpile(kingdom.id(), new BlockLocation(dimension, capital.getX(), capital.getY(), capital.getZ()));
            return null;
        });
        var citizen = KingdomService.state(server).citizensOf(kingdom.id()).getFirst();
        KingdomService.change(server, s -> s.assignProfession(kingdom.id(), citizen.id(), profession));
        // The other founders stay records only, so the worker is alone in the test area.
        for (var c : KingdomService.state(server).citizensOf(kingdom.id()))
            KingdomService.change(server, s -> {
                s.setMaterialized(c.id(), true);
                return null;
            });
        var fixture = new Fixture(helper, kingdom, spawnWorker(helper, citizen.id(), WORKER_START));
        // Pass or fail, the kingdom goes away: kingdoms claim whole chunks and the next tests are near.
        helper.addCleanup(passed -> fixture.cleanUp());
        return fixture;
    }

    private static CitizenEntity spawnWorker(GameTestHelper helper, UUID id, BlockPos pos) {
        var level = helper.getLevel();
        var npc = KingdomsEntities.CITIZEN.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        helper.assertTrue(npc != null, "citizen entity created");
        npc.setUUID(id);
        var at = helper.absolutePos(pos);
        npc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        helper.assertTrue(level.addFreshEntity(npc), "citizen entity added");
        return npc;
    }

    // ---- one test per profession ----

    /** Three mature wheat: harvested, replanted, and exactly three wheat delivered. */
    private static void farmer(GameTestHelper helper) {
        var f = setUp(helper, Citizen.Profession.FARMER);
        var crop = (CropBlock) Blocks.WHEAT;
        for (int x = 5; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 0, 6), Blocks.FARMLAND);
            helper.setBlock(new BlockPos(x, 1, 6), crop.getStateForAge(crop.getMaxAge()));
        }
        var order = f.order(OrderType.FARM, new BlockPos(4, 1, 5), new BlockPos(8, 1, 7), Optional.empty());
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertValueEqual(f.stored(Items.WHEAT), 3, "wheat in stockpile");
                helper.assertTrue(f.worker().getInventory().isEmpty(), "worker delivered everything");
                for (int x = 5; x <= 7; x++)
                    helper.assertBlockState(new BlockPos(x, 1, 6), s -> s.is(Blocks.WHEAT) && !crop.isMaxAge(s), s -> net.minecraft.network.chat.Component.literal("not replanted: " + s));
            })
            .thenExecute(() -> {
                var now = f.current(order);
                helper.assertValueEqual(now.status(), OrderStatus.ACTIVE, "farming never finishes");
                helper.assertValueEqual(now.progress(), 3, "progress");
            })
            .thenSucceed();
    }

    /** A four-log trunk: felled completely, four logs delivered, order done. */
    private static void lumberjack(GameTestHelper helper) {
        var f = setUp(helper, Citizen.Profession.LUMBERJACK);
        for (int y = 1; y <= 4; y++)
            helper.setBlock(new BlockPos(7, y, 7), Blocks.OAK_LOG);
        var order = f.order(OrderType.CHOP, new BlockPos(5, 1, 5), new BlockPos(9, 5, 9), Optional.empty());
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertValueEqual(f.current(order).status(), OrderStatus.DONE, "order status");
                helper.assertValueEqual(f.stored(Items.OAK_LOG), 4, "logs in stockpile");
                for (int y = 1; y <= 4; y++)
                    helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(7, y, 7));
            })
            .thenSucceed();
    }

    /**
     * A 3x2x3 mound of cobblestone over dirt, with obsidian (needs a better pickaxe) and a chest with
     * contents (never broken): 9 + 9 blocks delivered, the protected blocks stay, order done.
     */
    private static void miner(GameTestHelper helper) {
        var f = setUp(helper, Citizen.Profession.MINER);
        for (int x = 6; x <= 8; x++)
            for (int z = 6; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.COBBLESTONE);
            }
        var obsidian = new BlockPos(10, 1, 7);
        var chest = new BlockPos(10, 1, 9);
        helper.setBlock(obsidian, Blocks.OBSIDIAN);
        helper.setBlock(chest, Blocks.CHEST);
        helper.<ChestBlockEntity>getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 2));
        var order = f.order(OrderType.MINE, new BlockPos(6, 1, 6), new BlockPos(10, 2, 9), Optional.empty());
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertValueEqual(f.current(order).status(), OrderStatus.DONE, "order status");
                helper.assertValueEqual(f.stored(Items.COBBLESTONE), 9, "cobblestone in stockpile");
                helper.assertValueEqual(f.stored(Items.DIRT), 9, "dirt in stockpile");
            })
            .thenExecute(() -> {
                helper.assertBlockPresent(Blocks.OBSIDIAN, obsidian);
                helper.assertBlockPresent(Blocks.CHEST, chest);
                helper.assertContainerContains(chest, Items.DIAMOND);
                helper.assertValueEqual(f.current(order).progress(), 18, "progress");
            })
            .thenSucceed();
    }

    /** A chest and loose items in the area end up in the stockpile, nothing more and nothing less. */
    private static void carrier(GameTestHelper helper) {
        var f = setUp(helper, Citizen.Profession.CARRIER);
        var source = new BlockPos(9, 1, 9);
        helper.setBlock(source, Blocks.CHEST);
        var chest = helper.<ChestBlockEntity>getBlockEntity(source, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        chest.setItem(5, new ItemStack(Items.APPLE, 5));
        var drop = helper.absolutePos(new BlockPos(6, 1, 9));
        var loose = new ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY() + 0.1, drop.getZ() + 0.5, new ItemStack(Items.BREAD, 3));
        loose.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(loose);
        var order = f.order(OrderType.HAUL, new BlockPos(5, 1, 5), new BlockPos(10, 3, 10), Optional.empty());
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertValueEqual(f.current(order).status(), OrderStatus.DONE, "order status");
                helper.assertValueEqual(f.stored(Items.IRON_INGOT), 10, "iron in stockpile");
                helper.assertValueEqual(f.stored(Items.APPLE), 5, "apples in stockpile");
                helper.assertValueEqual(f.stored(Items.BREAD), 3, "bread in stockpile");
            })
            .thenExecute(() -> {
                helper.assertTrue(!Containers.hasAny(chest), "source chest emptied");
                helper.assertTrue(!loose.isAlive(), "loose item picked up");
            })
            .thenSucceed();
    }

    /**
     * Without material the order says so; with 20 cobblestone in the stockpile the 3x1x3 floor is
     * built, and the 11 left over go back to the stockpile.
     */
    private static void builder(GameTestHelper helper) {
        var f = setUp(helper, Citizen.Profession.BUILDER);
        var order = f.order(OrderType.BUILD, new BlockPos(6, 1, 6), new BlockPos(8, 1, 8), Optional.of("minecraft:cobblestone"));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertValueEqual(f.current(order).blocked(), Optional.of("missing_materials"), "blocked reason"))
            .thenExecute(() -> f.stockpile().setItem(0, new ItemStack(Items.COBBLESTONE, 20)))
            .thenWaitUntil(() -> {
                helper.assertValueEqual(f.current(order).status(), OrderStatus.DONE, "order status");
                for (int x = 6; x <= 8; x++)
                    for (int z = 6; z <= 8; z++)
                        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(x, 1, z));
                helper.assertValueEqual(f.stored(Items.COBBLESTONE), 11, "cobblestone left in stockpile");
                helper.assertTrue(f.worker().getInventory().isEmpty(), "worker returned leftovers");
            })
            .thenSucceed();
    }

    /** Pausing frees the worker and stops the digging; resuming brings the worker back. */
    private static void pause(GameTestHelper helper) {
        var f = setUp(helper, Citizen.Profession.MINER);
        for (int x = 5; x <= 10; x++)
            for (int z = 5; z <= 10; z++)
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
        var order = f.order(OrderType.MINE, new BlockPos(5, 1, 5), new BlockPos(10, 1, 10), Optional.empty());
        int[] progress = new int[1];
        var server = helper.getLevel().getServer();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(f.current(order).progress() >= 2, "worker started digging"))
            .thenExecute(() -> {
                KingdomService.change(server, s -> s.setOrderStatus(f.kingdom().id(), order.id(), OrderStatus.PAUSED));
                helper.assertTrue(KingdomService.state(server).workersOf(order.id()).isEmpty(), "worker released at once");
            })
            .thenExecuteAfter(40, () -> progress[0] = f.current(order).progress())
            .thenExecuteAfter(100, () -> {
                helper.assertValueEqual(f.current(order).progress(), progress[0], "no digging while paused");
                helper.assertTrue(KingdomService.state(server).citizen(f.worker().getUUID()).orElseThrow().order().isEmpty(), "worker idle");
                KingdomService.change(server, s -> s.setOrderStatus(f.kingdom().id(), order.id(), OrderStatus.ACTIVE));
            })
            .thenWaitUntil(() -> helper.assertTrue(f.current(order).progress() > progress[0], "digging resumed"))
            .thenSucceed();
    }

    /** Founding spawns one NPC per citizen with the citizen's id; a killed NPC leaves the kingdom. */
    private static void materialize(GameTestHelper helper) {
        for (int x = 0; x < SIZE; x++)
            for (int z = 0; z < SIZE; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var level = helper.getLevel();
        var server = level.getServer();
        var ruler = UUID.randomUUID();
        var kingdom = KingdomService.found(server, ruler, "Test " + ruler.toString().substring(0, 8), level, helper.absolutePos(new BlockPos(6, 1, 6)));
        var citizens = KingdomService.state(server).citizensOf(kingdom.id());
        helper.addCleanup(passed -> KingdomService.change(server, s -> {
            if (s.kingdom(kingdom.id()).isPresent())
                s.delete(kingdom.id());
            return null;
        }));
        helper.startSequence()
            .thenWaitUntil(() -> {
                for (var c : citizens) {
                    helper.assertTrue(level.getEntity(c.id()) instanceof CitizenEntity, "NPC for " + c.name());
                    helper.assertTrue(KingdomService.state(server).citizen(c.id()).orElseThrow().materialized(), "materialized " + c.name());
                }
            })
            .thenExecute(() -> {
                var victim = citizens.getFirst();
                ((CitizenEntity) level.getEntity(victim.id())).kill(level);
                helper.assertTrue(KingdomService.state(server).citizen(victim.id()).isEmpty(), "dead citizen removed");
                helper.assertValueEqual(KingdomService.state(server).citizensOf(kingdom.id()).size(), citizens.size() - 1, "citizens left");
                KingdomService.change(server, s -> {
                    s.delete(kingdom.id());
                    return null;
                });
            })
            .thenExecuteAfter(110, () -> {
                for (var c : citizens)
                    helper.assertTrue(level.getEntity(c.id()) == null, "NPC of deleted kingdom removed: " + c.name());
            })
            .thenSucceed();
    }
}
