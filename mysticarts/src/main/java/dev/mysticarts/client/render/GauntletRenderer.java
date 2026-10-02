package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mysticarts.client.CasterStates;
import dev.mysticarts.client.ClientFx;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Poses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item renderer for the gauntlet (inventory, ground, first person) and a player layer that wears it on the arm in third
 * person, so the glove follows every arm animation.
 */
public final class GauntletRenderer extends BlockEntityWithoutLevelRenderer {
    /** The player currently being drawn, set around player rendering so the item renderer can skip itself. */
    @Nullable public static Player rendering;

    public GauntletRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** Hand pose derived from what the wearer is doing. Returns {curl, snap, charge}. */
    static float[] handState(@Nullable Player p, float partial) {
        if (p == null) return new float[] {0.25f, 0f, 0f};
        CasterStates.Anim a = CasterStates.anim(p.getId());
        float blend = a.poseBlend(partial);
        float curl = 0.3f;
        if (a.pose == Poses.RAISE || a.pose == Poses.SLAM) curl = Mth.lerp(blend, 0.3f, 0.95f);
        else if (a.pose == Poses.PUSH || a.pose == Poses.POINT) curl = Mth.lerp(blend, 0.3f, 0.0f);
        float snap = 0f;
        if (ClientFx.snapCaster() == p.getId()) {
            float t = ClientFx.snapProgress(partial);
            snap = t < 0.95f ? Mth.clamp(t * 1.2f, 0f, 0.6f) : Mth.clamp(1f - (t - 0.95f) * 6f, 0f, 1f);
        }
        float charge = Math.max(a.charge(partial), CasterStates.flag(p.getId(), Payloads.CasterState.F_CHARGING) ? 0.7f : 0f);
        return new float[] {curl, snap, charge};
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        boolean thirdPerson = ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (thirdPerson && rendering != null) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
        boolean firstPerson = ctx.firstPerson();
        float[] hand = handState(firstPerson ? mc.player : null, partial);
        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);
        if (firstPerson) {
            ps.translate(0, -0.25f, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-10f));
            if (ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) ps.scale(-1, 1, 1);
            ps.scale(1.9f, 1.9f, 1.9f);
        } else {
            ps.translate(0, -0.22f, 0);
            ps.scale(2.0f, 2.0f, 2.0f);
        }
        GauntletModel.draw(ps, buffers, light, InfinityGauntletItem.stones(stack), time, hand[0], hand[1], hand[2]);
        ps.popPose();
    }

    /** Wears the gauntlet on whichever arm holds it. */
    public static final class ArmLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public ArmLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partial,
                           float age, float headYaw, float headPitch) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!(stack.getItem() instanceof InfinityGauntletItem)) continue;
                HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
                boolean right = arm == HumanoidArm.RIGHT;
                float[] state = handState(player, partial);
                ps.pushPose();
                (right ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(ps);
                ps.translate(right ? -0.0625f : 0.0625f, 0.47f, 0);
                ps.mulPose(Axis.YP.rotationDegrees(right ? -90f : 90f));
                if (!right) ps.scale(-1, 1, 1);
                ps.scale(0.95f, 0.95f, 0.95f);
                GauntletModel.draw(ps, buffers, light, InfinityGauntletItem.stones(stack), age, state[0], state[1], state[2]);
                ps.popPose();
            }
        }
    }
}
