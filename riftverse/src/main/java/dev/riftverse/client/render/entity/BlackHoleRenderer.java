package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.SpatialFx;
import dev.riftverse.entity.BlackHoleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Black holes have no geometry: they are queued for the gravitational-lensing pass. */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {
    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(BlackHoleEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.shouldRender(camX, camY, camZ);
    }

    @Override
    public void render(BlackHoleEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 c = entity.getPosition(partial);
        int style = entity.style();
        int hot;
        int cool;
        switch (style) {
            case BlackHoleEntity.STYLE_NEXUS -> {
                hot = 0xFFF6D0;
                cool = 0xFFA030;
            }
            case BlackHoleEntity.STYLE_WARDEN -> {
                hot = 0xFFE0A0;
                cool = 0xC02840;
            }
            case BlackHoleEntity.STYLE_GRAVITY -> {
                hot = 0x9FE8FF;
                cool = 0x3070FF;
            }
            default -> {
                hot = 0xFFE6C0;
                cool = 0xFF5A1F;
            }
        }
        float bloom = entity.bloom(partial);
        if (entity.lifetime() >= 0 && entity.lifetime() < 20) bloom *= Math.max(0.05f, (entity.lifetime() - partial) / 20f);
        SpatialFx.hole(c, entity.horizonRadius(), entity.diskNormal(), style, bloom, hot, cool);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity entity) {
        return RvRenderTypes.WHITE;
    }
}
