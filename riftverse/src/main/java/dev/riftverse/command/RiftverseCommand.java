package dev.riftverse.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftType;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseEntry;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.PromptInterpreter;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

public final class RiftverseCommand {
    private RiftverseCommand() {}

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("riftverse").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("manifest").then(Commands.argument("description", StringArgumentType.greedyString()).executes(RiftverseCommand::manifest)));
        root.then(Commands.literal("travel").then(Commands.argument("archetype", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Archetype.values()).map(a -> a.id), b))
                .executes(c -> {
                    Archetype a = Archetype.byName(StringArgumentType.getString(c, "archetype"));
                    if (a == null) {
                        c.getSource().sendFailure(Component.literal("Unknown archetype"));
                        return 0;
                    }
                    return travel(c, Destination.archetype(a), TransitKind.RIFT, a.signatureColor);
                })));
        root.then(Commands.literal("random").executes(c -> travel(c, Destination.random(), TransitKind.RIFT, 0x7DF9FF)));
        root.then(Commands.literal("nexus").executes(c -> travel(c, Destination.nexus(), TransitKind.CONSOLE, 0xFFC14D)));
        root.then(Commands.literal("home").executes(c -> travel(c, Destination.home(), TransitKind.HOMEWARD, 0xFFFFFF)));
        root.then(Commands.literal("blackhole").executes(c -> blackHole(c, 3.5f))
                .then(Commands.argument("radius", FloatArgumentType.floatArg(0.5f, 12f)).executes(c -> blackHole(c, FloatArgumentType.getFloat(c, "radius")))));
        root.then(Commands.literal("rift").then(Commands.argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RiftType.values()).map(RiftType::getSerializedName), b))
                .executes(RiftverseCommand::rift)));
        root.then(Commands.literal("info").executes(RiftverseCommand::info));
        event.getDispatcher().register(root);
    }

    private static int travel(CommandContext<CommandSourceStack> c, Destination dest, TransitKind kind, int color) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        boolean ok = TransitManager.begin(player, kind, dest, player.getEyePosition().add(player.getLookAngle().scale(3)), color, 0xFFFFFF, true);
        return ok ? 1 : 0;
    }

    private static int manifest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String prompt = StringArgumentType.getString(c, "description");
        PromptInterpreter.Result result = UniverseRegistry.get(player.server).manifest(prompt, player.getUUID());
        UniverseSpec spec = result.spec();
        c.getSource().sendSuccess(() -> Component.literal("Manifested " + spec.name + " [" + spec.id.designation() + "]"), false);
        for (String note : result.notes()) c.getSource().sendSuccess(() -> Component.literal("  • " + note), false);
        PacketDistributor.sendToPlayer(player, new Payloads.ManifestResult(UniverseEntry.of(spec, UniverseEntry.MANIFESTED), result.notes()));
        TransitManager.begin(player, TransitKind.CONSOLE, Destination.universe(spec.id), player.getEyePosition().add(player.getLookAngle().scale(3)), spec.accent, spec.nebulaB, true);
        return 1;
    }

    private static int blackHole(CommandContext<CommandSourceStack> c, float radius) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(radius * 9 + 6));
        BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), level);
        hole.moveTo(at.x, at.y + radius * 2, at.z, 0, 0);
        hole.setHorizonRadius(radius);
        hole.setNatural(true);
        level.addFreshEntity(hole);
        c.getSource().sendSuccess(() -> Component.literal("A singularity blooms into existence..."), false);
        return 1;
    }

    private static int rift(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "type");
        RiftType type = Arrays.stream(RiftType.values()).filter(t -> t.getSerializedName().equalsIgnoreCase(name)).findFirst().orElse(RiftType.AZURE);
        BlockPos at = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(4)));
        player.serverLevel().setBlockAndUpdate(at, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, type));
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (player.level().dimension() != RvWorldgen.EXPANSE) {
            c.getSource().sendSuccess(() -> Component.literal(player.level().dimension() == RvWorldgen.NEXUS ? "You stand in the Multiverse Nexus." : "You are not inside the Expanse."), false);
            return 1;
        }
        UniverseSpec spec = UniverseRegistry.specAt(player.getBlockX(), player.getBlockZ());
        c.getSource().sendSuccess(() -> Component.literal(spec.name + " [" + spec.id.designation() + "]"), false);
        c.getSource().sendSuccess(() -> Component.literal(spec.describe()), false);
        if (!spec.prompt.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Manifested from: \"" + spec.prompt + "\""), false);
        return 1;
    }
}
