package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.boss.AbyssalLeviathanEntity;
import dev.riftverse.util.ColorUtil;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A colossal sky-serpent. The body is not rigid: the renderer remembers where the head has been and lays the body
 * along that path, so it slithers through the air following its own wake.
 */
public class AbyssalLeviathanRenderer extends EntityRenderer<AbyssalLeviathanEntity> {
    private static final int SEGMENTS = 28;
    private static final double SPACING = 1.25;
    private static final float HEAD_Y = 1.75f;
    private static final int TOP = 0x0A2534;
    private static final int BELLY = 0x3FA8A8;
    private static final int GLOW = AbyssalLeviathanEntity.COLOR;

    private final Map<AbyssalLeviathanEntity, ArrayDeque<Vec3>> trails = new WeakHashMap<>();

    public AbyssalLeviathanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssalLeviathanEntity entity) {
        return RvRenderTypes.WHITE;
    }

    /** Arc-length samples along head → trail, extended straight back when the trail is still short. */
    private Vec3[] body(AbyssalLeviathanEntity entity, Vec3 head, float partial) {
        ArrayDeque<Vec3> trail = trails.computeIfAbsent(entity, e -> new ArrayDeque<>());
        if (trail.isEmpty() || trail.peekFirst().distanceToSqr(head) > 64 * 64) {
            trail.clear();
            float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
            for (int i = 1; i <= SEGMENTS; i++) trail.addLast(head.add(back.scale(i * SPACING)));
        }
        if (trail.peekFirst().distanceTo(head) >= SPACING * 0.5) {
            trail.addFirst(head);
            while (trail.size() > SEGMENTS * 2 + 4) trail.removeLast();
        }
        Vec3[] out = new Vec3[SEGMENTS];
        out[0] = head;
        Vec3 prev = head;
        double carried = 0;
        int k = 1;
        Vec3 lastDir = new Vec3(0, 0, -1);
        for (Vec3 p : trail) {
            Vec3 seg = p.subtract(prev);
            double len = seg.length();
            if (len < 1e-6) continue;
            lastDir = seg.scale(1 / len);
            while (k < SEGMENTS && carried + len >= k * SPACING) {
                double along = k * SPACING - carried;
                out[k++] = prev.add(lastDir.scale(along));
            }
            carried += len;
            prev = p;
            if (k >= SEGMENTS) break;
        }
        while (k < SEGMENTS) {
            out[k] = out[k - 1].add(lastDir.scale(SPACING));
            k++;
        }
        return out;
    }

    @Override
    public void render(AbyssalLeviathanEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 origin = entity.getPosition(partial);
        Vec3 headWorld = origin.add(0, HEAD_Y, 0);
        Vec3[] world = body(entity, headWorld, partial);
        float age = entity.tickCount + partial;
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        float dying = entity.deathTime > 0 ? Math.min(1f, (entity.deathTime + partial) / 20f) : 0f;

        ps.pushPose();
        PoseStack.Pose pose = ps.last();
        Vector3f[] spine = new Vector3f[SEGMENTS];
        float[] rw = new float[SEGMENTS];
        float[] rh = new float[SEGMENTS];
        int[] top = new int[SEGMENTS];
        int[] belly = new int[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            float t = i / (float) (SEGMENTS - 1);
            Vec3 p = world[i].subtract(origin);
            // a gentle lateral ripple on top of the path keeps it alive while hovering
            float ripple = (float) Math.sin(age * 0.12f - i * 0.55f) * 0.35f * t;
            spine[i] = new Vector3f((float) p.x + ripple, (float) p.y + ripple * 0.4f, (float) p.z);
            float r = t < 0.12f ? Mth.lerp(t / 0.12f, 1.45f, 1.85f) : Mth.lerp((float) Math.pow((t - 0.12f) / 0.88f, 1.3), 1.85f, 0.18f);
            r *= 1f - dying * 0.6f;
            rw[i] = r;
            rh[i] = r * 0.9f;
            top[i] = ColorUtil.lerp(TOP, 0x11364A, (float) Math.sin(i * 0.9f) * 0.5f + 0.5f);
            belly[i] = BELLY;
        }
        FxDraw.loft(buffers.getBuffer(RvRenderTypes.solid()), pose, spine, rw, rh, top, belly, 14, 1f, light, overlay);

        Vec3 eye = ProceduralRenderer.cameraLocal(pose);
        // dorsal fin-lights pulsing in a wave from head to tail, plus glowing lateral lines
        for (int i = 1; i < SEGMENTS - 1; i++) {
            Vector3f c = spine[i];
            Vector3f fwd = new Vector3f(spine[i - 1]).sub(spine[i + 1]).normalize();
            Vector3f up = new Vector3f(0, 1, 0);
            Vector3f side = new Vector3f(fwd).cross(up);
            if (side.lengthSquared() < 1e-4f) side.set(1, 0, 0);
            side.normalize();
            Vector3f realUp = new Vector3f(side).cross(fwd).normalize();
            float wave = (float) Math.sin(age * 0.25f - i * 0.5f) * 0.5f + 0.5f;
            Vector3f finBase = new Vector3f(c).add(new Vector3f(realUp).mul(rh[i] * 0.9f));
            Vector3f finTip = new Vector3f(finBase).add(new Vector3f(realUp).mul(0.5f + rw[i] * 0.4f)).sub(new Vector3f(fwd).mul(0.5f));
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(finBase.x, finBase.y, finBase.z), new Vec3(finTip.x, finTip.y, finTip.z), eye,
                    0.25f + rw[i] * 0.1f, GLOW, 0.35f + 0.6f * wave);
            if (i % 2 == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    Vector3f spot = new Vector3f(c).add(new Vector3f(side).mul(s * rw[i] * 0.95f)).sub(new Vector3f(realUp).mul(rh[i] * 0.2f));
                    FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, spot.x, spot.y, spot.z, 0.3f + 0.2f * wave, 0x7FFFF0, 0.4f + 0.5f * wave);
                }
            }
        }
        // tail fan
        Vector3f tail = spine[SEGMENTS - 1];
        Vector3f tailDir = new Vector3f(spine[SEGMENTS - 1]).sub(spine[SEGMENTS - 2]).normalize();
        for (int f = -2; f <= 2; f++) {
            Vector3f tip = new Vector3f(tail).add(new Vector3f(tailDir).mul(2.4f)).add(0, f * 0.7f, 0);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(tail.x, tail.y, tail.z), new Vec3(tip.x, tip.y, tip.z), eye, 0.5f, GLOW, 0.5f);
        }

        drawHead(entity, spine, rw, age, partial, ps, buffers, light, overlay, eye);
        ps.popPose();
        super.render(entity, yaw, partial, ps, buffers, light);
    }

    private void drawHead(AbyssalLeviathanEntity entity, Vector3f[] spine, float[] rw, float age, float partial, PoseStack ps, MultiBufferSource buffers,
                          int light, int overlay, Vec3 eyeLocal) {
        Vector3f head = spine[0];
        Vector3f dir = new Vector3f(spine[0]).sub(spine[1]).normalize();
        float headYaw = (float) Math.atan2(-dir.x, dir.z);
        float headPitch = (float) Math.asin(Mth.clamp(-dir.y, -1f, 1f));
        int roar = entity.roarTicks();
        float open = roar > 0 ? Mth.sin(Math.min(1f, (50 - roar + partial) / 8f) * Mth.HALF_PI) * Math.min(1f, (roar - partial) / 8f + 0.001f) : 0f;
        open = Math.max(open, 0.08f + 0.05f * (float) Math.sin(age * 0.07f));

        ps.pushPose();
        ps.translate(head.x, head.y, head.z);
        ps.mulPose(Axis.YP.rotation(-headYaw));
        ps.mulPose(Axis.XP.rotation(headPitch));
        PoseStack.Pose pose = ps.last();
        float s = rw[0] / 1.45f;
        // skull and upper jaw
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, 0.25f * s, 1.1f * s, 1.3f * s, 0.85f * s, 2.0f * s, 14, 0x11364A, BELLY, 1f, light, overlay, -2f);
        // lower jaw hinged at the back of the skull
        ps.pushPose();
        ps.translate(0, -0.25f * s, -0.2f * s);
        ps.mulPose(Axis.XP.rotation(open * 0.7f));
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, -0.2f * s, 1.4f * s, 1.05f * s, 0.45f * s, 1.75f * s, 12, BELLY, 0x0A2534, 1f, light, overlay, -2f);
        for (int i = 0; i < 6; i++) {
            float z = (0.6f + i * 0.38f) * s;
            for (int side = -1; side <= 1; side += 2) {
                float x = side * (0.85f - i * 0.11f) * s;
                FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), x, 0.18f * s, z, 0.06f * s, 0.2f * s, 0xE8FFF8, 1f, light, overlay);
            }
        }
        ps.popPose();
        // upper teeth
        for (int i = 0; i < 6; i++) {
            float z = (0.7f + i * 0.38f) * s;
            for (int side = -1; side <= 1; side += 2) {
                FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, side * (0.9f - i * 0.11f) * s, -0.45f * s, z, 0.06f * s, 0.22f * s, 0xE8FFF8, 1f, light, overlay);
            }
        }
        // swept-back horns
        for (int side = -1; side <= 1; side += 2) {
            for (int h = 0; h < 2; h++) {
                ps.pushPose();
                ps.translate(side * (0.75f + h * 0.25f) * s, (0.8f - h * 0.35f) * s, (0.2f - h * 0.4f) * s);
                ps.mulPose(Axis.XP.rotationDegrees(-120f + h * 15f));
                ps.mulPose(Axis.ZP.rotationDegrees(side * 20f));
                FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, 0.7f * s, 0, 0.16f * s, (0.9f - h * 0.25f) * s, 0x2E6E7A, 1f, light, overlay);
                ps.popPose();
            }
        }
        // four eyes and the throat glow that blazes during a roar
        for (int side = -1; side <= 1; side += 2) {
            for (int e = 0; e < 2; e++) {
                float x = side * (0.95f - e * 0.15f) * s, y = (0.45f - e * 0.25f) * s, z = (1.7f - e * 0.45f) * s;
                FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, x, y, z, 0.22f * s, 0xE0FFFF, 1f);
                FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, x, y, z, 0.6f * s, GLOW, 0.7f);
            }
        }
        float throat = 0.3f + open * 1.4f;
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, -0.2f * s, 1.6f * s, (0.8f + open * 2.5f) * s, GLOW, Math.min(1f, throat));
        if (roar > 0) {
            for (int i = 0; i < 3; i++) {
                float k = ((age * 0.1f) + i / 3f) % 1f;
                FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0, -0.2f * s, (3.2f + k * 6f) * s), new Vector3f(0, 0, 1), (0.6f + k * 3.5f) * s,
                        0.25f * s, GLOW, (1f - k) * open, 32);
            }
        }
        ps.popPose();
    }
}
