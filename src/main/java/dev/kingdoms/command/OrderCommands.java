package dev.kingdoms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kingdoms.core.KingdomsConfig;
import dev.kingdoms.core.KingdomsLog;
import dev.kingdoms.entity.CitizenEntity;
import dev.kingdoms.entity.Containers;
import dev.kingdoms.item.Selections;
import dev.kingdoms.jobs.BuildJob;
import dev.kingdoms.orders.Order;
import dev.kingdoms.orders.OrderStatus;
import dev.kingdoms.orders.OrderType;
import dev.kingdoms.world.BlockLocation;
import dev.kingdoms.world.Citizen;
import dev.kingdoms.world.Kingdom;
import dev.kingdoms.world.KingdomException;
import dev.kingdoms.world.KingdomService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;
import java.util.Optional;

/**
 * Phase 2 commands under {@code /kingdom}: orders, professions, stockpile and diagnostics (GDD 3.2–3.6).
 * Areas come from the Royal Scepter. Lists carry clickable actions so orders can be managed from chat.
 */
public final class OrderCommands {
    private static final double STOCKPILE_REACH = 5.0;

    private static final SimpleCommandExceptionType NOT_RULER = new SimpleCommandExceptionType(Component.translatable("kingdoms.error.not_ruler"));

    private static final SuggestionProvider<CommandSourceStack> CITIZENS = (ctx, builder) -> {
        var player = ctx.getSource().getPlayer();
        var state = KingdomService.state(ctx.getSource().getServer());
        var kingdom = player == null ? Optional.<Kingdom>empty() : state.kingdomOf(player.getUUID());
        return SharedSuggestionProvider.suggest(kingdom.map(k -> state.citizensOf(k.id()).stream()
            .map(c -> c.name().contains(" ") ? "\"" + c.name() + "\"" : c.name())).orElseGet(java.util.stream.Stream::empty), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> PROFESSIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(Arrays.stream(Citizen.Profession.values()).map(Citizen.Profession::id), builder);
    private static final SuggestionProvider<CommandSourceStack> OPEN_ORDERS = (ctx, builder) -> {
        var player = ctx.getSource().getPlayer();
        var state = KingdomService.state(ctx.getSource().getServer());
        var kingdom = player == null ? Optional.<Kingdom>empty() : state.kingdomOf(player.getUUID());
        return SharedSuggestionProvider.suggest(kingdom.map(k -> state.ordersOf(k.id()).stream()
            .filter(o -> o.status().open()).map(Order::shortId)).orElseGet(java.util.stream.Stream::empty), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> BLOCKS = (ctx, builder) ->
        SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder);

    private OrderCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var order = Commands.literal("order");
        for (var type : OrderType.values()) {
            if (type == OrderType.BUILD)
                continue;
            order.then(Commands.literal(type.id())
                .executes(ctx -> create(ctx, type, Optional.empty(), Order.DEFAULT_PRIORITY))
                .then(priority().executes(ctx -> create(ctx, type, Optional.empty(), IntegerArgumentType.getInteger(ctx, "priority")))));
        }
        order.then(Commands.literal(OrderType.BUILD.id())
            .then(Commands.argument("material", IdentifierArgument.id()).suggests(BLOCKS)
                .executes(ctx -> create(ctx, OrderType.BUILD, material(ctx), Order.DEFAULT_PRIORITY))
                .then(priority().executes(ctx -> create(ctx, OrderType.BUILD, material(ctx), IntegerArgumentType.getInteger(ctx, "priority"))))));
        order.then(status("pause", OrderStatus.PAUSED))
            .then(status("resume", OrderStatus.ACTIVE))
            .then(status("cancel", OrderStatus.CANCELLED))
            .then(Commands.literal("priority")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(OPEN_ORDERS)
                    .then(priority().executes(OrderCommands::setPriority))));

        dispatcher.register(Commands.literal("kingdom")
            .then(order)
            .then(Commands.literal("orders").executes(OrderCommands::orders))
            .then(Commands.literal("select")
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                    .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(OrderCommands::select))))
            .then(Commands.literal("assign")
                .then(Commands.argument("citizen", StringArgumentType.string()).suggests(CITIZENS)
                    .then(Commands.argument("profession", StringArgumentType.word()).suggests(PROFESSIONS).executes(OrderCommands::assign))))
            .then(Commands.literal("citizens").executes(OrderCommands::citizens))
            .then(Commands.literal("stockpile").executes(OrderCommands::stockpileLookedAt)
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> stockpile(ctx, BlockPosArgument.getBlockPos(ctx, "pos"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> priority() {
        return Commands.argument("priority", IntegerArgumentType.integer(Order.MIN_PRIORITY, Order.MAX_PRIORITY));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> status(String literal, OrderStatus status) {
        return Commands.literal(literal)
            .then(Commands.argument("id", StringArgumentType.word()).suggests(OPEN_ORDERS).executes(ctx -> setStatus(ctx, status)));
    }

    private static Optional<String> material(CommandContext<CommandSourceStack> ctx) {
        return Optional.of(IdentifierArgument.getId(ctx, "material").toString());
    }

    // ---- orders ----

    private static int create(CommandContext<CommandSourceStack> ctx, OrderType type, Optional<String> material, int priority) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var kingdom = own(ctx);
        var area = Selections.area(player.getUUID());
        if (area.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("kingdoms.error.no_selection"));
            return 0;
        }
        return run(ctx, () -> {
            if (type == OrderType.BUILD && BuildJob.block(material).isEmpty())
                throw new KingdomException("kingdoms.error.bad_material", material.orElse("?"));
            var server = ctx.getSource().getServer();
            var created = KingdomService.change(server, s -> s.createOrder(kingdom.id(), type, area.get(), priority, material,
                ctx.getSource().getLevel().getGameTime(), KingdomsConfig.rules()));
            KingdomsLog.LOGGER.info("Order {} ({}) created by {} over {} blocks, priority {}", created.shortId(), type.id(),
                player.getGameProfile().name(), area.get().volume(), priority);
            Selections.clear(player.getUUID());
            ok(ctx, Component.translatable("kingdoms.message.order_created", typeName(type), created.shortId(), area.get().volume()));
            if (KingdomService.state(server).citizensOf(kingdom.id()).stream().noneMatch(c -> c.profession() == type.profession()))
                ctx.getSource().sendSystemMessage(Component.translatable("kingdoms.message.no_workers",
                    Component.translatable("kingdoms.profession." + type.profession().id())).withStyle(ChatFormatting.YELLOW));
        });
    }

    private static int setStatus(CommandContext<CommandSourceStack> ctx, OrderStatus status) throws CommandSyntaxException {
        var kingdom = own(ctx);
        var order = order(ctx, kingdom);
        if (order.isEmpty())
            return 0;
        return run(ctx, () -> {
            var updated = KingdomService.change(ctx.getSource().getServer(), s -> s.setOrderStatus(kingdom.id(), order.get().id(), status));
            KingdomsLog.LOGGER.info("Order {} is now {}", updated.shortId(), status.id());
            ok(ctx, Component.translatable("kingdoms.message.order_status", updated.shortId(), statusName(status)));
        });
    }

    private static int setPriority(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = own(ctx);
        var order = order(ctx, kingdom);
        if (order.isEmpty())
            return 0;
        int priority = IntegerArgumentType.getInteger(ctx, "priority");
        return run(ctx, () -> {
            var updated = KingdomService.change(ctx.getSource().getServer(), s -> s.setOrderPriority(kingdom.id(), order.get().id(), priority));
            ok(ctx, Component.translatable("kingdoms.message.order_priority", updated.shortId(), priority));
        });
    }

    private static int orders(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = own(ctx);
        var source = ctx.getSource();
        var state = KingdomService.state(source.getServer());
        var list = state.ordersOf(kingdom.id());
        source.sendSystemMessage(Component.translatable("kingdoms.message.orders_header", list.size()).withStyle(ChatFormatting.GOLD));
        var stockpile = kingdom.stockpile().map(s -> Component.translatable("kingdoms.message.stockpile_at", s.x(), s.y(), s.z()))
            .orElseGet(() -> Component.translatable("kingdoms.blocked.no_stockpile"));
        source.sendSystemMessage(stockpile.withStyle(ChatFormatting.GRAY));
        for (var o : list) {
            var a = o.area();
            MutableComponent line = Component.literal("[" + o.shortId() + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(typeName(o.type()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" P" + o.priority() + " ").withStyle(ChatFormatting.AQUA))
                .append(statusName(o.status()).withStyle(o.status() == OrderStatus.ACTIVE ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.translatable("kingdoms.message.order_details", state.workersOf(o.id()).size(), o.progress(),
                    a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ()).withStyle(ChatFormatting.GRAY));
            o.material().ifPresent(m -> line.append(Component.literal(" " + m).withStyle(ChatFormatting.GRAY)));
            if (o.status() == OrderStatus.ACTIVE)
                o.blocked().ifPresent(reason -> line.append(" ").append(Component.translatable("kingdoms.blocked." + reason).withStyle(ChatFormatting.RED)));
            if (o.status().open()) {
                line.append(" ");
                if (o.status() == OrderStatus.ACTIVE)
                    line.append(button("kingdoms.button.pause", "/kingdom order pause " + o.shortId(), ChatFormatting.YELLOW));
                else
                    line.append(button("kingdoms.button.resume", "/kingdom order resume " + o.shortId(), ChatFormatting.GREEN));
                line.append(" ").append(button("kingdoms.button.cancel", "/kingdom order cancel " + o.shortId(), ChatFormatting.RED));
            }
            source.sendSystemMessage(line);
        }
        return list.size();
    }

    /** Typed alternative to the scepter, for exact areas. */
    private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var from = BlockPosArgument.getBlockPos(ctx, "from");
        var to = BlockPosArgument.getBlockPos(ctx, "to");
        var dimension = KingdomService.dimension(player.level());
        Selections.set(player.getUUID(), dimension, from, false);
        Selections.set(player.getUUID(), dimension, to, true);
        ok(ctx, Component.translatable("kingdoms.scepter.area", Selections.area(player.getUUID()).orElseThrow().volume()));
        return 1;
    }

    // ---- citizens ----

    private static int assign(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = own(ctx);
        var name = StringArgumentType.getString(ctx, "citizen");
        var server = ctx.getSource().getServer();
        var citizen = KingdomService.state(server).citizenNamed(kingdom.id(), name);
        if (citizen.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("kingdoms.error.unknown_citizen", name));
            return 0;
        }
        return run(ctx, () -> {
            var profession = parseProfession(StringArgumentType.getString(ctx, "profession"));
            var updated = KingdomService.change(server, s -> s.assignProfession(kingdom.id(), citizen.get().id(), profession));
            KingdomsLog.LOGGER.info("Citizen {} is now {}", updated.name(), profession.id());
            ok(ctx, Component.translatable("kingdoms.message.assigned", updated.name(), Component.translatable("kingdoms.profession." + profession.id())));
        });
    }

    /** Diagnostics: every citizen with profession, order and what its NPC is doing right now (GDD 3.6). */
    private static int citizens(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = own(ctx);
        var source = ctx.getSource();
        var state = KingdomService.state(source.getServer());
        var list = state.citizensOf(kingdom.id());
        source.sendSystemMessage(Component.translatable("kingdoms.message.citizens_header", kingdom.name(), list.size()).withStyle(ChatFormatting.GOLD));
        for (var c : list) {
            var npc = npc(source.getServer(), c);
            Component activity = npc.map(n -> (Component) Component.translatable("kingdoms.phase." + n.work().phase().name().toLowerCase(java.util.Locale.ROOT)))
                .orElseGet(() -> Component.translatable(c.materialized() ? "kingdoms.phase.away" : "kingdoms.phase.unborn"));
            var order = c.order().flatMap(state::order).map(o -> Component.literal(" [" + o.shortId() + "] ").append(typeName(o.type())).append(" -"))
                .orElseGet(() -> Component.literal(" -"));
            MutableComponent line = Component.literal(c.name()).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" (").append(Component.translatable("kingdoms.profession." + c.profession().id())).append(")").withStyle(ChatFormatting.AQUA))
                .append(order.withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" ").append(activity).withStyle(ChatFormatting.GRAY));
            npc.ifPresent(n -> line.append(Component.translatable("kingdoms.message.citizen_position",
                n.blockPosition().getX(), n.blockPosition().getY(), n.blockPosition().getZ(),
                CitizenEntity.INVENTORY_SIZE - Containers.emptySlots(n.getInventory()), CitizenEntity.INVENTORY_SIZE).withStyle(ChatFormatting.DARK_GRAY)));
            source.sendSystemMessage(line);
        }
        return list.size();
    }

    private static Optional<CitizenEntity> npc(MinecraftServer server, Citizen citizen) {
        for (var level : server.getAllLevels())
            if (level.getEntity(citizen.id()) instanceof CitizenEntity npc)
                return Optional.of(npc);
        return Optional.empty();
    }

    // ---- stockpile ----

    /** The stockpile is the container the player looks at, or the one at the given position. */
    private static int stockpileLookedAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var hit = player.pick(STOCKPILE_REACH, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.translatable("kingdoms.error.not_container"));
            return 0;
        }
        return stockpile(ctx, ((BlockHitResult) hit).getBlockPos());
    }

    private static int stockpile(CommandContext<CommandSourceStack> ctx, BlockPos pos) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var kingdom = own(ctx);
        if (Containers.at(player.level(), pos) == null) {
            ctx.getSource().sendFailure(Component.translatable("kingdoms.error.not_container"));
            return 0;
        }
        return run(ctx, () -> {
            KingdomService.change(ctx.getSource().getServer(), s -> {
                s.setStockpile(kingdom.id(), new BlockLocation(KingdomService.dimension(player.level()), pos.getX(), pos.getY(), pos.getZ()));
                return null;
            });
            KingdomsLog.LOGGER.info("Kingdom {} stockpile set to {}", kingdom.name(), pos.toShortString());
            ok(ctx, Component.translatable("kingdoms.message.stockpile_set", pos.getX(), pos.getY(), pos.getZ()));
        });
    }

    // ---- helpers ----

    private static Optional<Order> order(CommandContext<CommandSourceStack> ctx, Kingdom kingdom) {
        var id = StringArgumentType.getString(ctx, "id");
        var order = KingdomService.state(ctx.getSource().getServer()).orderByPrefix(kingdom.id(), id);
        if (order.isEmpty())
            ctx.getSource().sendFailure(Component.translatable("kingdoms.error.unknown_order", id));
        return order;
    }

    private static Citizen.Profession parseProfession(String id) {
        try {
            return Citizen.Profession.valueOf(id.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new KingdomException("kingdoms.error.unknown_value", id);
        }
    }

    private static MutableComponent typeName(OrderType type) {
        return Component.translatable("kingdoms.order." + type.id());
    }

    private static MutableComponent statusName(OrderStatus status) {
        return Component.translatable("kingdoms.status." + status.id());
    }

    private static MutableComponent button(String key, String command, ChatFormatting color) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style.applyFormat(color)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    private interface Action {
        void run();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Action action) {
        try {
            action.run();
            return 1;
        } catch (KingdomException e) {
            ctx.getSource().sendFailure(KingdomService.message(e));
            return 0;
        }
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
    }

    private static Kingdom own(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        return KingdomService.state(ctx.getSource().getServer()).kingdomOf(player.getUUID()).orElseThrow(NOT_RULER::create);
    }
}
