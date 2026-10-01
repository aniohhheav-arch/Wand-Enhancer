package dev.riftverse.network;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.block.RiftType;
import dev.riftverse.item.DimensionalKeyItem;
import dev.riftverse.player.PlayerMultiverseData;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.PromptInterpreter;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.util.Advancements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side logic behind the Multiverse Console. */
public final class MultiverseService {
    private static final Map<UUID, Long> LAST_MANIFEST = new HashMap<>();

    private MultiverseService() {}

    public static void openBrowser(ServerPlayer player, BlockPos console) {
        UniverseRegistry reg = UniverseRegistry.get(player.server);
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        List<UniverseEntry> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Archetype a : Archetype.values()) {
            UniverseSpec s = UniverseRegistry.specFor(UniverseId.prime(a));
            entries.add(UniverseEntry.of(s, UniverseEntry.PRIME));
            seen.add(s.id.pack());
        }
        for (UniverseSpec s : reg.manifestedBy(player.getUUID())) {
            if (seen.add(s.id.pack())) entries.add(UniverseEntry.of(s, UniverseEntry.MANIFESTED));
        }
        List<Long> discovered = data.discovered();
        for (int i = discovered.size() - 1; i >= 0; i--) {
            long id = discovered.get(i);
            if (!seen.add(id)) continue;
            entries.add(UniverseEntry.of(UniverseRegistry.specFor(UniverseId.unpack(id)), UniverseEntry.DISCOVERED));
        }
        int left = Math.max(0, RiftverseConfig.MAX_PROMPT_UNIVERSES_PER_PLAYER.get() - reg.manifestCount(player.getUUID()));
        PacketDistributor.sendToPlayer(player, new Payloads.OpenBrowser(console, entries, left));
        player.serverLevel().playSound(null, console, RvSounds.UI_SELECT.get(), SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    private static boolean nearConsole(ServerPlayer player, BlockPos console) {
        if (player.distanceToSqr(Vec3.atCenterOf(console)) > 10 * 10) return false;
        return player.level().getBlockState(console).is(RvBlocks.MULTIVERSE_CONSOLE.get());
    }

    private static boolean known(ServerPlayer player, UniverseId id) {
        if (id.isPrime()) return true;
        UniverseRegistry reg = UniverseRegistry.get(player.server);
        return reg.isStored(id);
    }

    public static void handle(Payloads.BrowserAction action, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        BlockPos console = action.console();
        if (!nearConsole(player, console)) return;
        Vec3 focus = Vec3.atCenterOf(console);
        switch (action.action()) {
            case Payloads.BrowserAction.TRAVEL_UNIVERSE -> {
                UniverseId id = UniverseId.unpack(action.universe());
                if (!known(player, id)) return;
                UniverseSpec s = UniverseRegistry.specFor(id);
                TransitManager.begin(player, TransitKind.CONSOLE, Destination.universe(id), focus, s.accent, s.nebulaB, true);
            }
            case Payloads.BrowserAction.TRAVEL_ARCHETYPE -> {
                Archetype a = Archetype.byId(action.archetype());
                TransitManager.begin(player, TransitKind.CONSOLE, Destination.archetype(a), focus, a.signatureColor, 0xFFFFFF, true);
            }
            case Payloads.BrowserAction.TRAVEL_RANDOM ->
                    TransitManager.begin(player, TransitKind.CONSOLE, Destination.random(), focus, 0x7DF9FF, 0xFF4FD8, true);
            case Payloads.BrowserAction.MANIFEST -> manifest(player, action.prompt());
            case Payloads.BrowserAction.IMPRINT_KEY -> imprint(player, UniverseId.unpack(action.universe()));
            case Payloads.BrowserAction.OPEN_GATE -> openGate(player, console, UniverseId.unpack(action.universe()));
            default -> {}
        }
    }

    private static void manifest(ServerPlayer player, String rawPrompt) {
        String prompt = rawPrompt == null ? "" : rawPrompt.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (prompt.length() < 3 || prompt.length() > 150) {
            player.displayClientMessage(Component.translatable("message.riftverse.prompt_length"), true);
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = LAST_MANIFEST.get(player.getUUID());
        if (last != null && now - last < 40) return;
        LAST_MANIFEST.put(player.getUUID(), now);
        UniverseRegistry reg = UniverseRegistry.get(player.server);
        if (reg.manifestCount(player.getUUID()) >= RiftverseConfig.MAX_PROMPT_UNIVERSES_PER_PLAYER.get()) {
            player.displayClientMessage(Component.translatable("message.riftverse.manifest_limit"), true);
            return;
        }
        PromptInterpreter.Result result = reg.manifest(prompt, player.getUUID());
        Advancements.award(player, "manifest");
        player.serverLevel().playSound(null, player.blockPosition(), RvSounds.UI_MANIFEST.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        PacketDistributor.sendToPlayer(player, new Payloads.ManifestResult(UniverseEntry.of(result.spec(), UniverseEntry.MANIFESTED), result.notes()));
    }

    private static void imprint(ServerPlayer player, UniverseId id) {
        if (!known(player, id)) return;
        UniverseSpec spec = UniverseRegistry.specFor(id);
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.is(RvItems.DIMENSIONAL_KEY.get())) {
                DimensionalKeyItem.imprint(stack, Destination.universe(id), spec.name, spec.accent);
                player.displayClientMessage(Component.translatable("message.riftverse.key_imprinted", spec.name), true);
                player.serverLevel().playSound(null, player.blockPosition(), RvSounds.ABILITY_ACTIVATE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
                return;
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(RvItems.DIMENSIONAL_KEY.get())) {
                DimensionalKeyItem.imprint(stack, Destination.universe(id), spec.name, spec.accent);
                player.displayClientMessage(Component.translatable("message.riftverse.key_imprinted", spec.name), true);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.riftverse.no_key"), true);
    }

    private static void openGate(ServerPlayer player, BlockPos console, UniverseId id) {
        if (!known(player, id)) return;
        ServerLevel level = player.serverLevel();
        Direction face = Direction.getNearest(player.getX() - console.getX() - 0.5, 0, player.getZ() - console.getZ() - 0.5);
        Direction side = face.getClockWise();
        for (int d = 2; d <= 4; d++) {
            BlockPos at = console.relative(side, d).above();
            BlockState here = level.getBlockState(at);
            if (!here.isAir() || !level.getBlockState(at.above()).isAir()) continue;
            level.setBlockAndUpdate(at, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, RiftType.STELLAR));
            if (level.getBlockEntity(at) instanceof RiftBlockEntity rift) rift.configure(Destination.universe(id), level.getGameTime() + 1200, false);
            level.playSound(null, at, RvSounds.RIFT_OPEN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            return;
        }
        player.displayClientMessage(Component.translatable("message.riftverse.no_room"), true);
    }
}
