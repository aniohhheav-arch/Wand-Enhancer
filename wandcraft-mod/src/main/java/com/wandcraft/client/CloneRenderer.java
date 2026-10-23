package com.wandcraft.client;

import com.wandcraft.WandCraft;
import com.wandcraft.entity.ShadowClone;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer for summoned fighters: black silhouette, owner-skinned mirror image, or pale ghost. */
public class CloneRenderer extends GeoEntityRenderer<ShadowClone> {
    private static final ResourceLocation SHADOW = rl("textures/entity/shadow_clone.png");
    private static final ResourceLocation SPECTRAL = rl("textures/entity/spectral_warrior.png");

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(WandCraft.ID, path); }

    public CloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        // Owner skins have no glow companion texture.
        addRenderLayer(new EmissiveLayer<>(this, e -> e.style() == ShadowClone.STYLE_MIRROR ? null : EmissiveLayer.glowOf(getTextureLocation(e))));
        withScale(1f);
    }

    @Override
    public RenderType getRenderType(ShadowClone e, ResourceLocation texture, net.minecraft.client.renderer.MultiBufferSource buffers, float partialTick) {
        return e.style() == ShadowClone.STYLE_SHADOW ? RenderType.entityTranslucent(texture) : super.getRenderType(e, texture, buffers, partialTick);
    }

    /** One geometry and animation set; only the texture varies by summon style. */
    private static final class Model extends DefaultedEntityGeoModel<ShadowClone> {
        Model() { super(rl("shadow_clone"), true); }

        @Override
        public ResourceLocation getTextureResource(ShadowClone e) {
            if (e.style() == ShadowClone.STYLE_MIRROR) {
                Player o = e.owner();
                if (o instanceof AbstractClientPlayer acp) return acp.getSkin().texture();
            }
            return e.style() == ShadowClone.STYLE_SPECTRAL ? SPECTRAL : SHADOW;
        }
    }
}
