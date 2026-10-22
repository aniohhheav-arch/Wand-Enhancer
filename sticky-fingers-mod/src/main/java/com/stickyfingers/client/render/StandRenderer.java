package com.stickyfingers.client.render;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.entity.StandAnim;
import com.stickyfingers.entity.StandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Sticky Fingers: a 1:1 player-proportioned GeckoLib model using the supplied skin layout,
 * rendered translucent with an emissive glowmask (eyes, zipper motifs).
 */
public class StandRenderer extends GeoEntityRenderer<StandEntity> {
    public StandRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(StickyFingersMod.id("sticky_fingers"), "head"));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.0f;
    }

    /** Idle: face exactly where the owner looks (interpolated per frame). Striking: face the synced attack direction. */
    @Override
    protected void applyRotations(StandEntity stand, com.mojang.blaze3d.vertex.PoseStack ps, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        var owner = stand.owner();
        float yaw = owner != null && !stand.isStriking()
                ? Mth.rotLerp(partialTick, owner.yHeadRotO, owner.yHeadRot)
                : Mth.rotLerp(partialTick, stand.yRotO, stand.getYRot());
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - yaw));
    }

    @Override
    public RenderType getRenderType(StandEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public ResourceLocation getTextureLocation(StandEntity stand) {
        return switch (stand.variant()) {
            case 1 -> StickyFingersMod.id("textures/entity/star_platinum.png");
            case 2 -> StickyFingersMod.id("textures/entity/d4c.png");
            default -> StickyFingersMod.id("textures/entity/sticky_fingers.png");
        };
    }

    @Override
    public Color getRenderColor(StandEntity stand, float partialTick, int packedLight) {
        float age = stand.age() + partialTick;
        float alpha = Mth.clamp(age / 8f, 0f, 1f) * 0.92f;
        if (stand.anim() == StandAnim.DISMISS) alpha *= 0.5f;
        var mc = Minecraft.getInstance();
        if (mc.player != null && stand.owner() == mc.player && mc.options.getCameraType().isFirstPerson()) alpha *= 0.55f;
        return switch (stand.variant()) {
            // Star Platinum: purple-blue tint
            case 1 -> Color.ofRGBA(0.65f, 0.55f, 1.0f, alpha);
            // D4C: white-red tint
            case 2 -> Color.ofRGBA(1.0f, 0.7f, 0.7f, alpha);
            // Sticky Fingers: white (default)
            default -> Color.ofRGBA(1f, 1f, 1f, alpha);
        };
    }
}
