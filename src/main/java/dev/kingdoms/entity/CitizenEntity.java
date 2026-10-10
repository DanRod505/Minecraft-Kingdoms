package dev.kingdoms.entity;

import dev.kingdoms.core.KingdomsLog;
import dev.kingdoms.world.Citizen;
import dev.kingdoms.world.KingdomService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/**
 * The physical NPC of a {@link Citizen}. Its entity UUID is the citizen id, so the save record and the NPC
 * can always find each other. Carried items live only here (saved with the entity), never duplicated in
 * the kingdom save.
 */
public final class CitizenEntity extends PathfinderMob implements InventoryCarrier {
    public static final int INVENTORY_SIZE = 9;
    private static final int CHECK_INTERVAL = 100;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    /** Created in registerGoals, which the Mob constructor calls before this class's fields are set. */
    private WorkGoal work;
    private Citizen.Profession shownProfession;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        work = new WorkGoal(this);
        goalSelector.addGoal(1, work);
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    /** Puts an item into the inventory; whatever does not fit falls at the NPC's feet, never lost. */
    public void keep(ItemStack stack) {
        var rest = inventory.addItem(stack);
        if (!rest.isEmpty() && level() instanceof ServerLevel server)
            spawnAtLocation(server, rest);
    }

    /** Uses one item from the inventory. Returns false when there is none. */
    public boolean consume(Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                inventory.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }

    public WorkGoal work() {
        return work;
    }

    /** The save record behind this NPC, if it still exists. */
    public Optional<Citizen> citizen() {
        if (!(level() instanceof ServerLevel server))
            return Optional.empty();
        return KingdomService.state(server.getServer()).citizen(getUUID());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel && tickCount % CHECK_INTERVAL == 0)
            refresh();
    }

    /** Removes NPCs whose citizen is gone (kingdom deleted) and keeps name and tool in sync with the record. */
    private void refresh() {
        var citizen = citizen();
        if (citizen.isEmpty()) {
            KingdomsLog.LOGGER.info("Removing NPC {} without a citizen record", getUUID());
            discard();
            return;
        }
        var profession = citizen.get().profession();
        if (profession != shownProfession) {
            shownProfession = profession;
            setCustomName(Component.literal(citizen.get().name() + " ")
                .append(Component.translatable("kingdoms.npc.profession", Component.translatable("kingdoms.profession." + profession.id()))));
            setCustomNameVisible(true);
            setItemSlot(EquipmentSlot.MAINHAND, toolFor(profession));
        }
    }

    /**
     * Phase 2 tools are virtual: they set which drops a block gives and what the NPC holds, and are never
     * dropped. Real tools come with the economy (Phase 4).
     */
    public static ItemStack toolFor(Citizen.Profession profession) {
        return switch (profession) {
            case MINER -> new ItemStack(Items.STONE_PICKAXE);
            case LUMBERJACK -> new ItemStack(Items.STONE_AXE);
            case FARMER -> new ItemStack(Items.STONE_HOE);
            case BUILDER, CARRIER, NONE -> ItemStack.EMPTY;
        };
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            var name = citizen().map(Citizen::name).orElse("?");
            KingdomService.change(server.getServer(), s -> {
                s.removeCitizen(getUUID());
                return null;
            });
            KingdomsLog.LOGGER.info("Citizen {} ({}) died: {}", name, getUUID(), source.getMsgId());
        }
    }

    /** Only carried items drop; the virtual tool never does. */
    @Override
    protected void dropEquipment(ServerLevel level) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty())
                spawnAtLocation(level, stack);
        }
    }

    /**
     * Reservations never outlive the NPC. An NPC removed without dying (fell out of the world, removed
     * by a command) while its citizen still exists is marked to appear again at its settlement.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (work != null)
            work.releaseAll();
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel server && citizen().isPresent()) {
            KingdomService.change(server.getServer(), s -> {
                s.setMaterialized(getUUID(), false);
                return null;
            });
            KingdomsLog.LOGGER.info("NPC of citizen {} was removed; it will appear again at its settlement", getUUID());
        }
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        readInventoryFromTag(input);
    }
}
