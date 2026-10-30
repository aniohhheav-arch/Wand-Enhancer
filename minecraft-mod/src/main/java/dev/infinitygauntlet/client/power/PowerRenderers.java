package dev.infinitygauntlet.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.power.GravityCoreEntity;
import dev.infinitygauntlet.entity.power.PlanetaryCoreEntity;
import dev.infinitygauntlet.entity.power.PowerOrbEntity;
import dev.infinitygauntlet.entity.power.SeveredHeadEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

import java.util.Iterator;

/** Renderers for the Power Stone entities: GeckoLib models wrapped in procedural glow, trails and lensing. */
public final class PowerRenderers {
    public static final class Orb extends GeoEntityRenderer<PowerOrbEntity> {
        public Orb(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("power_orb")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = 0;
        }

        @Override
        public Color getRenderColor(PowerOrbEntity orb, float partialTick, int packedLight) {
            int c = PowerOrbEntity.COLORS[orb.variant()];
            return Color.ofOpaque(0xFF000000 | c);
        }

        @Override
        public void render(PowerOrbEntity orb, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
            Vec3 camLocal = entityRenderDispatcher.camera.getPosition().subtract(orb.getPosition(pt));
            Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
            int c = PowerOrbEntity.COLORS[orb.variant()];
            float r = VfxDraw.r(c), g = VfxDraw.g(c), b = VfxDraw.b(c);
            float s = orb.orbScale();
            float t = orb.tickCount + pt;
            pose.pushPose();
            pose.translate(0, 0.4 * s, 0);
            PoseStack.Pose last = pose.last();
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, 2.6f * s * (1 + 0.1f * Mth.sin(t * 0.6f)), 0, r, g, b, 0.7f);
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, 1.1f * s, 0, 1, 1, 1, 0.8f);
            VfxDraw.billboard(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE)), camRot, 0, 0, 0, 0.9f * s, t * 0.2f, 1, 0.9f, 1, 1f);
            if (orb.state() == PowerOrbEntity.WELL) {
                VfxDraw.sphere(last, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), camLocal.subtract(0, 0.4 * s, 0), 3.2f, 12, 20, 0,
                        0.03f, 0.0f, 0.08f, 0.5f, 0.0f);
                VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
                VfxDraw.billboard(last, swirl, camRot, 0, 0, 0, 7f, t * 0.25f, r, g, b, 0.8f);
            }
            // ribbon trail through the last positions
            Vec3 here = orb.getPosition(pt).add(0, 0.4 * s, 0);
            VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
            Vec3 prev = Vec3.ZERO;
            int k = 0;
            Iterator<Vec3> it = orb.trail.iterator();
            while (it.hasNext()) {
                Vec3 p = it.next().add(0, 0.4 * s, 0).subtract(here);
                if (k > 0 && p.distanceToSqr(prev) > 1.0E-4) {
                    float fade = 1 - k / (float) Math.max(1, orb.trail.size());
                    VfxDraw.beam(last, beam, prev, p, camLocal.subtract(0, 0.4 * s, 0), 0.5f * s * fade, -t * 0.3f, r, g, b, 0.75f * fade);
                }
                prev = p;
                k++;
            }
            pose.popPose();
            pose.pushPose();
            pose.scale(s, s, s);
            super.render(orb, yaw, pt, pose, buffers, light);
            pose.popPose();
        }
    }

    public static final class Gravity extends GeoEntityRenderer<GravityCoreEntity> {
        public Gravity(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("gravity_core")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = 0;
        }

        @Override
        public void render(GravityCoreEntity core, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
            float t = core.tickCount + pt;
            float collapse = core.landed(pt) ? Mth.clamp((t - GravityCoreEntity.DESCEND) / 12f, 0, 1) : 0;
            float s = 1.6f * (1 - collapse);
            if (s <= 0.01f) {
                return;
            }
            Vec3 camLocal = entityRenderDispatcher.camera.getPosition().subtract(core.getPosition(pt));
            Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
            pose.pushPose();
            pose.translate(0, 0.25 * s, 0);
            PoseStack.Pose last = pose.last();
            Vec3 cam = camLocal.subtract(0, 0.25 * s, 0);
            // lensing shell that darkens the sky behind the singularity
            VfxDraw.sphere(last, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), cam, 3.2f * s, 14, 24, 0, 0.02f, 0.0f, 0.05f, 0.55f, 0.0f);
            VfxDraw.sphere(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE)), cam, 2.4f * s, 14, 24, t * 0.04f, 0.45f, 0.15f, 0.9f, 0.7f, 0.1f);
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, 12f * s, 0, 0.42f, 0.16f, 0.82f, 0.5f);
            VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
            VfxDraw.billboard(last, swirl, camRot, 0, 0, 0, 9f * s, -t * 0.15f, 0.6f, 0.3f, 1f, 0.6f);
            // gravity column down to the ground
            float drop = (float) (core.getY() - core.groundY());
            if (drop > 0.5f && !core.landed(pt)) {
                VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
                VfxDraw.beam(last, beam, Vec3.ZERO, new Vec3(0, -drop, 0), cam, core.fieldRadius() * 0.35f, t * 0.1f, 0.3f, 0.1f, 0.6f, 0.25f);
                pose.pushPose();
                pose.translate(0, -drop + 0.1, 0);
                pose.mulPose(Axis.YP.rotation(t * 0.05f));
                VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        core.fieldRadius() * 0.85f, core.fieldRadius(), 64, t * 0.02f, 0.5f, 0.2f, 1f, 0.6f);
                pose.popPose();
            }
            pose.popPose();
            pose.pushPose();
            pose.scale(s, s, s);
            super.render(core, yaw, pt, pose, buffers, light);
            pose.popPose();
        }
    }

    public static final class Planetary extends GeoEntityRenderer<PlanetaryCoreEntity> {
        public Planetary(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("planetary_core")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = 0;
        }

        @Override
        public void render(PlanetaryCoreEntity core, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
            float rad = core.coreRadius(pt);
            if (rad <= 0.02f) {
                return;
            }
            float t = core.tickCount + pt;
            Vec3 camLocal = entityRenderDispatcher.camera.getPosition().subtract(core.getPosition(pt));
            Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
            float s = rad / 1.6f * 2.2f;
            pose.pushPose();
            pose.translate(0, 0.37 * s, 0);
            PoseStack.Pose last = pose.last();
            Vec3 cam = camLocal.subtract(0, 0.37 * s, 0);
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, 9f * rad, 0, 0.63f, 0.2f, 1f, 0.75f);
            VfxDraw.billboard(last, glow, camRot, 0, 0, 0, 4f * rad, 0, 1f, 0.85f, 1f, 0.9f);
            VertexConsumer core0 = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE));
            VfxDraw.billboard(last, core0, camRot, 0, 0, 0, 2.6f * rad, t * 0.1f, 1f, 1f, 1f, 1f);
            VfxDraw.sphere(last, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE)), cam, rad * 1.25f, 14, 24, t * 0.06f, 1f, 0.4f, 0.9f, 0.8f, 0.3f);
            VfxDraw.sphere(last, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), cam, rad * 2.6f, 12, 20, 0, 0.1f, 0.02f, 0.18f, 0.3f, 0f);
            pose.popPose();
            pose.pushPose();
            pose.scale(s, s, s);
            super.render(core, yaw, pt, pose, buffers, light);
            pose.popPose();
        }
    }

    public static final class Head extends EntityRenderer<SeveredHeadEntity> {
        private final ItemRenderer items;

        public Head(EntityRendererProvider.Context context) {
            super(context);
            this.items = context.getItemRenderer();
            this.shadowRadius = 0.2f;
        }

        @Override
        public void render(SeveredHeadEntity head, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
            float fade = Math.min(1f, (SeveredHeadEntity.LIFETIME - head.tickCount - pt) / 20f);
            if (fade <= 0) {
                return;
            }
            float s = head.size() * 0.62f * (fade < 1 ? fade : 1);
            pose.pushPose();
            pose.translate(0, 0.25 * s, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-head.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, head.spinO, head.spin)));
            pose.scale(s, s, s);
            items.renderStatic(head.head(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, head.level(), head.getId());
            pose.popPose();
            super.render(head, yaw, pt, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(SeveredHeadEntity head) {
            return ModRenderTypes.WHITE;
        }
    }

    private PowerRenderers() {
    }
}
