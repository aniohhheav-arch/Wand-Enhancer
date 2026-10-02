package com.infinitemultiverse.client.mystic;

import com.infinitemultiverse.power.mystic.PhysicalBodyEntity;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Renders the left-behind body with its owner's skin (slim or wide), armour and held items, head bowed. */
public final class PhysicalBodyRenderer extends LivingEntityRenderer<PhysicalBodyEntity, PlayerModel<PhysicalBodyEntity>> {
    private final PlayerModel<PhysicalBodyEntity> wide;
    private final PlayerModel<PhysicalBodyEntity> slim;

    public PhysicalBodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    private static PlayerSkin skin(PhysicalBodyEntity body) {
        UUID owner = body.ownerId();
        if (owner == null) {
            return DefaultPlayerSkin.get(body.getUUID());
        }
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    @Override
    public void render(PhysicalBodyEntity body, float yaw, float pt, com.mojang.blaze3d.vertex.PoseStack pose,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        model = skin(body).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(body, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PhysicalBodyEntity body) {
        return skin(body).texture();
    }

    @Override
    protected boolean shouldShowName(PhysicalBodyEntity body) {
        return false;
    }
}
