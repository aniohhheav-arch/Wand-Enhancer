package dev.infinitygauntlet.client.power;

import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.entity.power.GravityCoreEntity;
import dev.infinitygauntlet.entity.power.PlanetaryCoreEntity;
import dev.infinitygauntlet.entity.power.PowerOrbEntity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side particle emission for the Power Stone entities. */
public final class PowerParticles {
    private static double g() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    public static void orbTick(PowerOrbEntity orb) {
        int color = PowerOrbEntity.COLORS[orb.variant()];
        boolean moving = orb.state() == PowerOrbEntity.DIVE || orb.state() == PowerOrbEntity.FLY;
        if (Particles.chance(moving ? 1f : 0.35f)) {
            Particles.spark(orb.position().add(g() * 0.15, g() * 0.15, g() * 0.15), new Vec3(g(), g(), g()).scale(0.03), color, 0.08f);
        }
        if (moving && orb.variant() == PowerOrbEntity.METEOR) {
            Particles.smoke(orb.position(), new Vec3(g(), g(), g()).scale(0.02), 0x2A0848, 0.6f, 24);
            Particles.glow(orb.position(), Vec3.ZERO, 0xFF80E0, 0.8f, 6);
        }
        if (orb.state() == PowerOrbEntity.WELL) {
            for (int i = 0; i < Particles.scaled(4); i++) {
                Vec3 from = orb.position().add(new Vec3(g(), g(), g()).normalize().scale(4 + g()));
                Particles.inward(from, orb.position(), i % 2 == 0 ? 0x5A1AA8 : 0xD8B0FF, 0.09f);
            }
        }
    }

    public static void gravityTick(GravityCoreEntity core) {
        float r = core.fieldRadius();
        if (!core.landed(0)) {
            for (int i = 0; i < Particles.scaled(3); i++) {
                double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double d = ThreadLocalRandom.current().nextDouble() * r;
                Vec3 at = new Vec3(core.getX() + Math.cos(a) * d, core.groundY() + 0.1, core.getZ() + Math.sin(a) * d);
                Particles.mote(at, new Vec3(0, 0.12 + ThreadLocalRandom.current().nextDouble() * 0.1, 0), i % 2 == 0 ? 0x6A2AD0 : 0xD8B0FF, 0.07f, 30);
            }
            for (int i = 0; i < Particles.scaled(2); i++) {
                Vec3 from = core.position().add(new Vec3(g(), g() * 0.3, g()).normalize().scale(7));
                Particles.inward(from, core.position(), 0xB070FF, 0.1f);
            }
            if (Particles.chance(0.4f)) {
                Particles.smoke(new Vec3(core.getX() + g() * r * 0.5, core.groundY() + 0.2, core.getZ() + g() * r * 0.5), new Vec3(0, 0.04, 0),
                        0x2A1A3A, 0.8f, 30);
            }
        }
    }

    public static void planetaryTick(PlanetaryCoreEntity core) {
        float rad = core.coreRadius(0);
        if (core.phase() == PlanetaryCoreEntity.PHASE_CHARGE) {
            float t = Math.min(1f, core.phaseAge(0) / PlanetaryCoreEntity.CHARGE);
            for (int i = 0; i < Particles.scaled(4 + 8 * t); i++) {
                Vec3 from = core.position().add(new Vec3(g(), g(), g()).normalize().scale(5 + 4 * t));
                Particles.inward(from, core.position(), i % 3 == 0 ? 0xFFFFFF : i % 2 == 0 ? 0xFF3FD2 : 0xA032FF, 0.1f + 0.05f * t);
            }
        } else if (core.phase() == PlanetaryCoreEntity.PHASE_FLIGHT) {
            for (int i = 0; i < Particles.scaled(6); i++) {
                Particles.smoke(core.position().add(g() * rad, g() * rad, g() * rad), Vec3.ZERO, 0x2A0848, 1.2f, 30);
                Particles.spark(core.position().add(g() * rad, g() * rad, g() * rad), new Vec3(g(), g(), g()).scale(0.1), 0xFFC0F0, 0.1f);
            }
        }
    }

    private PowerParticles() {
    }
}
