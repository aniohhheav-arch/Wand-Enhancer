package dev.mysticarts.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mysticarts.particle.GlowParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Additive, full-bright particle with several motion behaviours. */
public class GlowParticle extends TextureSheetParticle {
    public enum Behavior { SPARK, MOTE, STREAK, RING, RUNE, ASH, INFALL }

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
        public String toString() {
            return "MYSTICARTS_ADDITIVE";
        }
    };

    private final Behavior behavior;
    private final SpriteSet sprites;
    private final float baseSize;
    private final float baseAlpha = 1f;
    private Vec3 target;
    private final float spin;
    private final float startR;
    private final float startG;
    private final float startB;

    protected GlowParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, GlowParticleOptions options, SpriteSet sprites, Behavior behavior) {
        super(level, x, y, z);
        this.behavior = behavior;
        this.sprites = sprites;
        this.rCol = options.r;
        this.gCol = options.g;
        this.bCol = options.b;
        this.startR = options.r;
        this.startG = options.g;
        this.startB = options.b;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.96f;
        this.lifetime = Math.max(4, (int) (options.lifetime * (0.75f + random.nextFloat() * 0.5f)));
        this.baseSize = 0.12f * options.scale * (0.8f + random.nextFloat() * 0.4f);
        this.quadSize = baseSize;
        this.spin = (random.nextFloat() - 0.5f) * 0.2f;
        switch (behavior) {
            case INFALL -> {
                this.target = new Vec3(vx, vy, vz);
                this.xd = this.yd = this.zd = 0;
            }
            case MOTE, ASH, RUNE -> {
                this.xd = vx;
                this.yd = vy;
                this.zd = vz;
                this.friction = 0.98f;
            }
            default -> {
                this.xd = vx;
                this.yd = vy;
                this.zd = vz;
            }
        }
        if (behavior == Behavior.RING) {
            this.xd = this.yd = this.zd = 0;
            this.quadSize = 0.01f;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return behavior == Behavior.ASH ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : ADDITIVE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return behavior == Behavior.ASH ? super.getLightColor(partialTick) : 0xF000F0;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float life = age / (float) lifetime;
        switch (behavior) {
            case INFALL -> {
                Vec3 pos = new Vec3(x, y, z);
                Vec3 to = target.subtract(pos);
                double d = to.length();
                if (d < 0.15) {
                    remove();
                    return;
                }
                Vec3 dir = to.scale(1.0 / d);
                Vec3 swirl = dir.cross(new Vec3(0, 1, 0)).scale(0.5);
                double speed = Math.min(d, 0.05 + 0.25 * life * life + 0.6 / (d + 1.0) * life);
                xd = (dir.x + swirl.x) * speed;
                yd = (dir.y + swirl.y) * speed;
                zd = (dir.z + swirl.z) * speed;
                move(xd, yd, zd);
                alpha = Math.min(1f, life * 4f) * baseAlpha;
                quadSize = baseSize * (1f - life * 0.5f);
            }
            case RING -> {
                quadSize = baseSize * 10f * (float) Math.sqrt(life);
                alpha = (1f - life) * baseAlpha;
            }
            case RUNE -> {
                move(xd, yd, zd);
                xd *= friction;
                yd *= friction;
                zd *= friction;
                quadSize = baseSize * (0.6f + 0.4f * Mth.sin((float) (life * Math.PI)));
                alpha = Mth.sin((float) (life * Math.PI)) * baseAlpha;
                roll += spin * 0.5f;
            }
            case ASH -> {
                // disintegration: flakes peel upward and away, cooling from glowing ember to grey dust
                xd += (random.nextDouble() - 0.5) * 0.01;
                yd += 0.002;
                move(xd, yd, zd);
                xd *= 0.97;
                yd *= 0.97;
                zd *= 0.97;
                float cool = Math.min(1f, life * 2.5f);
                rCol = Mth.lerp(cool, startR, 0.25f);
                gCol = Mth.lerp(cool, startG, 0.22f);
                bCol = Mth.lerp(cool, startB, 0.2f);
                alpha = (1f - life) * baseAlpha;
            }
            default -> {
                move(xd, yd, zd);
                xd *= friction;
                yd *= friction;
                zd *= friction;
                alpha = (1f - life * life) * baseAlpha;
                if (behavior == Behavior.SPARK) quadSize = baseSize * (1f - life);
            }
        }
        oRoll = roll;
        roll += spin;
        setSpriteFromAge(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (behavior != Behavior.STREAK) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        Vector3f vel = new Vector3f((float) xd, (float) yd, (float) zd);
        float speed = vel.length();
        if (speed < 1e-4f) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        Vector3f dir = new Vector3f(vel).div(speed);
        Vector3f toCam = new Vector3f(-px, -py, -pz).normalize();
        Vector3f side = new Vector3f(dir).cross(toCam);
        if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
        side.normalize().mul(quadSize * 0.5f);
        float len = quadSize * 2f + speed * 6f;
        Vector3f tail = new Vector3f(dir).mul(-len);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        buffer.addVertex(px - side.x + tail.x, py - side.y + tail.y, pz - side.z + tail.z).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(px + side.x + tail.x, py + side.y + tail.y, pz + side.z + tail.z).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(px + side.x, py + side.y, pz + side.z).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        buffer.addVertex(px - side.x, py - side.y, pz - side.z).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    public record Provider(SpriteSet sprites, Behavior behavior) implements ParticleProvider<GlowParticleOptions> {
        @Override
        public Particle createParticle(GlowParticleOptions options, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GlowParticle(level, x, y, z, vx, vy, vz, options, sprites, behavior);
        }
    }
}
