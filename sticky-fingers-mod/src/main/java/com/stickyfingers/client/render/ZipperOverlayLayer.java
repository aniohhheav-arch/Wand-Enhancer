package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.client.ClientState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Glowing zipper seams drawn directly over any unzipped creature's own model (works for modded mobs too),
 * like the charged-creeper layer but stitched with zippers.
 */
public class ZipperOverlayLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation TEXTURE = StickyFingersMod.id("textures/entity/zipper_overlay.png");

    public ZipperOverlayLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean unzipped = ClientState.isUnzipped(entity.getId());
        if (!unzipped && !ClientState.isBody(entity.getId())) return;
        float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.3f);
        int alpha = (int) ((unzipped ? 230 : 150) * pulse);
        var vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        getParentModel().renderToBuffer(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0xFFFFFF);
    }
}
