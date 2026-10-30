package dev.infinitygauntlet.network;

import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.client.ClientPayloadHandler;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SelectAbilityPayload.TYPE, SelectAbilityPayload.CODEC,
                (payload, ctx) -> AbilityCaster.select((ServerPlayer) ctx.player(), payload.stone(), payload.ability()));
        registrar.playToServer(ControlInputPayload.TYPE, ControlInputPayload.CODEC,
                (payload, ctx) -> dev.infinitygauntlet.nexus.PossessionManager.input((ServerPlayer) ctx.player(), payload));
        registrar.playToServer(PossessionActionPayload.TYPE, PossessionActionPayload.CODEC,
                (payload, ctx) -> dev.infinitygauntlet.nexus.PossessionManager.action((ServerPlayer) ctx.player(), payload.action()));
        registrar.playToClient(PossessionStatePayload.TYPE, PossessionStatePayload.CODEC,
                (payload, ctx) -> ClientPayloadHandler.possession(payload));
        registrar.playToServer(PanelPressPayload.TYPE, PanelPressPayload.CODEC,
                (payload, ctx) -> dev.infinitygauntlet.breach.BreachManager.press((ServerPlayer) ctx.player(), payload.panelId(), payload.control()));
        registrar.playToClient(SlicePayload.TYPE, SlicePayload.CODEC, (payload, ctx) -> ClientPayloadHandler.slice(payload));
        registrar.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.view(payload));
        registrar.playToClient(GlitchPayload.TYPE, GlitchPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.glitch(payload));
        // Client handlers are only invoked on the logical client; the lambdas keep the client class unloaded on servers.
        registrar.playToClient(VfxPayload.TYPE, VfxPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.vfx(payload));
        registrar.playToClient(CameraPayload.TYPE, CameraPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.camera(payload));
        registrar.playToClient(TransitionPayload.TYPE, TransitionPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.transition(payload));
        registrar.playToClient(TimeScalePayload.TYPE, TimeScalePayload.CODEC, (payload, ctx) -> ClientPayloadHandler.timeScale(payload));
        registrar.playToClient(CooldownSyncPayload.TYPE, CooldownSyncPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.cooldowns(payload));
    }

    /**
     * Sends to one player if their connection negotiated this mod's channels. Fake players, test players and
     * connections without the mod are skipped instead of throwing.
     */
    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendNear(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
        double r2 = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) <= r2) {
                sendTo(player, payload);
            }
        }
    }

    private ModNetwork() {
    }
}
