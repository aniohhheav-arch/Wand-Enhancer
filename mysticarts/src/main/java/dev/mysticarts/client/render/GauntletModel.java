package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mysticarts.power.Source;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The Infinity Gauntlet, built from geometry. Glove space: origin at the wrist, +y toward the fingertips, +z out of the
 * back of the hand, +x toward the little finger (right hand). One unit is one block.
 *
 * Stone placement follows the classic gauntlet: Space on the index knuckle, Reality on the middle, Power on the ring,
 * Soul on the little finger, Time on the thumb and Mind at the centre of the back of the hand.
 */
public final class GauntletModel {
    private static final int GOLD = 0xE2B23C;
    private static final int GOLD_MID = 0xB8862A;
    private static final int GOLD_DARK = 0x6E4A12;
    private static final int SOCKET = 0x2A1A08;

    /** Finger x positions (index..little) and their stone. */
    private static final float[] FINGER_X = {-0.09f, -0.03f, 0.03f, 0.09f};
    private static final Source[] KNUCKLE = {Source.SPACE, Source.REALITY, Source.POWER, Source.SOUL};

    private GauntletModel() {}

    /**
     * @param curl   0 = open hand, 1 = clenched fist
     * @param snap   0..1 snap gesture (middle finger and thumb pressed, then released)
     * @param charge 0..1 energy build-up (stones flare, veins blaze)
     */
    public static void draw(PoseStack ps, MultiBufferSource buffers, int light, int stones, float time, float curl, float snap, float charge) {
        PoseStack.Pose pose = ps.last();
        VertexConsumer solid = buffers.getBuffer(MaRenderTypes.solid());
        VertexConsumer glow = buffers.getBuffer(MaRenderTypes.glow());
        VertexConsumer soft = buffers.getBuffer(MaRenderTypes.softGlow());
        int none = OverlayTexture.NO_OVERLAY;
        int full = FxDraw.FULL_BRIGHT;

        // cuff: a flared, banded tube over the forearm
        Vector3f[] spine = {new Vector3f(0, -0.24f, 0), new Vector3f(0, -0.16f, 0), new Vector3f(0, -0.07f, 0), new Vector3f(0, 0f, 0)};
        float[] rw = {0.15f, 0.135f, 0.12f, 0.115f};
        float[] rh = {0.13f, 0.115f, 0.1f, 0.095f};
        int[] top = {GOLD_MID, GOLD, GOLD, GOLD_MID};
        int[] belly = {GOLD_DARK, GOLD_MID, GOLD_MID, GOLD_DARK};
        FxDraw.loft(solid, pose, spine, rw, rh, top, belly, 12, 1f, light, none);
        for (float y : new float[] {-0.22f, -0.12f, -0.02f}) {
            FxDraw.loft(solid, pose, new Vector3f[] {new Vector3f(0, y - 0.008f, 0), new Vector3f(0, y + 0.008f, 0)}, new float[] {0.152f + y * 0.1f, 0.152f + y * 0.1f},
                    new float[] {0.132f + y * 0.1f, 0.132f + y * 0.1f}, new int[] {GOLD_DARK, GOLD_DARK}, new int[] {GOLD_DARK, GOLD_DARK}, 12, 1f, light, none);
        }
        // back of the hand: layered plates
        FxDraw.cube(solid, pose, 0, 0.1f, 0.01f, 0.13f, 0.1f, 0.06f, GOLD, 1f, light, none);
        FxDraw.cube(solid, pose, 0, 0.1f, 0.06f, 0.1f, 0.08f, 0.012f, GOLD_MID, 1f, light, none);
        FxDraw.cube(solid, pose, 0, 0.1f, 0.072f, 0.045f, 0.045f, 0.006f, SOCKET, 1f, light, none);
        // palm
        FxDraw.cube(solid, pose, 0, 0.1f, -0.045f, 0.12f, 0.09f, 0.012f, GOLD_DARK, 1f, light, none);

        // fingers: three segments each, curling toward the palm (-z)
        for (int f = 0; f < 4; f++) {
            float x = FINGER_X[f];
            float len = f == 1 ? 1.08f : f == 3 ? 0.82f : 1f;
            float fc = curl;
            if (snap > 0) fc = f == 1 ? Mth.clamp(snap * 2f, 0f, 1f) * 0.55f : Mth.lerp(snap, curl, 0.65f);
            ps.pushPose();
            ps.translate(x, 0.2f, 0.015f);
            for (int s = 0; s < 3; s++) {
                ps.mulPose(Axis.XP.rotationDegrees(-fc * (s == 0 ? 55f : 65f)));
                float seg = (s == 0 ? 0.055f : s == 1 ? 0.045f : 0.035f) * len;
                PoseStack.Pose fp = ps.last();
                FxDraw.cube(solid, fp, 0, seg * 0.5f, 0, 0.024f, seg * 0.5f, 0.024f, s == 2 ? GOLD_MID : GOLD, 1f, light, none);
                FxDraw.cube(solid, fp, 0, seg * 0.5f, 0.022f, 0.016f, seg * 0.35f, 0.004f, GOLD_DARK, 1f, light, none);
                ps.translate(0, seg, 0);
            }
            ps.popPose();
        }
        // thumb, on the index side
        ps.pushPose();
        ps.translate(-0.13f, 0.06f, -0.01f);
        ps.mulPose(Axis.ZP.rotationDegrees(35f - curl * 20f - snap * 15f));
        ps.mulPose(Axis.XP.rotationDegrees(-curl * 40f - snap * 35f));
        for (int s = 0; s < 2; s++) {
            float seg = s == 0 ? 0.06f : 0.045f;
            FxDraw.cube(solid, ps.last(), 0, seg * 0.5f, 0, 0.027f, seg * 0.5f, 0.027f, GOLD, 1f, light, none);
            ps.translate(0, seg, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-15f - curl * 30f));
        }
        ps.popPose();

        // energy veins: lines of light that pulse from the cuff toward the stones
        float boost = 0.35f + charge * 0.65f;
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            float vx = (float) Math.cos(a) * 0.135f, vz = (float) Math.sin(a) * 0.115f;
            for (int k = 0; k < 6; k++) {
                float y = -0.22f + k * 0.04f;
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.35f - k * 0.9f + i);
                int c = Source.STONES[i].color;
                if ((stones & Source.STONES[i].bit()) == 0) continue;
                FxDraw.cube(glow, pose, vx * (1.02f - k * 0.02f), y, vz * (1.02f - k * 0.02f), 0.006f, 0.018f, 0.006f, c, pulse * boost, full, none);
            }
        }

        // metallic sheen: a bright band sweeping up the cuff
        float sweep = (time * 0.02f) % 1.6f - 0.3f;
        if (sweep > -0.3f && sweep < 1.1f) {
            float y = -0.24f + sweep * 0.5f;
            FxDraw.loft(glow, pose, new Vector3f[] {new Vector3f(0, y - 0.015f, 0), new Vector3f(0, y + 0.015f, 0)}, new float[] {0.153f, 0.153f},
                    new float[] {0.133f, 0.133f}, new int[] {0xFFF0C0, 0xFFF0C0}, new int[] {0xFFF0C0, 0xFFF0C0}, 12, 0.35f, full, none);
        }

        // stones
        for (int f = 0; f < 4; f++) stone(solid, glow, soft, pose, KNUCKLE[f], stones, FINGER_X[f], 0.2f, 0.06f, 0.022f, time, charge, snap);
        stone(solid, glow, soft, pose, Source.MIND, stones, 0, 0.1f, 0.075f, 0.034f, time, charge, snap);
        ps.pushPose();
        ps.translate(-0.13f, 0.06f, -0.01f);
        ps.mulPose(Axis.ZP.rotationDegrees(35f - curl * 20f - snap * 15f));
        stone(solid, glow, soft, ps.last(), Source.TIME, stones, 0, 0.035f, 0.03f, 0.02f, time, charge, snap);
        ps.popPose();
    }

    private static void stone(VertexConsumer solid, VertexConsumer glow, VertexConsumer soft, PoseStack.Pose pose, Source s, int stones, float x, float y, float z,
                              float size, float time, float charge, float snap) {
        boolean in = (stones & s.bit()) != 0;
        int none = OverlayTexture.NO_OVERLAY;
        FxDraw.cube(solid, pose, x, y, z - size * 0.4f, size * 1.25f, size * 1.25f, size * 0.5f, GOLD_DARK, 1f, FxDraw.FULL_BRIGHT, none);
        if (!in) {
            FxDraw.cube(solid, pose, x, y, z, size * 0.8f, size * 0.8f, size * 0.3f, SOCKET, 1f, FxDraw.FULL_BRIGHT, none);
            return;
        }
        float pulse = 0.75f + 0.25f * Mth.sin(time * 0.15f + s.ordinal() * 1.3f);
        float flare = Math.max(charge, snap);
        FxDraw.crystal(glow, pose, x, y, z + size * 0.2f, size * 0.85f, size * 1.05f, s.color, 1f, FxDraw.FULL_BRIGHT, none);
        FxDraw.crystal(glow, pose, x, y, z + size * 0.25f, size * 0.4f, size * 0.6f, ColorUtil.lerp(s.color, 0xFFFFFF, 0.6f), 1f, FxDraw.FULL_BRIGHT, none);
        FxDraw.billboard(soft, pose, x, y, z + size, size * (2.2f + flare * 3f) * pulse, s.color, (0.45f + flare * 0.5f) * pulse);
    }
}
