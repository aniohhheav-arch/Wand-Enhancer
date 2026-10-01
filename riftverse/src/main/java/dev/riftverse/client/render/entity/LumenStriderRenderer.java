package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.LumenStriderEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Vector3f;

/** Tall, delicate four-legged grazer with a lantern lure hanging in front of its face. */
public class LumenStriderRenderer extends ProceduralRenderer<LumenStriderEntity> {
    private static final float[][] HIPS = {{0.32f, 0.38f}, {-0.32f, 0.38f}, {0.32f, -0.4f}, {-0.32f, -0.4f}};
    private static final float[] PHASE = {0f, (float) Math.PI, (float) Math.PI, 0f};

    public LumenStriderRenderer(EntityRendererProvider.Context context) {
        super(context, 0.8f);
    }

    @Override
    protected void draw(LumenStriderEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        float walk = entity.walkAnimation.position(partial) * 0.55f;
        float speed = Math.min(1f, entity.walkAnimation.speed(partial));
        float bob = (float) Math.sin(walk * 2) * 0.06f * speed + (float) Math.sin(age * 0.05f) * 0.03f;
        float bodyY = 2.55f + bob;

        // legs: thigh up and out to a high knee, shin down to the foot
        for (int i = 0; i < 4; i++) {
            float hx = HIPS[i][0], hz = HIPS[i][1];
            float ph = walk + PHASE[i];
            float lift = Math.max(0f, (float) Math.sin(ph)) * 0.45f * speed;
            float stride = (float) Math.cos(ph) * 0.55f * speed;
            Vector3f hip = new Vector3f(hx, bodyY - 0.1f, hz);
            Vector3f foot = new Vector3f(hx * 2.4f, lift, hz * 1.9f + stride);
            Vector3f knee = new Vector3f(hx * 3.2f, bodyY - 0.55f + lift * 0.5f, hz * 1.6f + stride * 0.4f);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, hip, knee, 0.09f, 0.065f, 0x3C3466, 0x4D4A86, 1f, light, overlay);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, knee, foot, 0.065f, 0.035f, 0x4D4A86, 0x7FD8FF, 1f, light, overlay);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, foot.x, foot.y + 0.05f, foot.z, 0.14f, 0x7FFFE0, 0.8f);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, knee.x, knee.y, knee.z, 0.08f, 0x9FC8FF, 0.6f);
        }

        // body and neck
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, bodyY, 0, 0.5f, 0.4f, 0.68f, 14, 0x3A3A72, 0x8C9CD0, 1f, light, overlay, -2f);
        Vector3f neckBase = new Vector3f(0, bodyY + 0.2f, 0.45f);
        float graze = (float) Math.sin(age * 0.02f + entity.getId()) * 0.15f;
        Vector3f head = new Vector3f(0, bodyY + 0.75f + graze, 0.95f);
        FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, neckBase, head, 0.16f, 0.11f, 0x3A3A72, 0x4E5296, 1f, light, overlay);
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, head.x, head.y, head.z + 0.12f, 0.17f, 0.15f, 0.3f, 10, 0x4E5296, 0x9AA8DE, 1f, light, overlay, -2f);
        for (int s = -1; s <= 1; s += 2) {
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, s * 0.13f, head.y + 0.05f, head.z + 0.2f, 0.07f, 0xFFFFFF, 1f);
        }
        // antenna stalk with the lantern
        Vector3f stalkTip = new Vector3f((float) Math.sin(age * 0.07f) * 0.1f, head.y + 0.35f, head.z + 0.6f);
        FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, new Vector3f(head.x, head.y + 0.12f, head.z + 0.1f), stalkTip, 0.03f, 0.02f, 0x4E5296, 0x7FFFE0, 1f, light, overlay);
        float lantern = 0.75f + 0.25f * (float) Math.sin(age * 0.13f);
        Vector3f bulb = new Vector3f(stalkTip.x, stalkTip.y - 0.18f, stalkTip.z + 0.05f);
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.glow()), pose, bulb.x, bulb.y, bulb.z, 0.09f, 0.11f, 0.09f, 10, 0xC8FFF0, 0x5FFFD0, 1f, FxDraw.FULL_BRIGHT, overlay, -2f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, bulb.x, bulb.y, bulb.z, 0.7f * lantern, 0x6FFFD8, 0.6f);
        // dorsal glow spots
        for (int i = 0; i < 5; i++) {
            float z = -0.5f + i * 0.25f;
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, bodyY + 0.36f - Math.abs(z) * 0.15f, z, 0.1f, 0x8FE8FF, 0.5f + 0.4f * (float) Math.sin(age * 0.1f + i));
        }
    }
}
