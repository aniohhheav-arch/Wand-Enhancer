package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.SpatialFx;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.entity.GravityWellEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public class GravityWellRenderer extends EntityRenderer<GravityWellEntity> {
    public GravityWellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GravityWellEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.shouldRender(camX, camY, camZ);
    }

    @Override
    public void render(GravityWellEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float p = entity.progress(partial);
        float age = entity.tickCount + partial;
        SpatialFx.hole(entity.getPosition(partial), 0.55f + p * 0.35f, new Vector3f(0, 1, 0), BlackHoleEntity.STYLE_GRAVITY, Math.min(1f, age / 10f), 0x9FE8FF, 0x3070FF);
        for (int i = 0; i < 3; i++) {
            float r = (float) (GravityWellEntity.RADIUS * (1f - ((age * 0.02f + i / 3f) % 1f)));
            float a = 0.25f + 0.4f * (1f - r / (float) GravityWellEntity.RADIUS);
            Vector3f n = new Vector3f((float) Math.sin(age * 0.05 + i), 1f, (float) Math.cos(age * 0.04 + i * 2)).normalize();
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), new Vector3f(0, 0, 0), n, r, 0.25f, 0x6FD8FF, a, 48);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GravityWellEntity entity) {
        return RvRenderTypes.WHITE;
    }
}
