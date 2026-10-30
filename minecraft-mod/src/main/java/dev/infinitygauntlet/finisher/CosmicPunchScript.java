package dev.infinitygauntlet.finisher;

import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static dev.infinitygauntlet.finisher.CosmicPunchTimeline.*;

/** Server beats of the Cosmic Punch execution (see {@link CosmicPunchTimeline}). */
final class CosmicPunchScript implements FinisherManager.Script {
    private Vec3 stand;

    private Vec3 stand(FinisherManager.Session s) {
        if (stand == null) {
            stand = standPoint(s.casterStart, s.targetPos.add(0, s.target.getBbHeight() * 0.5, 0), s.target.getBbWidth() * 0.5f);
        }
        return stand;
    }

    @Override
    public int fastUntil() {
        return IMPACT + 4;
    }

    @Override
    public Vec3 casterPos(FinisherManager.Session s, float t) {
        return CosmicPunchTimeline.casterPos(s.casterStart, stand(s), t);
    }

    private static Vec3 core(FinisherManager.Session s) {
        return s.targetPos.add(0, s.target.getBbHeight() * 0.55, 0);
    }

    @Override
    public void tick(FinisherManager.Session s, int t) {
        Vec3 core = core(s);
        Vec3 d = dir(s.casterStart, s.targetPos);
        var level = s.level;
        if (t == 0) {
            Vfx.sound(level, s.casterStart, ModSounds.POWER_CHARGE, 1.6f, 1.5f);
            CombatFx.slowmo(level, core, 18, 0.3f, DURATION, s.caster, s.target);
        } else if (t == LUNGE) {
            Vfx.sound(level, s.casterStart, ModSounds.POWER_WHOOSH, 2.5f, 0.7f);
        } else if (t == IMPACT) {
            // the fist goes through: energy and debris burst out of the target's back
            Vec3 exit = core.add(d.scale(s.target.getBbWidth() * 0.5 + 0.3));
            Vfx.send(level, VfxType.IMPACT_STAR, exit, d, PowerFx.MAGENTA, 0xFFFFFF, 3.4f, 8);
            Vfx.send(level, VfxType.SHOCKWAVE, exit, d, PowerFx.PURPLE, PowerFx.PINK, 6f, 12);
            Vfx.send(level, VfxType.SHOCKWAVE, exit.add(d.scale(0.6)), d, PowerFx.MAGENTA, 0xFFFFFF, 3.5f, 8);
            CombatFx.hit(level, s.target, d, 2.5f);
            Vfx.sound(level, core, ModSounds.POWER_PUNCH_IMPACT, 3.5f, 0.75f);
            Vfx.sound(level, core, ModSounds.POWER_HIT, 3f, 0.5f);
            Vfx.camera(level, core, 40, 1.3f, 0, 0, 0, 0, 0);
            Vfx.glitch(level, core, 24, 0.9f);
        } else if (t == IMPACT + 3) {
            Vfx.sound(level, core, ModSounds.POWER_NOVA_CHARGE, 3f, 0.75f);
        } else if (t == ERUPTION - 2) {
            Vfx.send(level, VfxType.IMPLOSION, core, Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 2.4f, 4);
        } else if (t == ERUPTION) {
            erupt(s, core, d);
        } else if (t == ERUPTION + 6) {
            Vfx.send(level, VfxType.SMOKE_COLUMN, s.targetPos, Vec3.ZERO, 0x2A0838, 0xFF60E0, 5f, 60);
        }
        for (int i = 0; i < HEARTBEATS.length; i++) {
            if (t == HEARTBEATS[i]) {
                float k = (i + 1f) / HEARTBEATS.length;
                Vfx.sound(level, core, ModSounds.POWER_HIT, 1.2f + 2f * k, 0.45f + 0.35f * k);
                Vfx.send(level, VfxType.SHOCKWAVE, core, d, PowerFx.MAGENTA, PowerFx.PINK, 1.2f + 2.5f * k, 8);
                Vfx.glitch(level, core, 20, 0.15f + 0.6f * k);
                Vfx.shake(level, core, 24, 0.1f + 0.35f * k);
            }
        }
    }

    private static void erupt(FinisherManager.Session s, Vec3 core, Vec3 d) {
        var level = s.level;
        LivingEntity target = s.target;
        Vfx.send(level, VfxType.POWER_EXPLOSION, core, Vec3.ZERO, PowerFx.MAGENTA, 0xFFE6FA, 5.5f, 34);
        Vfx.send(level, VfxType.SPHERE_PULSE, core, Vec3.ZERO, 0xFF60E0, 0xFFFFFF, 11f, 18);
        Vfx.send(level, VfxType.SHOCKWAVE, s.targetPos.add(0, 0.15, 0), new Vec3(0, 1, 0), PowerFx.MAGENTA, PowerFx.PINK, 18f, 22);
        Vfx.send(level, VfxType.SHOCKWAVE, core, d, PowerFx.PURPLE, 0xFFFFFF, 9f, 16);
        Vfx.send(level, VfxType.SHARD_NOVA, core, Vec3.ZERO, 0xFF80E8, 0xFFFFFF, 7f, 30);
        Vfx.send(level, VfxType.SPARK_BURST, core, Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 3f, 18);
        PowerFx.cracks(level, s.targetPos.add(0, 0.04, 0), 6f, 160, PowerFx.MAGENTA);
        Vfx.sound(level, core, ModSounds.POWER_PLANETARY_IMPACT, 4f, 1.25f);
        Vfx.sound(level, core, ModSounds.POWER_EXPLOSION, 4f, 0.7f);
        Vfx.sound(level, core, ModSounds.POWER_FINISHER, 3f, 0.9f);
        Vfx.camera(level, core, 64, 1.6f, 0, 0, 0xFFE0F8, 0.85f, 12);
        Vfx.glitch(level, core, 48, 1f);
        s.allowDamage = true;
        dev.infinitygauntlet.time.TimeManager.clear(target);
        CombatFx.strike(s.caster, target, Math.max(1000f, target.getMaxHealth() * 4), Vec3.ZERO, 3f, CombatFx.DeathStyle.VAPORIZE, false);
        s.released = true;
        for (LivingEntity e : CombatFx.targets(s.caster, core, 7)) {
            Vec3 away = e.position().subtract(core).normalize();
            CombatFx.strike(s.caster, e, 10f, away.scale(1.6).add(0, 0.7, 0), 1.2f, CombatFx.DeathStyle.RAGDOLL, false);
            CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, away, 24);
        }
    }
}
