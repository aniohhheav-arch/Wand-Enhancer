package dev.infinitygauntlet.client.breach;

import dev.infinitygauntlet.breach.DimensionalRiftEntity;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Per-tick particle emitters of the dimensional rifts (client only). */
public final class BreachClientEffects {
    private static final int CYAN = 0x5AE6FF, MAGENTA = 0xFF46C8, WHITE = 0xF4F8FF;

    public static void riftTick(DimensionalRiftEntity rift) {
        if (rift.phase() == DimensionalRiftEntity.CLOSING) {
            for (int i = 0; i < Particles.scaled(5); i++) {
                Particles.inward(rift.position().add(VfxDraw.unit(rift.getId() * 31L + rift.tickCount, i).scale(rift.radius() * 2.2)), rift.position(),
                        WHITE, 0.12f);
            }
            return;
        }
        RandomSource rnd = rift.getRandom();
        float r = rift.radius() * rift.openProgress(0);
        for (int i = 0; i < Particles.scaled(3); i++) {
            Vec3 dir = VfxDraw.unit(rnd.nextLong(), 0);
            Particles.inward(rift.position().add(dir.scale(r * (1.6 + rnd.nextDouble()))), rift.position(),
                    rift.isReturn() ? (rnd.nextBoolean() ? CYAN : MAGENTA) : WHITE, 0.07f);
        }
        if (Particles.chance(0.35f)) {
            Vec3 dir = VfxDraw.unit(rnd.nextLong(), 1);
            Particles.shard(rift.position().add(dir.scale(r * 0.9)), dir.scale(0.05), rnd.nextBoolean() ? CYAN : MAGENTA, 0.14f, 20);
        }
    }

    private BreachClientEffects() {
    }
}
