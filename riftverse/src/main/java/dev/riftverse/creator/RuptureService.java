package dev.riftverse.creator;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.entity.RealityTearEntity;
import dev.riftverse.item.ItemData;
import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.UniverseId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Server side of the creator interface and of every Reality Rupture ability. */
public final class RuptureService {
    public static final int PROMPT = 0, CREDENTIAL = 1, CONSOLE = 2;

    /** One disassembled region: every block removed, so it can be put back exactly. */
    public record Snapshot(ServerLevel level, Map<BlockPos, BlockState> blocks) {}

    private static final Deque<Snapshot> SNAPSHOTS = new ArrayDeque<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private RuptureService() {}

    // ------------------------------------------------------------------ interface

    public static void open(ServerPlayer p, int mode) {
        if (!CreatorAuthority.eligible(p)) {
            p.sendSystemMessage(Component.literal("Unknown command."));
            return;
        }
        PacketDistributor.sendToPlayer(p, new Payloads.CreatorScreen(mode, CreatorAuthority.credentialSet()));
    }

    public static void submit(ServerPlayer p, Payloads.CreatorSubmit msg) {
        if (!CreatorAuthority.eligible(p)) return;
        switch (msg.mode()) {
            case PROMPT -> {
                String err = CreatorAuthority.authenticate(p, msg.first());
                if (err != null) {
                    p.displayClientMessage(Component.literal(err).withColor(0xFF5070), true);
                    return;
                }
                open(p, CONSOLE);
            }
            case CREDENTIAL -> {
                String err = CreatorAuthority.setCredential(p, msg.first(), msg.second());
                p.displayClientMessage(Component.literal(err == null ? "Credential sealed." : err).withColor(err == null ? 0xC080FF : 0xFF5070), true);
            }
            case CONSOLE -> {
                if (!CreatorAuthority.inSession(p)) return;
                int action;
                try {
                    action = Integer.parseInt(msg.first());
                } catch (NumberFormatException e) {
                    return;
                }
                switch (action) {
                    case 0 -> materialize(p);
                    case 1 -> {
                        CreatorAuthority.toggleAura(p.server, p.getUUID());
                        p.refreshDisplayName();
                        p.displayClientMessage(Component.literal("Aura " + (CreatorAuthority.aura(p.getUUID()) ? "manifested" : "veiled") + ".").withColor(0xC080FF), true);
                    }
                    case 2 -> announce(p);
                    case 3 -> preview(p);
                    case 4 -> {
                        CreatorAuthority.endSession(p.getUUID());
                        p.displayClientMessage(Component.literal("Session sealed.").withColor(0xC080FF), true);
                    }
                    default -> {}
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ discovery

    public static void materialize(ServerPlayer p) {
        RealityOps.cinematic(p, CinematicType.DISCOVERY, 0, p.getEyePosition(), 0xC080FF, 0x10001A, "AUTHORITY RECOGNIZED.", "REALITY CONSTRAINTS DISABLED.");
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 2f, 0.5f);
        Scheduler.later(70, () -> RealityOps.cinematic(p, CinematicType.ANNOUNCE, 70, p.getEyePosition(), 0xC080FF, 0xFFFFFF, "THE RUPTURE IS YOURS.", "Reality Architect"));
        Scheduler.later(100, () -> {
            if (p.isRemoved()) return;
            Vec3 c = p.getEyePosition();
            p.serverLevel().sendParticles(RvParticles.RING.get().with(0xC080FF, 8f, 24), c.x, c.y, c.z, 3, 0, 0, 0, 0);
            p.serverLevel().sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 1.5f, 20), c.x, c.y, c.z, 80, 1.5, 1.5, 1.5, 0.3);
            p.getInventory().placeItemBackInInventory(forge(p.server, p.getUUID()));
        });
    }

    public static ItemStack forge(MinecraftServer server, UUID owner) {
        ItemStack stack = new ItemStack(RvItems.REALITY_RUPTURE.get());
        long serial = CreatorAuthority.issueSerial(server);
        ItemData.edit(stack, t -> {
            t.putLong("rupture_serial", serial);
            t.putUUID("rupture_owner", owner);
        });
        return stack;
    }

    private static void announce(ServerPlayer p) {
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            RealityOps.cinematic(o, CinematicType.ANNOUNCE, 90, o.getEyePosition(), 0xC080FF, 0xFFFFFF, "THE REALITY ARCHITECT", p.getGameProfile().getName() + " walks among you");
        }
    }

    public static void preview(ServerPlayer p) {
        RealityOps.cinematic(p, CinematicType.RUPTURE, 0, p.getEyePosition().add(0, 30, 0), 0xC080FF, 0x000000, "THE FINAL RUPTURE", "preview — nothing will be harmed");
    }

    // ------------------------------------------------------------------ abilities

    private static boolean ready(ServerPlayer p, int ticks) {
        long now = p.serverLevel().getGameTime();
        Long until = COOLDOWN.get(p.getUUID());
        if (until != null && until > now) return false;
        COOLDOWN.put(p.getUUID(), now + ticks);
        return true;
    }

    public static void use(ServerPlayer p, ItemStack stack) {
        if (!CreatorAuthority.mayWield(p) || !CreatorAuthority.validSerial(ItemData.read(stack).getLong("rupture_serial"))) return;
        if (p.isShiftKeyDown() && p.getXRot() < -70f) {
            if (ready(p, 400)) finalRupture(p, mode());
        } else if (p.isShiftKeyDown()) {
            if (ready(p, 30)) disassemble(p);
        } else if (ready(p, 40)) {
            tear(p);
        }
    }

    private static HitResult aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, limit, new AABB(eye, limit).inflate(1), e -> e != p && !e.isSpectator() && e.isPickable(), range * range);
        return ent != null ? ent : block;
    }

    /** Reality Tear: rips the universe open where the wielder points. */
    public static void tear(ServerPlayer p) {
        HitResult hit = aim(p, 48);
        Vec3 at = hit.getLocation().add(0, 1.5, 0);
        RealityTearEntity tear = RvEntities.REALITY_TEAR.get().create(p.serverLevel());
        if (tear == null) return;
        tear.setup(4f, 140, p.getUUID());
        tear.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        p.serverLevel().addFreshEntity(tear);
        p.serverLevel().playSound(null, at.x, at.y, at.z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 2.5f, 1.4f);
    }

    /** Existence Disassembly: unmakes the creature in sight, or (pointing at terrain) a restorable region. */
    public static void disassemble(ServerPlayer p) {
        HitResult hit = aim(p, 48);
        ServerLevel level = p.serverLevel();
        if (hit instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            level.sendParticles(RvParticles.STREAK.get().with(0xC080FF, 1f, 30), c.x, c.y, c.z, 60, e.getBbWidth(), e.getBbHeight() / 2, e.getBbWidth(), 0.2);
            level.playSound(null, c.x, c.y, c.z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1.5f, 1.8f);
            if (e instanceof Player target) {
                target.hurt(level.damageSources().magic(), 10f);
            } else if (e instanceof LivingEntity le) {
                le.hurt(level.damageSources().magic(), Float.MAX_VALUE);
                if (le.isAlive()) le.discard();
            } else {
                e.discard();
            }
            return;
        }
        if (hit.getType() != HitResult.Type.BLOCK) return;
        int r = Math.min(radius(), 12);
        disassembleArea(level, ((BlockHitResult) hit).getBlockPos(), r, 0);
        p.displayClientMessage(Component.literal("Region disassembled. /multiverse weapon restore puts it back.").withColor(0xC080FF), true);
    }

    /** Removes a sphere wave by wave (snapshotted first). If {@code restoreAfter} &gt; 0 the region comes back by itself. */
    public static Snapshot disassembleArea(ServerLevel level, BlockPos centre, int r, int restoreAfter) {
        Map<BlockPos, BlockState> saved = new HashMap<>();
        List<List<BlockPos>> shells = new ArrayList<>();
        for (int i = 0; i <= r; i++) shells.add(new ArrayList<>());
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            double d = Math.sqrt(pos.distSqr(centre));
            if (d > r) continue;
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || s.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) continue;
            BlockPos im = pos.immutable();
            saved.put(im, s);
            shells.get((int) d).add(im);
        }
        Snapshot snap = new Snapshot(level, saved);
        SNAPSHOTS.push(snap);
        while (SNAPSHOTS.size() > 16) SNAPSHOTS.removeLast();
        for (int i = 0; i <= r; i++) {
            List<BlockPos> shell = shells.get(i);
            Scheduler.later(i * 2, () -> {
                for (BlockPos pos : shell) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.random.nextInt(6) == 0) level.sendParticles(RvParticles.STREAK.get().with(0xA040FF, 0.8f, 20), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.05);
                }
            });
        }
        if (restoreAfter > 0) Scheduler.later(restoreAfter, () -> restore(snap));
        return snap;
    }

    public static int restoreLatest() {
        Snapshot s = SNAPSHOTS.poll();
        return s == null ? 0 : restore(s);
    }

    private static int restore(Snapshot s) {
        SNAPSHOTS.remove(s);
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : s.blocks().entrySet()) {
            if (s.level().getBlockState(e.getKey()).isAir()) {
                s.level().setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ ultimate

    public enum Mode { VISUAL, AREA, UNIVERSE }

    public static Mode mode() {
        String m;
        try {
            m = RiftverseConfig.RUPTURE_MODE.get();
        } catch (IllegalStateException e) {
            m = "area";
        }
        return switch (m.toLowerCase()) {
            case "visual" -> Mode.VISUAL;
            case "universe" -> Mode.UNIVERSE;
            default -> Mode.AREA;
        };
    }

    public static int radius() {
        return RiftverseConfig.get(RiftverseConfig.RUPTURE_RADIUS, 10);
    }

    /** THE FINAL RUPTURE: charge, distort the sky, crack, fracture overhead, consume, silence, scar. */
    public static void finalRupture(ServerPlayer p, Mode mode) {
        ServerLevel level = p.serverLevel();
        Vec3 sky = p.position().add(0, 28, 0);
        List<ServerPlayer> watchers = level.getPlayers(o -> o.distanceToSqr(p) < 160 * 160);
        for (ServerPlayer o : watchers) RealityOps.cinematic(o, CinematicType.RUPTURE, 0, sky, 0xC080FF, 0x000000, "THE FINAL RUPTURE", mode == Mode.VISUAL ? "" : "reality gives way");
        RealityOps.shield(p, 300);
        level.playSound(null, p.blockPosition(), RvSounds.BLACK_HOLE_PULL.get(), SoundSource.PLAYERS, 4f, 0.4f);
        for (int i = 0; i < 6; i++) {
            int k = i;
            Scheduler.later(10 + i * 10, () -> level.sendParticles(RvParticles.INFALL.get().with(0xC080FF, 1.4f, 30), p.getX(), p.getY() + 1, p.getZ(), 40 + k * 10, 4, 3, 4, 0.1));
        }
        Scheduler.later(80, () -> {
            RealityTearEntity tear = RvEntities.REALITY_TEAR.get().create(level);
            if (tear == null) return;
            tear.setup(14f, 150, p.getUUID());
            tear.moveTo(sky.x, sky.y, sky.z, p.getYRot(), 0);
            level.addFreshEntity(tear);
            level.playSound(null, sky.x, sky.y, sky.z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 6f, 0.3f);
        });
        Scheduler.later(150, () -> {
            switch (mode) {
                case AREA -> disassembleArea(level, p.blockPosition().below(2), Math.max(radius(), 8), 20 * 60);
                case UNIVERSE -> {
                    UniverseId id = RealityOps.universeOf(p);
                    if (id != null) {
                        RealityOps.Outcome o = RealityOps.erase(p.server, id, p);
                        p.displayClientMessage(Component.literal(o.message()).withColor(o.ok() ? 0xC080FF : 0xFF5070), false);
                    } else {
                        p.displayClientMessage(Component.literal("Only a generated universe can be ruptured; nothing here was erased.").withColor(0xFF5070), false);
                    }
                }
                default -> {}
            }
        });
        Scheduler.later(230, () -> level.sendParticles(RvParticles.RING.get().with(0x2A0040, 24f, 60), sky.x, sky.y, sky.z, 2, 0, 0, 0, 0));
    }

    public static int snapshotCount() {
        return SNAPSHOTS.size();
    }

    @Nullable
    public static UUID ownerOf(ItemStack stack) {
        var t = ItemData.read(stack);
        return t.hasUUID("rupture_owner") ? t.getUUID("rupture_owner") : null;
    }
}
