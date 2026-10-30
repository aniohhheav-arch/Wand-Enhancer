package dev.infinitygauntlet.client.breach;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.breach.DimensionalRiftEntity;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import static dev.infinitygauntlet.client.vfx.VfxDraw.hash;
import static dev.infinitygauntlet.client.vfx.VfxDraw.smooth;

/**
 * A tear in reality. The GeckoLib shard ring frames a living aperture that always faces the viewer: the breach rift
 * shows the white of the chamber behind it, the return rift a churning dark window back to the world. Its torn
 * edge is drawn three times, red and cyan offset around a white core, so the tear itself shows chromatic
 * aberration in the world; cracks run out from it into the surrounding space and a lens darkens what is behind it.
 */
public class RiftRenderer extends GeoEntityRenderer<DimensionalRiftEntity> {
    private static final int EDGE_POINTS = 44;

    public RiftRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("dimensional_rift")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0;
    }

    @Override
    public void render(DimensionalRiftEntity rift, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 camLocal = entityRenderDispatcher.camera.getPosition().subtract(rift.getPosition(pt));
        float face = (float) Math.toDegrees(Math.atan2(-camLocal.x, camLocal.z));
        rift.setYRot(face);
        rift.yRotO = face;
        float open = smooth(rift.openProgress(pt));
        float close = rift.closeProgress(pt);
        float r = rift.radius();
        float size = r * open * (1 - close * close);

        drawAperture(rift, pt, pose, buffers, camLocal, size, close);
        pose.pushPose();
        pose.scale(r, r, r);
        super.render(rift, entityYaw, pt, pose, buffers, light);
        pose.popPose();
    }

    private void drawAperture(DimensionalRiftEntity rift, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal, float s, float close) {
        if (s <= 0.02f) {
            return;
        }
        float t = rift.tickCount + pt;
        long seed = rift.getUUID().getLeastSignificantBits();
        Vec3 view = camLocal.normalize();
        Vec3 ax = new Vec3(-view.z, 0, view.x).normalize();
        Vec3 ay = view.cross(ax).normalize().scale(-1);
        if (ay.y < 0) {
            ay = ay.scale(-1);
        }
        boolean back = rift.isReturn();
        PoseStack.Pose p = pose.last();

        // refraction lens around the tear
        VfxDraw.sphere(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), camLocal, s * 1.45f, 14, 24, 0,
                0.05f, 0.02f, 0.1f, 0.45f, 0.0f);

        // the aperture: a window into the other layer of reality
        int segments = 40;
        float wobble = 1 + 0.05f * (float) Math.sin(t * 0.3);
        if (back) {
            VfxDraw.ring(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), ax, ay, 0, s * 0.8f * wobble, segments, 0,
                    0.03f, 0.04f, 0.1f, 0.96f);
        } else {
            VfxDraw.ring(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), ax, ay, 0, s * 0.8f * wobble, segments, 0,
                    0.97f, 0.975f, 1f, 0.97f);
        }
        // animated surface: counter-rotating swirls and a breathing core
        var camRot = entityRenderDispatcher.cameraOrientation();
        VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
        VfxDraw.billboard(p, swirl, camRot, 0, 0, 0, s * 1.55f, t * 0.05f, back ? 0.3f : 0.35f, back ? 0.5f : 0.85f, 1f, 0.55f);
        VfxDraw.billboard(p, swirl, camRot, 0, 0, 0, s * 1.15f, -t * 0.08f, 1f, back ? 0.35f : 0.3f, back ? 0.9f : 0.75f, 0.4f);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(p, glow, camRot, 0, 0, 0, s * 2.6f, 0, 0.75f, 0.85f, 1f, 0.35f + 0.15f * (float) Math.sin(t * 0.21));

        // torn edge with RGB separation
        long bucket = (long) (t / 2);
        Vec3[] pts = new Vec3[EDGE_POINTS];
        for (int i = 0; i < EDGE_POINTS; i++) {
            double a = Math.PI * 2 * i / EDGE_POINTS;
            double jag = 1 + (hash(seed + bucket, i, 1) - 0.5) * 0.22 + (hash(seed, i, 2) - 0.5) * 0.12;
            pts[i] = ax.scale(Math.cos(a) * s * 0.82 * jag * wobble).add(ay.scale(Math.sin(a) * s * 0.82 * jag * wobble));
        }
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        float split = 0.03f + 0.08f * RealityDistortion.spike();
        Vec3 red = ax.scale(split), cyan = ax.scale(-split);
        for (int i = 0; i < EDGE_POINTS; i++) {
            Vec3 a = pts[i], b = pts[(i + 1) % EDGE_POINTS];
            VfxDraw.line(p, lines, a.add(red), b.add(red), camLocal, 0.07f, 1f, 0.1f, 0.25f, 0.85f);
            VfxDraw.line(p, lines, a.add(cyan), b.add(cyan), camLocal, 0.07f, 0.1f, 0.9f, 1f, 0.85f);
            VfxDraw.line(p, lines, a, b, camLocal, 0.04f, 1f, 1f, 1f, 1f);
        }
        // cracks running out into the surrounding space
        int cracks = 8;
        for (int k = 0; k < cracks; k++) {
            int i = (int) (hash(seed, k, 3) * EDGE_POINTS);
            Vec3 from = pts[i];
            Vec3 dir = from.normalize();
            double len = s * (0.4 + 0.6 * hash(seed, k, 4)) * (1 - close);
            Vec3 prev = from;
            for (int seg = 1; seg <= 4; seg++) {
                Vec3 side = ax.scale((hash(seed + bucket / 3, k * 9 + seg, 5) - 0.5) * 0.35).add(ay.scale((hash(seed, k * 9 + seg, 6) - 0.5) * 0.35));
                Vec3 next = from.add(dir.add(side).normalize().scale(len * seg / 4));
                float fade = 1 - seg / 5f;
                VfxDraw.line(p, lines, prev, next, camLocal, 0.035f, k % 2 == 0 ? 1f : 0.35f, k % 2 == 0 ? 0.3f : 0.9f, 1f, 0.7f * fade);
                prev = next;
            }
        }
        // ripples while reality is still tearing open
        float open = rift.openProgress(pt);
        if (open < 1) {
            VertexConsumer ring = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
            for (int k = 0; k < 3; k++) {
                float q = ((open * 2.5f) + k / 3f) % 1f;
                VfxDraw.ring(p, ring, ax, ay, s * (0.6f + 2.4f * q), s * (0.75f + 2.6f * q), 48, 0, 0.8f, 0.9f, 1f, 0.5f * (1 - q));
            }
        }
    }
}
