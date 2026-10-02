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
        protected void applyRotations(DeLoreanEntity e, com.mojang.blaze3d.vertex.PoseStack pose, float age, float yaw, float partial, float scale) {
            // GeckoLib only turns living entities; vehicles must follow their own (interpolated) yaw
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
        }

        @Override
        public void render(DeLoreanEntity e, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose, MultiBufferSource buf, int light) {
            super.render(e, yaw, partial, pose, buf, light);
            float k = e.jump() > 0 ? 1f - e.jump() / (float) DeLoreanEntity.CHARGE_TICKS * 0.6f : e.armed() && e.mph() > 60 ? (e.mph() - 60) / 60f : 0f;
            if (k <= 0f || e.isInvisible()) return;
            // the time field: forked blue arcs crawling over the bodywork, brighter and wilder as the jump nears
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
            var p = pose.last();
            net.minecraft.world.phys.Vec3 eye = dev.riftverse.client.render.FxDraw.eyeLocal(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), e.position());
            float t = dev.riftverse.client.render.RvShaders.time();
            int arcs = 4 + (int) (k * 10);
            for (int i = 0; i < arcs; i++) {
                net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create((long) (t * 20) * 31 + i);
                net.minecraft.world.phys.Vec3 a = new net.minecraft.world.phys.Vec3((r.nextDouble() - 0.5) * 2.2, 0.3 + r.nextDouble() * 1.2, (r.nextDouble() - 0.5) * 4.4);
                for (int seg = 0; seg < 4; seg++) {
                    net.minecraft.world.phys.Vec3 b = a.add((r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.9);
                    dev.riftverse.client.render.FxDraw.beam(buf.getBuffer(dev.riftverse.client.render.RvRenderTypes.ENERGY), p, a, b, eye, 0.07f, i % 3 == 0 ? 0xFFFFFF : 0x60C0FF, Math.min(1f, 0.4f + k));
                    a = b;
                }
            }
            if (e.jump() > 0) {
                // a glowing sphere of time-field rings closing in around the car
                for (int ring = 0; ring < 3; ring++) {
                    float rr = 3.2f - k * 1.2f + ring * 0.25f;
                    dev.riftverse.client.render.FxDraw.ring(buf.getBuffer(dev.riftverse.client.render.RvRenderTypes.ENERGY), p, new org.joml.Vector3f(0, 0.8f, 0),
                            new org.joml.Vector3f(Mth.sin(t * 2 + ring), 1, Mth.cos(t * 2 + ring)).normalize(), rr, 0.08f, 0x80D0FF, 0.5f * k, 48);
                }
            }
            pose.popPose();
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
        protected void applyRotations(TardisEntity e, com.mojang.blaze3d.vertex.PoseStack pose, float age, float yaw, float partial, float scale) {
            // GeckoLib only turns living entities; vehicles must follow their own (interpolated) yaw
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - e.getYRot()));
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

        @Override
        protected void applyRotations(TimeDoorEntity e, com.mojang.blaze3d.vertex.PoseStack pose, float age, float yaw, float partial, float scale) {
            // GeckoLib only turns living entities; vehicles must follow their own (interpolated) yaw
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-e.getYRot()));
        }
    }
}
