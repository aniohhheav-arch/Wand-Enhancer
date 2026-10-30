package dev.infinitygauntlet.client;

import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.sound.PortalAmbientSound;
import dev.infinitygauntlet.entity.MiniPortalEntity;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.entity.SpaceBoltEntity;
import dev.infinitygauntlet.entity.SpatialCompressionEntity;
import dev.infinitygauntlet.entity.SpatialPrisonEntity;
import dev.infinitygauntlet.entity.SpatialRiftEntity;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side per-tick emitters for the Space Stone entities: the particle layers (suction, sparks, billowing smoke)
 * that complement their renderers, plus attached looping sounds. Only ever called on the logical client.
 */
public final class ClientEffects {
    private static final int SPACE = InfinityStone.SPACE.color();
    private static final int SPACE_HI = InfinityStone.SPACE.highlight();
    private static final int SMOKE = 0x101A2E;
    private static final Set<Integer> AMBIENT = new HashSet<>();

    public static void portalTick(RupturePortalEntity portal) {
        RandomSource rnd = portal.getRandom();
        Vec3 c = portal.position();
        float r = portal.radius();
        float open = portal.openProgress(0);
        float close = portal.closeProgress(0);

        if (AMBIENT.add(portal.getId())) {
            int id = portal.getId();
            Minecraft.getInstance().getSoundManager().play(new PortalAmbientSound(portal, () -> AMBIENT.remove(id)));
        }

        if (open < 1) {
            // opening: sparks spit from the forming point, distortion motes are drawn in
            for (int i = 0; i < Particles.scaled(3); i++) {
                Particles.spark(c, randomDir(rnd).scale(0.08 + 0.15 * open), i % 2 == 0 ? SPACE_HI : SPACE, 0.1f);
            }
        }
        if (close > 0) {
            // collapse: everything rushes inward
            for (int i = 0; i < Particles.scaled(6); i++) {
                Particles.inward(c.add(randomDir(rnd).scale(r * (1.5 + rnd.nextDouble()))), c, SPACE_HI, 0.12f);
            }
            return;
        }
        float scale = r / 1.6f;
        for (int i = 0; i < Particles.scaled(2.5f * scale * open); i++) {
            Particles.inward(c.add(randomDir(rnd).scale(r * (1.6 + rnd.nextDouble() * 1.4))), c, rnd.nextBoolean() ? SPACE_HI : SPACE, 0.07f + 0.05f * rnd.nextFloat());
        }
        if (open >= 1) {
            if (Particles.chance(0.5f * scale)) {
                Vec3 dir = randomDir(rnd);
                Particles.spark(c.add(dir.scale(r * 0.95)), dir.scale(0.12), SPACE_HI, 0.09f);
            }
            if (Particles.chance(0.22f * scale)) {
                Vec3 dir = randomDir(rnd);
                Particles.smoke(c.add(dir.scale(r * 0.95)), dir.scale(0.03), SMOKE, 0.35f + r * 0.12f, 34);
            }
            if (portal.style() == RupturePortalEntity.STYLE_GATEWAY && Particles.chance(0.6f)) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Vec3 edge = c.add(Math.cos(a) * r * 1.4, (rnd.nextDouble() - 0.5) * r * 0.4, Math.sin(a) * r * 1.4);
                Particles.spark(edge, new Vec3(0, -0.15, 0), SPACE_HI, 0.15f);
            }
        }
    }

    public static void riftTick(SpatialRiftEntity rift) {
        RandomSource rnd = rift.getRandom();
        Vec3 c = rift.position();
        float r = rift.visualRadius();
        float form = Math.min(1f, rift.tickCount / (float) SpatialRiftEntity.FORM_TICKS);
        for (int i = 0; i < Particles.scaled(5 * form); i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = r * (1.0 + rnd.nextDouble() * 1.4);
            Particles.inward(c.add(Math.cos(a) * d, 0.1 + rnd.nextDouble() * 0.6, Math.sin(a) * d), c.add(0, 0.1, 0),
                    rnd.nextBoolean() ? SPACE_HI : SPACE, 0.08f);
        }
        if (Particles.chance(0.5f * form)) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = rnd.nextDouble() * r;
            Particles.smoke(c.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), new Vec3(0, 0.03, 0), SMOKE, 0.5f, 30);
        }
        if (Particles.chance(0.35f * form)) {
            Particles.spark(c.add(0, 0.1, 0), new Vec3(rnd.nextGaussian() * 0.1, 0.25 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.1), SPACE_HI, 0.1f);
        }
    }

    public static void compressionTick(SpatialCompressionEntity sphere) {
        RandomSource rnd = sphere.getRandom();
        Vec3 c = sphere.position();
        float life = sphere.life(0);
        float reach = sphere.visualRadius() * 4;
        for (int i = 0; i < Particles.scaled(4 + 8 * life); i++) {
            Particles.inward(c.add(randomDir(rnd).scale(reach * (0.5 + rnd.nextDouble() * 0.6))), c, rnd.nextBoolean() ? SPACE_HI : SPACE, 0.1f);
        }
        if (Particles.chance(0.3f)) {
            Particles.smoke(c.add(randomDir(rnd).scale(reach * 0.8)), randomDir(rnd).scale(-0.02), SMOKE, 0.45f, 20);
        }
    }

    public static void prisonTick(SpatialPrisonEntity prison) {
        RandomSource rnd = prison.getRandom();
        Vec3 c = prison.position();
        float r = prison.visualRadius();
        if (Particles.chance(0.6f + prison.strain())) {
            Vec3 dir = randomDir(rnd);
            Particles.mote(c.add(dir.scale(r)), dir.scale(-0.02), SPACE_HI, 0.07f, 16);
        }
        if (prison.strain() > 0.25f && Particles.chance(prison.strain())) {
            Particles.shard(c.add(randomDir(rnd).scale(r * 0.8)), randomDir(rnd).scale(0.08), SPACE_HI, 0.12f, 10);
        }
    }

    public static void miniPortalTick(MiniPortalEntity portal) {
        float aperture = portal.aperture(0);
        if (aperture <= 0) {
            return;
        }
        RandomSource rnd = portal.getRandom();
        Vec3 c = portal.position();
        if (Particles.chance(0.6f * aperture)) {
            Particles.inward(c.add(randomDir(rnd).scale(1.1)), c, SPACE_HI, 0.05f);
        }
    }

    public static void boltTick(SpaceBoltEntity bolt) {
        RandomSource rnd = bolt.getRandom();
        Vec3 p = bolt.position();
        Vec3 back = bolt.getDeltaMovement().scale(-0.5);
        for (int i = 0; i < Particles.scaled(3); i++) {
            Particles.glow(p.add(back.scale(rnd.nextDouble())), randomDir(rnd).scale(0.01), i == 0 ? SPACE_HI : SPACE, 0.22f, 8);
        }
        if (Particles.chance(0.4f)) {
            Particles.spark(p, randomDir(rnd).scale(0.06), SPACE_HI, 0.07f);
        }
    }

    public static void clear() {
        AMBIENT.clear();
    }

    private static Vec3 randomDir(RandomSource rnd) {
        double z = rnd.nextDouble() * 2 - 1;
        double phi = rnd.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - z * z);
        return new Vec3(s * Math.cos(phi), z, s * Math.sin(phi));
    }

    private ClientEffects() {
    }
}
