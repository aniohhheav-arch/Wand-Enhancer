package dev.infinitygauntlet.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.infinitygauntlet.InfinityGauntlet;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Render types of the VFX engine. Everything is unlit, depth-tested but not depth-writing, so layered effects blend
 * into each other: additive types for energy, a sorted translucent type for smoke and haze.
 */
public final class ModRenderTypes {
    /** 2×2 atlas of smoke puffs, so all smoke of one effect shares a single depth-sorted batch. */
    public static final ResourceLocation SMOKE_ATLAS = tex("smoke_atlas");
    public static final ResourceLocation GLOW = tex("glow");
    public static final ResourceLocation CORE = tex("core");
    public static final ResourceLocation SWIRL = tex("swirl");
    public static final ResourceLocation RING = tex("ring");
    public static final ResourceLocation BEAM = tex("beam");
    public static final ResourceLocation GLYPH_SPACE = tex("glyph_space");
    public static final ResourceLocation CRACKLE = tex("crackle");
    public static final ResourceLocation GLYPH_TIME = tex("glyph_time");
    public static final ResourceLocation GEAR_RING = tex("gear_ring");
    public static final ResourceLocation WHITE = tex("white");

    private static final RenderStateShard.ShaderStateShard POSITION_TEX_COLOR = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

    private static final Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(texture -> RenderType.create(
            InfinityGauntlet.MOD_ID + "_additive", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_TEX_COLOR)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(texture -> RenderType.create(
            InfinityGauntlet.MOD_ID + "_translucent", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_TEX_COLOR)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false)));

    /** Untextured additive geometry (lightning arcs). */
    public static final RenderType ENERGY_LINES = RenderType.create(
            InfinityGauntlet.MOD_ID + "_energy_lines", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false));

    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    public static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT.apply(texture);
    }

    private static ResourceLocation tex(String name) {
        return InfinityGauntlet.id("textures/vfx/" + name + ".png");
    }

    private ModRenderTypes() {
    }
}
