package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.AstralJellyEntity;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Drifting translucent bell with a starlit core and luminous trailing tentacles. */
public class AstralJellyRenderer extends ProceduralRenderer<AstralJellyEntity> {
    private static final float BELL_Y = 1.25f;

    public AstralJellyRenderer(EntityRendererProvider.Context context) {
        super(context, 0f);
    }

    @Override
    protected void draw(AstralJellyEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        float seed = entity.getId() * 0.6180339f;
        float pulse = (float) Math.sin(age * 0.16f + seed * 6f);
        float rx = 0.74f * (1f + 0.11f * pulse);
        float ry = 0.62f * (1f - 0.13f * pulse);
        float hue = (seed + age * 0.0015f) % 1f;
        int main = ColorUtil.hsv(hue, 0.65f, 1f);
        int second = ColorUtil.hsv((hue + 0.18f) % 1f, 0.55f, 1f);

        // inner bell first so the translucent shell layers over it
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.glow()), pose, 0, BELL_Y + 0.05f, 0, rx * 0.45f, ry * 0.5f, rx * 0.45f, 12,
                ColorUtil.scale(second, 0.8f), ColorUtil.scale(main, 0.5f), 1f, FxDraw.FULL_BRIGHT, overlay, -0.6f);
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.translucent()), pose, 0, BELL_Y, 0, rx, ry, rx, 18,
                ColorUtil.lerp(main, 0xFFFFFF, 0.25f), ColorUtil.scale(second, 0.9f), 0.42f, FxDraw.FULL_BRIGHT, overlay, 0.02f);

        Vec3 eye = cameraLocal(pose);
        // rim and tentacles
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0, BELL_Y + 0.02f, 0), new Vector3f(0, 1, 0), rx * 0.98f, 0.08f, main, 0.9f, 32);
        int tentacles = 9;
        for (int j = 0; j < tentacles; j++) {
            double a = Math.PI * 2 * j / tentacles + seed;
            float baseR = rx * 0.82f;
            Vec3[] pts = new Vec3[8];
            for (int i = 0; i < pts.length; i++) {
                float t = i / (float) (pts.length - 1);
                double sway = Math.sin(age * 0.12 + j * 1.7 + t * 4.0) * 0.16 * t;
                double drift = (1f - t * 0.5f) * baseR;
                pts[i] = new Vec3(Math.cos(a) * drift + sway * Math.sin(a), BELL_Y - 0.02 - t * (1.5 + 0.15 * pulse), Math.sin(a) * drift - sway * Math.cos(a));
            }
            FxDraw.ribbon(buffers.getBuffer(RvRenderTypes.ENERGY), pose, pts, eye, 0.09f, main, second, 0.85f);
        }
        // oral arms: broader, slower ribbons near the centre
        for (int j = 0; j < 4; j++) {
            double a = Math.PI * 0.5 * j + seed * 2;
            Vec3[] pts = new Vec3[7];
            for (int i = 0; i < pts.length; i++) {
                float t = i / (float) (pts.length - 1);
                double curl = Math.sin(age * 0.07 + j + t * 3.0) * 0.22 * t;
                pts[i] = new Vec3(Math.cos(a) * (0.12 + curl), BELL_Y - 0.1 - t * 1.0, Math.sin(a) * (0.12 + curl));
            }
            FxDraw.ribbon(buffers.getBuffer(RvRenderTypes.ENERGY), pose, pts, eye, 0.2f, 0xFFFFFF, main, 0.5f);
        }

        // starlit core with orbiting motes
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, BELL_Y + 0.05f, 0, 1.1f + 0.15f * pulse, main, 0.55f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, BELL_Y + 0.08f, 0, 0.35f, 0xFFFFFF, 0.9f);
        for (int i = 0; i < 6; i++) {
            double a = age * 0.05 + i * Math.PI / 3;
            float r = 0.32f + 0.08f * (float) Math.sin(age * 0.11 + i);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, (float) Math.cos(a) * r, BELL_Y + 0.15f + 0.1f * (float) Math.sin(a * 2), (float) Math.sin(a) * r,
                    0.09f, i % 2 == 0 ? second : 0xFFFFFF, 0.9f);
        }
    }
}
