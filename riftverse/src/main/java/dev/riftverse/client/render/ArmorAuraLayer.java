package dev.riftverse.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.entity.ProceduralRenderer;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.item.ArmorSet;
import dev.riftverse.player.ArmorAbilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Full-set auras: each Riftverse armour set wraps its wearer in its own signature effect. */
public class ArmorAuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public ArmorAuraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partial, float age, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        ArmorSet set = ArmorAbilities.fullSet(player);
        if (set == null) return;
        ps.pushPose();
        // undo the model flip: world-up, +Z forward, origin at the feet
        ps.translate(0, 1.501, 0);
        ps.scale(-1, -1, 1);
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        switch (set) {
            case RIFT_WALKER -> riftWalker(ps, buffers, age, set);
            case VOYAGER -> voyager(ps, buffers, player, age, netHeadYaw, headPitch, set);
            case EVENT_HORIZON -> eventHorizon(ps, buffers, player, age, set);
            case ASTRAL -> astral(ps, buffers, age, set);
        }
        ps.popPose();
    }

    /** Shards of broken reality orbiting the waist, each trailing a short rift streak. */
    private static void riftWalker(PoseStack ps, MultiBufferSource buffers, float age, ArmorSet set) {
        PoseStack.Pose pose = ps.last();
        Vec3 eye = ProceduralRenderer.cameraLocal(pose);
        for (int i = 0; i < 3; i++) {
            double a = age * 0.08 + i * Math.PI * 2 / 3;
            float y = 1.0f + (float) Math.sin(age * 0.06 + i * 2) * 0.25f;
            float x = (float) Math.cos(a) * 0.75f, z = (float) Math.sin(a) * 0.75f;
            int color = i == 1 ? set.secondary : set.primary;
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.glow()), pose, x, y, z, 0.05f, 0.12f, color, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, x, y, z, 0.2f, color, 0.6f);
            double back = a - 0.5;
            Vec3 tail = new Vec3(Math.cos(back) * 0.75, y, Math.sin(back) * 0.75);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(x, y, z), tail, eye, 0.05f, color, 0.6f);
        }
        // a small rift tear flickering across the back
        float flicker = 0.4f + 0.3f * (float) Math.sin(age * 0.5f);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(-0.08, 1.05, -0.2), new Vec3(0.06, 1.55, -0.2), eye, 0.06f, set.primary, flicker);
    }

    /** Holographic visor band and a rotating scanner monocle that follow the head. */
    private static void voyager(PoseStack ps, MultiBufferSource buffers, AbstractClientPlayer player, float age, float netHeadYaw, float headPitch, ArmorSet set) {
        ps.pushPose();
        ps.translate(0, player.isCrouching() ? 1.501 - 0.2625 : 1.501, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-netHeadYaw));
        ps.mulPose(Axis.XP.rotationDegrees(headPitch));
        PoseStack.Pose pose = ps.last();
        float pulse = 0.7f + 0.3f * (float) Math.sin(age * 0.2f);
        FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), pose, 0, 0.27f, 0.27f, 0.24f, 0.035f, 0.01f, set.primary, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, 0.27f, 0.3f, 0.32f, set.primary, 0.45f * pulse);
        Vector3f lens = new Vector3f(0.12f, 0.29f, 0.33f);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, lens, new Vector3f(0, 0, 1), 0.07f, 0.012f, set.secondary, 0.9f, 20);
        float sweep = age * 0.15f;
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(lens).add(0, 0, 0.01f),
                new Vector3f((float) Math.sin(sweep) * 0.4f, (float) Math.cos(sweep) * 0.4f, 1f), 0.1f, 0.006f, set.primary, 0.6f, 20);
        ps.popPose();
    }

    /** A miniature event horizon hovering behind the head, wrapped in counter-rotating accretion rings. */
    private static void eventHorizon(PoseStack ps, MultiBufferSource buffers, AbstractClientPlayer player, float age, ArmorSet set) {
        PoseStack.Pose pose = ps.last();
        Vector3f c = new Vector3f(0, 1.75f, -0.42f);
        for (int i = 0; i < 3; i++) {
            float a = age * (0.05f + i * 0.02f) * (i % 2 == 0 ? 1 : -1);
            Vector3f n = new Vector3f((float) Math.sin(a) * 0.3f, 0.4f + i * 0.2f, -1f);
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, c, n, 0.32f + i * 0.12f, 0.05f, i == 1 ? set.secondary : set.primary, 0.75f, 36);
        }
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, c.x, c.y, c.z, 0.7f, set.primary, 0.35f);
        // queue a real lensing hole at the same spot - only in the world, where pose space is camera-relative and
        // unscaled (the inventory preview renders the player with a large GUI scale)
        if (pose.pose().getScale(new Vector3f()).x > 2f) return;
        Vector3f world = pose.pose().transformPosition(new Vector3f(c));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        SpatialFx.hole(cam.add(world.x, world.y, world.z), 0.06f, new Vector3f(0, 1, 0), BlackHoleEntity.STYLE_CLASSIC, 1f, 0xFFE6C0, set.primary);
    }

    /** Stars in tilted orbits, joined by faint constellation lines. */
    private static void astral(PoseStack ps, MultiBufferSource buffers, float age, ArmorSet set) {
        PoseStack.Pose pose = ps.last();
        Vec3 eye = ProceduralRenderer.cameraLocal(pose);
        int count = 5;
        Vec3[] stars = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double a = age * (0.03 + i * 0.006) + i * 1.3;
            double tilt = 0.5 + i * 0.35;
            double r = 0.8 + 0.1 * i;
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            double y = 1.0 + Math.sin(a) * Math.sin(tilt) * 0.5;
            stars[i] = new Vec3(x, y, z * Math.cos(tilt * 0.3));
            float twinkle = 0.6f + 0.4f * (float) Math.sin(age * 0.3f + i * 2);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, (float) stars[i].x, (float) stars[i].y, (float) stars[i].z, 0.18f * twinkle, set.primary, 1f);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, (float) stars[i].x, (float) stars[i].y, (float) stars[i].z, 0.06f, 0xFFFFFF, 1f);
        }
        for (int i = 0; i < count; i++) {
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, stars[i], stars[(i + 2) % count], eye, 0.012f, set.secondary, 0.35f);
        }
    }
}
