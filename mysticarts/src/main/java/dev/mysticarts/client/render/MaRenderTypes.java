package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.mysticarts.MysticArts;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Render types for spell geometry. ENERGY and RUNE are additive and full-bright. */
public final class MaRenderTypes {
    public static final ResourceLocation WHITE = MysticArts.id("textures/misc/white.png");
    public static final ResourceLocation GLOW = MysticArts.id("textures/misc/glow.png");

    /** Additive energy ribbons (beams, whips, arcs). UV: u along the beam, v across it. */
    public static final RenderType ENERGY = RenderType.create("mysticarts_energy", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            16384, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> MaShaders.energy))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Procedural mystic patterns (mandalas, rims, clocks, chains); see {@link MaDraw#rune}. */
    public static final RenderType RUNE = RenderType.create("mysticarts_rune", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            16384, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> MaShaders.rune))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Same as RUNE but drawn over everything (HUD-like world markers such as detection glyphs). */
    public static final RenderType RUNE_SEE_THROUGH = RenderType.create("mysticarts_rune_xray", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
            4096, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> MaShaders.rune))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private MaRenderTypes() {}

    public static RenderType solid() {
        return RenderType.entityCutoutNoCull(WHITE);
    }

    public static RenderType translucent() {
        return RenderType.entityTranslucent(WHITE);
    }

    public static RenderType glow() {
        return RenderType.eyes(WHITE);
    }

    public static RenderType softGlow() {
        return RenderType.eyes(GLOW);
    }
}
