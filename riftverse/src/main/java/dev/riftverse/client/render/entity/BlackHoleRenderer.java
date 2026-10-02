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
        if (entity.isNexusCore()) {
            // the Nexus core is drawn without the lensing pass so it never warps the gates around it
            float age = entity.tickCount + partial;
            float r = entity.horizonRadius();
            var pose = ps.last();
            org.joml.Vector3f n = entity.diskNormal();
            org.joml.Vector3f o = new org.joml.Vector3f(0, (float) (c.y - entity.getY()), 0);
            dev.riftverse.client.render.FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, o.y, 0, r * 3.2f, cool, 0.35f * bloom);
            for (int i = 0; i < 6; i++) {
                float rr = r * (1.4f + i * 0.35f);
                dev.riftverse.client.render.FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, o, n, rr, r * 0.12f, i % 2 == 0 ? hot : cool,
                        0.55f - i * 0.07f, 64);
            }
            dev.riftverse.client.render.FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, o.y, 0, r, r, r, 24, 0x000000, 0x000000, 1f,
                    dev.riftverse.client.render.FxDraw.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1f);
            if (age < 0) return;
            return;
        }
        SpatialFx.hole(c, entity.horizonRadius(), entity.diskNormal(), style, bloom, hot, cool);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity entity) {
        return RvRenderTypes.WHITE;
    }
}
