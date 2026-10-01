package com.infinitemultiverse.client.vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Full-bright, sprite-animated particle whose colour fades from a start to an end tint while it swells in,
 * then shrinks and fades out. One class backs both the mote and spark sprite sets.
 */
public class EnergyParticle extends TextureSheetParticle {
    private static final float FADE_IN = 0.12f;
    private static final float FADE_OUT_START = 0.55f;

    private final SpriteSet sprites;
    private float startR = 1f, startG = 1f, startB = 1f;
    private float endR = 1f, endG = 1f, endB = 1f;
    private float baseSize = 0.15f;

    protected EnergyParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.9f;
        this.lifetime = 20;
        this.quadSize = baseSize;
        setSpriteFromAge(sprites);
    }

    public EnergyParticle configure(int startColor, int endColor, float size, int lifetime, float friction) {
        startR = ((startColor >> 16) & 0xFF) / 255f;
        startG = ((startColor >> 8) & 0xFF) / 255f;
        startB = (startColor & 0xFF) / 255f;
        endR = ((endColor >> 16) & 0xFF) / 255f;
        endG = ((endColor >> 8) & 0xFF) / 255f;
        endB = (endColor & 0xFF) / 255f;
        this.baseSize = size;
        this.quadSize = size;
        this.lifetime = Math.max(2, lifetime);
        this.friction = friction;
        applyColor(0f);
        return this;
    }

    public EnergyParticle withGravity(float gravity) {
        this.gravity = gravity;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
            applyColor(age / (float) lifetime);
        }
    }

    private void applyColor(float t) {
        rCol = Mth.lerp(t, startR, endR);
        gCol = Mth.lerp(t, startG, endG);
        bCol = Mth.lerp(t, startB, endB);
        alpha = t < FADE_OUT_START ? 1f : 1f - (t - FADE_OUT_START) / (1f - FADE_OUT_START);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
        float envelope = t < FADE_IN ? t / FADE_IN : 1f - 0.6f * (t - FADE_IN) / (1f - FADE_IN);
        return baseSize * envelope;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new EnergyParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
