package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Base for procedural VFX entity renderers: no shadow, no texture, camera position handed in entity-local space. */
public abstract class EffectEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    protected EffectEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public final void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 camLocal = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick));
        draw(entity, partialTick, pose, buffers, camLocal);
    }

    protected abstract void draw(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal);

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return ModRenderTypes.WHITE;
    }
}
