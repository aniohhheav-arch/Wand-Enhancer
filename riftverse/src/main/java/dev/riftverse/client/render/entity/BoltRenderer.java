package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.entity.PortalBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Glowing energy projectiles: a bright head with a streaking tail. */
public abstract class BoltRenderer<T extends Entity> extends EntityRenderer<T> {
    protected BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    protected abstract int color(T entity);

    protected abstract float size(T entity);

    @Override
    public void render(T entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        PoseStack.Pose pose = ps.last();
        Vec3 eye = ProceduralRenderer.cameraLocal(pose);
        Vec3 v = entity.getDeltaMovement();
        double speed = v.length();
        Vec3 tail = speed > 1e-3 ? v.normalize().scale(-Math.min(2.2, 0.6 + speed * 1.2)) : new Vec3(0, 0, 0);
        int color = color(entity);
        float size = size(entity);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, Vec3.ZERO, tail, eye, size * 1.4f, color, 0.9f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0f, 0f, 0f, size * 1.6f, color, 0.9f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0f, 0f, 0f, size * 0.6f, 0xFFFFFF, 0.9f);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return RvRenderTypes.WHITE;
    }

    public static class PortalBolt extends BoltRenderer<PortalBoltEntity> {
        public PortalBolt(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected int color(PortalBoltEntity entity) {
            return entity.color();
        }

        @Override
        protected float size(PortalBoltEntity entity) {
            return entity.slot() == PortalBoltEntity.RIFT_SLOT ? 0.55f : 0.35f;
        }
    }

    public static class Energy extends BoltRenderer<EnergyBoltEntity> {
        public Energy(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected int color(EnergyBoltEntity entity) {
            return entity.color();
        }

        @Override
        protected float size(EnergyBoltEntity entity) {
            return entity.size();
        }
    }
}
