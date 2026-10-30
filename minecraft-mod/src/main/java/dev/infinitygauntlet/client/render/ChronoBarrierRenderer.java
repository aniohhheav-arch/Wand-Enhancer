package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.ChronoBarrierEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** A disc of concentric clockwork rings turning at different speeds, with a clock dial at its heart. */
public class ChronoBarrierRenderer extends EffectEntityRenderer<ChronoBarrierEntity> {
    public ChronoBarrierRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(ChronoBarrierEntity barrier, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = barrier.tickCount + pt;
        float appear = VfxDraw.smooth(t / 10f) * (1 - VfxDraw.smooth((t - barrier.maxLife() + 8) / 8f));
        // from the caster's own first-person view the shield is right in front of the eyes: keep it see-through
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        float viewAlpha = mc.getCameraEntity() == barrier.ownerEntity() && mc.options.getCameraType().isFirstPerson() ? 0.4f : 1f;
        float r = barrier.visualRadius() * (0.3f + 0.7f * appear);
        Vec3 n = barrier.facing(pt);
        Vec3 ax = new Vec3(-n.z, 0, n.x).normalize();
        Vec3 ay = new Vec3(0, 1, 0);

        VertexConsumer fill = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.GLOW));
        VfxDraw.plane(pose.last(), fill, ax, ay, r * 2.6f, 0.05f, 0.35f, 0.15f, 0.35f * appear * viewAlpha);
        VertexConsumer dial = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME));
        VfxDraw.plane(pose.last(), dial, ax, ay, r * 1.15f, 0.7f, 1f, 0.78f, 0.75f * appear * viewAlpha);
        VertexConsumer gear = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING));
        for (int k = 0; k < 4; k++) {
            float speed = (0.02f + k * 0.012f) * (k % 2 == 0 ? 1 : -1);
            double angle = t * speed;
            Vec3 rx = VfxDraw.rotate(ax, n, angle);
            Vec3 ry = VfxDraw.rotate(ay, n, angle);
            float rr = r * (0.45f + k * 0.2f);
            VfxDraw.ring(pose.last(), gear, rx, ry, rr * 0.86f, rr, 56, 0, k == 3 ? 0.7f : 0.12f, k == 3 ? 1f : 0.86f, k == 3 ? 0.78f : 0.35f,
                    0.85f * appear * viewAlpha);
        }
        VertexConsumer rim = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        VfxDraw.ring(pose.last(), rim, ax, ay, r * 1.05f, r * 1.25f, 64, t * 0.01f, 0.12f, 0.86f, 0.35f, 0.5f * appear * viewAlpha);
    }
}
