package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.time.ClientHistory;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.TimeFractureEntity;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * A shattered-glass pane of time: radial cracks with branches and concentric fracture rings, spreading from the
 * centre, slowly turning, flaring with every slowing pulse, and finally collapsing to a point. Creatures near it are
 * shown with afterimages of where they were moments ago.
 */
public class TimeFractureRenderer extends EffectEntityRenderer<TimeFractureEntity> {
    private static final int MAIN_CRACKS = 11;
    private static final int RINGS = 3;

    public TimeFractureRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(TimeFractureEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    protected void draw(TimeFractureEntity fracture, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = fracture.tickCount + pt;
        float spread = fracture.spread(pt);
        float collapse = VfxDraw.smooth(fracture.collapse(pt));
        float pulse = fracture.pulse(pt);
        float r = fracture.visualRadius() * (1 - collapse);
        long seed = fracture.getUUID().getLeastSignificantBits();
        Vec3 normal = Vec3.directionFromRotation(0, fracture.facingYaw() + t * 0.25f);
        Vec3 ax = new Vec3(-normal.z, 0, normal.x).normalize();
        Vec3 ay = new Vec3(0, 1, 0);
        float bright = 0.65f + 0.35f * pulse;

        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        List<double[]> joints = new ArrayList<>();
        for (int i = 0; i < MAIN_CRACKS; i++) {
            double angle = i * Math.PI * 2 / MAIN_CRACKS + (VfxDraw.hash(seed, i, 1) - 0.5) * 0.4;
            double length = r * (0.7 + 0.45 * VfxDraw.hash(seed, i, 2));
            int segments = 7;
            double u = 0, v = 0;
            for (int s = 0; s < segments; s++) {
                double f0 = (double) s / segments, f1 = (double) (s + 1) / segments;
                if (f0 > spread) {
                    break;
                }
                double fEnd = Math.min(f1, spread);
                double wobble = (VfxDraw.hash(seed, i * 31 + s, 3) - 0.5) * 0.5;
                double a = angle + wobble * (1 - f0);
                double nu = Math.cos(a) * length * fEnd, nv = Math.sin(a) * length * fEnd;
                Vec3 p0 = ax.scale(u).add(ay.scale(v));
                Vec3 p1 = ax.scale(nu).add(ay.scale(nv));
                float width = 0.11f * (1 - (float) f0 * 0.7f);
                VfxDraw.line(pose.last(), lines, p0, p1, camLocal, width * 4, 0.12f, 0.86f, 0.35f, 0.25f * bright);
                VfxDraw.line(pose.last(), lines, p0, p1, camLocal, width, 0.4f, 1f, 0.5f, bright);
                if (s > 0 && s < segments - 1 && VfxDraw.hash(seed, i * 31 + s, 4) > 0.55 && f1 <= spread) {
                    double ba = a + (VfxDraw.hash(seed, i * 31 + s, 5) > 0.5 ? 0.7 : -0.7);
                    double bl = length * 0.18;
                    Vec3 b1 = p1.add(ax.scale(Math.cos(ba) * bl)).add(ay.scale(Math.sin(ba) * bl));
                    VfxDraw.line(pose.last(), lines, p1, b1, camLocal, width * 0.6f, 0.4f, 1f, 0.5f, 0.8f * bright);
                }
                u = nu;
                v = nv;
                if (s == 1 || s == 3 || s == 5) {
                    joints.add(new double[]{i, s, u, v});
                }
            }
        }
        // concentric fracture rings link neighbouring cracks, like a spider-web break in glass
        for (int k = 0; k < RINGS; k++) {
            int s = 1 + k * 2;
            for (int i = 0; i < MAIN_CRACKS; i++) {
                double[] a = find(joints, i, s);
                double[] b = find(joints, (i + 1) % MAIN_CRACKS, s);
                if (a == null || b == null || VfxDraw.hash(seed, i, 40 + k) < 0.2) {
                    continue;
                }
                Vec3 pa = ax.scale(a[2]).add(ay.scale(a[3]));
                Vec3 pb = ax.scale(b[2]).add(ay.scale(b[3]));
                VfxDraw.line(pose.last(), lines, pa, pb, camLocal, 0.05f, 0.3f, 1f, 0.45f, 0.75f * bright);
                // faint glass shard between the two cracks
                VertexConsumer shard = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
                var m = pose.last().pose();
                float sa = 0.05f * bright * (k + 1);
                shard.addVertex(m, 0, 0, 0).setUv(0, 0).setColor(0.12f, 0.86f, 0.35f, 0);
                shard.addVertex(m, (float) pa.x, (float) pa.y, (float) pa.z).setUv(0, 1).setColor(0.12f, 0.86f, 0.35f, sa);
                shard.addVertex(m, (float) pb.x, (float) pb.y, (float) pb.z).setUv(1, 1).setColor(0.12f, 0.86f, 0.35f, sa);
                shard.addVertex(m, 0, 0, 0).setUv(1, 0).setColor(0.12f, 0.86f, 0.35f, 0);
                lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            }
        }
        Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, fracture.visualRadius() * 1.2f * (1 - collapse * 0.7f), 0, 0.12f, 0.86f, 0.35f,
                0.35f + 0.4f * pulse);
        VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE)), camRot, 0, 0, 0, 1.6f + 2 * collapse, 0,
                0.85f, 1f, 0.9f, 0.9f);
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * 0.01f));
        VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME)), ax, ay, r * 0.55f, 0.12f, 0.86f, 0.35f,
                0.5f * bright);
        pose.popPose();

        drawAfterimages(fracture, pt, pose, buffers);
    }

    private static double[] find(List<double[]> joints, int crack, int segment) {
        for (double[] j : joints) {
            if (j[0] == crack && j[1] == segment) {
                return j;
            }
        }
        return null;
    }

    private static void drawAfterimages(TimeFractureEntity fracture, float pt, PoseStack pose, MultiBufferSource buffers) {
        ClientHistory.keepAlive(40);
        Vec3 origin = fracture.getPosition(pt);
        double range = fracture.visualRadius() * 1.6;
        for (LivingEntity e : fracture.level().getEntitiesOfClass(LivingEntity.class, fracture.getBoundingBox().inflate(range))) {
            for (int k = 1; k <= 3; k++) {
                ClientHistory.Frame f = ClientHistory.get(e.getId(), k * 9);
                if (f == null || e.position().distanceToSqr(f.x(), f.y(), f.z()) < 0.09) {
                    continue;
                }
                Afterimages.drawFrame(pose, buffers, e, f, origin.x, origin.y, origin.z, 0.38f - k * 0.09f, InfinityStone.TIME.color());
            }
        }
    }
}
