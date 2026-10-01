package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.CrystalSentinelEntity;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Living crystal golem. Raises both fists overhead during its slam wind-up; its core blazes brighter as it charges. */
public class CrystalSentinelRenderer extends ProceduralRenderer<CrystalSentinelEntity> {
    private static final int CRYSTAL = 0x8A7BEF;
    private static final int CRYSTAL_DARK = 0x4F3FA8;
    private static final int CORE = 0x7DF9FF;
    private static final int WINDUP = 18;

    public CrystalSentinelRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0f);
    }

    @Override
    protected void draw(CrystalSentinelEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        float walk = entity.walkAnimation.position(partial) * 0.6662f;
        float speed = Math.min(1f, entity.walkAnimation.speed(partial));
        int slam = entity.slamTicks();
        float windup = slam > 0 ? Mth.clamp(1f - (slam - partial) / WINDUP, 0f, 1f) : 0f;
        float raise = windup < 0.8f ? windup / 0.8f : 1f - (windup - 0.8f) / 0.2f;
        raise = raise * raise * (3 - 2 * raise);
        float attack = entity.getAttackAnim(partial);

        // legs: tapering crystal columns
        for (int s = -1; s <= 1; s += 2) {
            float a = Mth.cos(walk + (s > 0 ? 0 : Mth.PI)) * 0.45f * speed;
            Vector3f hip = new Vector3f(s * 0.4f, 1.3f, 0);
            Vector3f foot = new Vector3f(s * 0.45f, 0.12f, Mth.sin(a) * 0.6f);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, hip, foot, 0.24f, 0.3f, CRYSTAL_DARK, CRYSTAL, 1f, light, overlay);
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, foot.x, 0.15f, foot.z, 0.34f, 0.18f, CRYSTAL, 1f, light, overlay);
        }
        FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, 1.35f, 0, 0.55f, 0.18f, 0.32f, CRYSTAL_DARK, 1f, light, overlay);

        // torso: one huge crystal plus a crown of smaller spikes on the back
        float breathe = (float) Math.sin(age * 0.06f) * 0.03f;
        FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, 2.2f + breathe, 0, 0.66f, 0.92f, CRYSTAL, 1f, light, overlay);
        for (int i = 0; i < 5; i++) {
            float x = (i - 2) * 0.22f;
            ps.pushPose();
            ps.translate(x, 2.55f + breathe, -0.35f);
            ps.mulPose(Axis.XP.rotationDegrees(-35f));
            ps.mulPose(Axis.ZP.rotationDegrees((i - 2) * -14f));
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, 0.25f, 0, 0.1f, 0.38f - Math.abs(i - 2) * 0.06f, ColorUtil.lerp(CRYSTAL, 0xC9B8FF, 0.4f), 1f, light, overlay);
            ps.popPose();
        }

        // head with a glowing visor slit
        FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, 3.15f + breathe, 0.06f, 0.24f, 0.3f, ColorUtil.lerp(CRYSTAL, 0xFFFFFF, 0.2f), 1f, light, overlay);
        FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), pose, 0, 3.15f + breathe, 0.25f, 0.14f, 0.025f, 0.02f, CORE, 1f, FxDraw.FULL_BRIGHT, overlay);

        // the core: brighter and faster as the slam winds up
        float pulse = 0.6f + 0.4f * (float) Math.sin(age * (0.12f + windup * 0.6f));
        float coreGlow = Mth.lerp(windup, 0.55f, 1f) * pulse;
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.glow()), pose, 0, 2.25f + breathe, 0.42f, 0.17f, 0.17f, 0.1f, 10, ColorUtil.scale(CORE, coreGlow),
                ColorUtil.scale(0xD69CFF, coreGlow), 1f, FxDraw.FULL_BRIGHT, overlay, -2f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, 2.25f + breathe, 0.5f, 0.6f + windup * 0.9f, CORE, coreGlow);

        // arms with crystal fists
        for (int s = -1; s <= 1; s += 2) {
            float swing = -Mth.cos(walk + (s > 0 ? 0 : Mth.PI)) * 0.35f * speed;
            float angle = swing + raise * 2.7f + attack * 1.4f;
            Vector3f shoulder = new Vector3f(s * 0.82f, 2.65f + breathe, 0);
            Vector3f hand = new Vector3f(shoulder).add(s * (0.18f - raise * 0.25f), -1.45f * Mth.cos(angle), 1.45f * Mth.sin(angle) * 0.85f);
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, shoulder.x, shoulder.y + 0.1f, shoulder.z, 0.3f, 0.42f, ColorUtil.lerp(CRYSTAL, 0xC9B8FF, 0.3f), 1f, light, overlay);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, shoulder, hand, 0.17f, 0.2f, CRYSTAL_DARK, CRYSTAL, 1f, light, overlay);
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, hand.x, hand.y, hand.z, 0.34f, 0.3f, CRYSTAL, 1f, light, overlay);
            if (windup > 0f) {
                FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, hand.x, hand.y, hand.z, 0.5f + raise * 0.6f, 0xD69CFF, raise);
            }
        }

        // shards orbiting the body
        for (int i = 0; i < 4; i++) {
            double a = age * 0.04 + i * Math.PI / 2;
            float x = (float) Math.cos(a) * 1.2f, z = (float) Math.sin(a) * 1.2f;
            float y = 2.4f + (float) Math.sin(age * 0.07 + i) * 0.25f;
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, x, y, z, 0.08f, 0.17f, 0xC9B8FF, 1f, FxDraw.FULL_BRIGHT, overlay);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, x, y, z, 0.22f, CORE, 0.45f);
        }
    }
}
