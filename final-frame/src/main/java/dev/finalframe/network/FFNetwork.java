package dev.finalframe.network;

import dev.finalframe.FinalFrame;
import dev.finalframe.finisher.FinisherSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Finisher session protocol. The server is authoritative; clients only replay what it announces. */
public final class FFNetwork {
    /** Installed by the client entry point; the dedicated server never needs it. */
    public static volatile ClientSink clientSink = ClientSink.NONE;

    private FFNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Start.TYPE, Start.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientSink.start(p)));
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientSink.sync(p)));
        registrar.playToClient(End.TYPE, End.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientSink.end(p)));
    }

    public interface ClientSink {
        ClientSink NONE = new ClientSink() {
        };

        default void start(Start payload) {
        }

        default void sync(Sync payload) {
        }

        default void end(End payload) {
        }
    }

    /** A finisher began (or is already {@code elapsed} ticks in, for players who start tracking late). */
    public record Start(FinisherSnapshot snapshot, int elapsed) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(FinalFrame.id("finisher_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.composite(
            FinisherSnapshot.STREAM_CODEC, Start::snapshot, ByteBufCodecs.VAR_INT, Start::elapsed, Start::new);

        @Override
        public Type<Start> type() {
            return TYPE;
        }
    }

    /** Periodic clock correction. */
    public record Sync(int sessionId, int tick) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(FinalFrame.id("finisher_sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Sync::sessionId, ByteBufCodecs.VAR_INT, Sync::tick, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** The finisher completed, or was interrupted and every client must restore gameplay now. */
    public record End(int sessionId, boolean aborted) implements CustomPacketPayload {
        public static final Type<End> TYPE = new Type<>(FinalFrame.id("finisher_end"));
        public static final StreamCodec<ByteBuf, End> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, End::sessionId, ByteBufCodecs.BOOL, End::aborted, End::new);

        @Override
        public Type<End> type() {
            return TYPE;
        }
    }
}
