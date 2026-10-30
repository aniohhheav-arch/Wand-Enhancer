package dev.infinitygauntlet.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.vfx.EnergyParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Lit (not glowing) impact debris for the Power Stone's stylized combat effects: droplets that fall and splat,
 * flat splash decals on the ground, and solid fragments that bounce.
 */
public class ImpactParticle extends TextureSheetParticle {
    public enum Kind {DROPLET, SPLAT, CHUNK}

    private final Kind kind;
    private final float baseSize;
    private final int color;
    private float spin;

    protected ImpactParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, EnergyParticleOptions options,
                             SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.color = options.color();
        this.rCol = (color >> 16 & 255) / 255f;
        this.gCol = (color >> 8 & 255) / 255f;
        this.bCol = (color & 255) / 255f;
        this.baseSize = options.size() * (0.8f + random.nextFloat() * 0.4f);
        this.quadSize = baseSize;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.6f;
        switch (kind) {
            case DROPLET -> {
                gravity = 1.1f;
                hasPhysics = true;
                friction = 0.98f;
                lifetime = 40 + random.nextInt(20);
            }
            case SPLAT -> {
                gravity = 0;
                hasPhysics = false;
                lifetime = options.lifetime() > 0 ? options.lifetime() : 140;
                quadSize = baseSize * 0.4f;
            }
            case CHUNK -> {
                gravity = 1.2f;
                hasPhysics = true;
                friction = 0.98f;
                lifetime = 50 + random.nextInt(30);
            }
        }
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float life = (float) age / lifetime;
        switch (kind) {
            case DROPLET -> {
                yd -= 0.04 * gravity;
                move(xd, yd, zd);
                xd *= friction;
                zd *= friction;
                if (onGround) {
                    if (random.nextFloat() < 0.55f) {
                        Particles.splat(new net.minecraft.world.phys.Vec3(x, y + 0.02, z), color, baseSize * 2.4f, 100 + random.nextInt(60));
                    }
                    remove();
                }
            }
            case SPLAT -> {
                quadSize = baseSize * Math.min(1f, 0.4f + age / 3f);
                alpha = life < 0.7f ? 0.92f : 0.92f * (1 - (life - 0.7f) / 0.3f);
            }
            case CHUNK -> {
                double vy = yd;
                yd -= 0.04 * gravity;
                move(xd, yd, zd);
                roll += spin;
                if (onGround) {
                    xd *= 0.6;
                    zd *= 0.6;
                    spin *= 0.6f;
                    if (vy < -0.12) {
                        yd = -vy * 0.35;
                    }
                } else {
                    xd *= friction;
                    zd *= friction;
                }
                alpha = life < 0.8f ? 1 : 1 - (life - 0.8f) / 0.2f;
            }
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (kind != Kind.SPLAT) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        // lies flat on the ground, facing up
        Quaternionf flat = new Quaternionf().rotationX(-Mth.HALF_PI).rotateZ(roll);
        renderRotatedQuad(buffer, camera, flat, partialTicks);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<EnergyParticleOptions> {
        @Override
        public Particle createParticle(EnergyParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new ImpactParticle(level, x, y, z, xd, yd, zd, options, sprites, kind);
        }
    }
}
