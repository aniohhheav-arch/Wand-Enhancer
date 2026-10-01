package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.RvRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Base for creatures built from procedural geometry rather than box models. Subclasses draw in a local frame where the
 * creature stands at the origin facing +Z, already scaled by the entity's size attribute.
 */
public abstract class ProceduralRenderer<T extends LivingEntity> extends EntityRenderer<T> {
    protected ProceduralRenderer(EntityRendererProvider.Context context, float shadow) {
        super(context);
        this.shadowRadius = shadow;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return RvRenderTypes.WHITE;
    }

    /** Whether to tilt the creature along its pitch (flyers and swimmers). */
    protected boolean pitches() {
        return false;
    }

    /** Height of the point the creature pitches around. */
    protected float pitchPivot(T entity) {
        return entity.getBbHeight() * 0.5f;
    }

    @Override
    public void render(T entity, float entityYaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        drawWorldAligned(entity, partial, ps, buffers, light);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (pitches()) {
            float pivot = pitchPivot(entity);
            ps.translate(0, pivot, 0);
            ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, entity.xRotO, entity.getXRot())));
            ps.translate(0, -pivot, 0);
        }
        float scale = entity.getScale();
        if (entity.deathTime > 0) {
            float d = Math.min(1f, (entity.deathTime + partial) / 20f);
            scale *= 1f - d * 0.7f;
            ps.mulPose(Axis.ZP.rotationDegrees(d * 70f));
        }
        ps.scale(scale, scale, scale);
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        draw(entity, partial, entity.tickCount + partial, ps, buffers, light, overlay);
        ps.popPose();
        super.render(entity, entityYaw, partial, ps, buffers, light);
    }

    /** Hook for effects that must not rotate with the body (beams, trails). Pose origin is the entity position. */
    protected void drawWorldAligned(T entity, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
    }

    protected abstract void draw(T entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay);

    /** Camera position expressed in the current local frame. */
    public static Vec3 cameraLocal(PoseStack.Pose pose) {
        Vector4f v = new Vector4f(0f, 0f, 0f, 1f);
        new Matrix4f(pose.pose()).invert().transform(v);
        return new Vec3(v.x / v.w, v.y / v.w, v.z / v.w);
    }
}
