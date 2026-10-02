package dev.mysticarts.network;

import dev.mysticarts.client.ClientNetwork;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MaNetwork {
    private MaNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Payloads.Cast.TYPE, Payloads.Cast.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PowerManager.handleCast(sp, p.ability(), p.phase());
        });
        r.playToServer(Payloads.Select.TYPE, Payloads.Select.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PowerManager.handleSelect(sp, p.ability());
        });
        r.playToServer(Payloads.ArtifactAction.TYPE, Payloads.ArtifactAction.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) artifactAction(sp, p);
        });
        r.playToClient(Payloads.PowerSync.TYPE, Payloads.PowerSync.CODEC, (p, ctx) -> ClientNetwork.onPowerSync(p));
        r.playToClient(Payloads.CasterState.TYPE, Payloads.CasterState.CODEC, (p, ctx) -> ClientNetwork.onCasterState(p));
        r.playToClient(Payloads.Fx.TYPE, Payloads.Fx.CODEC, (p, ctx) -> ClientNetwork.onFx(p));
        r.playToClient(Payloads.Marks.TYPE, Payloads.Marks.CODEC, (p, ctx) -> ClientNetwork.onMarks(p));
    }

    private static void artifactAction(ServerPlayer sp, Payloads.ArtifactAction p) {
        PowerData d = PowerManager.data(sp);
        if (p.action() == Payloads.ArtifactAction.UNBIND) {
            int slot = p.slot();
            if (slot < 0 || slot >= Artifact.values().length) return;
            ItemStack stack = d.artifacts[slot];
            if (stack.isEmpty()) return;
            d.artifacts[slot] = ItemStack.EMPTY;
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        } else if (p.action() == Payloads.ArtifactAction.TOGGLE_FLIGHT) {
            d.cloakFlight = !d.cloakFlight;
        }
        d.dirty = true;
        d.casterDirty = true;
        PowerManager.sync(sp, d);
    }
}
