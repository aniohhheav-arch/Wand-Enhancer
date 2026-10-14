package com.stickyfingers.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Custom zipper VFX particles. Emissive ones ignore world light so they glow at night. */
public final class ZipperParticles {
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Client particle density setting: randomly drops particles below 100%. Returning null skips the particle. */
    private static boolean skip(ClientLevel level) {
        int density = com.stickyfingers.config.SFConfig.safe(com.stickyfingers.config.SFConfig.PARTICLE_DENSITY::get, 100);
        return density < 100 && level.random.nextInt(100) >= density;
    }

    abstract static class Base extends TextureSheetParticle {
        protected final SpriteSet sprites;
        protected boolean emissive = true;
        protected boolean animated = true;
        protected float startSize;
        protected float endSizeMul = 1f;
        protected boolean fade = true;

        Base(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z, xd, yd, zd);
            this.sprites = sprites;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (animated && !removed) setSpriteFromAge(sprites);
            float p = age / (float) lifetime;
            quadSize = startSize * Mth.lerp(p, 1f, endSizeMul);
            if (fade) alpha = 1f - p * p;
        }

        void life(int v) { lifetime = v; }
        void grav(float v) { gravity = v; }
        void fric(float v) { friction = v; }
        void physics(boolean v) { hasPhysics = v; }
        void still() { xd = yd = zd = 0; }
        void alphaV(float v) { alpha = v; }
        void randomRoll() { roll = oRoll = random.nextFloat() * Mth.TWO_PI; }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            return emissive ? FULL_BRIGHT : super.getLightColor(partial);
        }
    }

    /** Bright four-point metallic star. */
    public static final class Spark implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Spark(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (skip(level)) return null;
            Base p = new Base(level, x, y, z, xd, yd, zd, sprites) {};
            p.life(8 + level.random.nextInt(8));
            p.startSize = 0.07f + level.random.nextFloat() * 0.07f;
            p.endSizeMul = 0.2f;
            p.grav(0.4f);
            p.fric(0.86f);
            p.physics(true);
            p.setColor(1f, 0.92f - level.random.nextFloat() * 0.2f, 0.7f);
            return p;
        }
    }

    /** A golden tooth that tumbles and falls, lit by the world. */
    public static final class Tooth implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Tooth(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (skip(level)) return null;
            Base p = new Base(level, x, y, z, xd, yd + 0.08, zd, sprites) {
                final float spin = (level.random.nextFloat() - 0.5f) * 0.6f;

                @Override
                public void tick() {
                    oRoll = roll;
                    roll += spin;
                    super.tick();
                }
            };
            p.pickSprite(sprites);
            p.animated = false;
            p.emissive = false;
            p.life(20 + level.random.nextInt(15));
            p.startSize = 0.06f + level.random.nextFloat() * 0.03f;
            p.grav(1.0f);
            p.fric(0.96f);
            p.physics(true);
            return p;
        }
    }

    /** Glowing mote leaking from the space behind a zipper. */
    public static final class Mote implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Mote(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (skip(level)) return null;
            Base p = new Base(level, x, y, z, xd, yd, zd, sprites) {};
            p.life(25 + level.random.nextInt(25));
            p.startSize = 0.05f + level.random.nextFloat() * 0.08f;
            p.endSizeMul = 0.3f;
            p.grav(-0.02f);
            p.fric(0.92f);
            float c = level.random.nextFloat();
            p.setColor(0.45f + c * 0.3f, 0.35f + c * 0.25f, 1f);
            return p;
        }
    }

    /** Short cyan seam light. */
    public static final class Seam implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Seam(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (skip(level)) return null;
            Base p = new Base(level, x, y, z, xd, yd, zd, sprites) {};
            p.life(8 + level.random.nextInt(6));
            p.startSize = 0.12f;
            p.endSizeMul = 0.1f;
            p.grav(0f);
            p.fric(0.7f);
            return p;
        }
    }

    /** Expanding shockwave ring for punches. */
    public static final class Impact implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Impact(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (skip(level)) return null;
            Base p = new Base(level, x, y, z, 0, 0, 0, sprites) {};
            p.life(7);
            p.startSize = 0.35f + level.random.nextFloat() * 0.2f;
            p.endSizeMul = 3.2f;
            p.grav(0f);
            p.physics(false);
            p.still();
            p.randomRoll();
            return p;
        }
    }

    /** Stand-coloured afterimage silhouette for dashes and dodges. */
    public static final class Afterimage implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Afterimage(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            if (skip(level)) return null;
            Base p = new Base(level, x, y, z, 0, 0, 0, sprites) {};
            p.pickSprite(sprites);
            p.animated = false;
            p.life(10 + level.random.nextInt(5));
            p.startSize = 0.9f;
            p.endSizeMul = 1.1f;
            p.grav(0f);
            p.physics(false);
            p.still();
            p.alphaV(0.6f);
            p.setColor(0.55f, 0.6f, 1f);
            return p;
        }
    }

    private ZipperParticles() {}
}
