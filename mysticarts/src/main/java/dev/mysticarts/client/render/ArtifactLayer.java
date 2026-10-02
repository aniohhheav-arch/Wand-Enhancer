package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.CasterStates;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.power.Poses;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Worn artifacts: the Eye of Agamotto on the chest (its lids part and a green clock-glyph unfolds whenever time is
 * bent), the sling ring on the left hand and the wrist wraps on both forearms.
 */
public class ArtifactLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int GOLD = 0xD9A836;
    private static final int GOLD_DARK = 0x8A6418;
    private static final int GREEN = 0x22E06A;
    private static final int WRAP = 0xE8D6B0;

    public ArtifactLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partial, float age, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        int id = player.getId();
        int full = FxDraw.FULL_BRIGHT;
        int none = OverlayTexture.NO_OVERLAY;
        CasterStates.Anim anim = CasterStates.anim(id);
        boolean timeMagic = anim.pose == Poses.MUDRA && anim.poseTicks > 0;
        float open = timeMagic ? Math.min(1f, anim.poseBlend(partial) * 1.5f) : 0f;

        if (CasterStates.hasArtifact(id, Artifact.AMULET.ordinal())) {
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            PoseStack.Pose pose = ps.last();
            VertexConsumer solid = buffers.getBuffer(MaRenderTypes.solid());
            VertexConsumer glow = buffers.getBuffer(MaRenderTypes.glow());
            // chain around the neck
            for (int i = 0; i < 8; i++) {
                float a = (float) (Math.PI * (0.15 + 0.7 * i / 7.0));
                FxDraw.cube(solid, pose, (float) Math.cos(a) * 0.15f, 0.03f + (float) Math.sin(a) * 0.12f, -0.135f, 0.012f, 0.012f, 0.008f, GOLD_DARK, 1f, light, none);
            }
            // the amulet: a round gold frame with four petals and the eye at its heart
            float cy = 0.25f, cz = -0.145f;
            FxDraw.ellipsoid(solid, pose, 0, cy, cz, 0.085f, 0.085f, 0.02f, 12, GOLD, GOLD_DARK, 1f, light, none, -2f);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                FxDraw.cube(solid, pose, (float) Math.cos(a) * 0.09f, cy + (float) Math.sin(a) * 0.09f, cz, 0.022f, 0.022f, 0.012f, GOLD, 1f, light, none);
            }
            // lids part as the eye opens
            float lid = 0.03f * (1f - open) + 0.004f;
            FxDraw.cube(solid, pose, 0, cy - 0.022f - (0.03f - lid) * 0.5f, cz - 0.022f, 0.05f, lid * 0.5f + 0.006f, 0.006f, GOLD_DARK, 1f, light, none);
            FxDraw.cube(solid, pose, 0, cy + 0.022f + (0.03f - lid) * 0.5f, cz - 0.022f, 0.05f, lid * 0.5f + 0.006f, 0.006f, GOLD_DARK, 1f, light, none);
            FxDraw.cube(glow, pose, 0, cy, cz - 0.02f, 0.03f, 0.008f + open * 0.018f, 0.004f, GREEN, 0.6f + open * 0.4f, full, none);
            if (open > 0.05f) {
                VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
                MaDraw.rune(rune, pose, new Vector3f(0, cy, cz - 0.08f - open * 0.15f), new Vector3f(0, 0, -1), 0.12f + open * 0.35f, age * 0.08f, MaDraw.CLOCK, id & 255, GREEN, open);
                MaDraw.rune(rune, pose, new Vector3f(0, cy, cz - 0.05f), new Vector3f(0, 0, -1), 0.2f * open, 0, MaDraw.HALO, 1, GREEN, open * 0.8f);
            }
            ps.popPose();
        }

        boolean wraps = CasterStates.hasArtifact(id, Artifact.BRACERS.ordinal());
        boolean ring = CasterStates.hasArtifact(id, Artifact.RING.ordinal());
        if (wraps || ring) {
            for (int side = 0; side < 2; side++) {
                ps.pushPose();
                (side == 0 ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(ps);
                PoseStack.Pose pose = ps.last();
                VertexConsumer solid = buffers.getBuffer(MaRenderTypes.solid());
                float x = side == 0 ? -0.0625f : 0.0625f;
                if (wraps) {
                    for (int i = 0; i < 4; i++) {
                        float y = 0.36f + i * 0.055f;
                        FxDraw.cube(solid, pose, x, y, 0, 0.13f, 0.022f, 0.13f, i % 2 == 0 ? WRAP : 0xC8B48E, 1f, light, none);
                    }
                }
                if (ring && side == 1) {
                    // the sling ring sits across two fingers of the left hand
                    VertexConsumer glow = buffers.getBuffer(MaRenderTypes.glow());
                    FxDraw.cube(solid, pose, x + 0.02f, 0.66f, -0.06f, 0.11f, 0.018f, 0.04f, GOLD, 1f, light, none);
                    FxDraw.cube(glow, pose, x + 0.02f, 0.66f, -0.1f, 0.02f, 0.012f, 0.008f, 0xFF9A2E, 1f, full, none);
                }
                ps.popPose();
            }
        }
    }
}
