package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.CasterStates;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Cloak of Levitation, drawn as a live cloth: a high, flared collar and a long cape that trails with speed, sways
 * when its wearer turns, billows straight back in flight, flares on take-off, settles on landing and floats gently at
 * rest. Built in body space so it follows the torso.
 */
public class CloakLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int RED = 0xB0141E;
    private static final int RED_DARK = 0x5E0A10;
    private static final int LINING = 0x3A0C24;
    private static final int GOLD = 0xD9A836;
    private static final int ROWS = 9;
    private static final int COLS = 7;

    public CloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partial, float age, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !CasterStates.hasArtifact(player.getId(), Artifact.CLOAK.ordinal())) return;
        CasterStates.Anim anim = CasterStates.anim(player.getId());
        float flight = anim.flight(partial);

        // motion in the body's frame
        Vec3 vel = player.getPosition(partial).subtract(player.getPosition(partial - 1f));
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        double yr = Math.toRadians(bodyYaw);
        double forward = vel.x * -Math.sin(yr) + vel.z * Math.cos(yr);
        double sideways = vel.x * Math.cos(yr) + vel.z * Math.sin(yr);
        float turn = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO);
        float speed = (float) Mth.clamp(Math.max(0, forward) * 4.5, 0, 1.4);
        float fall = (float) Mth.clamp(-vel.y * 3, -0.6, 1.2);

        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        PoseStack.Pose pose = ps.last();
        VertexConsumer cloth = buffers.getBuffer(MaRenderTypes.solid());
        VertexConsumer glow = buffers.getBuffer(MaRenderTypes.glow());

        // body space: y grows downward from the shoulders, -z is the front, so the cape hangs at +z
        Vector3f[][] grid = new Vector3f[ROWS][COLS];
        float length = 1.25f;
        for (int j = 0; j < ROWS; j++) {
            float v = j / (float) (ROWS - 1);
            // how far the row swings back (radians from hanging straight down)
            float lift = 0.08f + speed * 0.55f * v + flight * (1.1f + 0.2f * (float) Math.sin(age * 0.18f + v * 3)) * v
                    + Math.max(0, fall) * 0.5f * v;
            float flutter = (float) Math.sin(age * (0.25f + speed * 0.4f + flight * 0.35f) - v * 4.5f) * (0.04f + speed * 0.08f + flight * 0.1f) * v;
            float idle = (float) Math.sin(age * 0.07f - v * 2f) * 0.03f * v;
            float angle = Mth.clamp(lift + flutter + idle, 0f, 1.45f);
            float sway = (float) Mth.clamp(-sideways * 2.0 + turn * 0.02, -0.5, 0.5) * v;
            for (int i = 0; i < COLS; i++) {
                float u = i / (float) (COLS - 1) - 0.5f;
                // the cape widens toward the hem and its edges curl forward
                float halfWidth = 0.27f + v * 0.2f + flight * 0.12f * v;
                float x = u * 2 * halfWidth + sway * 0.5f;
                float curl = (u * u) * 0.25f * v;
                float drop = v * length;
                float y = (float) Math.cos(angle) * drop;
                float z = 0.15f + (float) Math.sin(angle) * drop - curl + (float) Math.sin(age * 0.2f + u * 6 + v * 3) * 0.015f * v * (1 + flight);
                grid[j][i] = new Vector3f(x, y, z);
            }
        }
        for (int j = 0; j < ROWS - 1; j++) {
            for (int i = 0; i < COLS - 1; i++) {
                float shade = 0.75f + 0.25f * (1 - j / (float) ROWS);
                int outer = ColorUtil.scale(j % 3 == 0 ? RED : ColorUtil.lerp(RED, RED_DARK, 0.25f), shade);
                FxDraw.quad(cloth, pose, grid[j][i], grid[j + 1][i], grid[j + 1][i + 1], grid[j][i + 1], outer, 1f, light, OverlayTexture.NO_OVERLAY);
                Vector3f o = new Vector3f(0, 0, -0.012f);
                FxDraw.quad(cloth, pose, new Vector3f(grid[j][i + 1]).add(o), new Vector3f(grid[j + 1][i + 1]).add(o), new Vector3f(grid[j + 1][i]).add(o),
                        new Vector3f(grid[j][i]).add(o), LINING, 1f, light, OverlayTexture.NO_OVERLAY);
            }
        }
        // gold trim along both edges and the hem
        for (int j = 0; j < ROWS - 1; j++) {
            trim(glow, pose, grid[j][0], grid[j + 1][0]);
            trim(glow, pose, grid[j][COLS - 1], grid[j + 1][COLS - 1]);
        }
        for (int i = 0; i < COLS - 1; i++) trim(glow, pose, grid[ROWS - 1][i], grid[ROWS - 1][i + 1]);

        // the high flared collar, rising behind the head and opening outward
        float flare = 0.15f + flight * 0.1f + (float) Math.sin(age * 0.1f) * 0.02f;
        for (int s = -1; s <= 1; s += 2) {
            Vector3f a = new Vector3f(s * 0.08f, 0.02f, 0.13f);
            Vector3f b = new Vector3f(s * 0.3f, 0.02f, 0.02f);
            Vector3f c = new Vector3f(s * (0.36f + flare), -0.38f, 0.12f + flare * 0.5f);
            Vector3f d = new Vector3f(s * 0.1f, -0.42f, 0.24f);
            if (s < 0) FxDraw.quad(cloth, pose, a, b, c, d, RED, 1f, light, OverlayTexture.NO_OVERLAY);
            else FxDraw.quad(cloth, pose, d, c, b, a, RED, 1f, light, OverlayTexture.NO_OVERLAY);
            if (s < 0) FxDraw.quad(cloth, pose, d, c, b, a, LINING, 1f, light, OverlayTexture.NO_OVERLAY);
            else FxDraw.quad(cloth, pose, a, b, c, d, LINING, 1f, light, OverlayTexture.NO_OVERLAY);
            trim(glow, pose, c, d);
            trim(glow, pose, b, c);
        }
        // clasps on the chest
        FxDraw.cube(glow, pose, -0.17f, 0.06f, -0.14f, 0.035f, 0.035f, 0.015f, GOLD, 1f, light, OverlayTexture.NO_OVERLAY);
        FxDraw.cube(glow, pose, 0.17f, 0.06f, -0.14f, 0.035f, 0.035f, 0.015f, GOLD, 1f, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static void trim(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b) {
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1e-4f) return;
        FxDraw.cube(vc, pose, mid.x, mid.y, mid.z, Math.abs(d.x) * 0.5f + 0.012f, Math.abs(d.y) * 0.5f + 0.012f, Math.abs(d.z) * 0.5f + 0.012f, GOLD, 1f,
                FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
}
