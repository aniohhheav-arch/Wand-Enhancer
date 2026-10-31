package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * The gauntlet is worn, not held. In third person a layer fits it over the player model's own hand and wrist, so it
 * follows every arm pose; in first person the player's real arm is drawn reaching into view wearing it. Both use
 * {@link #fit}, which sizes the glove around the arm's last few pixels with the palm turned inward.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class WornGauntlet {
    /**
     * Draws the gauntlet on an arm. The pose must be in the arm's own space (after {@code arm.translateAndRotate}):
     * +Y runs from the shoulder pivot down to the hand, which ends 10 pixels below it.
     */
    public static void fit(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, HumanoidArm arm, boolean slim) {
        boolean right = arm == HumanoidArm.RIGHT;
        float centre = (right ? -1 : 1) * (slim ? 0.5f : 1f);
        pose.pushPose();
        // wrist cuff starts just above the hand; the glove's fingers run on down the arm
        pose.translate(centre / 16f, 3.0f / 16f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(right ? 90 : -90)); // palm toward the body
        pose.scale(0.6f, 0.47f, slim ? 0.66f : 0.8f);             // width x length x depth, wrapping a 4x4 (or 3x4) arm
        pose.translate(-0.5f, -0.51f, -0.5f);                    // undo the item renderer's block-centre offset
        IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.NONE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    static boolean slim(AbstractClientPlayer player) {
        return player.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    /** Third person: the glove rides the model's arm. */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!(stack.getItem() instanceof InfinityGauntletItem)) {
                    continue;
                }
                HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
                pose.pushPose();
                getParentModel().translateToHand(arm, pose);
                fit(pose, buffers, light, stack, arm, slim(player));
                pose.popPose();
            }
        }
    }

    /** First person: the player's own arm, wearing the gauntlet, replaces the held-item view. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        Minecraft mc = Minecraft.getInstance();
        if (!(stack.getItem() instanceof InfinityGauntletItem) || mc.player == null || mc.player.isInvisible()) {
            return;
        }
        event.setCanceled(true);
        AbstractClientPlayer player = mc.player;
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // the vanilla bare-arm placement, with equip and swing motion
        boolean right = arm == HumanoidArm.RIGHT;
        float f = right ? 1 : -1;
        // in screen space: a little higher and nearer the centre than a bare arm, so the stones are in view
        pose.translate(-f * 0.2f, 0.16f, -0.08f);
        float swing = event.getSwingProgress();
        float sq = Mth.sqrt(swing);
        pose.translate(f * (-0.3f * Mth.sin(sq * Mth.PI) + 0.64f), 0.4f * Mth.sin(sq * Mth.TWO_PI) - 0.6f + event.getEquipProgress() * -0.6f,
                -0.4f * Mth.sin(swing * Mth.PI) - 0.72f);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45));
        pose.mulPose(Axis.YP.rotationDegrees(f * Mth.sin(sq * Mth.PI) * 70));
        pose.mulPose(Axis.ZP.rotationDegrees(f * Mth.sin(swing * swing * Mth.PI) * -20));
        pose.translate(f * -1, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120));
        pose.mulPose(Axis.XP.rotationDegrees(200));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135));
        pose.translate(f * 5.6f, 0, 0);
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        (right ? model.rightArm : model.leftArm).translateAndRotate(pose);
        fit(pose, event.getMultiBufferSource(), event.getPackedLight(), stack, arm, slim(player));
        pose.popPose();
    }

    private WornGauntlet() {
    }
}
