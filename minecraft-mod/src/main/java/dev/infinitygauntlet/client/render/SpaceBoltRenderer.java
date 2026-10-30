package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.SpaceBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class SpaceBoltRenderer extends EffectEntityRenderer<SpaceBoltEntity> {
    public SpaceBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(SpaceBoltEntity bolt, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        Quaternionf rot = entityRenderDispatcher.cameraOrientation();
        float t = bolt.tickCount + pt;
        Vec3 tail = bolt.getDeltaMovement().scale(-2.2);
        VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
        VfxDraw.beam(pose.last(), beam, Vec3.ZERO, tail, camLocal, 0.5f, -t * 0.3f, 0.16f, 0.43f, 1f, 0.8f);
        VfxDraw.beam(pose.last(), beam, Vec3.ZERO, tail.scale(0.7), camLocal, 0.18f, -t * 0.5f, 0.75f, 0.95f, 1f, 1f);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(pose.last(), glow, rot, 0, 0, 0, 1.4f, 0, 0.16f, 0.43f, 1f, 0.7f);
        VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
        VfxDraw.billboard(pose.last(), swirl, rot, 0, 0, 0, 0.8f, t * 0.4f, 0.5f, 0.85f, 1f, 1f);
        VertexConsumer core = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE));
        VfxDraw.billboard(pose.last(), core, rot, 0, 0, 0, 0.35f, 0, 1f, 1f, 1f, 1f);
    }
}
