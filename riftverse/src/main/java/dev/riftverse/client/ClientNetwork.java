package dev.riftverse.client;

import dev.riftverse.client.cinematic.CinematicDirector;
import dev.riftverse.client.cinematic.RealityCinematics;
import dev.riftverse.client.screen.MultiverseScreen;
import dev.riftverse.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Client-side handlers for Riftverse payloads. Only ever invoked on the logical client. */
public final class ClientNetwork {
    private ClientNetwork() {}

    public static void onUniverseSync(Payloads.UniverseSync p) {
        ClientUniverseState.update(p.mode(), p.spec());
    }

    public static void onCinematic(Payloads.Cinematic p) {
        CinematicDirector.begin(p.kind(), p.teleportTick(), new Vec3(p.fx(), p.fy(), p.fz()), p.colorA(), p.colorB());
    }

    public static void onArrival(Payloads.Arrival p) {
        CinematicDirector.arrive(p.title(), p.subtitle(), p.color());
    }

    public static void onShake(Payloads.Shake p) {
        CinematicDirector.shake(p.intensity(), p.duration(), p.flash(), p.color());
    }

    public static void onOpenBrowser(Payloads.OpenBrowser p) {
        Minecraft.getInstance().setScreen(new MultiverseScreen(p.console(), p.entries(), p.manifestsLeft()));
    }

    public static void onManifestResult(Payloads.ManifestResult p) {
        if (Minecraft.getInstance().screen instanceof MultiverseScreen screen) screen.onManifested(p.entry(), p.notes());
    }

    public static void onBossIntro(Payloads.BossIntro p) {
        CinematicDirector.bossIntro(p.entityId(), p.name(), p.subtitle(), p.color());
    }

    public static void onRealityCinematic(Payloads.RealityCinematic p) {
        if (p.kind() == Payloads.RealityCinematic.STOP) RealityCinematics.stop();
        else RealityCinematics.play(p.kind(), p.duration(), new Vec3(p.fx(), p.fy(), p.fz()), p.colorA(), p.colorB(), p.title(), p.subtitle());
    }

    public static void onOpenRemote(Payloads.OpenRemote p) {
        Minecraft.getInstance().setScreen(new dev.riftverse.client.screen.RealityRemoteScreen(p));
    }

    public static void onGenesis(Payloads.Genesis p) {
        dev.riftverse.client.cinematic.GenesisCinematic.play(p);
    }

    public static void onGenesisEnd() {
        dev.riftverse.client.cinematic.GenesisCinematic.finish();
    }

    public static void onCreatorScreen(Payloads.CreatorScreen p) {
        Minecraft.getInstance().setScreen(new dev.riftverse.client.screen.CreatorScreen(p.mode(), p.credentialSet()));
    }
}
