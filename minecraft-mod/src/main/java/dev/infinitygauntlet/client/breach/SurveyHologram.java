package dev.infinitygauntlet.client.breach;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A miniature (aligned with the world's compass, like the panel's movement arrows) of the blocks around the real
 * target, cut from the live view's streamed slice, with a tiny copy of the target standing where it really is.
 */
public final class SurveyHologram {
    private static final float SCALE = 0.12f;

    public static void render(int specimenId, LivingEntity dummy, float t, PoseStack pose, MultiBufferSource buffers) {
        LiveView.Slice slice = LiveView.currentSlice(specimenId);
        dev.infinitygauntlet.network.ViewPayload view = LiveView.currentView(specimenId);
        // projector base
        VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                0.55f, 0.85f, 40, t * 0.01f, 0.35f, 0.9f, 1f, 0.8f);
        VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_SPACE)), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                1.6f, 0.3f, 0.7f, 1f, 0.5f);
        if (slice == null || view == null) {
            return;
        }
        int r = 5, cx = slice.sx / 2, cz = slice.sz / 2, y0 = Math.max(0, slice.sy / 2 - 5), y1 = Math.min(slice.sy, y0 + 9);
        int size = 2 * r + 1;
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.translate(0, 0.08, 0);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-size / 2.0, 0, -size / 2.0);
        for (LiveView.Cell c : slice.exposed) {
            if (Math.abs(c.x() - cx) > r || Math.abs(c.z() - cz) > r || c.y() < y0 || c.y() >= y1
                    || c.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pose.pushPose();
            pose.translate(c.x() - cx + r, c.y() - y0, c.z() - cz + r);
            blocks.renderSingleBlock(c.state(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        // the target where it really stands (eye position minus its eye height)
        double tx = view.eye().x - slice.origin.getX() - cx + r;
        double tz = view.eye().z - slice.origin.getZ() - cz + r;
        double ty = view.eye().y - (dummy == null ? 1.5 : dummy.getEyeHeight()) - slice.origin.getY() - y0;
        if (dummy != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, tx, ty, tz, 0, 0, pose, buffers, LightTexture.FULL_BRIGHT);
        }
        VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)),
                Minecraft.getInstance().gameRenderer.getMainCamera().rotation(), (float) tx, (float) ty + 1, (float) tz, 3f, 0, 1f, 0.3f, 0.6f,
                0.6f + 0.3f * (float) Math.sin(t * 0.3));
        pose.popPose();
        // scan plane sweeping up through the miniature
        float h = (y1 - y0) * SCALE;
        float y = 0.08f + (t * 0.02f % 1f) * h;
        pose.pushPose();
        pose.translate(0, y, 0);
        VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0,
                size * SCALE * 0.72f, 32, 0, 0.3f, 0.85f, 1f, 0.25f);
        pose.popPose();
    }

    public static void clear() {
    }

    private SurveyHologram() {
    }
}
