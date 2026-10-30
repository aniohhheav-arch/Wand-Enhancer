package dev.arcana.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arcana.item.Handle;
import dev.arcana.item.WandItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Renders the 3D wand: stage-dependent bones, crystal tint, handle material, fullbright glow bones. */
public class WandRenderer extends GeoItemRenderer<WandItem> {
    private static final int[] HANDLE_RGB = {0xC89A6B, 0xF2EBD8, 0xFFD24D, 0x5A4880, 0x3AA0A0};

    public WandRenderer() {
        super(new WandGeoModel());
    }

    @Override
    public void preRender(PoseStack pose, WandItem wand, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int light, int overlay, int colour) {
        ItemStack stack = getCurrentItemStack();
        int stage = stack == null ? 2 : WandItem.stageOf(stack);
        model.getBone("stage1").ifPresent(b -> b.setHidden(stage < 1));
        model.getBone("stage2").ifPresent(b -> b.setHidden(stage < 2));
        pose.translate(0f, -0.72f, 0f); // the model's origin is its base; centre it in item space
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        ItemStack stack = getCurrentItemStack();
        String n = bone.getName();
        if (stack != null) {
            if (n.startsWith("tint")) colour = 0xFF000000 | WandItem.tintOf(stack, WandItem.schoolOf(stack));
            else if (n.equals("handle")) colour = 0xFF000000 | HANDLE_RGB[Math.floorMod(WandItem.styleOf(stack).handle(), Handle.values().length)];
        }
        super.renderCubesOfBone(pose, bone, buffer, GeoBright.light(bone, light), overlay, colour);
    }
}
