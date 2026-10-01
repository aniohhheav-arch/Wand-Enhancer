package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.SpatialFx;
import dev.riftverse.entity.PortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class PortalRenderer extends EntityRenderer<PortalEntity> {
    public PortalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(PortalEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.shouldRender(camX, camY, camZ);
    }

    @Override
    public void render(PortalEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        PortalEntity partner = entity.partner();
        float open = Math.min(1f, (entity.tickCount + partial) / 8f);
        int partnerColor = partner != null ? partner.color() : 0x404050;
        SpatialFx.rift(entity.getPosition(partial).add(entity.normal().scale(0.01)), entity.normal(), entity.up(), PortalEntity.HALF_W, PortalEntity.HALF_H,
                entity.color(), 0xFFFFFF, SpatialFx.STYLE_PORTAL, open, SpatialFx.SHAPE_ELLIPSE, 1f, partnerColor, partner != null ? 0.55f : 0f);
    }

    @Override
    public ResourceLocation getTextureLocation(PortalEntity entity) {
        return RvRenderTypes.WHITE;
    }
}
