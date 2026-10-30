package dev.arcana.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arcana.ArcanaMod;
import dev.arcana.entity.ArchmageEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class ArchmageRenderer extends HumanoidMobRenderer<ArchmageEntity, HumanoidModel<ArchmageEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, "textures/entity/hollow_archmage.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, "textures/entity/hollow_archmage_glow.png");

    public ArchmageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.8f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    protected void scale(ArchmageEntity e, PoseStack pose, float partial) {
        float s = 1.35f;
        if (e.deathTime > 0) s *= 1f + Math.min(0.3f, e.deathTime / 300f);
        pose.scale(s, s, s);
    }

    @Override
    public ResourceLocation getTextureLocation(ArchmageEntity e) {
        return TEXTURE;
    }
}
