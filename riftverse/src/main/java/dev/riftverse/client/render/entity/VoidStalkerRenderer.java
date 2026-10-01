package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.VoidStalkerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Gaunt, too-tall silhouette of living darkness with overlong clawed arms and three violet eyes. */
public class VoidStalkerRenderer extends ProceduralRenderer<VoidStalkerEntity> {
    private static final int SKIN = 0x0E0A16;
    private static final int SKIN_LIGHT = 0x261A3C;
    private static final int GLOW = 0x9B5CFF;

    public VoidStalkerRenderer(EntityRendererProvider.Context context) {
        super(context, 0.5f);
    }

    @Override
    protected void draw(VoidStalkerEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        float walk = entity.walkAnimation.position(partial) * 0.6662f;
        float speed = Math.min(1f, entity.walkAnimation.speed(partial));
        float attack = entity.getAttackAnim(partial);
        float sway = (float) Math.sin(age * 0.04f + entity.getId()) * 0.04f;

        // legs
        for (int s = -1; s <= 1; s += 2) {
            float a = Mth.cos(walk + (s > 0 ? 0 : Mth.PI)) * 0.8f * speed;
            float bend = Math.max(0f, -Mth.sin(walk + (s > 0 ? 0 : Mth.PI))) * 0.9f * speed + 0.1f;
            Vector3f hip = new Vector3f(s * 0.12f, 1.3f, 0);
            Vector3f knee = new Vector3f(hip).add(0, -0.66f * Mth.cos(a), 0.66f * Mth.sin(a));
            Vector3f foot = new Vector3f(knee).add(0, -0.66f * Mth.cos(a - bend), 0.66f * Mth.sin(a - bend));
            foot.y = Math.max(0f, foot.y);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, hip, knee, 0.075f, 0.05f, SKIN_LIGHT, SKIN, 1f, light, overlay);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, knee, foot, 0.05f, 0.03f, SKIN, SKIN, 1f, light, overlay);
        }

        // hunched torso, neck and head
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, sway, 1.76f, 0.05f, 0.18f, 0.46f, 0.12f, 12, SKIN_LIGHT, SKIN, 1f, light, overlay, -2f);
        Vector3f head = new Vector3f(sway * 2f, 2.38f, 0.16f);
        FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, new Vector3f(sway, 2.15f, 0.06f), head, 0.06f, 0.05f, SKIN, SKIN, 1f, light, overlay);
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, head.x, head.y, head.z, 0.13f, 0.2f, 0.16f, 12, SKIN_LIGHT, SKIN, 1f, light, overlay, -2f);
        float blink = (Math.floorMod((int) age + entity.getId() * 37, 90) < 3) ? 0.15f : 1f;
        float[][] eyes = {{-0.055f, 2.42f}, {0.055f, 2.42f}, {0f, 2.5f}};
        for (float[] e : eyes) {
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, head.x + e[0], e[1], head.z + 0.15f, 0.06f, 0xE6D0FF, blink);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, head.x + e[0], e[1], head.z + 0.15f, 0.16f, GLOW, 0.7f * blink);
        }

        // overlong arms ending in claws
        Vec3 eye = cameraLocal(pose);
        for (int s = -1; s <= 1; s += 2) {
            // angles measured from straight down towards +Z (forward)
            float upper = -Mth.cos(walk + (s > 0 ? 0 : Mth.PI)) * 0.5f * speed + attack * 1.9f;
            float fore = upper + 0.25f + attack * 0.4f;
            Vector3f shoulder = new Vector3f(s * 0.25f + sway, 2.05f, 0.04f);
            Vector3f elb = new Vector3f(shoulder).add(s * 0.06f, -0.72f * Mth.cos(upper), 0.72f * Mth.sin(upper));
            Vector3f hand = new Vector3f(elb).add(s * 0.02f, -0.78f * Mth.cos(fore), 0.78f * Mth.sin(fore));
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, shoulder, elb, 0.06f, 0.045f, SKIN_LIGHT, SKIN, 1f, light, overlay);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, elb, hand, 0.045f, 0.035f, SKIN, SKIN, 1f, light, overlay);
            for (int c = -1; c <= 1; c++) {
                Vec3 from = new Vec3(hand.x, hand.y, hand.z);
                Vec3 to = from.add(c * 0.06f, -0.32f * Mth.cos(fore + 0.2f), 0.32f * Mth.sin(fore + 0.2f));
                FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, from, to, eye, 0.04f, GLOW, 0.9f);
            }
            // violet veins running down the arm
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(shoulder.x, shoulder.y, shoulder.z + 0.05f), new Vec3(elb.x, elb.y, elb.z + 0.04f), eye, 0.02f, GLOW, 0.5f);
        }

        // void wisps rising off the shoulders
        for (int w = 0; w < 4; w++) {
            float base = w * 1.7f + entity.getId();
            Vec3[] pts = new Vec3[6];
            for (int i = 0; i < pts.length; i++) {
                float t = i / (float) (pts.length - 1);
                pts[i] = new Vec3((w % 2 == 0 ? 1 : -1) * (0.22f + t * 0.15f) + Math.sin(age * 0.1 + base + t * 3) * 0.1 * t,
                        2.05f + t * 0.7f, Math.cos(age * 0.08 + base + t * 2) * 0.12 * t);
            }
            FxDraw.ribbon(buffers.getBuffer(RvRenderTypes.ENERGY), pose, pts, eye, 0.1f, 0x5C2BCF, 0x10061F, 0.4f);
        }
    }
}
