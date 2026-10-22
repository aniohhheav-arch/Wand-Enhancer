package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.StandAnim;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import com.stickyfingers.world.TimeStopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Star Platinum ability implementations. */
public final class SPAbilities {

    public static boolean activate(AbilityType type, ServerPlayer p, StandData data, boolean alt) {
        return switch (type) {
            case SP_ORA_BARRAGE -> oraBarrage(p, data);
            case SP_PRECISION_STRIKE -> precisionStrike(p, data);
            case SP_HEAVY_PUNCH -> heavyPunch(p, data);
            case SP_UPPERCUT -> uppercut(p, data);
            case SP_STAR_FINGER -> starFinger(p, data);
            case SP_TIME_STOP -> timeStop(p, data);
            case SP_GUARD -> guard(p, data);
            case SP_GRAB_THROW -> grabThrow(p, data);
            case SP_ORA_FINISHER -> oraFinisher(p, data);
            case SP_THE_WORLD -> theWorld(p, data);
            default -> false;
        };
    }

    // ─── ORA ORA ORA Barrage ───────────────────────────────────────────────
    private static boolean oraBarrage(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_ORA_BARRAGE));
        if (target == null || !Abilities.canAffect(target)) return false;

        StandManager.animate(p, StandAnim.BARRAGE);
        ServerLevel level = p.serverLevel();
        float dmgPerHit = (float)(6.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
        final int HITS = 8;

        Fx.sound(level, target.position(), ModSounds.PUNCH_BARRAGE.get(), 1.2f, 1.0f);
        p.displayClientMessage(Component.literal("ORA ORA ORA!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (!target.isAlive()) return false;
                if (t < HITS * 4 && t % 4 == 0) {
                    target.hurt(pl.damageSources().playerAttack(pl), dmgPerHit);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position().add(0, 1, 0), 5, 0.3, 0.2);
                }
                if (t == HITS * 4 + 4) {
                    target.hurt(pl.damageSources().playerAttack(pl), dmgPerHit * 2);
                    Fx.shake(level, target.position(), 5, 0.8f, 8);
                    Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), target.position(), 20, 0.8, 0.15);
                    Fx.sound(level, target.position(), ModSounds.PUNCH_HEAVY.get(), 1.5f, 0.9f);
                    target.setDeltaMovement(pl.getLookAngle().scale(-1.5).add(0, 0.4, 0));
                    return false;
                }
                return t <= HITS * 4 + 4;
            }
            @Override public String key() { return "sp_barrage"; }
        });
        return true;
    }

    // ─── Precision Strike ─────────────────────────────────────────────────
    private static boolean precisionStrike(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_PRECISION_STRIKE));
        if (target == null || !Abilities.canAffect(target)) return false;

        StandManager.animate(p, StandAnim.HEAVY);
        ServerLevel level = p.serverLevel();
        float dmg = (float)(10.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 6) {
                    if (!target.isAlive()) return false;
                    target.hurt(pl.damageSources().playerAttack(pl), dmg);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position().add(0, 1, 0), 20, 0.2, 0.3);
                    Fx.sound(level, target.position(), ModSounds.PUNCH.get(), 1.0f, 1.2f);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "sp_strike"; }
        });
        return true;
    }

    // ─── Heavy Punch ──────────────────────────────────────────────────────
    private static boolean heavyPunch(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_HEAVY_PUNCH));
        if (target == null || !Abilities.canAffect(target)) return false;

        StandManager.animate(p, StandAnim.SLAM);
        ServerLevel level = p.serverLevel();
        float dmg = (float)(14.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 8) {
                    if (!target.isAlive()) return false;
                    target.hurt(pl.damageSources().playerAttack(pl), dmg);
                    Vec3 knockback = target.position().subtract(pl.position()).normalize().scale(2.0).add(0, 0.5, 0);
                    target.setDeltaMovement(knockback);
                    List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                            target.getBoundingBox().inflate(3), e -> e != target && e != pl && Abilities.canAffect(e));
                    for (LivingEntity e : nearby) {
                        e.hurt(pl.damageSources().playerAttack(pl), dmg * 0.4f);
                        e.setDeltaMovement(e.position().subtract(target.position()).normalize().scale(1.5).add(0, 0.3, 0));
                    }
                    Fx.shake(level, target.position(), 8, 0.8f, 10);
                    Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), target.position(), 30, 1.0, 0.15);
                    Fx.sound(level, target.position(), ModSounds.PUNCH_HEAVY.get(), 1.5f, 0.8f);
                    Fx.screen(pl, ScreenFx.SHAKE, 0.7f, 8);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "sp_heavy"; }
        });
        return true;
    }

    // ─── Uppercut ─────────────────────────────────────────────────────────
    private static boolean uppercut(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_UPPERCUT));
        if (target == null || !Abilities.canAffect(target)) return false;

        StandManager.animate(p, StandAnim.HEAVY);
        ServerLevel level = p.serverLevel();
        float dmg = (float)(12.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 5) {
                    if (!target.isAlive()) return false;
                    target.hurt(pl.damageSources().playerAttack(pl), dmg);
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 2.5, 0));
                    Fx.particles(level, ModParticles.VOID_MOTE.get(), target.position().add(0, 1, 0), 20, 0.4, 0.25);
                    Fx.sound(level, target.position(), ModSounds.PUNCH.get(), 1.0f, 0.7f);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "sp_uppercut"; }
        });
        return true;
    }

    // ─── Star Finger ──────────────────────────────────────────────────────
    private static boolean starFinger(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_STAR_FINGER));
        if (target == null || !Abilities.canAffect(target)) return false;

        StandManager.animate(p, StandAnim.CAST);
        ServerLevel level = p.serverLevel();
        float dmg = (float)(9.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
        Fx.sound(level, p.position(), ModSounds.STAR_FINGER.get(), 1.0f, 1.0f);

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 4) {
                    if (!target.isAlive()) return false;
                    target.hurt(pl.damageSources().playerAttack(pl), dmg);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position().add(0, 1.2, 0), 30, 0.1, 0.4);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "sp_starfinger"; }
        });
        return true;
    }

    // ─── Time Stop (toggle) ───────────────────────────────────────────────
    static boolean timeStop(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        if (data.isTimeStopActive()) {
            data.setTimeStop(false, 0);
            TimeStopManager.releaseAll(level);
            Fx.sound(level, p.position(), ModSounds.TIME_RESUME.get(), 1.5f, 1.0f);
            p.displayClientMessage(Component.literal("Time has resumed.").withStyle(ChatFormatting.YELLOW), true);
            StandManager.sync(p);
        } else {
            int duration = SFConfig.duration(AbilityType.SP_TIME_STOP);
            data.setTimeStop(true, duration);
            TimeStopManager.freezeArea(level, p.position(), 24.0, p.getId());
            Fx.sound(level, p.position(), ModSounds.TIME_STOP.get(), 2.0f, 1.0f);
            Fx.particles(level, ModParticles.VOID_MOTE.get(), p.position(), 80, 8.0, 0.05);
            Fx.screen(p, ScreenFx.SHAKE, 1.0f, 12);
            p.displayClientMessage(Component.literal("ZA WARUDO! Time has stopped.")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            StandManager.animate(p, StandAnim.SUMMON);
            StandManager.sync(p);
        }
        return true;
    }

    // ─── Guard ────────────────────────────────────────────────────────────
    private static boolean guard(ServerPlayer p, StandData data) {
        int dur = SFConfig.duration(AbilityType.SP_GUARD);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 0));
        Fx.sound(p.serverLevel(), p.position(), ModSounds.STAND_SUMMON.get(), 1.0f, 1.3f);
        Fx.particles(p.serverLevel(), ModParticles.VOID_MOTE.get(), p.position().add(0, 1, 0), 20, 0.5, 0.08);
        StandManager.animate(p, StandAnim.IDLE);
        return true;
    }

    // ─── Grab and Throw ───────────────────────────────────────────────────
    private static boolean grabThrow(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_GRAB_THROW));
        if (target == null || !Abilities.canAffect(target)) return false;

        StandManager.animate(p, StandAnim.HEAVY);
        ServerLevel level = p.serverLevel();
        Vec3 throwDir = p.getLookAngle().scale(2.5).add(0, 0.8, 0);

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 8) {
                    if (!target.isAlive()) return false;
                    target.hurt(pl.damageSources().playerAttack(pl), (float)(8.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0)));
                    target.setDeltaMovement(throwDir);
                    Fx.sound(level, target.position(), ModSounds.PUNCH.get(), 1.0f, 0.8f);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "sp_grab"; }
        });
        return true;
    }

    // ─── ORA ORA Finisher ─────────────────────────────────────────────────
    private static boolean oraFinisher(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.SP_ORA_FINISHER));
        if (target == null || !Abilities.canAffect(target)) return false;

        ServerLevel level = p.serverLevel();
        StandManager.animate(p, StandAnim.BARRAGE);
        Fx.sound(level, p.position(), ModSounds.ORA.get(), 2.0f, 1.0f);
        Fx.screen(p, ScreenFx.SHAKE, 0.5f, 6);

        float singleDmg = (float)(8.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (!target.isAlive()) return false;
                if (t < 36 && t % 3 == 0) {
                    target.hurt(pl.damageSources().playerAttack(pl), singleDmg);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position().add(0, 1, 0), 6, 0.3, 0.25);
                }
                if (t == 40) {
                    target.hurt(pl.damageSources().playerAttack(pl), singleDmg * 3);
                    target.setDeltaMovement(pl.getLookAngle().scale(-3.0).add(0, 0.6, 0));
                    Fx.shake(level, target.position(), 8, 1.0f, 12);
                    Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), target.position(), 50, 2.0, 0.2);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position(), 30, 1.0, 0.2);
                    Fx.sound(level, target.position(), ModSounds.FINAL_IMPACT.get(), 2.0f, 0.7f);
                    Fx.screen(pl, ScreenFx.SHAKE, 1.5f, 14);
                    pl.displayClientMessage(Component.literal("ORA ORA ORA!!! ★").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "sp_finisher"; }
        });
        return true;
    }

    // ─── The World (Ultimate Time Stop) ───────────────────────────────────
    private static boolean theWorld(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();

        if (data.isTimeStopActive()) {
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    p.getBoundingBox().inflate(16), e -> TimeStopManager.isFrozen(e.getId()) && e.isAlive());

            float dmg = (float)(20.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
            for (int i = 0; i < targets.size() && i < 4; i++) {
                LivingEntity t = targets.get(i);
                TimeStopManager.queueHit(t.getId(), dmg);
                Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), t.position().add(0, 1, 0), 12, 0.4, 0.2);
            }
            data.setTimeStop(false, 0);
            TimeStopManager.releaseAll(level);
            Fx.sound(level, p.position(), ModSounds.TIME_RESUME.get(), 2.0f, 0.8f);
            Fx.shake(level, p.position(), 12, 1.0f, 14);
            Fx.particles(level, ModParticles.VOID_MOTE.get(), p.position(), 80, 8.0, 0.06);
            p.displayClientMessage(Component.literal("Time resumes...").withStyle(ChatFormatting.AQUA), true);
        } else {
            int dur = SFConfig.duration(AbilityType.SP_THE_WORLD);
            data.setTimeStop(true, dur);
            TimeStopManager.freezeArea(level, p.position(), 32.0, p.getId());
            Fx.sound(level, p.position(), ModSounds.TIME_STOP.get(), 2.5f, 0.8f);
            Fx.particles(level, ModParticles.VOID_MOTE.get(), p.position(), 120, 12.0, 0.04);
            Fx.screen(p, ScreenFx.SHAKE, 2.0f, 16);
            p.displayClientMessage(Component.literal("ZA WARUDO! Time Stop!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            StandManager.animate(p, StandAnim.SUMMON);
            StandManager.sync(p);
        }
        return true;
    }

    private SPAbilities() {}
}
