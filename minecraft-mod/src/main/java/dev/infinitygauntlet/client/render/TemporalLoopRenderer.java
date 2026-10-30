package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.TemporalLoopEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** A clock symbol on the ground whose rim fills as the loop period runs out, with a ring rising and resetting each loop. */
public class TemporalLoopRenderer extends EffectEntityRenderer<TemporalLoopEntity> {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    public TemporalLoopRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(TemporalLoopEntity loop, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = loop.tickCount + pt;
        float appear = VfxDraw.smooth(t / 12f) * (1 - VfxDraw.smooth((t - loop.maxLife() + 12) / 12f));
        float r = loop.visualRadius() * (0.4f + 0.6f * appear);
        float phase = (t % loop.period()) / loop.period();
        pose.pushPose();
        pose.translate(0, 0.04, 0);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-phase * (float) Math.PI * 2));
        VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME)), X, Z, r * 2f, 0.12f, 0.86f, 0.35f, 0.85f * appear);
        pose.popPose();
        VertexConsumer white = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        VfxDraw.ring(pose.last(), white, X, Z, r * 1.05f, r * 1.12f, 64, 0, 0.12f, 0.86f, 0.35f, 0.5f * appear);
        VfxDraw.arc(pose.last(), white, X, Z, r * 1.12f, r * 1.25f, 64, phase, 0.7f, 1f, 0.78f, 0.9f * appear);
        // the rising ring: climbs over the period and snaps back down when the loop resets
        VertexConsumer gear = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING));
        pose.translate(0, phase * 3.2f, 0);
        pose.mulPose(Axis.YP.rotation(t * 0.1f));
        VfxDraw.ring(pose.last(), gear, X, Z, r * 0.85f, r, 48, 0, 0.12f, 0.86f, 0.35f, (1 - phase) * 0.8f * appear);
        pose.popPose();
    }
}
