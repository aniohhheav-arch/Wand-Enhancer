package dev.riftverse.client.render.entity;

import dev.riftverse.Riftverse;
import dev.riftverse.entity.misc.TimeDoorEntity;
import dev.riftverse.entity.vehicle.DeLoreanEntity;
import dev.riftverse.entity.vehicle.TardisEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/** GeckoLib renderers for the time machines and the TSA time door (models/animations under geo/ and animations/). */
public final class VehicleRenderers {
    private VehicleRenderers() {}

    public static final class DeLorean extends GeoEntityRenderer<DeLoreanEntity> {
        public DeLorean(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(Riftverse.id("delorean")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            shadowRadius = 1.2f;
        }

        @Override
        public Color getRenderColor(DeLoreanEntity e, float partial, int light) {
            return e.isInvisible() ? Color.ofARGB(0, 255, 255, 255) : Color.WHITE;
        }

        @Override
        public RenderType getRenderType(DeLoreanEntity e, ResourceLocation tex, MultiBufferSource buf, float partial) {
            return RenderType.entityTranslucent(tex);
        }
    }

    public static final class Tardis extends GeoEntityRenderer<TardisEntity> {
        public Tardis(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(Riftverse.id("tardis")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            shadowRadius = 0.8f;
        }

        @Override
        public Color getRenderColor(TardisEntity e, float partial, int light) {
            if (e.fade() <= 0) return Color.WHITE;
            int f = e.fade();
            float phase = f > 60 ? (f - 60) / 60f : 1f - f / 60f;
            float t = (e.tickCount + partial) * 0.3f;
            float a = Mth.clamp(phase * (0.6f + 0.4f * Mth.sin(t)), 0f, 1f);
            return Color.ofARGB((int) (a * 255), 255, 255, 255);
        }

        @Override
        public void render(TardisEntity e, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose, MultiBufferSource buf, int light) {
            if (!e.console()) {
                super.render(e, yaw, partial, pose, buf, light);
                return;
            }
            // the interior console: a hexagonal brass desk around a glowing time rotor
            float t = dev.riftverse.client.render.RvShaders.time();
            int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            for (int i = 0; i < 6; i++) {
                pose.pushPose();
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(i * 60));
                dev.riftverse.client.render.FxDraw.box(buf.getBuffer(dev.riftverse.client.render.RvRenderTypes.solid()), pose.last(), -0.45f, 0f, 0.6f, 0.45f, 0.95f, 1.4f, i % 2 == 0 ? 0x7A5030 : 0x8A6A40, 1f, light, ov);
                dev.riftverse.client.render.FxDraw.box(buf.getBuffer(dev.riftverse.client.render.RvRenderTypes.solid()), pose.last(), -0.4f, 0.95f, 0.65f, 0.4f, 1.0f, 1.35f, 0x404850, 1f, light, ov);
                dev.riftverse.client.render.FxDraw.box(buf.getBuffer(dev.riftverse.client.render.RvRenderTypes.glow()), pose.last(), -0.3f, 1.0f, 0.8f, -0.1f, 1.05f, 0.9f, i % 2 == 0 ? 0x40FF80 : 0xFF6040, 1f, light, ov);
                pose.popPose();
            }
            float bob = e.fade() > 0 ? Mth.sin(t * 4f) * 0.6f : 0f;
            dev.riftverse.client.render.FxDraw.box(buf.getBuffer(dev.riftverse.client.render.RvRenderTypes.glow()), pose.last(), -0.25f, 1.0f + bob * 0.3f, -0.25f, 0.25f, 3.2f + bob, 0.25f,
                    0x60C0FF, 0.6f + 0.4f * Mth.sin(t * 3f), dev.riftverse.client.render.FxDraw.FULL_BRIGHT, ov);
        }

        @Override
        public RenderType getRenderType(TardisEntity e, ResourceLocation tex, MultiBufferSource buf, float partial) {
            return RenderType.entityTranslucent(tex);
        }
    }

    public static final class TimeDoor extends GeoEntityRenderer<TimeDoorEntity> {
        public TimeDoor(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(Riftverse.id("time_door")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }
}
