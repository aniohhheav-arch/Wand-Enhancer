package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib renderer for the gauntlet. Stone sockets are hidden until the matching stone is installed; installed
 * stones render through the emissive glowmask layer.
 */
public class GauntletItemRenderer extends GeoItemRenderer<InfinityGauntletItem> {
    public GauntletItemRenderer() {
        super(new DefaultedItemGeoModel<>(InfinityGauntlet.id("infinity_gauntlet")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack pose, InfinityGauntletItem animatable, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int light, int overlay, int colour) {
        super.preRender(pose, animatable, model, buffers, buffer, isReRender, partialTick, light, overlay, colour);
        if (isReRender) {
            return;
        }
        GauntletState state = InfinityGauntletItem.state(getCurrentItemStack());
        for (InfinityStone stone : InfinityStone.values()) {
            model.getBone("stone_" + stone.getSerializedName()).ifPresent(bone -> bone.setHidden(!state.has(stone)));
        }
    }
}
