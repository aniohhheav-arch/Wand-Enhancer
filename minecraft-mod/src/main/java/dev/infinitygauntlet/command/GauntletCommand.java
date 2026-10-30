package dev.infinitygauntlet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.entity.PortalNetwork;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModItems;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /gauntlet} testing commands (permission level 2):
 * give, cast (ignores cooldowns), cooldowns reset, portals list/clear, vfx preview and ability listing.
 */
public final class GauntletCommand {
    private static final SuggestionProvider<CommandSourceStack> ABILITY_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(Abilities.all().stream().map(Ability::id), builder);
    private static final SuggestionProvider<CommandSourceStack> STONE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(InfinityStone.values()).map(InfinityStone::getSerializedName), builder);
    private static final SuggestionProvider<CommandSourceStack> VFX_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(VfxType.values()).map(v -> v.name().toLowerCase(Locale.ROOT)), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gauntlet")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.literal("full").executes(ctx -> give(ctx, InfinityGauntletItem.fullGauntlet())))
                        .then(Commands.literal("empty").executes(ctx -> give(ctx, new ItemStack(ModItems.INFINITY_GAUNTLET.get()))))
                        .then(Commands.literal("stone").then(Commands.argument("stone", StringArgumentType.word()).suggests(STONE_SUGGESTIONS)
                                .executes(ctx -> {
                                    InfinityStone stone = parseStone(ctx);
                                    return stone == null ? 0 : give(ctx, new ItemStack(ModItems.stone(stone)));
                                }))))
                .then(Commands.literal("cast").then(Commands.argument("ability", StringArgumentType.greedyString()).suggests(ABILITY_SUGGESTIONS)
                        .executes(GauntletCommand::cast)))
                .then(Commands.literal("cooldowns").then(Commands.literal("reset")
                        .executes(ctx -> resetCooldowns(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> resetCooldowns(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("portals")
                        .then(Commands.literal("list").executes(GauntletCommand::listPortals))
                        .then(Commands.literal("clear").executes(GauntletCommand::clearPortals)))
                .then(Commands.literal("vfx").then(Commands.argument("type", StringArgumentType.word()).suggests(VFX_SUGGESTIONS)
                        .executes(GauntletCommand::vfx)))
                .then(Commands.literal("abilities").executes(GauntletCommand::listAbilities)));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ItemStack stack) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitygauntlet.given", stack.getHoverName()), false);
        return 1;
    }

    private static InfinityStone parseStone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "stone");
        for (InfinityStone stone : InfinityStone.values()) {
            if (stone.getSerializedName().equals(name)) {
                return stone;
            }
        }
        ctx.getSource().sendFailure(Component.translatable("command.infinitygauntlet.unknown_stone", name));
        return null;
    }

    private static int cast(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "ability").trim());
        Ability ability = id == null ? null : Abilities.byId(id);
        if (ability == null) {
            ctx.getSource().sendFailure(Component.translatable("command.infinitygauntlet.unknown_ability", StringArgumentType.getString(ctx, "ability")));
            return 0;
        }
        InteractionHand hand = player.getMainHandItem().getItem() instanceof InfinityGauntletItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof InfinityGauntletItem ? InteractionHand.OFF_HAND : null;
        ItemStack stack = hand == null ? InfinityGauntletItem.fullGauntlet() : player.getItemInHand(hand);
        boolean ok = AbilityCaster.cast(player, hand == null ? InteractionHand.MAIN_HAND : hand, stack, ability, true);
        return ok ? 1 : 0;
    }

    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            player.getData(ModAttachments.POWER_DATA).clearCooldowns();
            AbilityCaster.syncCooldowns(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitygauntlet.cooldowns_reset", players.size()), true);
        return players.size();
    }

    private static int listPortals(CommandContext<CommandSourceStack> ctx) {
        List<PortalNetwork.Entry> all = PortalNetwork.get(ctx.getSource().getServer()).all();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitygauntlet.portals.count", all.size()), false);
        for (PortalNetwork.Entry e : all) {
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " %s %s (%.1f, %.1f, %.1f) r=%.1f %s",
                    e.id().toString().substring(0, 8), e.dimension().location(), e.pos().x, e.pos().y, e.pos().z, e.radius(),
                    e.partner() == null ? "unlinked" : "-> " + e.partner().toString().substring(0, 8))), false);
        }
        return all.size();
    }

    private static int clearPortals(CommandContext<CommandSourceStack> ctx) {
        PortalNetwork network = PortalNetwork.get(ctx.getSource().getServer());
        List<PortalNetwork.Entry> all = network.all();
        all.forEach(e -> network.close(ctx.getSource().getServer(), e.id()));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitygauntlet.portals.cleared", all.size()), true);
        return all.size();
    }

    private static int vfx(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VfxType type;
        try {
            type = VfxType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("command.infinitygauntlet.unknown_vfx"));
            return 0;
        }
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(6));
        Vec3 b = switch (type) {
            case BEAM, ARC, LINK -> at.add(player.getLookAngle().scale(10)).add(0, 2, 0);
            case SHOCKWAVE -> new Vec3(0, 1, 0);
            case DISSOLVE -> new Vec3(0.6, 1.8, 0);
            default -> Vec3.ZERO;
        };
        Vfx.send(player.serverLevel(), type, at, b, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), 3f, 40);
        return 1;
    }

    private static int listAbilities(CommandContext<CommandSourceStack> ctx) {
        for (InfinityStone stone : InfinityStone.values()) {
            List<Ability> list = Abilities.forStone(stone);
            ctx.getSource().sendSuccess(() -> stone.displayName().copy().append(Component.literal(" (" + list.size() + ")")), false);
            for (Ability ability : list) {
                ctx.getSource().sendSuccess(() -> Component.literal("  " + ability.id() + " - ").append(ability.displayName()), false);
            }
        }
        return 1;
    }

    private GauntletCommand() {
    }
}
