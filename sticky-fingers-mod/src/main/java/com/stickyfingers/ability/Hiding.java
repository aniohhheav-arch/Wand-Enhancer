package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.ZipperEntity;
import com.stickyfingers.entity.ZipperKind;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Zipper Escape / Zipper Ambush: the user steps inside zipper space and emerges later. */
public final class Hiding {
    public static void start(ServerPlayer player, int ticks, boolean ambush) {
        StandData data = StandData.get(player);
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        ZipperEntity z = ZipperEntity.create(level, player, ZipperKind.ESCAPE, player.position().add(0, 1, 0).add(look.scale(0.4)),
                look.reverse(), 90f, 2.2f, 1.1f).life(ticks + 40);
        level.addFreshEntity(z);
        z.open();
        data.setHideOrigin(z.zipperId(), player.position());
        data.setHidden(ticks, ambush);
        StandManager.dismiss(player);
        player.addEffect(new MobEffectInstance(ModEffects.IN_ZIPPER, ticks + 5, 0, false, false, true));
        player.setInvisible(true);
        Fx.screen(player, ScreenFx.ZIP_TRANSITION, 1f, 16);
        Fx.screen(player, ScreenFx.VIGNETTE, 1f, ticks);
        Fx.sound(level, player.position(), ModSounds.TELEPORT.get(), 1f, 0.8f);
        level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), m -> m.getTarget() == player)
                .forEach(m -> m.setTarget(null));
    }

    static void tick(ServerPlayer player, StandData data) {
        if (!data.isHidden()) return;
        data.tickHidden();
        player.setInvisible(true);
        if (player.tickCount % 4 == 0) {
            ZipperEntity origin = ZipperNetwork.byId(data.hideOriginZipper());
            if (origin != null) {
                Fx.particles(player.serverLevel(), ModParticles.VOID_MOTE.get(), origin.position(), 2, 0.3, 0.02);
            }
        }
        if (!data.isHidden()) emerge(player);
    }

    /** Leave zipper space. Ambush emerges behind the nearest enemy; escape uses the selected exit zipper. */
    public static void emerge(ServerPlayer player) {
        StandData data = StandData.get(player);
        ServerLevel level = player.serverLevel();
        data.setHidden(0, data.isAmbushHide());
        player.removeEffect(ModEffects.IN_ZIPPER);
        player.setInvisible(false);

        Vec3 dest = null;
        float yaw = player.getYRot();
        if (data.isAmbushHide()) {
            double range = SFConfig.range(AbilityType.ZIPPER_AMBUSH);
            LivingEntity victim = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                            e -> e != player && e.isAlive() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
            if (victim != null) {
                Vec3 behind = victim.position().subtract(victim.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
                dest = Abilities.safeSpot(level, behind, victim.position());
                Vec3 face = victim.position().subtract(dest);
                yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
                victim.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, 30, 0, false, true));
            }
        }
        if (dest == null) {
            ZipperEntity exit = ZipperNetwork.byId(data.exitZipper());
            if (exit != null && exit.isOwnedBy(player) && exit.level() == level) {
                dest = ZipperNetwork.exitPoint(exit, player);
                exit.open();
            }
        }
        if (dest == null) dest = data.hideOrigin().equals(Vec3.ZERO) ? player.position() : data.hideOrigin();

        Vec3 look = Vec3.directionFromRotation(0, yaw);
        ZipperEntity out = ZipperEntity.create(level, player, ZipperKind.ESCAPE, dest.add(0, 1, 0).subtract(look.scale(0.3)),
                look, 90f, 2.2f, 1.1f).life(24);
        level.addFreshEntity(out);
        out.open();
        player.teleportTo(level, dest.x, dest.y, dest.z, yaw, player.getXRot());
        Fx.screen(player, ScreenFx.ZIP_TRANSITION, 1f, 14);
        Fx.sound(level, dest, ModSounds.ZIPPER_OPEN.get(), 1f, 1.2f);
        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), dest.add(0, 1, 0), 18, 0.4, 0.1);
        ZipperEntity origin = ZipperNetwork.byId(data.hideOriginZipper());
        if (origin != null) origin.closeAndDiscard();
        StandManager.summon(player);
    }

    static void forceEmerge(ServerPlayer player) {
        StandData data = StandData.get(player);
        if (data.isHidden()) {
            data.setHidden(0, false);
            player.removeEffect(ModEffects.IN_ZIPPER);
            player.setInvisible(false);
        }
    }

    private Hiding() {}
}
