package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.RuptureDraw;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.MiniPortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class MiniPortalRenderer extends EffectEntityRenderer<MiniPortalEntity> {
    public MiniPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(MiniPortalEntity portal, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float aperture = portal.aperture(pt);
        if (aperture <= 0) {
            return;
        }
        boolean closing = portal.tickCount + pt > portal.maxLife() - MiniPortalEntity.CLOSE_TICKS;
        float open = closing ? 1 : aperture;
        float close = closing ? 1 - aperture : 0;
        float t = portal.tickCount + pt;
        RuptureDraw.draw(pose, buffers, camLocal, portal.visualRadius(), open, close, t, portal.getId() * 7919L, RuptureDraw.SPACE, 1.1f, false);

        // muzzle ring: an aperture facing the tracked target
        Entity target = portal.target();
        Vec3 aim = target != null ? target.getBoundingBox().getCenter().subtract(portal.getPosition(pt)).normalize() : camLocal.normalize();
        Vec3 ax = VfxDraw.perpendicular(aim);
        Vec3 ay = aim.cross(ax).normalize();
        VertexConsumer ring = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        float rr = portal.visualRadius() * 1.5f * aperture;
        VfxDraw.ring(pose.last(), ring, ax, ay, rr * 0.75f, rr, 32, t * 0.05f, 0.62f, 0.9f, 1f, 0.8f * aperture);
    }
}
