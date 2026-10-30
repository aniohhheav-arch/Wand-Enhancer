package dev.arcana.magic;

import java.util.function.BiConsumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Spell combinations: hitting a target with a second, different school within five seconds triggers a combo.
 * Pairs without a bespoke entry fall back to "resonance" (bonus damage and a mana refund).
 */
public final class Combos {
    public record Combo(String id, BiConsumer<SpellContext, LivingEntity> effect) {}

    public static Combo find(School a, School b) {
        if (a == b) return null;
        if (is(a, b, School.STORM, School.GLACIER)) return new Combo("shatter", (c, t) -> {
            Magic.rawHurt(c, t, 6 * c.damage);
            Magic.fx(c.level, ParticleTypes.CRIT, t.position().add(0, 1, 0), 24, 0.4, 0.5, 0.4, 0.4);
            Magic.snd(c.level, t.position(), SoundEvents.GLASS_BREAK, 1f, 1.1f);
        });
        if (is(a, b, School.CRIMSON, School.SOLAR)) return new Combo("eclipse", (c, t) -> {
            t.igniteForSeconds(6);
            for (LivingEntity o : Magic.enemies(c, t.position(), 3.5)) if (o != t) Magic.rawHurt(c, o, 4 * c.damage);
            Magic.fx(c.level, ParticleTypes.EXPLOSION, t.position().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
            Magic.snd(c.level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
        });
        if (is(a, b, School.VOID, School.STORM)) return new Combo("singular_surge", (c, t) -> {
            for (LivingEntity o : Magic.enemies(c, t.position(), 7)) if (o != t) Magic.pull(o, t.position().add(0, 1, 0), 1.2);
            Magic.fx(c.level, ParticleTypes.REVERSE_PORTAL, t.position().add(0, 1, 0), 40, 1, 1, 1, 0.3);
        });
        if (is(a, b, School.VERDANT, School.SOLAR)) return new Combo("photosynthesis", (c, t) -> {
            c.player.heal(4);
            Magic.effect(c.player, MobEffects.REGENERATION, 100, 1);
            Magic.fx(c.level, ParticleTypes.HAPPY_VILLAGER, c.player.position().add(0, 1, 0), 18, 0.5, 0.8, 0.5, 0.1);
        });
        if (is(a, b, School.GLACIER, School.SOLAR)) return new Combo("steam_burst", (c, t) -> {
            for (LivingEntity o : Magic.enemies(c, t.position(), 4)) {
                Magic.effect(o, MobEffects.BLINDNESS, 80, 0);
                if (o != t) Magic.rawHurt(c, o, 3 * c.damage);
            }
            Magic.fx(c.level, ParticleTypes.CLOUD, t.position().add(0, 1, 0), 40, 1.2, 0.8, 1.2, 0.05);
        });
        if (is(a, b, School.CHRONOS, School.ARCANE)) return new Combo("time_echo", (c, t) -> {
            float dmg = 5 * c.damage;
            EffectManager.later(c.level, 20, () -> {
                if (t.isAlive()) {
                    Magic.rawHurt(c, t, dmg);
                    Magic.fx(c.level, ParticleTypes.ENCHANT, t.position().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0.6);
                }
            });
        });
        if (is(a, b, School.VOID, School.CHRONOS)) return new Combo("event_collapse", (c, t) -> {
            Magic.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 50, 9);
            Magic.rawHurt(c, t, 5 * c.damage);
            Magic.fx(c.level, ParticleTypes.PORTAL, t.position().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 1);
        });
        if (is(a, b, School.CRIMSON, School.VERDANT)) return new Combo("bloodbloom", (c, t) -> {
            c.player.heal(5);
            Magic.effect(t, MobEffects.POISON, 100, 1);
            Magic.fx(c.level, ParticleTypes.CRIMSON_SPORE, t.position().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.1);
        });
        return new Combo("resonance", (c, t) -> {
            c.player.getData(dev.arcana.registry.ModAttachments.MAGIC).mana += 3;
            Magic.fx(c.level, ParticleTypes.ENCHANT, t.position().add(0, 1, 0), 20, 0.5, 0.7, 0.5, 0.5);
        });
    }

    private static boolean is(School a, School b, School x, School y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    private Combos() {}

}
