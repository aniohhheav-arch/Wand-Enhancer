package dev.riftverse.fusion;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.block.RiftType;
import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.transit.Destination;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side of the Fusion Engine: lists carried samples, validates requests and births the fused universe. */
public final class FusionService {
    private FusionService() {}

    public static void open(ServerPlayer p) {
        List<Integer> slots = new ArrayList<>();
        List<CompoundTag> specs = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            UniverseSpec s = UniverseSampleItem.spec(p.getInventory().getItem(i));
            if (s != null) {
                slots.add(i);
                specs.add(s.save());
            }
        }
        if (slots.isEmpty()) {
            p.displayClientMessage(Component.literal("Carry at least two filled Universe Samples to use the Fusion Engine.").withColor(0xFF8090), true);
            return;
        }
        PacketDistributor.sendToPlayer(p, new Payloads.FusionOpen(slots, specs));
    }

    public static void request(ServerPlayer p, Payloads.FusionRequest req) {
        List<UniverseSpec> parents = new ArrayList<>();
        for (int slot : req.slots()) {
            if (slot < 0 || slot >= p.getInventory().getContainerSize()) return;
            UniverseSpec s = UniverseSampleItem.spec(p.getInventory().getItem(slot));
            if (s == null) {
                p.displayClientMessage(Component.literal("A selected sample is no longer in your inventory.").withColor(0xFF8090), true);
                return;
            }
            parents.add(s);
        }
        if (parents.size() < 2 || parents.size() > 4) return;
        int[] choice = new int[FusionEngine.Gene.values().length];
        for (int i = 0; i < choice.length; i++) {
            int c = i < req.choice().size() ? req.choice().get(i) : 0;
            choice[i] = c == FusionEngine.BLEND ? c : Math.max(0, Math.min(parents.size() - 1, c));
        }
        UniverseRegistry reg = UniverseRegistry.get(p.server);
        int max = RiftverseConfig.get(RiftverseConfig.MAX_PROMPT_UNIVERSES_PER_PLAYER, 64);
        if (!p.hasPermissions(2) && reg.manifestCount(p.getUUID()) >= max) {
            p.displayClientMessage(Component.literal("You have created the maximum of " + max + " universes.").withColor(0xFF8090), true);
            return;
        }
        long seed = p.getRandom().nextLong();
        List<String> notes = new ArrayList<>();
        int[] compat = new int[1];
        UniverseSpec spec = reg.manifestCustom(id -> {
            FusionEngine.Result r = FusionEngine.fuse(parents, choice, req.name(), id, seed);
            notes.addAll(r.notes());
            compat[0] = r.compatibility();
            return r.spec();
        }, p.getUUID());
        p.getData(RvAttachments.MULTIVERSE.get()).discover(spec.id.pack());
        RealityOps.research(p, 40, "universes fused");
        RealityOps.cinematic(p, CinematicType.BIRTH, 160, p.getEyePosition().add(p.getLookAngle().scale(4)), spec.accent, spec.nebulaA, "FUSION COMPLETE",
                spec.name + " • compatibility " + compat[0] + "%");
        p.displayClientMessage(Component.literal("Fused " + spec.name + " [" + spec.id.designation() + "] at " + compat[0] + "% compatibility.").withColor(spec.accent), false);
        for (String n : notes) p.displayClientMessage(Component.literal("  • " + n).withColor(0xC0B0E0), false);
        openRift(p, spec);
    }

    /** A rift into the newborn universe opens in front of its creator. */
    private static void openRift(ServerPlayer p, UniverseSpec spec) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        for (int d = 3; d <= 6; d++) {
            BlockPos at = BlockPos.containing(p.position().add(look.scale(d))).above();
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
            level.setBlockAndUpdate(at, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, RiftType.STELLAR));
            if (level.getBlockEntity(at) instanceof RiftBlockEntity rift) rift.configure(Destination.universe(spec.id), level.getGameTime() + 2400, false);
            level.sendParticles(RvParticles.RING.get().with(spec.accent, 4f, 20), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2, 0, 0, 0, 0);
            return;
        }
        p.displayClientMessage(Component.literal("No room for a rift here: reach " + spec.name + " from your Remote's target list.").withColor(0xC0B0E0), false);
    }
}
