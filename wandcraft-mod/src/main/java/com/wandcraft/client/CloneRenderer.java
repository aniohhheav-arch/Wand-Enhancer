package com.wandcraft.client;

import com.wandcraft.WandCraft;
import com.wandcraft.entity.ShadowClone;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Draws summoned fighters with the player model: a black silhouette, the owner's skin for mirror images, or a pale ghost. */
public class CloneRenderer extends MobRenderer<ShadowClone, PlayerModel<ShadowClone>> {
    private static final ResourceLocation SHADOW = ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "textures/entity/shadow_clone.png");
    private static final ResourceLocation SPECTRAL = ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "textures/entity/spectral_warrior.png");

    public CloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowClone e) {
        if (e.style() == ShadowClone.STYLE_MIRROR) {
            Player o = e.owner();
            if (o instanceof AbstractClientPlayer acp) return acp.getSkin().texture();
        }
        return e.style() == ShadowClone.STYLE_SPECTRAL ? SPECTRAL : SHADOW;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(ShadowClone e, boolean visible, boolean translucent, boolean glowing) {
        return e.style() == ShadowClone.STYLE_SHADOW ? RenderType.entityTranslucent(getTextureLocation(e)) : super.getRenderType(e, visible, translucent, glowing);
    }
}
