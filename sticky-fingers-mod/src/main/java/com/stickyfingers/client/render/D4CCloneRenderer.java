package com.stickyfingers.client.render;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.entity.D4CCloneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renders a D4C clone as a slightly translucent humanoid with a reddish tint. */
public class D4CCloneRenderer extends HumanoidMobRenderer<D4CCloneEntity, HumanoidModel<D4CCloneEntity>> {
    private static final ResourceLocation TEXTURE = StickyFingersMod.id("textures/entity/d4c_clone.png");

    public D4CCloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(D4CCloneEntity entity) {
        return TEXTURE;
    }
}
