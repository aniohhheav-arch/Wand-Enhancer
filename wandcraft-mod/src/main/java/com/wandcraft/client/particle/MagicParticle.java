package com.wandcraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fullbright, tinted particle with a few motion styles. Used for every Wandcraft effect. */
public class MagicParticle extends TextureSheetParticle {
    public enum Style { GLOW, SPARK, WISP, RUNE }

    private final Style style;
    private final float baseSize;
    private final SpriteSet sprites;

    MagicParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, ColorParticleOption o, SpriteSet sprites, Style style) {
        super(level, x, y, z, xd, yd, zd);
        this.style = style;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.rCol = o.getRed();
        this.gCol = o.getGreen();
        this.bCol = o.getBlue();
        float packed = Math.max(1, Math.round(o.getAlpha() * 255));
        pickSprite(sprites);
        this.hasPhysics = false;
        switch (style) {
            case GLOW -> {
                baseSize = packed / 100f * 0.18f;
                lifetime = 12 + random.nextInt(10);
                friction = 0.88f;
            }
            case SPARK -> {
                baseSize = packed / 100f * 0.22f;
                lifetime = 8 + random.nextInt(8);
                friction = 0.9f;
                roll = random.nextFloat() * Mth.TWO_PI;
                oRoll = roll;
            }
            case WISP -> {
                baseSize = packed / 100f * 0.35f;
                lifetime = 24 + random.nextInt(16);
                friction = 0.94f;
                gravity = -0.004f;
                roll = random.nextFloat() * Mth.TWO_PI;
                oRoll = roll;
            }
            default -> {
                baseSize = packed / 20f; // radius in blocks
                lifetime = 16;
                friction = 1f;
                this.xd = this.yd = this.zd = 0;
            }
        }
        this.quadSize = baseSize;
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= gravity;
        move(xd, yd, zd);
        xd *= friction; yd *= friction; zd *= friction;
        float t = age / (float) lifetime;
        switch (style) {
            case GLOW -> { quadSize = baseSize * (1f - t * 0.7f); alpha = Mth.clamp(1f - t * t, 0, 1); }
            case SPARK -> { quadSize = baseSize * (1f - t * t); alpha = 1f - t; roll += 0.12f; }
            case WISP -> { quadSize = baseSize * (0.6f + t * 0.9f); alpha = Mth.clamp((1f - t) * 0.7f, 0, 1) * Math.min(1f, t * 8f); roll += 0.02f; }
            default -> { alpha = Mth.clamp(Math.min(t * 6f, (1f - t) * 3f), 0, 1) * 0.9f; roll += 0.02f; }
        }
    }

    @Override
    public void render(VertexConsumer buf, Camera cam, float pt) {
        if (style != Style.RUNE) {
            super.render(buf, cam, pt);
            return;
        }
        // Flat decal lying on the ground, slowly turning.
        Vector3f c = cam.getPosition().toVector3f();
        float px = (float) (Mth.lerp(pt, xo, x) - c.x), py = (float) (Mth.lerp(pt, yo, y) - c.y) + 0.02f, pz = (float) (Mth.lerp(pt, zo, z) - c.z);
        float ang = Mth.lerp(pt, oRoll, roll);
        float s = baseSize;
        Vector3f[] v = {new Vector3f(-s, 0, -s), new Vector3f(-s, 0, s), new Vector3f(s, 0, s), new Vector3f(s, 0, -s)};
        Quaternionf q = new Quaternionf().rotationY(ang);
        for (Vector3f p : v) p.rotate(q).add(px, py, pz);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = 15728880;
        buf.addVertex(v[0].x, v[0].y, v[0].z).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buf.addVertex(v[1].x, v[1].y, v[1].z).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buf.addVertex(v[2].x, v[2].y, v[2].z).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buf.addVertex(v[3].x, v[3].y, v[3].z).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        // underside so it is visible from below glass/ice too
        buf.addVertex(v[3].x, v[3].y, v[3].z).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buf.addVertex(v[2].x, v[2].y, v[2].z).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buf.addVertex(v[1].x, v[1].y, v[1].z).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buf.addVertex(v[0].x, v[0].y, v[0].z).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    @Override public int getLightColor(float pt) { return 15728880; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<ColorParticleOption> {
        @Override
        public Particle createParticle(ColorParticleOption o, ClientLevel l, double x, double y, double z, double xd, double yd, double zd) {
            return new MagicParticle(l, x, y, z, xd, yd, zd, o, sprites, style);
        }
    }
}
