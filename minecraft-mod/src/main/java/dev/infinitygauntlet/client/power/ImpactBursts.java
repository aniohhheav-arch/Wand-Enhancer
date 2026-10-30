package dev.infinitygauntlet.client.power;

import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.power.CombatFx;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stylized impact effects per creature material: a spray of droplets or fragments thrown out along the blow, a hot
 * flash at the point of contact, and material-specific extras (embers, sparks, dust, slime). Nothing photoreal:
 * flat colours, chunky sprites and readable silhouettes.
 */
final class ImpactBursts {
    private static double rnd() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static Vec3 spray(Vec3 dir, double spread, double speed) {
        Vec3 r = new Vec3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).scale(spread * 2);
        return dir.add(r).normalize().scale(speed * (0.5 + rnd() * 0.7)).add(0, 0.12 + rnd() * 0.15, 0);
    }

    static void hit(Entity e, CombatClient.Material m, Vec3 dir, float intensity) {
        Vec3 c = e.getBoundingBox().getCenter();
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();
        Vec3 contact = c.subtract(d.scale(e.getBbWidth() * 0.4));
        float scale = Mth.clamp(e.getBbHeight() / 1.8f, 0.5f, 2f);
        Particles.glow(contact, Vec3.ZERO, m.flash, 0.6f * scale * (0.7f + intensity * 0.3f), 4);
        int n = Particles.scaled(6 + 8 * intensity);
        switch (m) {
            case FLESH, UNDEAD, SLIME, MAGMA, ICHOR -> {
                for (int i = 0; i < n; i++) {
                    Particles.droplet(c.add(spray(d, 0.3, 0.1)), spray(d, 0.55, 0.32 + 0.12 * intensity), i % 3 == 0 ? m.secondary : m.primary,
                            (m == CombatClient.Material.SLIME ? 0.16f : 0.1f) * scale);
                }
                if (m == CombatClient.Material.UNDEAD) {
                    for (int i = 0; i < n / 3; i++) {
                        Particles.chunk(c, spray(d, 0.6, 0.3), m.secondary, 0.08f * scale);
                    }
                }
                if (m == CombatClient.Material.MAGMA) {
                    for (int i = 0; i < n / 2; i++) {
                        Particles.spark(c, spray(d, 0.7, 0.35), 0xFFC040, 0.08f);
                    }
                }
            }
            case BONE, WITHERED, SNOW -> {
                for (int i = 0; i < n; i++) {
                    Particles.chunk(c.add(spray(d, 0.3, 0.1)), spray(d, 0.6, 0.35 + 0.1 * intensity), i % 3 == 0 ? m.secondary : m.primary,
                            0.08f * scale);
                }
                Particles.smoke(c, d.scale(0.05), m.secondary, 0.5f * scale, 16);
            }
            case EMBER -> {
                for (int i = 0; i < n * 2; i++) {
                    Particles.spark(c, spray(d, 0.8, 0.35), i % 2 == 0 ? m.primary : 0xFF5010, 0.09f);
                }
                Particles.smoke(c, new Vec3(0, 0.05, 0), m.secondary, 0.6f * scale, 22);
            }
            case METAL -> {
                for (int i = 0; i < n * 2; i++) {
                    Particles.spark(contact, spray(d, 0.9, 0.5), m.primary, 0.07f);
                }
                for (int i = 0; i < n / 3; i++) {
                    Particles.chunk(contact, spray(d, 0.6, 0.3), m.secondary, 0.07f * scale);
                }
            }
            case ENDER -> {
                for (int i = 0; i < n * 2; i++) {
                    Particles.mote(c.add(spray(d, 0.5, 0.3)), spray(d, 0.8, 0.15), i % 2 == 0 ? m.primary : m.flash, 0.08f, 22);
                }
            }
            case GUNPOWDER -> {
                for (int i = 0; i < n; i++) {
                    Particles.chunk(c, spray(d, 0.6, 0.3), m.primary, 0.08f * scale);
                }
                Particles.smoke(c, d.scale(0.04), m.secondary, 0.7f * scale, 24);
            }
            case ENERGY -> {
                for (int i = 0; i < n * 2; i++) {
                    Particles.spark(c, spray(d, 0.8, 0.4), i % 2 == 0 ? m.primary : m.flash, 0.09f);
                }
            }
        }
    }

    static void death(Entity e, CombatClient.Material m, CombatFx.DeathStyle style, Vec3 dir, boolean headless) {
        if (style == CombatFx.DeathStyle.ERASED) {
            return; // the finisher draws its own ending
        }
        Vec3 c = e.getBoundingBox().getCenter();
        float scale = Mth.clamp(e.getBbHeight() / 1.8f, 0.5f, 2f);
        hit(e, m, dir, 2f);
        if (headless) {
            Vec3 neck = e.position().add(0, e.getBbHeight() * 0.86, 0);
            Particles.smoke(neck, new Vec3(0, 0.06, 0), m.liquid ? m.secondary : 0x6A6A6A, 0.5f, 18);
            for (int i = 0; i < Particles.scaled(14); i++) {
                Vec3 up = new Vec3(rnd() - 0.5, 1.2 + rnd(), rnd() - 0.5).normalize().scale(0.25 + rnd() * 0.2);
                if (m.liquid) {
                    Particles.droplet(neck, up, i % 3 == 0 ? m.secondary : m.primary, 0.1f);
                } else {
                    Particles.chunk(neck, up, m.primary, 0.07f);
                }
            }
        }
        switch (style) {
            case CRUSH -> {
                Vec3 ground = e.position().add(0, 0.05, 0);
                if (m.liquid) {
                    Particles.splat(ground, m.primary, 1.6f * scale, 220);
                    for (int i = 0; i < Particles.scaled(5); i++) {
                        double a = rnd() * Math.PI * 2, r = 0.6 + rnd() * 1.2;
                        Particles.splat(ground.add(Math.cos(a) * r, 0.01 * i, Math.sin(a) * r), i % 2 == 0 ? m.secondary : m.primary,
                                0.4f + (float) rnd() * 0.5f, 180);
                    }
                }
                for (int i = 0; i < Particles.scaled(16); i++) {
                    double a = rnd() * Math.PI * 2;
                    Particles.chunk(ground.add(0, 0.2, 0), new Vec3(Math.cos(a) * 0.3, 0.2 + rnd() * 0.2, Math.sin(a) * 0.3), m.secondary, 0.08f);
                }
            }
            case EXECUTION -> {
                Particles.glow(c, Vec3.ZERO, 0xFFFFFF, 2.5f * scale, 5);
                Particles.glow(c, Vec3.ZERO, 0xA032FF, 4f * scale, 8);
            }
            case DISINTEGRATE -> Particles.glow(c, Vec3.ZERO, 0xFFB0F0, 2.2f * scale, 6);
            case VAPORIZE -> {
                for (int i = 0; i < Particles.scaled(60); i++) {
                    Vec3 out = new Vec3(rnd() - 0.5, rnd() - 0.3, rnd() - 0.5).normalize().scale(0.3 + rnd() * 0.6);
                    Particles.spark(c, out, i % 3 == 0 ? 0xFFFFFF : i % 2 == 0 ? 0xFF4FD8 : 0xA032FF, 0.16f);
                    if (i % 4 == 0) {
                        Particles.mote(c.add(out), out.scale(0.3), 0xFF90E8, 0.12f, 40);
                    }
                }
                for (int i = 0; i < Particles.scaled(10); i++) {
                    Particles.smoke(c, new Vec3(rnd() - 0.5, rnd() * 0.5, rnd() - 0.5).scale(0.2), 0x3A0A40, 1.2f * scale, 50);
                }
            }
            default -> {
            }
        }
    }

    /** Per tick while a styled death plays out. */
    static void dying(Entity e, CombatClient.Material m, CombatFx.DeathStyle style, long age) {
        if (style == CombatFx.DeathStyle.DISINTEGRATE && age < 20) {
            for (int i = 0; i < Particles.scaled(6); i++) {
                Vec3 at = e.position().add((rnd() - 0.5) * e.getBbWidth() * 1.4, rnd() * e.getBbHeight() * (1 - age / 22.0), (rnd() - 0.5) * e.getBbWidth() * 1.4);
                if (i % 3 == 0) {
                    Particles.smoke(at, new Vec3(0, 0.05, 0), 0x1A0A20, 0.35f, 26);
                } else {
                    Particles.mote(at, new Vec3((rnd() - 0.5) * 0.04, 0.05 + rnd() * 0.05, (rnd() - 0.5) * 0.04), i % 2 == 0 ? 0xFF60E0 : 0xA032FF,
                            0.06f, 20);
                }
            }
        } else if (style == CombatFx.DeathStyle.RAGDOLL && !e.onGround() && age < 30 && Particles.chance(0.5f)) {
            Particles.glow(e.getBoundingBox().getCenter(), Vec3.ZERO, 0xA032FF, 0.5f, 6);
        } else if (style == CombatFx.DeathStyle.EXECUTION && age < 24 && m.liquid && Particles.chance(0.7f)) {
            Particles.droplet(e.getBoundingBox().getCenter(), new Vec3((rnd() - 0.5) * 0.2, 0.1, (rnd() - 0.5) * 0.2), m.primary, 0.09f);
        }
    }

    private ImpactBursts() {
    }
}
