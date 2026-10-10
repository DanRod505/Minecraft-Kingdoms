package dev.kingdoms.entity;

import dev.kingdoms.core.KingdomsLog;
import dev.kingdoms.jobs.Job;
import dev.kingdoms.orders.Order;
import dev.kingdoms.world.Citizen;
import dev.kingdoms.world.Kingdom;
import dev.kingdoms.world.KingdomService;
import dev.kingdoms.world.KingdomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The worker's state machine (GDD 3.5): pick an order, walk to a free target, work, deliver to the
 * stockpile when full, repeat. Builders fetch material first. When nothing can be done the goal stops
 * for a while and the order shows why (GDD 3.6), so the NPC wanders instead of freezing.
 */
public final class WorkGoal extends Goal {
    public enum Phase { IDLE, GO_WORK, WORKING, GO_DEPOSIT, GO_FETCH }

    private enum Move { ARRIVED, MOVING, STUCK }

    private static final int CHECK_INTERVAL = 20;
    private static final int REPLAN_INTERVAL = 40;
    private static final int STUCK_TICKS = 100;
    private static final int WAIT_TICKS = 100;
    private static final int MAX_FAILURES = 10;
    private static final int MIN_FREE_SLOTS = 3;
    private static final double SPEED = 1.0;

    private final CitizenEntity npc;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private boolean running;
    private Phase phase = Phase.IDLE;
    private @Nullable UUID orderId;
    private @Nullable BlockPos target;
    private int timer;
    private int moveTicks;
    private double bestDistance;
    private boolean partialPath;
    private int failures;
    private long resumeAt;
    private int checkDelay;

    public WorkGoal(CitizenEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public Phase phase() {
        return running ? phase : Phase.IDLE;
    }

    public Optional<BlockPos> target() {
        return Optional.ofNullable(target);
    }

    @Override
    public boolean canUse() {
        // Mobs ask idle goals every other tick, offset by entity id, so count calls instead of ticks.
        if (--checkDelay > 0)
            return false;
        checkDelay = CHECK_INTERVAL / 2;
        if (!(npc.level() instanceof ServerLevel level) || level.getGameTime() < resumeAt)
            return false;
        var server = level.getServer();
        var state = KingdomService.state(server);
        var citizen = state.citizen(npc.getUUID()).orElse(null);
        if (citizen == null || citizen.profession() == Citizen.Profession.NONE)
            return hasItemsToDeliver(state, citizen);
        Optional<Order> order;
        if (state.shouldReplan(citizen.id())) {
            var previous = citizen.order();
            order = KingdomService.change(server, s -> s.pickOrder(citizen.id()));
            if (!order.map(Order::id).equals(previous))
                order.ifPresent(o -> KingdomsLog.LOGGER.info("Citizen {} takes order {} ({})", citizen.name(), o.shortId(), o.type().id()));
        } else {
            order = citizen.order().flatMap(state::order);
        }
        if (order.isEmpty())
            return hasItemsToDeliver(state, citizen);
        if (!order.get().id().equals(orderId))
            unreachable.clear();
        orderId = order.get().id();
        phase = Phase.GO_WORK;
        return true;
    }

    private boolean hasItemsToDeliver(KingdomState state, @Nullable Citizen citizen) {
        if (citizen == null || npc.getInventory().isEmpty() || state.kingdom(citizen.kingdom()).flatMap(Kingdom::stockpile).isEmpty())
            return false;
        orderId = null;
        phase = Phase.GO_DEPOSIT;
        return true;
    }

    @Override
    public void start() {
        running = true;
        target = null;
        resetMove();
    }

    @Override
    public boolean canContinueToUse() {
        return running;
    }

    @Override
    public void stop() {
        running = false;
        dropTarget();
        npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Called when the NPC leaves the world, so no reservation outlives it. */
    public void releaseAll() {
        Reservations.releaseWorker(npc.getUUID());
        target = null;
    }

    @Override
    public void tick() {
        var level = (ServerLevel) npc.level();
        var server = level.getServer();
        var state = KingdomService.state(server);
        var citizen = state.citizen(npc.getUUID()).orElse(null);
        if (citizen == null) {
            pause(0);
            return;
        }
        var kingdom = state.kingdom(citizen.kingdom()).orElse(null);
        if (kingdom == null) {
            pause(0);
            return;
        }
        Order order = null;
        if (orderId != null) {
            // Paused, cancelled, finished or taken away: stop working on it, but deliver what we carry.
            if (citizen.order().filter(orderId::equals).isEmpty() || state.order(orderId).isEmpty()) {
                dropTarget();
                orderId = null;
                if (npc.getInventory().isEmpty()) {
                    pause(0);
                    return;
                }
                phase = Phase.GO_DEPOSIT;
            } else if (npc.tickCount % REPLAN_INTERVAL == 0 && phase != Phase.WORKING && state.shouldReplan(citizen.id())) {
                pause(0);
                return;
            } else {
                order = state.order(orderId).get();
            }
        }
        switch (phase) {
            case GO_WORK -> goWork(level, kingdom, order);
            case WORKING -> working(level, order);
            case GO_DEPOSIT -> goDeposit(level, kingdom, order);
            case GO_FETCH -> goFetch(level, kingdom, order);
            case IDLE -> pause(0);
        }
    }

    private void goWork(ServerLevel level, Kingdom kingdom, @Nullable Order order) {
        if (order == null) {
            pause(0);
            return;
        }
        var job = Job.of(order.type());
        if (target == null) {
            if (job.collects() && Containers.emptySlots(npc.getInventory()) < MIN_FREE_SLOTS) {
                switchTo(Phase.GO_DEPOSIT);
                return;
            }
            target = findTarget(level, order, job);
            if (target == null) {
                noTarget(level, order, job);
                return;
            }
            var material = job.material(order);
            if (material.isPresent() && Containers.count(npc.getInventory(), material.get()) == 0) {
                dropTarget();
                switchTo(Phase.GO_FETCH);
                return;
            }
            resetMove();
        }
        if (!job.isValid(level, order, target)) {
            dropTarget();
            return;
        }
        switch (approach(target)) {
            case ARRIVED -> {
                switchTo(Phase.WORKING);
                timer = job.workTicks(level, target);
            }
            case STUCK -> {
                KingdomsLog.LOGGER.debug("Citizen {} cannot reach {} of order {}", npc.getUUID(), target.toShortString(), order.shortId());
                unreachable.add(target);
                dropTarget();
                failed(level, kingdom);
            }
            case MOVING -> {}
        }
    }

    private void working(ServerLevel level, @Nullable Order order) {
        if (order == null || target == null) {
            switchTo(Phase.GO_WORK);
            return;
        }
        npc.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (timer % 10 == 0)
            npc.swing(InteractionHand.MAIN_HAND);
        if (--timer > 0)
            return;
        var job = Job.of(order.type());
        int done = job.isValid(level, order, target) ? job.perform(level, order, npc, target) : 0;
        if (done == 0 && job.isValid(level, order, target)) {
            KingdomsLog.LOGGER.debug("Citizen {} could not work {} of order {}", npc.getUUID(), target.toShortString(), order.shortId());
            unreachable.add(target); // something stands in the way; try other targets first
        }
        if (done > 0) {
            failures = 0;
            KingdomService.change(level.getServer(), s -> {
                s.recordProgress(order.id(), done);
                return null;
            });
        }
        dropTarget();
        switchTo(Phase.GO_WORK);
    }

    private void goDeposit(ServerLevel level, Kingdom kingdom, @Nullable Order order) {
        if (npc.getInventory().isEmpty()) {
            afterDelivery(order);
            return;
        }
        var pos = stockpile(level, kingdom, order);
        if (pos == null)
            return;
        switch (approach(pos)) {
            case ARRIVED -> {
                var container = Containers.at(level, pos);
                if (container == null) {
                    block(level, order, "stockpile_missing");
                    pause(WAIT_TICKS);
                    return;
                }
                int left = Containers.deposit(npc.getInventory(), container);
                if (left > 0) {
                    block(level, order, "stockpile_full");
                    pause(WAIT_TICKS);
                    return;
                }
                afterDelivery(order);
            }
            case STUCK -> {
                KingdomsLog.LOGGER.debug("Citizen {} cannot reach the stockpile at {}", npc.getUUID(), pos.toShortString());
                block(level, order, "unreachable");
                failed(level, kingdom);
                pause(WAIT_TICKS);
            }
            case MOVING -> {}
        }
    }

    private void afterDelivery(@Nullable Order order) {
        if (order == null)
            pause(0);
        else
            switchTo(Phase.GO_WORK);
    }

    private void goFetch(ServerLevel level, Kingdom kingdom, @Nullable Order order) {
        if (order == null) {
            pause(0);
            return;
        }
        var material = Job.of(order.type()).material(order);
        if (material.isEmpty()) {
            block(level, order, "missing_materials");
            pause(WAIT_TICKS);
            return;
        }
        var pos = stockpile(level, kingdom, order);
        if (pos == null)
            return;
        switch (approach(pos)) {
            case ARRIVED -> {
                var container = Containers.at(level, pos);
                if (container == null) {
                    block(level, order, "stockpile_missing");
                    pause(WAIT_TICKS);
                    return;
                }
                var item = material.get();
                Containers.take(container, npc.getInventory(), item, item.getDefaultMaxStackSize());
                if (Containers.count(npc.getInventory(), item) == 0) {
                    block(level, order, "missing_materials");
                    pause(WAIT_TICKS);
                    return;
                }
                switchTo(Phase.GO_WORK);
            }
            case STUCK -> {
                KingdomsLog.LOGGER.debug("Citizen {} cannot reach the stockpile at {}", npc.getUUID(), pos.toShortString());
                block(level, order, "unreachable");
                failed(level, kingdom);
                pause(WAIT_TICKS);
            }
            case MOVING -> {}
        }
    }

    /** Nothing left to take in the area: deliver, finish the order, or explain why it waits. */
    private void noTarget(ServerLevel level, Order order, Job job) {
        if (job.collects() && !npc.getInventory().isEmpty() || !job.collects() && hasLeftovers(order, job)) {
            switchTo(Phase.GO_DEPOSIT);
            return;
        }
        if (!unreachable.isEmpty()) {
            block(level, order, "unreachable");
            unreachable.clear();
            pause(WAIT_TICKS * 3);
            return;
        }
        if (!order.type().finishes()) {
            block(level, order, "waiting_crops");
            pause(WAIT_TICKS);
            return;
        }
        if (Reservations.othersWorking(order.id(), npc.getUUID())) {
            pause(WAIT_TICKS);
            return;
        }
        KingdomService.change(level.getServer(), s -> {
            s.completeOrder(order.id());
            return null;
        });
        KingdomsLog.LOGGER.info("Order {} ({}) done, {} units", order.shortId(), order.type().id(), order.progress());
        pause(0);
    }

    private boolean hasLeftovers(Order order, Job job) {
        return !npc.getInventory().isEmpty() && job.material(order).isPresent();
    }

    private @Nullable BlockPos findTarget(ServerLevel level, Order order, Job job) {
        var me = npc.getUUID();
        Comparator<BlockPos> byDistance = Comparator.comparingDouble(p -> npc.distanceToSqr(Vec3.atCenterOf(p)));
        Comparator<BlockPos> preference = switch (job.layer()) {
            case TOP -> Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparing(byDistance);
            case BOTTOM -> Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparing(byDistance);
            case ANY -> byDistance;
        };
        return job.candidates(level, order).stream()
            .filter(p -> !unreachable.contains(p) && !Reservations.heldByOther(order.id(), p, me))
            .sorted(preference)
            .filter(p -> Reservations.reserve(order.id(), p, me))
            .findFirst().orElse(null);
    }

    private @Nullable BlockPos stockpile(ServerLevel level, Kingdom kingdom, @Nullable Order order) {
        var location = kingdom.stockpile().orElse(null);
        if (location == null) {
            block(level, order, "no_stockpile");
            pause(WAIT_TICKS);
            return null;
        }
        var pos = new BlockPos(location.x(), location.y(), location.z());
        if (!location.dimension().equals(KingdomService.dimension(level)) || !level.isLoaded(pos) || Containers.at(level, pos) == null) {
            block(level, order, "stockpile_missing");
            pause(WAIT_TICKS);
            return null;
        }
        return pos;
    }

    /** Walks towards a block. Workers reach a bit further up than down, so trees can be felled from the ground. */
    private Move approach(BlockPos pos) {
        double dx = pos.getX() + 0.5 - npc.getX();
        double dz = pos.getZ() + 0.5 - npc.getZ();
        double dy = pos.getY() - npc.getY();
        double horizontal = dx * dx + dz * dz;
        if (horizontal <= 6.25 && dy >= -3 && dy <= 5) {
            npc.getNavigation().stop();
            return Move.ARRIVED;
        }
        var navigation = npc.getNavigation();
        if (navigation.isDone()) {
            if (moveTicks > 0 && partialPath)
                return Move.STUCK;
            var path = navigation.createPath(pos, 1);
            if (path == null) // no path can be planned in mid-air (just spawned, falling): wait for the ground
                return npc.onGround() ? Move.STUCK : Move.MOVING;
            partialPath = !path.canReach();
            navigation.moveTo(path, SPEED);
        }
        if (++moveTicks % STUCK_TICKS == 0) {
            double distance = Math.sqrt(horizontal + dy * dy);
            if (distance > bestDistance - 1)
                return Move.STUCK;
            bestDistance = distance;
        }
        return Move.MOVING;
    }

    /** Too many failures in a row: the NPC is probably trapped, so it walks home (GDD 3.6 recovery). */
    private void failed(ServerLevel level, Kingdom kingdom) {
        if (++failures < MAX_FAILURES)
            return;
        failures = 0;
        var capital = KingdomService.state(level.getServer()).settlement(kingdom.capital()).orElse(null);
        if (capital == null || !capital.dimension().equals(KingdomService.dimension(level)))
            return;
        var ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(capital.x(), capital.y(), capital.z()));
        npc.getNavigation().stop();
        npc.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        KingdomsLog.LOGGER.info("Citizen {} was stuck and returned to the capital", npc.getUUID());
    }

    private void block(ServerLevel level, @Nullable Order order, String reason) {
        if (order == null)
            return;
        boolean changed = KingdomService.change(level.getServer(), s -> s.markBlocked(order.id(), reason));
        if (changed)
            KingdomsLog.LOGGER.info("Order {} ({}) blocked: {}", order.shortId(), order.type().id(), reason);
    }

    private void switchTo(Phase next) {
        phase = next;
        resetMove();
    }

    private void resetMove() {
        moveTicks = 0;
        bestDistance = Double.MAX_VALUE;
        partialPath = false;
        npc.getNavigation().stop();
    }

    private void dropTarget() {
        if (target != null && orderId != null)
            Reservations.release(orderId, target, npc.getUUID());
        target = null;
    }

    private void pause(int ticks) {
        resumeAt = npc.level().getGameTime() + ticks;
        running = false;
    }
}
