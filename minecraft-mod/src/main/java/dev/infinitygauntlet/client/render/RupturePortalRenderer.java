package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.client.vfx.RuptureDraw;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class RupturePortalRenderer extends EffectEntityRenderer<RupturePortalEntity> {
    public RupturePortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(RupturePortalEntity portal, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = portal.tickCount + pt;
        boolean gateway = portal.style() == RupturePortalEntity.STYLE_GATEWAY;
        // an unlinked rupture is visibly less stable: its energy stutters until its twin is opened
        float energy = portal.isLinked() ? 1f : 0.7f + 0.25f * Mth.sin(t * 0.7f) * Mth.sin(t * 0.23f);
        RuptureDraw.draw(pose, buffers, camLocal, portal.radius(), portal.openProgress(pt), portal.closeProgress(pt), t,
                portal.getUUID().getLeastSignificantBits(), RuptureDraw.SPACE, gateway ? 1.25f * energy : energy, gateway);
    }
}
