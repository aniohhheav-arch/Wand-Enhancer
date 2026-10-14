package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.entity.*;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The two cinematic abilities: Zipper Combo and the Zipper World ultimate. */
public final class Sequences {

    private static float dmg(double base) {
        return (float) (base * SFConfig.DAMAGE_MULTIPLIER.get());
    }

    private static void hit(ServerPlayer p, LivingEntity e, float amount) {
        e.invulnerableTime = 0;
        e.hurt(p.serverLevel().damageSources().playerAttack(p), amount);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    // ================================================================= combo

    public static boolean combo(ServerPlayer p) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.ZIPPER_COMBO));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        List<ZipperEntity> ring = new ArrayList<>();
        StandEntity stand = StandManager.ensure(p);
        Fx.sound(level, p.position(), ModSounds.STAND_SUMMON.get(), 1f, 1.2f);
        Vec3 home = p.position();

        Scheduler.run(p, new Scheduler.Sequence() {
            Vec3 last = home;

            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (!target.isAlive()) { finish(pl); return false; }
                Vec3 tc = target.position().add(0, target.getBbHeight() * 0.5, 0);
                StandEntity s = StandManager.stand(pl);
                if (t == 0 && s != null) {
                    s.play(StandAnim.CAST);
                    s.strikeAt(target.position().add(pl.position().subtract(target.position()).normalize().scale(1.2)), 10);
                    Fx.afterimage(level, pl.position(), target.position());
                }
                if (t == 4) {
                    if (Abilities.canAffect(target)) target.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, 70, 0, false, true));
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3;
                        Vec3 pos = tc.add(Math.cos(a) * 2.6, (i % 2) * 0.6 - 0.2, Math.sin(a) * 2.6);
                        ZipperEntity z = Abilities.spawn(pl, ZipperKind.AERIAL, pos, tc.subtract(pos).multiply(1, 0, 1), 90f + (i * 25 % 60) - 30, 2.2f, 1.1f).life(90);
                        ring.add(z);
                    }
                    Fx.screen(pl, ScreenFx.SPEED_LINES, 0.6f, 60);
                }
                if (t >= 8 && t <= 36) {
                    if (s != null) {
                        if (t == 8) s.play(StandAnim.BARRAGE);
                        s.strikeAt(target.position().add(pl.position().subtract(target.position()).normalize().scale(1.1)), 3);
                    }
                    if (t % 2 == 0) {
                        hit(pl, target, dmg(1.0));
                        Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), tc.add(Abilities.randomOffset(pl, 0.6)), 1, 0, 0);
                        Fx.sound(level, tc, ModSounds.PUNCH.get(), 0.6f, 0.9f + pl.getRandom().nextFloat() * 0.4f);
                    }
                    if (t % 6 == 0) {
                        for (int i = 0; i < ring.size(); i++) {
                            ZipperEntity z = ring.get(i);
                            if ((i + t / 6) % 2 == 0) z.open(); else z.close();
                        }
                        Fx.screen(pl, ScreenFx.SHAKE, 0.3f, 5);
                    }
                    if (t == 10) Fx.sound(level, tc, ModSounds.PUNCH_BARRAGE.get(), 1f, 1.1f);
                }
                if (t >= 38 && t <= 58 && (t - 38) % 5 == 0 && !ring.isEmpty()) {
                    ZipperEntity z = ring.get(((t - 38) / 5) % ring.size());
                    z.open();
                    Vec3 dest = Abilities.safeSpot(level, z.position().subtract(0, 1, 0), pl.position());
                    Fx.afterimage(level, last, dest);
                    pl.teleportTo(level, dest.x, dest.y, dest.z, yawTo(dest, target.position()), 0);
                    last = dest;
                    hit(pl, target, dmg(2.5));
                    target.knockback(0.15, dest.x - target.getX(), dest.z - target.getZ());
                    Fx.sound(level, dest, ModSounds.ZIPPER_OPEN.get(), 0.7f, 1.5f);
                    Fx.sound(level, tc, ModSounds.PUNCH.get(), 1f, 0.8f);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), tc, 1, 0, 0);
                    Fx.screen(pl, ScreenFx.FOV_PUNCH, 0.5f, 5);
                    if (s != null) s.strikeAt(target.position().add(dest.subtract(target.position()).normalize().scale(1.0)), 5);
                }
                if (t == 60) {
                    Fx.screen(pl, ScreenFx.SLOWMO, 1f, 8);
                    Fx.sound(level, tc, ModSounds.SLOWMO.get(), 0.8f, 1.2f);
                    if (s != null) { s.play(StandAnim.HEAVY); s.strikeAt(target.position().add(pl.position().subtract(target.position()).normalize().scale(1.0)), 12); }
                }
                if (t == 66) {
                    target.removeEffect(ModEffects.UNZIPPED);
                    hit(pl, target, dmg(9));
                    Vec3 away = target.position().subtract(pl.position()).multiply(1, 0, 1).normalize();
                    target.setDeltaMovement(away.x * 2.4, 0.7, away.z * 2.4);
                    target.hurtMarked = true;
                    Fx.sound(level, tc, ModSounds.FINAL_IMPACT.get(), 1.4f, 1.1f);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), tc, 4, 0.3, 0);
                    Fx.particles(level, ModParticles.ZIPPER_TOOTH.get(), tc, 24, 0.4, 0.3);
                    Fx.shake(level, tc, 16, 1.2f, 14);
                    Fx.screen(pl, ScreenFx.FLASH, 0.6f, 6);
                }
                if (t == 72) { finish(pl); return false; }
                return true;
            }

            void finish(ServerPlayer pl) {
                ring.forEach(ZipperEntity::closeAndDiscard);
                if (!ring.isEmpty()) Fx.sound(level, ring.getFirst().position(), ModSounds.WORLD_CLOSE.get(), 0.8f, 1.3f);
                ring.clear();
            }

            @Override
            public void cancel(ServerPlayer pl) { finish(pl); }

            @Override
            public String key() { return "combo"; }
        });
        return true;
    }

    // ================================================================= ZIPPER WORLD

    public static boolean zipperWorld(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double radius = SFConfig.range(AbilityType.ZIPPER_WORLD);
        Vec3 origin = p.position();
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius), e ->
                        e != p && e.isAlive() && !(e instanceof DecoyEntity) && !e.isSpectator()
                                && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == p || e instanceof ServerPlayer && SFConfig.PVP_EFFECTS.get()))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(12).toList();

        ZipperEntity field = ZipperEntity.create(level, p, ZipperKind.FIELD, origin.add(0, 0.05, 0), new Vec3(0, 1, 0), 0f, (float) radius, SFConfig.WORLD_ZIPPER_COUNT.get());
        field.life(400);
        level.addFreshEntity(field);
        List<ZipperEntity> seams = new ArrayList<>();

        final int openAt = 30;
        final int hops = enemies.isEmpty() ? 6 : Math.min(14, Math.max(8, enemies.size() * 2));
        final int hopEvery = 5;
        final int slowAt = openAt + 8 + hops * hopEvery;
        final int finalAt = slowAt + 18;
        final int closeAt = finalAt + 12;

        p.addEffect(new MobEffectInstance(ModEffects.IN_ZIPPER, closeAt + 10, 0, false, false, true));

        Scheduler.run(p, new Scheduler.Sequence() {
            Vec3 last = origin;

            @Override
            public boolean tick(ServerPlayer pl, int t) {
                StandEntity s = StandManager.ensure(pl);
                if (t == 0) {
                    if (s != null) { s.play(StandAnim.SLAM); s.strikeAt(origin.add(pl.getLookAngle().multiply(1, 0, 1).normalize()), 20); }
                    Fx.sound(level, origin, ModSounds.WORLD_SLAM.get(), 2f, 1f);
                    Fx.shake(level, origin, radius * 2, 1.5f, 20);
                    Fx.screen(pl, ScreenFx.FLASH, 0.5f, 8);
                    Fx.screen(pl, ScreenFx.FOV_PUNCH, 1f, 12);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), origin.add(0, 0.2, 0), 6, 1.5, 0);
                    Fx.particles(level, ModParticles.ZIPPER_TOOTH.get(), origin.add(0, 0.5, 0), 40, 2, 0.4);
                }
                if (t > 0 && t < openAt && t % 2 == 0) {
                    // seams race outward across every surface
                    double r = radius * t / openAt;
                    for (int i = 0; i < 8; i++) {
                        double a = pl.getRandom().nextDouble() * Math.PI * 2;
                        Fx.particles(level, ModParticles.SEAM_GLOW.get(), origin.add(Math.cos(a) * r, 0.2 + pl.getRandom().nextDouble() * 3, Math.sin(a) * r), 2, 0.2, 0);
                    }
                    Fx.sound(level, origin, ModSounds.ZIPPER_PLACE.get(), 0.6f, 0.6f + t / (float) openAt);
                }
                if (t == 10) {
                    for (LivingEntity e : enemies) {
                        ZipperEntity z = ZipperEntity.create(level, pl, ZipperKind.MOB, e.position().add(0, e.getBbHeight() * 0.55, 0),
                                pl.position().subtract(e.position()).multiply(1, 0, 1), 15f, e.getBbWidth() + 0.7f, 0.6f)
                                .attachTo(e).life(closeAt - 10 + 20).effectDuration(closeAt);
                        level.addFreshEntity(z);
                        seams.add(z);
                    }
                }
                if (t == openAt) {
                    field.open();
                    seams.forEach(ZipperEntity::open);
                    enemies.forEach(e -> e.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, closeAt - openAt + 10, 0, false, true)));
                    Fx.sound(level, origin, ModSounds.WORLD_OPEN.get(), 2f, 1f);
                    Fx.screen(pl, ScreenFx.SPEED_LINES, 1f, closeAt - openAt);
                    Fx.screen(pl, ScreenFx.VIGNETTE, 0.8f, closeAt - openAt);
                    Fx.shake(level, origin, radius * 2, 0.8f, 12);
                }
                if (t >= openAt + 8 && t < slowAt && (t - openAt - 8) % hopEvery == 0) {
                    int hop = (t - openAt - 8) / hopEvery;
                    LivingEntity e = enemies.isEmpty() ? null : enemies.get(hop % enemies.size());
                    Vec3 focus = e != null && e.isAlive() ? e.position() : origin;
                    double a = hop * 2.39996 + pl.getRandom().nextDouble();
                    Vec3 wanted = focus.add(Math.cos(a) * 2.2, (hop % 3 == 0) ? 1.5 : 0, Math.sin(a) * 2.2);
                    Vec3 dest = Abilities.safeSpot(level, wanted, focus);
                    ZipperEntity portal = ZipperEntity.create(level, pl, ZipperKind.AERIAL, dest.add(0, 1, 0), focus.subtract(dest).multiply(1, 0, 1), 90f, 2.2f, 1.2f).life(12);
                    level.addFreshEntity(portal);
                    portal.open();
                    Fx.afterimage(level, last, dest);
                    pl.teleportTo(level, dest.x, dest.y, dest.z, yawTo(dest, focus), 10);
                    pl.fallDistance = 0;
                    last = dest;
                    if (s != null) { s.play(hop % 2 == 0 ? StandAnim.BARRAGE : StandAnim.HEAVY); s.strikeAt(focus.add(dest.subtract(focus).normalize().scale(1.0)), hopEvery); }
                    if (e != null && e.isAlive()) {
                        hit(pl, e, dmg(3.0));
                        Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
                        Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), ec, 2, 0.2, 0);
                        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), ec, 8, 0.3, 0.15);
                        Fx.sound(level, ec, ModSounds.PUNCH_HEAVY.get(), 0.7f, 1.2f + pl.getRandom().nextFloat() * 0.3f);
                    }
                    Fx.sound(level, dest, ModSounds.ZIPPER_OPEN.get(), 0.6f, 1.6f);
                    Fx.screen(pl, ScreenFx.FOV_PUNCH, 0.4f, 4);
                    Fx.screen(pl, ScreenFx.SHAKE, 0.35f, 4);
                }
                if (t == slowAt) {
                    // the world holds its breath
                    Fx.sound(level, origin, ModSounds.SLOWMO.get(), 1.5f, 1f);
                    for (ServerPlayer viewer : level.players()) {
                        if (viewer.distanceToSqr(origin) < radius * radius * 4) Fx.screen(viewer, ScreenFx.SLOWMO, 1f, finalAt - slowAt + 4);
                    }
                    for (LivingEntity e : enemies) {
                        e.setDeltaMovement(Vec3.ZERO);
                        e.hurtMarked = true;
                    }
                    Vec3 focus = enemies.isEmpty() ? origin.add(pl.getLookAngle().scale(3)) : enemies.getFirst().position();
                    Vec3 dest = Abilities.safeSpot(level, focus.add(focus.subtract(origin).multiply(1, 0, 1).normalize().scale(-2.5)), origin);
                    Fx.afterimage(level, last, dest);
                    pl.teleportTo(level, dest.x, dest.y, dest.z, yawTo(dest, focus), 0);
                    if (s != null) { s.play(StandAnim.HEAVY); s.strikeAt(focus.add(dest.subtract(focus).normalize().scale(1.2)), finalAt - slowAt + 6); }
                }
                if (t > slowAt && t < finalAt) {
                    enemies.forEach(e -> { e.setDeltaMovement(Vec3.ZERO); e.hurtMarked = true; });
                }
                if (t == finalAt) {
                    for (LivingEntity e : enemies) {
                        if (!e.isAlive()) continue;
                        e.removeEffect(ModEffects.UNZIPPED);
                        hit(pl, e, dmg(12));
                        Vec3 away = e.position().subtract(origin).multiply(1, 0, 1);
                        away = away.lengthSqr() < 1e-3 ? pl.getLookAngle() : away.normalize();
                        e.setDeltaMovement(away.x * 2.2, 0.9, away.z * 2.2);
                        e.hurtMarked = true;
                        Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
                        Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), ec, 4, 0.4, 0);
                        Fx.particles(level, ModParticles.ZIPPER_TOOTH.get(), ec, 20, 0.5, 0.3);
                    }
                    Fx.sound(level, pl.position(), ModSounds.FINAL_IMPACT.get(), 2.5f, 0.9f);
                    Fx.shake(level, pl.position(), radius * 2, 2f, 24);
                    for (ServerPlayer viewer : level.players()) {
                        if (viewer.distanceToSqr(origin) < radius * radius * 4) Fx.screen(viewer, ScreenFx.FLASH, 1f, 10);
                    }
                    Fx.screen(pl, ScreenFx.FOV_PUNCH, 1.5f, 14);
                }
                if (t == closeAt) {
                    finish(pl);
                    return false;
                }
                return true;
            }

            void finish(ServerPlayer pl) {
                field.closeAndDiscard();
                seams.forEach(ZipperEntity::closeAndDiscard);
                seams.clear();
                pl.removeEffect(ModEffects.IN_ZIPPER);
                Fx.sound(level, origin, ModSounds.WORLD_CLOSE.get(), 2f, 1f);
                Fx.shake(level, origin, radius * 2, 0.6f, 10);
                Fx.screen(pl, ScreenFx.ZIP_TRANSITION, 1f, 16);
                Vec3 home = Abilities.safeSpot(level, origin, pl.position());
                pl.teleportTo(level, home.x, home.y, home.z, pl.getYRot(), pl.getXRot());
            }

            @Override
            public void cancel(ServerPlayer pl) { finish(pl); }

            @Override
            public String key() { return "zipper_world"; }
        });
        return true;
    }

    private Sequences() {}
}
