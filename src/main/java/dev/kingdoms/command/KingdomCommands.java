package dev.kingdoms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kingdoms.world.Emblem;
import dev.kingdoms.world.HeraldicColor;
import dev.kingdoms.world.Kingdom;
import dev.kingdoms.world.KingdomException;
import dev.kingdoms.world.KingdomService;
import dev.kingdoms.core.KingdomsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /kingdom} for players (acts on the caller's own kingdom) and {@code /kingdoms} for operators
 * (GDD 14.10). All changes go through {@link KingdomService}; refusals come back as translated messages.
 */
public final class KingdomCommands {
    private static final int MAP_RADIUS = 5;
    private static final String MAP_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final SuggestionProvider<CommandSourceStack> KINGDOM_NAMES = (ctx, builder) -> SharedSuggestionProvider.suggest(
        KingdomService.state(ctx.getSource().getServer()).kingdoms().stream()
            .map(k -> k.name().contains(" ") ? "\"" + k.name() + "\"" : k.name()), builder);
    private static final SuggestionProvider<CommandSourceStack> COLORS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(Arrays.stream(HeraldicColor.values()).map(HeraldicColor::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SYMBOLS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(Arrays.stream(Emblem.Symbol.values()).map(Emblem.Symbol::id), builder);

    private static final SimpleCommandExceptionType NOT_RULER = new SimpleCommandExceptionType(Component.translatable("kingdoms.error.not_ruler"));
    private static final DynamicCommandExceptionType UNKNOWN_KINGDOM = new DynamicCommandExceptionType(
        name -> Component.translatable("kingdoms.error.unknown_kingdom", name));

    private KingdomCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdom")
            .then(Commands.literal("found")
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(KingdomCommands::found)))
            .then(Commands.literal("info").executes(KingdomCommands::infoHere))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(KingdomCommands::rename)))
            .then(Commands.literal("emblem")
                .then(Commands.argument("primary", StringArgumentType.word()).suggests(COLORS)
                    .then(Commands.argument("secondary", StringArgumentType.word()).suggests(COLORS)
                        .then(Commands.argument("symbol", StringArgumentType.word()).suggests(SYMBOLS).executes(KingdomCommands::emblem)))))
            .then(Commands.literal("claim").executes(ctx -> claim(ctx, true)))
            .then(Commands.literal("unclaim").executes(ctx -> claim(ctx, false)))
            .then(Commands.literal("map").executes(KingdomCommands::map)));

        dispatcher.register(Commands.literal("kingdoms")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("list").executes(KingdomCommands::list))
            .then(Commands.literal("info")
                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOM_NAMES).executes(ctx -> info(ctx, named(ctx)))))
            .then(Commands.literal("citizens")
                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOM_NAMES).executes(KingdomCommands::citizens)))
            .then(Commands.literal("treasury")
                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOM_NAMES)
                    .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0))
                        .executes(ctx -> treasury(ctx, false))))
                    .then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg())
                        .executes(ctx -> treasury(ctx, true))))))
            .then(Commands.literal("delete")
                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOM_NAMES).executes(KingdomCommands::delete)))
            .then(Commands.literal("validate").executes(KingdomCommands::validate)));
    }

    // ---- player commands ----

    private static int found(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        return run(ctx, () -> {
            var kingdom = KingdomService.found(player, StringArgumentType.getString(ctx, "name"), player.blockPosition());
            ok(ctx, Component.translatable("kingdoms.message.founded", kingdom.name()));
        });
    }

    private static int infoHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var source = ctx.getSource();
        var state = KingdomService.state(source.getServer());
        Optional<Kingdom> kingdom = source.getPlayer() != null ? state.kingdomOf(source.getPlayer().getUUID()) : Optional.empty();
        if (kingdom.isEmpty()) {
            var pos = BlockPos.containing(source.getPosition());
            kingdom = state.ownerAt(KingdomService.dimension(source.getLevel()), pos.getX() >> 4, pos.getZ() >> 4);
        }
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable("kingdoms.error.no_kingdom"));
            return 0;
        }
        return info(ctx, kingdom.get());
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = own(ctx);
        return run(ctx, () -> {
            var renamed = KingdomService.change(ctx.getSource().getServer(), s -> s.rename(kingdom.id(), StringArgumentType.getString(ctx, "name")));
            ok(ctx, Component.translatable("kingdoms.message.renamed", renamed.name()));
        });
    }

    private static int emblem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = own(ctx);
        return run(ctx, () -> {
            var emblem = new Emblem(
                parse(HeraldicColor.class, StringArgumentType.getString(ctx, "primary")),
                parse(HeraldicColor.class, StringArgumentType.getString(ctx, "secondary")),
                parse(Emblem.Symbol.class, StringArgumentType.getString(ctx, "symbol")));
            KingdomService.change(ctx.getSource().getServer(), s -> s.setEmblem(kingdom.id(), emblem));
            ok(ctx, Component.translatable("kingdoms.message.emblem_changed").append(" ").append(describe(emblem)));
        });
    }

    private static int claim(CommandContext<CommandSourceStack> ctx, boolean claim) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var kingdom = own(ctx);
        var dim = KingdomService.dimension(player.level());
        int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
        return run(ctx, () -> {
            KingdomService.change(ctx.getSource().getServer(), s -> {
                if (claim)
                    s.claim(kingdom.id(), dim, cx, cz, KingdomsConfig.rules());
                else
                    s.unclaim(kingdom.id(), dim, cx, cz);
                return null;
            });
            ok(ctx, Component.translatable(claim ? "kingdoms.message.claimed" : "kingdoms.message.unclaimed", cx, cz));
        });
    }

    /** Text map of nearby chunks. Each kingdom gets a letter, so the map reads without colors (GDD 11.5). */
    private static int map(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var state = KingdomService.state(source.getServer());
        var dim = KingdomService.dimension(source.getLevel());
        var pos = BlockPos.containing(source.getPosition());
        int px = pos.getX() >> 4, pz = pos.getZ() >> 4;
        var own = source.getPlayer() == null ? Optional.<Kingdom>empty() : state.kingdomOf(source.getPlayer().getUUID());

        var letters = new LinkedHashMap<UUID, Character>();
        source.sendSystemMessage(Component.translatable("kingdoms.map.header", px, pz).withStyle(ChatFormatting.GOLD));
        for (int dz = -MAP_RADIUS; dz <= MAP_RADIUS; dz++) {
            MutableComponent row = Component.empty();
            for (int dx = -MAP_RADIUS; dx <= MAP_RADIUS; dx++) {
                var owner = state.ownerAt(dim, px + dx, pz + dz);
                MutableComponent cell;
                if (dx == 0 && dz == 0)
                    cell = Component.literal("@").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
                else if (owner.isEmpty())
                    cell = Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
                else {
                    var k = owner.get();
                    char letter = own.filter(o -> o.id().equals(k.id())).isPresent() ? '#'
                        : letters.computeIfAbsent(k.id(), id -> MAP_LETTERS.charAt(Math.min(letters.size(), MAP_LETTERS.length() - 1)));
                    cell = Component.literal(String.valueOf(letter)).withColor(color(k.emblem().primary()));
                }
                row.append(cell).append(" ");
            }
            source.sendSystemMessage(row);
        }
        source.sendSystemMessage(Component.translatable("kingdoms.map.legend_self").withStyle(ChatFormatting.GRAY));
        letters.forEach((id, letter) -> state.kingdom(id).ifPresent(k ->
            source.sendSystemMessage(Component.literal(letter + " = " + k.name()).withColor(color(k.emblem().primary())))));
        return 1;
    }

    // ---- operator commands ----

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var state = KingdomService.state(source.getServer());
        var kingdoms = state.kingdoms();
        source.sendSystemMessage(Component.translatable("kingdoms.message.list_header", kingdoms.size()).withStyle(ChatFormatting.GOLD));
        for (var k : kingdoms)
            source.sendSystemMessage(Component.translatable("kingdoms.message.list_entry", k.name(),
                KingdomService.rulerName(source.getServer(), k.ruler()), state.citizensOf(k.id()).size(), state.claimCount(k.id()), k.treasury()));
        return kingdoms.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, Kingdom kingdom) {
        var source = ctx.getSource();
        var state = KingdomService.state(source.getServer());
        source.sendSystemMessage(Component.literal(kingdom.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        source.sendSystemMessage(Component.translatable("kingdoms.info.ruler", KingdomService.rulerName(source.getServer(), kingdom.ruler())));
        state.settlement(kingdom.capital()).ifPresent(c -> source.sendSystemMessage(Component.translatable("kingdoms.info.capital",
            c.name(), c.x(), c.y(), c.z(), c.dimension(), Component.translatable("kingdoms.stage." + c.stage().id()))));
        source.sendSystemMessage(Component.translatable("kingdoms.info.citizens", state.citizensOf(kingdom.id()).size()));
        source.sendSystemMessage(Component.translatable("kingdoms.info.territory", state.claimCount(kingdom.id())));
        source.sendSystemMessage(Component.translatable("kingdoms.info.treasury", kingdom.treasury()));
        source.sendSystemMessage(Component.translatable("kingdoms.info.emblem").append(" ").append(describe(kingdom.emblem())));
        source.sendSystemMessage(Component.literal(kingdom.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int citizens(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var source = ctx.getSource();
        var kingdom = named(ctx);
        var state = KingdomService.state(source.getServer());
        var list = state.citizensOf(kingdom.id());
        source.sendSystemMessage(Component.translatable("kingdoms.message.citizens_header", kingdom.name(), list.size()).withStyle(ChatFormatting.GOLD));
        for (var c : list) {
            var home = state.settlement(c.settlement()).map(s -> s.name()).orElse("?");
            source.sendSystemMessage(Component.translatable("kingdoms.message.citizen_entry", c.name(),
                Component.translatable("kingdoms.profession." + c.profession().id()), home, c.id().toString()));
        }
        return list.size();
    }

    private static int treasury(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        var kingdom = named(ctx);
        long amount = LongArgumentType.getLong(ctx, "amount");
        return run(ctx, () -> {
            var updated = KingdomService.change(ctx.getSource().getServer(),
                s -> add ? s.addTreasury(kingdom.id(), amount) : s.setTreasury(kingdom.id(), amount));
            ok(ctx, Component.translatable("kingdoms.message.treasury", updated.name(), updated.treasury()));
        });
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var kingdom = named(ctx);
        return run(ctx, () -> {
            KingdomService.change(ctx.getSource().getServer(), s -> {
                s.delete(kingdom.id());
                return null;
            });
            ok(ctx, Component.translatable("kingdoms.message.deleted", kingdom.name()));
        });
    }

    private static int validate(CommandContext<CommandSourceStack> ctx) {
        var problems = KingdomService.state(ctx.getSource().getServer()).validate();
        if (problems.isEmpty()) {
            ok(ctx, Component.translatable("kingdoms.message.valid"));
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("kingdoms.message.invalid", problems.size()));
        problems.forEach(p -> ctx.getSource().sendSystemMessage(Component.literal("- " + p).withStyle(ChatFormatting.RED)));
        return 0;
    }

    // ---- helpers ----

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
        return KingdomService.state(ctx.getSource().getServer()).kingdomOf(player.getUUID())
            .orElseThrow(NOT_RULER::create);
    }

    private static Kingdom named(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var name = StringArgumentType.getString(ctx, "kingdom");
        return KingdomService.state(ctx.getSource().getServer()).kingdomNamed(name)
            .orElseThrow(() -> UNKNOWN_KINGDOM.create(name));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String id) {
        try {
            return Enum.valueOf(type, id.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new KingdomException("kingdoms.error.unknown_value", id);
        }
    }

    private static int color(HeraldicColor color) {
        return DyeColor.valueOf(color.name()).getTextColor();
    }

    private static MutableComponent describe(Emblem emblem) {
        return Component.translatable("kingdoms.emblem.description",
            Component.translatable("color.minecraft." + emblem.primary().id()).withColor(color(emblem.primary())),
            Component.translatable("color.minecraft." + emblem.secondary().id()).withColor(color(emblem.secondary())),
            Component.translatable("kingdoms.symbol." + emblem.symbol().id()));
    }
}
