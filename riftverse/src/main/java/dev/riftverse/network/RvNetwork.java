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
        r.playToServer(Payloads.BrowserAction.TYPE, Payloads.BrowserAction.CODEC, MultiverseService::handle);
        r.playToServer(Payloads.Ability.TYPE, Payloads.Ability.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmorAbilities.activate(sp, p.ability());
        });
    }
}
