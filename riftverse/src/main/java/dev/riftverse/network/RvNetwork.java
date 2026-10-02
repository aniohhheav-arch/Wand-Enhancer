package dev.riftverse.network;

import dev.riftverse.client.ClientNetwork;
import dev.riftverse.player.ArmorAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RvNetwork {
    private RvNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Payloads.UniverseSync.TYPE, Payloads.UniverseSync.CODEC, (p, ctx) -> ClientNetwork.onUniverseSync(p));
        r.playToClient(Payloads.Cinematic.TYPE, Payloads.Cinematic.CODEC, (p, ctx) -> ClientNetwork.onCinematic(p));
        r.playToClient(Payloads.Arrival.TYPE, Payloads.Arrival.CODEC, (p, ctx) -> ClientNetwork.onArrival(p));
        r.playToClient(Payloads.Shake.TYPE, Payloads.Shake.CODEC, (p, ctx) -> ClientNetwork.onShake(p));
        r.playToClient(Payloads.OpenBrowser.TYPE, Payloads.OpenBrowser.CODEC, (p, ctx) -> ClientNetwork.onOpenBrowser(p));
        r.playToClient(Payloads.ManifestResult.TYPE, Payloads.ManifestResult.CODEC, (p, ctx) -> ClientNetwork.onManifestResult(p));
        r.playToClient(Payloads.BossIntro.TYPE, Payloads.BossIntro.CODEC, (p, ctx) -> ClientNetwork.onBossIntro(p));
        r.playToClient(Payloads.RealityCinematic.TYPE, Payloads.RealityCinematic.CODEC, (p, ctx) -> ClientNetwork.onRealityCinematic(p));
        r.playToClient(Payloads.OpenRemote.TYPE, Payloads.OpenRemote.CODEC, (p, ctx) -> ClientNetwork.onOpenRemote(p));
        r.playToServer(Payloads.RemoteAction.TYPE, Payloads.RemoteAction.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) dev.riftverse.multiverse.RealityRemoteService.handle(sp, p);
        });
        r.playToClient(Payloads.Genesis.TYPE, Payloads.Genesis.CODEC, (p, ctx) -> ClientNetwork.onGenesis(p));
        r.playBidirectional(Payloads.GenesisSkip.TYPE, Payloads.GenesisSkip.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) dev.riftverse.multiverse.GenesisManager.skip(sp);
            else ClientNetwork.onGenesisEnd();
        });
        r.playToClient(Payloads.CreatorScreen.TYPE, Payloads.CreatorScreen.CODEC, (p, ctx) -> ClientNetwork.onCreatorScreen(p));
        r.playToServer(Payloads.CreatorSubmit.TYPE, Payloads.CreatorSubmit.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) dev.riftverse.creator.RuptureService.submit(sp, p);
        });
        r.playBidirectional(Payloads.Vehicle.TYPE, Payloads.Vehicle.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                if (sp.level().getEntity(p.entityId()) instanceof dev.riftverse.entity.vehicle.DeLoreanEntity car && car.distanceToSqr(sp) < 64) {
                    if (p.action() == Payloads.Vehicle.SET_YEAR) car.setCircuits(Math.max(-1_000_000, Math.min(1_000_000, p.value())), car.armed());
                    else if (p.action() == Payloads.Vehicle.ARM) car.setCircuits(car.targetYear(), p.value() != 0);
                }
            } else {
                ClientNetwork.onVehicle(p);
            }
        });
        r.playToServer(Payloads.BrowserAction.TYPE, Payloads.BrowserAction.CODEC, MultiverseService::handle);
        r.playToServer(Payloads.Ability.TYPE, Payloads.Ability.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmorAbilities.activate(sp, p.ability());
        });
    }
}
