package dev.finalframe.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.finalframe.FinalFrame;
import dev.finalframe.client.RevolverAnimator;
import dev.finalframe.client.choreo.WeaponState;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.registry.FFRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws The Sheriff's Last Word from its separately baked parts so the cylinder and hammer can move,
 * and applies choreographed handling (finger spins, wrist rolls, flips) while it is held.
 * Pivot constants mirror {@code tools/gen_models.py}.
 */
public final class RevolverRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation FRAME = ModelResourceLocation.standalone(FinalFrame.id("part/revolver_frame"));
    public static final ModelResourceLocation CYLINDER = ModelResourceLocation.standalone(FinalFrame.id("part/revolver_cylinder"));
    public static final ModelResourceLocation HAMMER = ModelResourceLocation.standalone(FinalFrame.id("part/revolver_hammer"));
    public static final ModelResourceLocation HOLSTER = ModelResourceLocation.standalone(FinalFrame.id("part/holster"));

    private static final float PX = 1f / 16f;
    private static final float CYLINDER_Y = 7.85f * PX;
    private static final float CYLINDER_Z = 8f * PX;
    private static final float HAMMER_X = 5.7f * PX;
    private static final float HAMMER_Y = 8.7f * PX;
    private static final float TRIGGER_X = 7.1f * PX;
    private static final float TRIGGER_Y = 5.3f * PX;
    private static final float GRIP_X = 4.8f * PX;
    private static final float GRIP_Y = 4.6f * PX;
    private static final float BORE_Y = 8.6f * PX;
    private static final float HAMMER_COCKED_DEGREES = 38f;

    private static RevolverRenderer instance;
    private static ItemStack displayStack;

    private RevolverRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static RevolverRenderer get() {
        if (instance == null) {
            instance = new RevolverRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        WeaponState state = RevolverAnimator.idleState(stack, context);
        Entity holder = EntityPoses.current();
        boolean thirdPersonHand = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (holder != null && thirdPersonHand) {
            ClientSession s = ClientFinisherManager.INSTANCE.asPerformer(holder);
            if (s != null && !s.isAborting()) {
                state = s.choreography().weapon(s, s.time(EntityPoses.partialTick()));
                if (state.placement() != WeaponState.Placement.HAND) {
                    return;
                }
            }
        }
        poseStack.pushPose();
        applyHandling(poseStack, state);
        drawParts(poseStack, buffers, light, overlay, state.cylinder(), state.hammer(), stack);
        poseStack.popPose();
    }

    /** Finger spin around the trigger guard, flip around the grip and wrist roll around the bore line. */
    public static void applyHandling(PoseStack ps, WeaponState s) {
        if (s.lift() != 0) {
            ps.translate(0, s.lift(), 0);
        }
        if (s.spin() != 0) {
            ps.translate(TRIGGER_X, TRIGGER_Y, 0.5f);
            ps.mulPose(Axis.ZP.rotationDegrees(s.spin()));
            ps.translate(-TRIGGER_X, -TRIGGER_Y, -0.5f);
        }
        if (s.flip() != 0) {
            ps.translate(GRIP_X, GRIP_Y, 0.5f);
            ps.mulPose(Axis.ZP.rotationDegrees(s.flip()));
            ps.translate(-GRIP_X, -GRIP_Y, -0.5f);
        }
        if (s.roll() != 0) {
            ps.translate(0, BORE_Y, 0.5f);
            ps.mulPose(Axis.XP.rotationDegrees(s.roll()));
            ps.translate(0, -BORE_Y, -0.5f);
        }
    }

    /** Draws frame, cylinder and hammer in the 0..1 item space. */
    public static void drawParts(PoseStack ps, MultiBufferSource buffers, int light, int overlay, float cylinderDegrees,
                                 float hammerCock, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer items = mc.getItemRenderer();
        ItemStack s = stack.isEmpty() ? displayStack() : stack;
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), true, false);
        items.renderModelLists(model(FRAME), s, light, overlay, ps, vc);

        ps.pushPose();
        ps.translate(0, CYLINDER_Y, CYLINDER_Z);
        ps.mulPose(Axis.XP.rotationDegrees(cylinderDegrees));
        ps.translate(0, -CYLINDER_Y, -CYLINDER_Z);
        items.renderModelLists(model(CYLINDER), s, light, overlay, ps, vc);
        ps.popPose();

        ps.pushPose();
        ps.translate(HAMMER_X, HAMMER_Y, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(HAMMER_COCKED_DEGREES * hammerCock));
        ps.translate(-HAMMER_X, -HAMMER_Y, 0);
        items.renderModelLists(model(HAMMER), s, light, overlay, ps, vc);
        ps.popPose();
    }

    public static void drawHolster(PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), true, false);
        Minecraft.getInstance().getItemRenderer().renderModelLists(model(HOLSTER), displayStack(), light, overlay, ps, vc);
    }

    private static BakedModel model(ModelResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(id);
    }

    private static ItemStack displayStack() {
        if (displayStack == null) {
            displayStack = new ItemStack(FFRegistry.SHERIFFS_LAST_WORD.get());
        }
        return displayStack;
    }
}
