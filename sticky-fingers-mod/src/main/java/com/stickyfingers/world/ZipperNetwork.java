package com.stickyfingers.world;

import com.stickyfingers.ability.AbilityType;
import com.stickyfingers.ability.Fx;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.ZipperEntity;
import com.stickyfingers.network.NetworkSnapshotPayload;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-wide registry of live zippers keyed by their unique network ID, plus all network operations
 * (linking, travelling, snapshotting for the network screen).
 */
public final class ZipperNetwork {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<Integer, ZipperEntity> LIVE = new LinkedHashMap<>();

    public static int nextId() { return NEXT_ID.getAndIncrement(); }

    public static void register(ZipperEntity z) {
        LIVE.put(z.zipperId(), z);
        ServerPlayer owner = z.ownerPlayer();
        if (owner != null) enforceLimit(owner);
    }

    public static void unregister(ZipperEntity z) {
        LIVE.remove(z.zipperId());
        for (ZipperEntity other : LIVE.values()) other.removeLink(z.zipperId());
    }

    public static void clear() { LIVE.clear(); }

    @Nullable
    public static ZipperEntity byId(int id) {
        ZipperEntity z = LIVE.get(id);
        return z != null && !z.isRemoved() ? z : null;
    }

    public static List<ZipperEntity> ownedBy(UUID owner) {
        List<ZipperEntity> out = new ArrayList<>();
        for (ZipperEntity z : LIVE.values()) if (!z.isRemoved() && owner.equals(z.ownerId())) out.add(z);
        return out;
    }

    private static void enforceLimit(ServerPlayer owner) {
        List<ZipperEntity> mine = ownedBy(owner.getUUID());
        int max = SFConfig.MAX_ZIPPERS.get();
        for (int i = 0; i < mine.size() - max; i++) mine.get(i).closeAndDiscard();
    }

    /** Two-way link. */
    public static void connect(ZipperEntity a, ZipperEntity b) {
        a.addLink(b.zipperId());
        b.addLink(a.zipperId());
    }

    @Nullable
    public static ZipperEntity pickDestination(ZipperEntity src, Entity traveller) {
        if (traveller instanceof ServerPlayer sp && src.isOwnedBy(sp)) {
            int exit = StandData.get(sp).exitZipper();
            if (src.links().contains(exit)) {
                ZipperEntity z = byId(exit);
                if (z != null) return z;
            }
        }
        for (int id : src.links()) {
            ZipperEntity z = byId(id);
            if (z != null) return z;
        }
        return null;
    }

    /** Exit point in front of a zipper that fits the traveller. */
    public static Vec3 exitPoint(ZipperEntity dest, Entity e) {
        Vec3 n = dest.normal();
        Vec3 base = dest.position();
        if (n.y > 0.7) return base.add(0, 0.1, 0);
        if (n.y < -0.7) return base.add(0, -e.getBbHeight() - 0.2, 0);
        Vec3 out = base.add(n.x * 0.9, 0, n.z * 0.9);
        // stand the traveller on the floor below the zipper centre
        return new Vec3(out.x, Math.floor(base.y - dest.length() * 0.35) + 0.01, out.z);
    }

    public static void transport(Entity e, ZipperEntity from, ZipperEntity to) {
        if (!(to.level() instanceof ServerLevel destLevel) || !(from.level() instanceof ServerLevel srcLevel)) return;
        if (!to.isOpen()) to.open();
        Vec3 exit = exitPoint(to, e);
        Fx.sound(srcLevel, e.position(), ModSounds.TELEPORT.get(), 0.8f, 1.2f);
        Fx.particles(srcLevel, ModParticles.VOID_MOTE.get(), e.position().add(0, 1, 0), 20, 0.4, 0.05);
        Vec3 n = to.normal();
        float yaw = Math.abs(n.y) > 0.7 ? e.getYRot() : (float) (Math.toDegrees(Math.atan2(-n.x, n.z)));
        if (e instanceof ServerPlayer sp) {
            Fx.screen(sp, ScreenFx.ZIP_TRANSITION, 1f, 14);
            sp.teleportTo(destLevel, exit.x, exit.y, exit.z, yaw, sp.getXRot());
        } else if (destLevel == srcLevel) {
            e.teleportTo(exit.x, exit.y, exit.z);
            e.setYRot(yaw);
        } else {
            return;
        }
        e.setDeltaMovement(n.scale(0.25));
        e.hurtMarked = true;
        e.fallDistance = 0;
        Fx.sound(destLevel, exit, ModSounds.ZIPPER_OPEN.get(), 0.8f, 1.3f);
        Fx.particles(destLevel, ModParticles.ZIPPER_SPARK.get(), exit.add(0, 1, 0), 16, 0.4, 0.1);
        Fx.particles(destLevel, ModParticles.VOID_MOTE.get(), exit.add(0, 1, 0), 20, 0.4, 0.05);
    }

    /** First own zipper under the crosshair. */
    @Nullable
    public static ZipperEntity raycast(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        ZipperEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (ZipperEntity z : ownedBy(player.getUUID())) {
            if (z.level() != player.level()) continue;
            double r = Math.max(0.6, Math.max(z.length(), z.width()) * 0.5);
            AABB box = new AABB(z.position(), z.position()).inflate(r);
            Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(range)));
            if (hit.isPresent()) {
                double t = hit.get().distanceToSqr(eye);
                if (t < bestT) { bestT = t; best = z; }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- network screen actions

    @Nullable
    private static ZipperEntity own(ServerPlayer sp, int id) {
        ZipperEntity z = byId(id);
        return z != null && z.isOwnedBy(sp) ? z : null;
    }

    public static void travelTo(ServerPlayer sp, int id) {
        ZipperEntity z = own(sp, id);
        StandData data = StandData.get(sp);
        if (z == null || !data.hasStand()) return;
        double range = SFConfig.range(AbilityType.ZIPPER_NETWORK);
        if (z.level() != sp.level() || z.position().distanceTo(sp.position()) > range) {
            sp.displayClientMessage(Component.translatable("message.stickyfingers.out_of_range"), true);
            return;
        }
        float cost = SFConfig.cost(AbilityType.ZIPPER_TELEPORT) * 0.5f;
        if (data.energy() < cost) {
            sp.displayClientMessage(Component.translatable("message.stickyfingers.no_energy"), true);
            return;
        }
        data.setEnergy(data.energy() - cost);
        ZipperEntity origin = ZipperEntity.create((ServerLevel) sp.level(), sp, com.stickyfingers.entity.ZipperKind.ESCAPE,
                sp.position().add(0, 1, 0), sp.getLookAngle().reverse().multiply(1, 0, 1), 90f, 2f, 0.9f).life(30);
        sp.level().addFreshEntity(origin);
        origin.open();
        transport(sp, origin, z);
        sendSnapshot(sp, false);
    }

    public static void link(ServerPlayer sp, int a, int b) {
        ZipperEntity za = own(sp, a), zb = own(sp, b);
        if (za == null || zb == null || za == zb) return;
        connect(za, zb);
        Fx.sound((ServerLevel) za.level(), za.position(), ModSounds.ZIPPER_PLACE.get(), 0.7f, 1.4f);
        sendSnapshot(sp, false);
    }

    public static void unlink(ServerPlayer sp, int a, int b) {
        ZipperEntity za = own(sp, a), zb = own(sp, b);
        if (za != null) za.removeLink(b);
        if (zb != null) zb.removeLink(a);
        sendSnapshot(sp, false);
    }

    public static void close(ServerPlayer sp, int id) {
        ZipperEntity z = own(sp, id);
        if (z != null) z.closeAndDiscard();
        sendSnapshot(sp, false);
    }

    public static void toggleOpen(ServerPlayer sp, int id) {
        ZipperEntity z = own(sp, id);
        if (z == null) return;
        if (z.isOpen()) z.close(); else z.open();
        sendSnapshot(sp, false);
    }

    public static void setExit(ServerPlayer sp, int id) {
        StandData.get(sp).setExitZipper(own(sp, id) != null ? id : -1);
        sendSnapshot(sp, false);
    }

    public static void closeAll(ServerPlayer sp) {
        ownedBy(sp.getUUID()).forEach(ZipperEntity::closeAndDiscard);
        sendSnapshot(sp, false);
    }

    public static void sendSnapshot(ServerPlayer sp, boolean openScreen) {
        List<NetworkSnapshotPayload.Entry> entries = new ArrayList<>();
        for (ZipperEntity z : ownedBy(sp.getUUID())) {
            entries.add(new NetworkSnapshotPayload.Entry(z.zipperId(), z.kind().ordinal(), z.blockPosition(),
                    z.level().dimension().location().toString(), z.isOpen(), z.life(),
                    z.links().stream().mapToInt(Integer::intValue).toArray()));
        }
        PacketDistributor.sendToPlayer(sp, new NetworkSnapshotPayload(entries, openScreen));
    }

    private ZipperNetwork() {}
}
