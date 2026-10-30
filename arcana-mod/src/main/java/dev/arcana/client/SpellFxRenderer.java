package dev.arcana.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arcana.entity.SpellFxEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SpellFxRenderer extends GeoEntityRenderer<SpellFxEntity> {
    public SpellFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SpellFxModel());
        this.shadowRadius = 0f;
    }

    @Override
    public void preRender(PoseStack pose, SpellFxEntity e, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int light, int overlay, int colour) {
        float s = e.visualScale(partialTick);
        pose.scale(s, s, s);
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        super.renderCubesOfBone(pose, bone, buffer, GeoBright.light(bone, light), overlay, colour);
    }
}
