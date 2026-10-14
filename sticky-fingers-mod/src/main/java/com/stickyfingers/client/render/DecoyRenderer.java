package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stickyfingers.entity.DecoyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** The decoy wears its owner's skin, with a visible seam down its front. */
public class DecoyRenderer extends LivingEntityRenderer<DecoyEntity, PlayerModel<DecoyEntity>> {
    public DecoyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public void render(DecoyEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(e, yaw, partial, ps, buffers, light);
        float bodyYaw = (float) Math.toRadians(e.yBodyRot);
        Vec3 n = new Vec3(-Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
        ZipperDraw.draw(ps, buffers, new Vec3(0, 1.05, 0).add(n.scale(0.17)), n, new Vec3(0, 1, 0), 0.7f, 0.2f,
                0.15f, 1f, light, 1f, e.tickCount + partial);
    }

    @Override
    protected boolean shouldShowName(DecoyEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(DecoyEntity e) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null && e.ownerId().isPresent()) {
            PlayerInfo info = conn.getPlayerInfo(e.ownerId().get());
            if (info != null) return info.getSkin().texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }
}
