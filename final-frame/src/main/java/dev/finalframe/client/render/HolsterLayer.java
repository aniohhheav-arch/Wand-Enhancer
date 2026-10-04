package dev.finalframe.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.finalframe.FFConfig;
import dev.finalframe.client.choreo.WeaponState;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.sheriff.SheriffsLastWordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Leather holster on the right hip. The revolver rests in it whenever it is carried but not drawn,
 * and the finisher draws from / returns to it.
 */
public final class HolsterLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Maps revolver item space (barrel +X, top +Y) to body space: muzzle down, top forward. */
    private static final Matrix4f HOLSTER_BASIS = new Matrix4f().set3x3(new Matrix3f(
        0, 1, 0,
        0, 0, -1,
        -1, 0, 0));

    public HolsterLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!FFConfig.get(FFConfig.RENDER_HOLSTER) || player.isInvisible()) {
            return;
        }
        boolean held = isRevolver(player.getMainHandItem()) || isRevolver(player.getOffhandItem());
        boolean carried = held || player == Minecraft.getInstance().player && player.getInventory().contains(st -> st.getItem() instanceof SheriffsLastWordItem);
        ClientSession s = ClientFinisherManager.INSTANCE.asPerformer(player);
        if (!carried && s == null) {
            return;
        }
        boolean gunInHolster;
        if (s != null && !s.isAborting()) {
            gunInHolster = s.choreography().weapon(s, s.time(partialTick)).placement() == WeaponState.Placement.HOLSTER;
        } else {
            gunInHolster = carried && !held;
        }

        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        ps.translate(-4.85f / 16f, 10.2f / 16f, 0.2f / 16f);
        ps.mulPose(Axis.XP.rotationDegrees(-12f));
        ps.mulPose(Axis.ZP.rotationDegrees(-6f));
        ps.mulPose(new org.joml.Quaternionf().setFromNormalized(HOLSTER_BASIS));
        ps.scale(0.72f, 0.72f, 0.72f);
        ps.translate(-10f / 16f, -8.4f / 16f, -0.5f);
        RevolverRenderer.drawHolster(ps, buffers, light, OverlayTexture.NO_OVERLAY);
        if (gunInHolster) {
            RevolverRenderer.drawParts(ps, buffers, light, OverlayTexture.NO_OVERLAY, 0f, 0f, ItemStack.EMPTY);
        }
        ps.popPose();
    }

    private static boolean isRevolver(ItemStack stack) {
        return stack.getItem() instanceof SheriffsLastWordItem;
    }
}
