package com.tardismod;

import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.tardismod.block.TardisBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

/** Client entrypoint: GeckoLib renderer for the police box (model/animation/texture paths derive from the id). */
public class TardisModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(FlightPayload.TYPE, (payload, ctx) -> VortexOverlay.trigger(payload.ticks()));
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(TardisMod.id("vortex"), new VortexOverlay());
        BlockEntityRendererRegistry.register(TardisMod.TARDIS_BE,
            ctx -> new GeoBlockRenderer<TardisBlockEntity, net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState>(ctx, new DefaultedBlockGeoModel<>(TardisMod.id("tardis"))));
    }
}
