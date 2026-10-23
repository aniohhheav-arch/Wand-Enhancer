package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wandcraft.WandCraft;
import com.wandcraft.entity.Props;
import com.wandcraft.entity.SpellProp;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Draws {@link SpellProp}s: geometry/animation/texture are looked up by kind, render mode (solid, glassy, additive) by {@link Props}. */
public class PropRenderer extends GeoEntityRenderer<SpellProp> {
    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(WandCraft.ID, p); }

    public PropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GeoModel<SpellProp>() {
            @Override public ResourceLocation getModelResource(SpellProp p) { return rl("geo/entity/prop/" + p.kind() + ".geo.json"); }
            @Override public ResourceLocation getTextureResource(SpellProp p) { return rl("textures/entity/prop/" + p.kind() + ".png"); }
            @Override public ResourceLocation getAnimationResource(SpellProp p) { return rl("animations/entity/prop/" + p.kind() + ".animation.json"); }
        });
        shadowRadius = 0f;
    }

    @Override public boolean shouldRender(SpellProp e, Frustum f, double x, double y, double z) { return true; }

    @Override
    public void render(SpellProp e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Props.Def d = Props.get(e.kind());
        super.render(e, yaw, pt, pose, buffers, d != null && d.fullbright() ? 15728880 : light);
    }

    @Override
    public RenderType getRenderType(SpellProp e, ResourceLocation tex, MultiBufferSource buffers, float pt) {
        Props.Def d = Props.get(e.kind());
        if (d == null) return RenderType.entityTranslucent(tex);
        return switch (d.mode()) {
            case SOLID -> RenderType.entityCutoutNoCull(tex);
            case TRANSLUCENT -> RenderType.entityTranslucent(tex);
            case ADDITIVE -> RenderType.eyes(tex);
        };
    }

    /** Props are not living: use the entity's own yaw/pitch and synced scale instead of body rotation. */
    @Override
    protected void applyRotations(SpellProp e, PoseStack pose, float age, float bodyYaw, float pt, float nativeScale) {
        float s = e.scale();
        pose.scale(s, s, s);
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
    }
}
