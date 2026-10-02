package dev.mysticarts.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import dev.mysticarts.registry.MaItems;
import dev.mysticarts.world.MysticStructures;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator helpers for trying everything quickly: {@code /mystic ...}. */
public final class MysticCommand {
    private MysticCommand() {}

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mystic").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("unlock").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            PowerData d = PowerManager.data(p);
            d.tiers = PowerData.TIER_INITIATE | PowerData.TIER_ADEPT;
            d.dirty = true;
            c.getSource().sendSuccess(() -> Component.translatable("command.mysticarts.unlocked"), false);
            return 1;
        }));
        root.then(Commands.literal("sorcerer").executes(MysticCommand::sorcerer));
        root.then(Commands.literal("gauntlet").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            give(p, InfinityGauntletItem.full(MaItems.INFINITY_GAUNTLET.get()));
            return 1;
        }));
        root.then(Commands.literal("restore").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            PowerData d = PowerManager.data(p);
            d.mystic = d.maxMystic;
            d.cosmic = d.maxCosmic;
            d.ultimate = PowerManager.ULTIMATE_MAX;
            Arrays.fill(d.cooldowns, 0);
            d.dirty = true;
            c.getSource().sendSuccess(() -> Component.translatable("command.mysticarts.restored"), false);
            return 1;
        }));
        root.then(Commands.literal("structure").then(Commands.argument("kind", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MysticStructures.Kind.values()).map(k -> k.id), b))
                .executes(MysticCommand::structure)));
        event.getDispatcher().register(root);
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    /** Equips the full Sorcerer Supreme kit: tomes learned, all four artifacts bound, robes in the inventory. */
    private static int sorcerer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PowerData d = PowerManager.data(p);
        d.tiers = PowerData.TIER_INITIATE | PowerData.TIER_ADEPT;
        d.artifacts[Artifact.CLOAK.ordinal()] = new ItemStack(MaItems.CLOAK_OF_LEVITATION.get());
        d.artifacts[Artifact.AMULET.ordinal()] = new ItemStack(MaItems.EYE_OF_AGAMOTTO.get());
        d.artifacts[Artifact.RING.ordinal()] = new ItemStack(MaItems.SLING_RING.get());
        d.artifacts[Artifact.BRACERS.ordinal()] = new ItemStack(MaItems.MYSTIC_WRIST_WRAPS.get());
        d.dirty = true;
        d.casterDirty = true;
        give(p, new ItemStack(MaItems.SORCERER_TUNIC.get()));
        give(p, new ItemStack(MaItems.SORCERER_TROUSERS.get()));
        give(p, new ItemStack(MaItems.SORCERER_BOOTS.get()));
        c.getSource().sendSuccess(() -> Component.translatable("command.mysticarts.sorcerer"), false);
        return 1;
    }

    private static int structure(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "kind").toLowerCase(Locale.ROOT);
        for (MysticStructures.Kind k : MysticStructures.Kind.values()) {
            if (k.id.equals(id)) {
                int x = p.getBlockX() + (int) Math.round(p.getLookAngle().x * 16);
                int z = p.getBlockZ() + (int) Math.round(p.getLookAngle().z * 16);
                boolean ok = MysticStructures.generate(k, p.serverLevel(), p.getRandom(), x, z, true);
                if (ok) c.getSource().sendSuccess(() -> Component.translatable("command.mysticarts.structure", id, x, z), false);
                else c.getSource().sendFailure(Component.translatable("command.mysticarts.structure_failed"));
                return ok ? 1 : 0;
            }
        }
        c.getSource().sendFailure(Component.translatable("command.mysticarts.unknown_structure"));
        return 0;
    }
}
