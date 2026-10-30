package dev.infinitygauntlet.finisher;

import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Server beats of the finishers in which the caster stays put (Space, Mind, Reality, Time, Soul). The visuals between
 * the beats are drawn by the matching client scripts from the same {@link Timelines}.
 */
final class StoneFinisherScripts {
    /** Base: the caster holds their ground; helpers for the target's core and the finishing blow. */
    abstract static class Stationary implements FinisherManager.Script {
        @Override
        public Vec3 casterPos(FinisherManager.Session s, float t) {
            return s.casterStart;
        }

        static Vec3 core(FinisherManager.Session s) {
            return s.targetPos.add(0, s.target.getBbHeight() * 0.55, 0);
        }

        static void kill(FinisherManager.Session s, CombatFx.DeathStyle style, Vec3 push) {
            s.allowDamage = true;
            TimeManager.clear(s.target);
            CombatFx.strike(s.caster, s.target, Math.max(1000f, s.target.getMaxHealth() * 4), push, 2f, style, false);
            s.released = true;
        }

        static void flash(FinisherManager.Session s, Vec3 at, int color, float alpha) {
            Vfx.camera(s.level, at, 64, 1.2f, 0, 0, color, alpha, 10);
            Vfx.sound(s.level, at, ModSounds.FINISHER_BASS, 4f, 1f);
        }
    }

    /** Space: Dimensional Erasure. */
    static final class Erasure extends Stationary {
        @Override
        public void tick(FinisherManager.Session s, int t) {
            ServerLevel level = s.level;
            Vec3 core = core(s);
            int blue = InfinityStone.SPACE.color(), pale = InfinityStone.SPACE.highlight();
            switch (t) {
                case 0 -> {
                    Vfx.sound(level, s.casterStart, ModSounds.SPACE_ARC, 1.5f, 0.6f);
                    CombatFx.slowmo(level, core, 18, 0.3f, Timelines.Erasure.DURATION, s.caster, s.target);
                }
                case Timelines.Erasure.OPEN -> {
                    Vfx.sound(level, core, ModSounds.SPACE_PORTAL_OPEN, 3f, 0.8f);
                    Vfx.glitch(level, core, 24, 0.5f);
                    Vfx.send(level, VfxType.SHOCKWAVE, core, s.casterStart.subtract(s.targetPos).normalize(), blue, pale, 4f, 12);
                }
                case Timelines.Erasure.PULL -> {
                    Vfx.sound(level, core, ModSounds.SPACE_COMPRESSION, 3f, 0.7f);
                    Vfx.sound(level, core, ModSounds.FINISHER_SPACE_COLLAPSE, 3f, 1f); // its fold-shut lands on ERASE
                }
                case Timelines.Erasure.COLLAPSE -> {
                    Vfx.sound(level, core, ModSounds.SPACE_SHOCKWAVE, 2.5f, 1.6f);
                    Vfx.glitch(level, core, 24, 0.8f);
                }
                case Timelines.Erasure.ERASE -> {
                    kill(s, CombatFx.DeathStyle.ERASED, Vec3.ZERO);
                    Vfx.send(level, VfxType.IMPLOSION, core, Vec3.ZERO, blue, 0xFFFFFF, 1.2f, 6);
                    Vfx.send(level, VfxType.SHOCKWAVE, core, new Vec3(0, 1, 0), blue, pale, 12f, 16);
                    Vfx.send(level, VfxType.SHOCKWAVE, core, s.casterStart.subtract(s.targetPos).normalize(), pale, 0xFFFFFF, 7f, 12);
                    Vfx.send(level, VfxType.SPARK_BURST, core, Vec3.ZERO, blue, 0xFFFFFF, 1.6f, 14);
                    Vfx.sound(level, core, ModSounds.SPACE_PORTAL_CLOSE, 3f, 1.4f);
                    flash(s, core, 0xDDF6FF, 0.75f);
                    Vfx.glitch(level, core, 40, 1f);
                }
                default -> {
                }
            }
        }
    }

    /** Mind: Psychic Overload. */
    static final class Psychic extends Stationary {
        @Override
        public void tick(FinisherManager.Session s, int t) {
            ServerLevel level = s.level;
            Vec3 core = core(s).add(0, 3, 0);
            int gold = InfinityStone.MIND.color(), pale = InfinityStone.MIND.highlight();
            switch (t) {
                case 0 -> {
                    Vfx.sound(level, s.casterStart, ModSounds.MIND_CONTROL, 2f, 0.8f);
                    CombatFx.slowmo(level, core, 18, 0.3f, Timelines.Psychic.DURATION, s.caster, s.target);
                }
                case Timelines.Psychic.LIFT -> Vfx.sound(level, s.targetPos, ModSounds.MIND_GRAB, 2.5f, 0.6f);
                case Timelines.Psychic.BUILD -> {
                    Vfx.sound(level, core, ModSounds.MIND_MARK, 3f, 0.8f);
                    Vfx.send(level, VfxType.SHOCKWAVE, core, new Vec3(0, 1, 0), gold, pale, 5f, 14);
                }
                case Timelines.Psychic.PULSE - 4 -> Vfx.sound(level, core, ModSounds.MIND_BEAM_CHARGE, 3f, 0.6f);
                case Timelines.Psychic.PULSE -> {
                    kill(s, CombatFx.DeathStyle.RAGDOLL, new Vec3(0, -0.4, 0));
                    Vfx.send(level, VfxType.SPHERE_PULSE, core, Vec3.ZERO, gold, 0xFFFFFF, 14f, 20);
                    Vfx.send(level, VfxType.SHOCKWAVE, core, new Vec3(0, 1, 0), gold, pale, 20f, 22);
                    Vfx.send(level, VfxType.SHOCKWAVE, core, s.casterStart.subtract(s.targetPos).normalize(), pale, 0xFFFFFF, 12f, 16);
                    Vfx.send(level, VfxType.SPARK_BURST, core, Vec3.ZERO, gold, 0xFFFFFF, 2.5f, 16);
                    Vfx.sound(level, core, ModSounds.MIND_PULSE, 4f, 0.7f);
                    Vfx.sound(level, core, ModSounds.MIND_BURST, 4f, 0.8f);
                    flash(s, core, 0xFFF4C0, 0.8f);
                    Vfx.glitch(level, core, 48, 1f);
                    for (LivingEntity e : CombatFx.targets(s.caster, core, 12)) {
                        Vec3 away = e.position().subtract(core).multiply(1, 0, 1).normalize();
                        CombatFx.strike(s.caster, e, 6f, away.scale(1.4).add(0, 0.5, 0), 1f, CombatFx.DeathStyle.RAGDOLL, false);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                    }
                }
                default -> {
                }
            }
            for (int i = 0; i < Timelines.Psychic.CHIMES.length; i++) {
                if (t == Timelines.Psychic.CHIMES[i]) {
                    Vfx.sound(level, core, ModSounds.MIND_SHIELD_HIT, 1.5f + i * 0.4f, 0.6f + i * 0.12f);
                    Vfx.glitch(level, core, 20, 0.2f + i * 0.1f);
                }
            }
        }
    }

    /** Reality: Reality Rewrite. */
    static final class Rewrite extends Stationary {
        @Override
        public void tick(FinisherManager.Session s, int t) {
            ServerLevel level = s.level;
            Vec3 core = core(s);
            int red = InfinityStone.REALITY.color(), pale = InfinityStone.REALITY.highlight();
            if (t == 0) {
                Vfx.sound(level, s.casterStart, ModSounds.REALITY_GLITCH, 2f, 0.6f);
                CombatFx.slowmo(level, core, 18, 0.3f, Timelines.Rewrite.DURATION, s.caster, s.target);
            } else if (t == Timelines.Rewrite.FREEZE) {
                Vfx.sound(level, core, ModSounds.TIME_FREEZE, 2.5f, 0.5f);
                Vfx.sound(level, core, ModSounds.FINISHER_REALITY_SHIFT, 3f, 0.8f);
                Vfx.glitch(level, core, 24, 0.8f);
            } else if (t >= Timelines.Rewrite.STATES && t < Timelines.Rewrite.SHATTER && (t - Timelines.Rewrite.STATES) % Timelines.Rewrite.STATE_LENGTH == 0) {
                int k = (t - Timelines.Rewrite.STATES) / Timelines.Rewrite.STATE_LENGTH;
                Vfx.sound(level, core, ModSounds.REALITY_GLITCH, 2.5f, 0.7f + k * 0.15f);
                Vfx.sound(level, core, ModSounds.FINISHER_REALITY_SHIFT, 2f, 1f + k * 0.12f);
                Vfx.glitch(level, core, 24, 0.6f + k * 0.1f);
            } else if (t == Timelines.Rewrite.SHATTER) {
                kill(s, CombatFx.DeathStyle.ERASED, Vec3.ZERO);
                Vfx.send(level, VfxType.SHARD_NOVA, core, Vec3.ZERO, red, pale, 6f, 30);
                Vfx.send(level, VfxType.SHOCKWAVE, core, new Vec3(0, 1, 0), red, pale, 10f, 16);
                Vfx.sound(level, core, ModSounds.TIME_SHATTER, 3f, 0.6f);
                flash(s, core, 0xFFD0D0, 0.7f);
                Vfx.glitch(level, core, 40, 1f);
            }
        }
    }

    /** Time: Temporal Collapse. */
    static final class Collapse extends Stationary {
        private final List<Entity> stopped = new ArrayList<>();

        @Override
        public void tick(FinisherManager.Session s, int t) {
            ServerLevel level = s.level;
            Vec3 core = core(s);
            int green = InfinityStone.TIME.color(), pale = InfinityStone.TIME.highlight();
            if (t == 0) {
                Vfx.sound(level, s.casterStart, ModSounds.TIME_FIELD_OPEN, 2f, 0.7f);
                Vfx.send(level, VfxType.CLOCK_GLYPH, s.targetPos.add(0, 0.05, 0), Vec3.ZERO, green, pale, 3.5f, Timelines.Collapse.RELEASE + 6);
            } else if (t == Timelines.Collapse.FREEZE) {
                // the whole scene stops; only the caster and the finisher move
                Vfx.sound(level, core, ModSounds.TIME_FREEZE, 3f, 0.8f);
                for (Entity e : level.getEntities(s.caster, new AABB(core, core).inflate(24), e -> e != s.target && !(e instanceof Player))) {
                    stopped.add(e);
                }
            } else if (t == Timelines.Collapse.COPIES) {
                Vfx.sound(level, core, ModSounds.TIME_ECHO, 2.5f, 0.8f);
                Vfx.sound(level, core, ModSounds.FINISHER_TIME_TICK, 3f, 1f);
            } else if (t == 40) {
                Vfx.sound(level, core, ModSounds.TIME_REWIND, 2.5f, 1.1f);
            } else if (t == Timelines.Collapse.CONVERGE) {
                Vfx.sound(level, core, ModSounds.TIME_LOOP, 2.5f, 0.6f);
                Vfx.glitch(level, core, 24, 0.6f);
            } else if (t == Timelines.Collapse.RELEASE) {
                for (Entity e : stopped) {
                    TimeManager.clear(e);
                }
                stopped.clear();
                kill(s, CombatFx.DeathStyle.DISINTEGRATE, Vec3.ZERO);
                Vfx.send(level, VfxType.SHOCKWAVE, core, new Vec3(0, 1, 0), green, pale, 22f, 22);
                Vfx.send(level, VfxType.SPHERE_PULSE, core, Vec3.ZERO, green, 0xFFFFFF, 10f, 16);
                Vfx.send(level, VfxType.CLOCK_RINGS, core, Vec3.ZERO, green, pale, 2.5f, 20);
                Vfx.sound(level, core, ModSounds.TIME_RESUME, 3f, 0.7f);
                Vfx.sound(level, core, ModSounds.TIME_SHATTER, 3f, 0.9f);
                flash(s, core, 0xE0FFE8, 0.8f);
                Vfx.glitch(level, core, 40, 0.9f);
            }
            if (t >= Timelines.Collapse.FREEZE && t < Timelines.Collapse.RELEASE) {
                for (Entity e : stopped) {
                    if (e.isAlive()) {
                        TimeManager.apply(e, 0f, 3);
                    }
                }
            }
        }
    }

    /** Soul: Soul Extraction. */
    static final class Extraction extends Stationary {
        @Override
        public void tick(FinisherManager.Session s, int t) {
            ServerLevel level = s.level;
            Vec3 core = core(s);
            int orange = InfinityStone.SOUL.color(), pale = InfinityStone.SOUL.highlight();
            switch (t) {
                case 0 -> {
                    Vfx.sound(level, s.casterStart, ModSounds.NEXUS_STATIC, 1.5f, 0.5f);
                    CombatFx.slowmo(level, core, 18, 0.3f, Timelines.Extraction.DURATION, s.caster, s.target);
                }
                case Timelines.Extraction.REACH -> Vfx.sound(level, core, ModSounds.FINISHER_SOUL_WAIL, 3f, 0.9f);
                case Timelines.Extraction.SUSPEND -> Vfx.sound(level, core, ModSounds.FINISHER_SOUL_WAIL, 2.5f, 0.75f);
                case Timelines.Extraction.ABSORB -> Vfx.sound(level, s.casterStart, ModSounds.FINISHER_SOUL_ABSORB, 3f, 0.8f);
                case Timelines.Extraction.CONSUMED -> {
                    kill(s, CombatFx.DeathStyle.ERASED, Vec3.ZERO);
                    Vec3 hand = s.casterStart.add(0, 1.2, 0);
                    Vfx.send(level, VfxType.SPARK_BURST, hand, Vec3.ZERO, orange, pale, 1.4f, 14);
                    Vfx.send(level, VfxType.SHOCKWAVE, hand, new Vec3(0, 1, 0), orange, pale, 6f, 14);
                    Vfx.send(level, VfxType.DISSOLVE, s.targetPos, new Vec3(s.target.getBbWidth(), s.target.getBbHeight(), 0), 0x505050, 0x909090, 1f, 20);
                    flash(s, hand, 0xFFE0B0, 0.7f);
                    Vfx.glitch(level, hand, 20, 0.6f);
                }
                default -> {
                }
            }
        }
    }

    private StoneFinisherScripts() {
    }
}
