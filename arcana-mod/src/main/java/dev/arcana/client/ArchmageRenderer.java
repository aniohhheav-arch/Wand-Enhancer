package dev.arcana.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arcana.entity.ArchmageEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The Hollow Archmage as an animated 3D GeckoLib model (idle, walk, cast, summon, phase roar, 5-second death). */
public class ArchmageRenderer extends GeoEntityRenderer<ArchmageEntity> {
    public ArchmageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ArchmageGeoModel());
        this.shadowRadius = 0.9f;
    }

    @Override
    public void preRender(PoseStack pose, ArchmageEntity e, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int light, int overlay, int colour) {
        float s = 1.25f;
        pose.scale(s, s, s);
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        super.renderCubesOfBone(pose, bone, buffer, GeoBright.light(bone, light), overlay, colour);
    }

    @Override
    protected float getDeathMaxRotation(ArchmageEntity e) {
        return 0f; // the death animation is authored in the model; don't topple the whole entity
    }
}
