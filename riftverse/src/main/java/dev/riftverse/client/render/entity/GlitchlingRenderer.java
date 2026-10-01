package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.GlitchlingEntity;
import dev.riftverse.util.ColorUtil;
import dev.riftverse.util.Hash;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * A corrupted humanoid made of misaligned voxels. Parts tear away from the body, colours cycle through a broken
 * palette and chromatic ghost copies trail behind it.
 */
public class GlitchlingRenderer extends ProceduralRenderer<GlitchlingEntity> {
    private static final int[] PALETTE = {0x20FF8F, 0xFF2BD6, 0x2BD6FF, 0xF6FF3B};

    public GlitchlingRenderer(EntityRendererProvider.Context context) {
        super(context, 0.4f);
    }

    @Override
    protected void draw(GlitchlingEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        int frame = (int) (age / 2f);
        int id = entity.getId();
        float walk = entity.walkAnimation.position(partial) * 0.6662f;
        float speed = Math.min(1f, entity.walkAnimation.speed(partial));
        float swing = Mth.cos(walk) * 1.1f * speed;
        boolean burst = Hash.unit(id, frame / 3, 7) < 0.18f || entity.hurtTime > 0;

        drawBody(entity, ps, buffers, light, overlay, frame, swing, burst, 0f, -1);
        if (burst) {
            // chromatic aberration ghosts
            drawBody(entity, ps, buffers, light, overlay, frame, swing, true, 0.07f, 0xFF2040);
            drawBody(entity, ps, buffers, light, overlay, frame, swing, true, -0.07f, 0x20E0FF);
        }
        // pixel shards orbiting erratically
        for (int i = 0; i < 6; i++) {
            float hx = Hash.unit(id, frame, i * 3) - 0.5f;
            float hy = Hash.unit(id, frame, i * 3 + 1);
            float hz = Hash.unit(id, frame, i * 3 + 2) - 0.5f;
            int col = PALETTE[(i + frame) % PALETTE.length];
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), ps.last(), hx * 1.4f, 0.2f + hy * 1.6f, hz * 1.4f, 0.04f, 0.04f, 0.04f, col, 1f, FxDraw.FULL_BRIGHT, overlay);
        }
    }

    /** ghost < 0 draws the real body; otherwise a tinted additive copy offset sideways. */
    private void drawBody(GlitchlingEntity entity, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int frame, float swing, boolean burst,
                          float offset, int ghost) {
        int id = entity.getId();
        boolean real = ghost < 0;
        ps.pushPose();
        ps.translate(offset, 0, 0);
        part(entity, ps, buffers, light, overlay, frame, 0, real, ghost, burst, 0f, 1.4f, 0f, 0.26f, 0.26f, 0.26f, 0f, 0f);
        part(entity, ps, buffers, light, overlay, frame, 1, real, ghost, burst, 0f, 0.9f, 0f, 0.24f, 0.3f, 0.14f, 0f, 0f);
        part(entity, ps, buffers, light, overlay, frame, 2, real, ghost, burst, 0.34f, 0.9f, 0f, 0.09f, 0.3f, 0.09f, -swing, 0.16f);
        part(entity, ps, buffers, light, overlay, frame, 3, real, ghost, burst, -0.34f, 0.9f, 0f, 0.09f, 0.3f, 0.09f, swing, -0.16f);
        part(entity, ps, buffers, light, overlay, frame, 4, real, ghost, burst, 0.12f, 0.3f, 0f, 0.1f, 0.3f, 0.1f, swing, 0f);
        part(entity, ps, buffers, light, overlay, frame, 5, real, ghost, burst, -0.12f, 0.3f, 0f, 0.1f, 0.3f, 0.1f, -swing, 0f);
        if (real) {
            // eyes: two blazing pixels that occasionally swap places with static
            boolean flicker = Hash.unit(id, frame, 99) < 0.1f;
            for (int s = -1; s <= 1; s += 2) {
                int col = flicker ? PALETTE[frame % PALETTE.length] : 0xFFFFFF;
                FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), ps.last(), s * 0.1f, 1.43f, 0.265f, 0.05f, 0.03f, 0.01f, col, 1f, FxDraw.FULL_BRIGHT, overlay);
            }
        }
        ps.popPose();
    }

    /**
     * One limb or block of the body. (px, py, pz) is the joint; the block hangs down from it by 2*hy when it swings,
     * otherwise it is centred on the joint. Displacement is deterministic per frame so the jitter is stable for a few
     * frames before snapping again.
     */
    private void part(GlitchlingEntity entity, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int frame, int index, boolean real, int ghost,
                      boolean burst, float px, float py, float pz, float hx, float hy, float hz, float swing, float roll) {
        int id = entity.getId();
        float jitter = burst ? 0.18f : 0.03f;
        float dx = (Hash.unit(id, frame, index * 11) - 0.5f) * jitter;
        float dy = (Hash.unit(id, frame, index * 11 + 1) - 0.5f) * jitter * 0.5f;
        float dz = (Hash.unit(id, frame, index * 11 + 2) - 0.5f) * jitter;
        boolean limb = swing != 0f || roll != 0f;
        ps.pushPose();
        ps.translate(px + dx, py + dy + (limb ? hy : 0f), pz + dz);
        if (limb) {
            ps.mulPose(Axis.XP.rotation(swing));
            ps.mulPose(Axis.ZP.rotation(roll * 0.5f));
        }
        float cy = limb ? -hy : 0f;
        if (real) {
            int base = 0x17171F;
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, cy, 0, hx, hy, hz, base, 1f, light, overlay);
            // emissive "dead pixel" faces cycling through the palette
            int col = PALETTE[(index + frame / 4 + id) % PALETTE.length];
            float on = Hash.unit(id, frame / 2, index + 50) < 0.55f ? 0.9f : 0.25f;
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), ps.last(), 0, cy, 0, hx * 1.04f, hy * 1.04f, hz * 1.04f, ColorUtil.scale(col, on * 0.45f), 1f, FxDraw.FULL_BRIGHT, overlay);
        } else {
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), ps.last(), 0, cy, 0, hx, hy, hz, ColorUtil.scale(ghost, 0.35f), 1f, FxDraw.FULL_BRIGHT, overlay);
        }
        ps.popPose();
    }
}
