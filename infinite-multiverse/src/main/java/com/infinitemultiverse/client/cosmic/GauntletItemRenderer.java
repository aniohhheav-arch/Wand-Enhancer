package com.infinitemultiverse.client.cosmic;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.cosmic.CosmicContent;
import com.infinitemultiverse.cosmic.gauntlet.Gauntlet;
import com.infinitemultiverse.cosmic.gauntlet.InfinityStone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The 3D Infinity Gauntlet: a modelled golden glove plus a gem model for each set stone. Stones that are set glow at
 * full brightness; empty sockets stay bare gold.
 */
public final class GauntletItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation BASE = ModelResourceLocation.standalone(InfiniteMultiverse.id("item/gauntlet/base"));
    private static final ModelResourceLocation[] STONES = new ModelResourceLocation[InfinityStone.values().length];
    private static final int FULL_BRIGHT = 0xF000F0;

    static {
        for (InfinityStone stone : InfinityStone.values()) {
            STONES[stone.ordinal()] = ModelResourceLocation.standalone(InfiniteMultiverse.id("item/gauntlet/stone_" + stone.id()));
        }
    }

    private GauntletItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer items = mc.getItemRenderer();
        var models = mc.getModelManager();
        var buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        BakedModel base = models.getModel(BASE);
        items.renderModelLists(base, stack, light, overlay, pose, buffer);
        int mask = Gauntlet.stones(stack);
        for (InfinityStone stone : InfinityStone.values()) {
            if ((mask & stone.bit()) != 0) {
                items.renderModelLists(models.getModel(STONES[stone.ordinal()]), stack, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffer);
            }
        }
        if (stack.hasFoil()) {
            items.renderModelLists(base, stack, light, overlay, pose, ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), true, true));
        }
    }

    @EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onModels(ModelEvent.RegisterAdditional event) {
            event.register(BASE);
            for (ModelResourceLocation stone : STONES) {
                event.register(stone);
            }
        }

        @SubscribeEvent
        public static void onExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                private GauntletItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new GauntletItemRenderer();
                    }
                    return renderer;
                }
            }, CosmicContent.INFINITY_GAUNTLET.get());
        }
    }
}
