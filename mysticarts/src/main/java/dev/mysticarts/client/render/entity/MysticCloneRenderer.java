package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mysticarts.entity.MysticCloneEntity;
import dev.mysticarts.util.ColorUtil;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** Draws a clone, decoy or astral body with its owner's own skin, wrapped in a glowing spectral overlay. */
public class MysticCloneRenderer extends HumanoidMobRenderer<MysticCloneEntity, PlayerModel<MysticCloneEntity>> {
    public MysticCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(MysticCloneEntity entity) {
        UUID owner = entity.ownerId();
        if (owner == null) return DefaultPlayerSkin.getDefaultTexture();
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(owner).texture();
    }

    @Override
    public void render(MysticCloneEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        model.crouching = false;
        super.render(entity, yaw, partial, ps, buffers, light);
    }

    /** The spectral sheen: the same model again, emissive and tinted by mode, pulsing gently. */
    private static final class Glow extends RenderLayer<MysticCloneEntity, PlayerModel<MysticCloneEntity>> {
        Glow(RenderLayerParent<MysticCloneEntity, PlayerModel<MysticCloneEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, MysticCloneEntity e, float limbSwing, float limbSwingAmount, float partial,
                           float age, float headYaw, float headPitch) {
            int rgb = switch (e.mode()) {
                case MysticCloneEntity.DECOY -> 0xFFC81E;
                case MysticCloneEntity.BODY -> 0x9FD4FF;
                default -> 0xFF9A2E;
            };
            float pulse = 0.35f + 0.15f * (float) Math.sin(age * 0.15f);
            if (e.mode() == MysticCloneEntity.BODY) pulse = 0.18f;
            int argb = FastColor.ARGB32.color((int) (pulse * 255), (int) (ColorUtil.r(rgb) * 255), (int) (ColorUtil.g(rgb) * 255), (int) (ColorUtil.b(rgb) * 255));
            ResourceLocation tex = getTextureLocation(e);
            getParentModel().renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucentEmissive(tex)), 0xF000F0, OverlayTexture.NO_OVERLAY, argb);
        }
    }
}
