package dev.telephoneorder.net;

import dev.telephoneorder.TelephoneOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** All network payloads of the mod. */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TelephoneOrder.MOD_ID, name));
    }

    // ------------------------------------------------------------- client -> server
    public record Reload() implements CustomPacketPayload {
        public static final Type<Reload> TYPE = mk("reload");
        public static final StreamCodec<RegistryFriendlyByteBuf, Reload> CODEC = StreamCodec.unit(new Reload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FireMode() implements CustomPacketPayload {
        public static final Type<FireMode> TYPE = mk("fire_mode");
        public static final StreamCodec<RegistryFriendlyByteBuf, FireMode> CODEC = StreamCodec.unit(new FireMode());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Aim(boolean aiming) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = mk("aim");
        public static final StreamCodec<RegistryFriendlyByteBuf, Aim> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Aim::aiming, Aim::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** action: dial | key | order | deposit | choice | ringtone | hangup | refresh | pickup */
    public record PhoneAction(String action, String arg) implements CustomPacketPayload {
        public static final Type<PhoneAction> TYPE = mk("phone_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, PhoneAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PhoneAction::action, ByteBufCodecs.STRING_UTF8, PhoneAction::arg, PhoneAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** action: attach | detach ; slot: optic.. ; id: attachment id */
    public record WorkbenchAction(String action, String slot, String id) implements CustomPacketPayload {
        public static final Type<WorkbenchAction> TYPE = mk("workbench_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, WorkbenchAction::action, ByteBufCodecs.STRING_UTF8, WorkbenchAction::slot,
                ByteBufCodecs.STRING_UTF8, WorkbenchAction::id, WorkbenchAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------- server -> client
    public record Recoil(float pitch, float yaw) implements CustomPacketPayload {
        public static final Type<Recoil> TYPE = mk("recoil");
        public static final StreamCodec<RegistryFriendlyByteBuf, Recoil> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Recoil::pitch, ByteBufCodecs.FLOAT, Recoil::yaw, Recoil::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Shake(float strength, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = mk("shake");
        public static final StreamCodec<RegistryFriendlyByteBuf, Shake> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Shake::strength, ByteBufCodecs.VAR_INT, Shake::ticks, Shake::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ScreenFlash(int rgb, int ticks) implements CustomPacketPayload {
        public static final Type<ScreenFlash> TYPE = mk("screen_flash");
        public static final StreamCodec<RegistryFriendlyByteBuf, ScreenFlash> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, ScreenFlash::rgb, ByteBufCodecs.VAR_INT, ScreenFlash::ticks, ScreenFlash::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** kind: 0 hit, 1 headshot, 2 kill */
    public record HitMarker(int kind) implements CustomPacketPayload {
        public static final Type<HitMarker> TYPE = mk("hit_marker");
        public static final StreamCodec<RegistryFriendlyByteBuf, HitMarker> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, HitMarker::kind, HitMarker::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** entityId -1 = no target. progress 0..100. */
    public record LockState(int entityId, int progress, boolean locked) implements CustomPacketPayload {
        public static final Type<LockState> TYPE = mk("lock_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, LockState> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, LockState::entityId, ByteBufCodecs.VAR_INT, LockState::progress, ByteBufCodecs.BOOL, LockState::locked, LockState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PhoneOpen(BlockPos pos, int model) implements CustomPacketPayload {
        public static final Type<PhoneOpen> TYPE = mk("phone_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, PhoneOpen> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PhoneOpen::pos, ByteBufCodecs.VAR_INT, PhoneOpen::model, PhoneOpen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PhoneState(CompoundTag data) implements CustomPacketPayload {
        public static final Type<PhoneState> TYPE = mk("phone_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, PhoneState> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, PhoneState::data, PhoneState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PhoneClose() implements CustomPacketPayload {
        public static final Type<PhoneClose> TYPE = mk("phone_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, PhoneClose> CODEC = StreamCodec.unit(new PhoneClose());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WorkbenchOpen(BlockPos pos) implements CustomPacketPayload {
        public static final Type<WorkbenchOpen> TYPE = mk("workbench_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchOpen> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, WorkbenchOpen::pos, WorkbenchOpen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** a short toast / chat-style notification shown on the HUD */
    public record Notify(String text, int color) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = mk("notify");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Notify::text, ByteBufCodecs.INT, Notify::color, Notify::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        var c2s = net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay();
        c2s.register(Reload.TYPE, Reload.CODEC);
        c2s.register(FireMode.TYPE, FireMode.CODEC);
        c2s.register(Aim.TYPE, Aim.CODEC);
        c2s.register(PhoneAction.TYPE, PhoneAction.CODEC);
        c2s.register(WorkbenchAction.TYPE, WorkbenchAction.CODEC);
        var s2c = net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay();
        s2c.register(Recoil.TYPE, Recoil.CODEC);
        s2c.register(Shake.TYPE, Shake.CODEC);
        s2c.register(ScreenFlash.TYPE, ScreenFlash.CODEC);
        s2c.register(HitMarker.TYPE, HitMarker.CODEC);
        s2c.register(LockState.TYPE, LockState.CODEC);
        s2c.register(PhoneOpen.TYPE, PhoneOpen.CODEC);
        s2c.register(PhoneState.TYPE, PhoneState.CODEC);
        s2c.register(PhoneClose.TYPE, PhoneClose.CODEC);
        s2c.register(WorkbenchOpen.TYPE, WorkbenchOpen.CODEC);
        s2c.register(Notify.TYPE, Notify.CODEC);
    }
}
