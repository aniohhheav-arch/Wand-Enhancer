package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.ability.AbilityExecutor;
import com.stickyfingers.ability.StandManager;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = StickyFingersMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) AbilityExecutor.tryActivate(sp, p.ability(), p.alt());
        });
        r.playToServer(StandActionPayload.TYPE, StandActionPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) handleAction(sp, p);
        });
        r.playToClient(StandSyncPayload.TYPE, StandSyncPayload.CODEC,
                (p, ctx) -> com.stickyfingers.client.ClientState.onSync(p));
        r.playToClient(ScreenFxPayload.TYPE, ScreenFxPayload.CODEC,
                (p, ctx) -> com.stickyfingers.client.fx.CameraFx.trigger(p));
        r.playToClient(SeparatedBlocksPayload.TYPE, SeparatedBlocksPayload.CODEC, (p, ctx) -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null && level.getEntity(p.entityId()) instanceof com.stickyfingers.entity.ZipperEntity z) z.setSeparated(p.positions(), p.states());
        });
        r.playToClient(NetworkSnapshotPayload.TYPE, NetworkSnapshotPayload.CODEC,
                (p, ctx) -> com.stickyfingers.client.ClientState.onNetwork(p));
    }

    private static void handleAction(ServerPlayer sp, StandActionPayload p) {
        switch (p.action()) {
            case TOGGLE_STAND -> StandManager.toggle(sp);
            case SELECT -> StandManager.select(sp, p.a());
            case REQUEST_NETWORK -> ZipperNetwork.sendSnapshot(sp, true);
            case REFRESH_NETWORK -> ZipperNetwork.sendSnapshot(sp, false);
            case NET_TRAVEL -> ZipperNetwork.travelTo(sp, p.a());
            case NET_LINK -> ZipperNetwork.link(sp, p.a(), p.b());
            case NET_UNLINK -> ZipperNetwork.unlink(sp, p.a(), p.b());
            case NET_CLOSE -> ZipperNetwork.close(sp, p.a());
            case NET_SET_EXIT -> ZipperNetwork.setExit(sp, p.a());
            case NET_TOGGLE_OPEN -> ZipperNetwork.toggleOpen(sp, p.a());
            case CLOSE_ALL -> ZipperNetwork.closeAll(sp);
            case INTERACT -> com.stickyfingers.ability.Interactions.interact(sp, p.a());
            case SET_ORIENT -> com.stickyfingers.data.StandData.get(sp).setOrientation(p.a());
        }
    }

    private ModNetwork() {}
}
