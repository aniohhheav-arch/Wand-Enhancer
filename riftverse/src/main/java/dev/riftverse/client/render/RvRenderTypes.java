package dev.riftverse.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.riftverse.Riftverse;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Render types shared by every procedural creature and effect. */
public final class RvRenderTypes {
    public static final ResourceLocation WHITE = Riftverse.id("textures/misc/white.png");
    public static final ResourceLocation GLOW = Riftverse.id("textures/misc/glow.png");

    /** Additive, animated energy ribbons (beams, rings, auras). UV: u along the beam, v across it. */
    public static final RenderType ENERGY = RenderType.create("riftverse_energy", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            8192, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> RvShaders.energy))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Same ribbons blended normally, so dark colours occlude (the black seam of a reality tear). */
    public static final RenderType VOID_RIBBON = RenderType.create("riftverse_void_ribbon", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            2048, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> RvShaders.energy))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private RvRenderTypes() {}

    /** Lit, opaque vertex-coloured geometry. */
    public static RenderType solid() {
        return RenderType.entityCutoutNoCull(WHITE);
    }

    /** Lit, translucent vertex-coloured geometry (jelly bells, ghostly robes). */
    public static RenderType translucent() {
        return RenderType.entityTranslucent(WHITE);
    }

    /** Full-bright additive geometry. */
    public static RenderType glow() {
        return RenderType.eyes(WHITE);
    }

    /** Full-bright additive soft sprite (billboard halos). */
    public static RenderType softGlow() {
        return RenderType.eyes(GLOW);
    }
}
