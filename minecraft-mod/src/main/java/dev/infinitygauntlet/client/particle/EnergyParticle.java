package dev.infinitygauntlet.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.infinitygauntlet.vfx.EnergyParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

/**
 * The single particle implementation behind all mod particle types. Behaviour (drift, inward suction, billowing
 * smoke, suspended embers) and colour come from {@link EnergyParticleOptions}; energy particles render additively and
 * full-bright, smoke renders as sorted translucent puffs.
 */
public class EnergyParticle extends TextureSheetParticle {
    /** Additive particle sheet: overlapping sparks and glows add up to a hot, bloom-like core. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }

        @Override
        public String toString() {
            return "INFINITYGAUNTLET_ADDITIVE";
        }
    };

    private final int behavior;
    private final boolean smoke;
    private final SpriteSet sprites;
    private final float baseSize;
    private final double tx, ty, tz;
    private final float baseAlpha;

    protected EnergyParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, EnergyParticleOptions options,
                             SpriteSet sprites, boolean smoke, int defaultLife) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.smoke = smoke;
        this.behavior = options.behavior();
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = smoke ? 0.94f : 0.9f;
        int color = options.color();
        this.rCol = (color >> 16 & 255) / 255f;
        this.gCol = (color >> 8 & 255) / 255f;
        this.bCol = (color & 255) / 255f;
        this.baseSize = options.size() * (0.8f + random.nextFloat() * 0.4f);
        this.quadSize = baseSize;
        this.lifetime = options.lifetime() > 0 ? options.lifetime() + random.nextInt(Math.max(1, options.lifetime() / 4)) : defaultLife + random.nextInt(8);
        this.baseAlpha = smoke ? 0.7f : 1f;
        this.alpha = baseAlpha;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        if (behavior == EnergyParticleOptions.BEHAVIOR_INWARD) {
            this.tx = xd;
            this.ty = yd;
            this.tz = zd;
            this.xd = this.yd = this.zd = 0;
        } else {
            this.tx = this.ty = this.tz = 0;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
        }
        if (smoke) {
            setSprite(sprites.get(random.nextInt(4), 3));
        } else {
            pickSprite(sprites);
        }
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
        switch (behavior) {
            case EnergyParticleOptions.BEHAVIOR_INWARD -> {
                double dx = tx - x, dy = ty - y, dz = tz - z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 0.15) {
                    remove();
                    return;
                }
                double accel = 0.025 + 0.06 / Math.max(0.5, dist);
                xd = xd * 0.88 + dx / dist * accel * Math.min(dist, 3);
                yd = yd * 0.88 + dy / dist * accel * Math.min(dist, 3);
                zd = zd * 0.88 + dz / dist * accel * Math.min(dist, 3);
                quadSize = baseSize * (float) Math.min(1, dist * 0.6 + 0.3);
                alpha = Math.min(1f, age / 5f);
            }
            case EnergyParticleOptions.BEHAVIOR_BILLOW -> {
                xd *= friction;
                yd = yd * friction + 0.002;
                zd *= friction;
                quadSize = baseSize * (1 + life * 1.1f);
                roll += 0.02f;
                alpha = baseAlpha * Math.min(1f, age / 4f) * (1 - life);
            }
            case EnergyParticleOptions.BEHAVIOR_SUSPENDED -> {
                xd = yd = zd = 0;
                alpha = (0.6f + 0.4f * Mth.sin(age * 0.6f + roll * 5)) * (1 - life * life);
            }
            default -> {
                xd *= friction;
                yd *= friction;
                zd *= friction;
                alpha = baseAlpha * (life < 0.6f ? 1 : 1 - (life - 0.6f) / 0.4f);
                quadSize = baseSize * (1 - life * 0.5f);
                roll += 0.05f;
            }
        }
        move(xd, yd, zd);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return smoke ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : ADDITIVE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public record Provider(SpriteSet sprites, boolean smoke, int defaultLife) implements ParticleProvider<EnergyParticleOptions> {
        @Override
        public Particle createParticle(EnergyParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new EnergyParticle(level, x, y, z, xd, yd, zd, options, sprites, smoke, defaultLife);
        }
    }
}
