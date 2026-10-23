package com.wandcraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Wraps a buffer source so an entity renders with the ender dragon's death dissolve: every textured layer (body, armor,
 * eyes...) is drawn as a noise-alpha depth pass followed by an equal-depth decal pass, so only the parts whose noise value
 * exceeds a threshold survive. The threshold is set per vertex from its distance to the body core, so extremities (head,
 * hands, feet) crumble first and the torso goes last, while the survivors darken toward ash black.
 */
final class DissolveBuffer implements MultiBufferSource {
    private static final ResourceLocation NOISE = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_exploding.png");

    /** Noise-cut pass that only writes depth, so transparent texels of armor or fur layers never show the noise colour. */
    private static final RenderType DEPTH = RenderType.create("wandcraft_dissolve_depth", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, true, false,
            RenderType.CompositeState.builder().setShaderState(net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_ALPHA_SHADER)
                    .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(NOISE, false, false))
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                    .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.DEPTH_WRITE)
                    .createCompositeState(false));

    private final MultiBufferSource delegate;
    private final float progress;
    private final boolean noisePass;
    private final double ox, oy, oz, coreY, size;

    /** @param ox,oy,oz entity origin in the vertex coordinate space (camera relative), height the body height. */
    DissolveBuffer(MultiBufferSource delegate, boolean noisePass, float progress, double ox, double oy, double oz, double height) {
        this.delegate = delegate;
        this.noisePass = noisePass;
        this.progress = progress;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.coreY = oy + height * 0.55;
        this.size = Math.max(0.8, height * 0.62);
    }

    /** Each pass re-renders the entity; vanilla buffer sources only keep one shared builder open at a time. */
    @Override
    public VertexConsumer getBuffer(RenderType type) {
        ResourceLocation tex = textureOf(type);
        boolean plain = tex == null || skip(type);
        if (noisePass) return plain ? NOOP : new Mod(delegate.getBuffer(DEPTH), true);
        return plain ? delegate.getBuffer(type) : new Mod(delegate.getBuffer(RenderType.entityDecal(tex)), false);
    }

    private static boolean skip(RenderType t) {
        String str = t.toString();
        int c = str.indexOf(':');
        String n = c > 11 ? str.substring(11, c) : str;
        return n.contains("translucent") || n.contains("glint") || n.contains("outline") || n.contains("lines");
    }

    private static ResourceLocation textureOf(RenderType t) {
        if (t instanceof RenderType.CompositeRenderType c) {
            if (c.state.textureState instanceof net.minecraft.client.renderer.RenderStateShard.TextureStateShard ts) {
                Optional<ResourceLocation> o = ts.texture;
                return o.orElse(null);
            }
        }
        return null;
    }

    private static final VertexConsumer NOOP = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    /** Rewrites vertex colour: the noise pass gets a spatial alpha threshold, the decal pass darkens toward ash black. */
    private final class Mod implements VertexConsumer {
        private final VertexConsumer out;
        private final boolean noise;
        private double x, y, z;

        Mod(VertexConsumer out, boolean noise) {
            this.out = out;
            this.noise = noise;
        }

        @Override
        public VertexConsumer addVertex(float px, float py, float pz) {
            x = px;
            y = py;
            z = pz;
            out.addVertex(px, py, pz);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int bl, int al) {
            if (noise) {
                double dx = x - ox, dy = y - coreY, dz = z - oz;
                double d = Mth.clamp(Math.sqrt(dx * dx * 1.6 + dy * dy + dz * dz * 1.6) / size, 0, 1);
                // extremities (d -> 1) cross the threshold first, the core (d -> 0) last
                double spread = 0.75;
                double thr = Mth.clamp(progress * (1 + spread) - spread * (1 - d), 0, 1);
                out.setColor(255, 255, 255, (int) (thr * 255));
            } else {
                double dark = 1 - 0.8 * Mth.clamp(progress * 1.4, 0, 1);
                out.setColor((int) (r * dark), (int) (g * dark), (int) (bl * dark), al);
            }
            return this;
        }

        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) { out.setNormal(nx, ny, nz); return this; }
    }
}
