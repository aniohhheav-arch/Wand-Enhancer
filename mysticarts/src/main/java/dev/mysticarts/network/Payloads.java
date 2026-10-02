package dev.mysticarts.network;

import dev.mysticarts.MysticArts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Every Mystic Arts network payload. */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(MysticArts.id(name));
    }

    // ============================================================================================ client -> server

    /** An ability key was pressed (phase 0) or released (phase 1). */
    public record Cast(int ability, int phase) implements CustomPacketPayload {
        public static final int PRESS = 0;
        public static final int RELEASE = 1;

        public static final Type<Cast> TYPE = payloadType("cast");
        public static final StreamCodec<FriendlyByteBuf, Cast> CODEC = CustomPacketPayload.<FriendlyByteBuf, Cast>codec(Cast::write, Cast::new);

        Cast(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(ability);
            buf.writeVarInt(phase);
        }

        @Override
        public Type<Cast> type() {
            return TYPE;
        }
    }

    /** The player picked an ability in the radial menu. */
    public record Select(int ability) implements CustomPacketPayload {
        public static final Type<Select> TYPE = payloadType("select");
        public static final StreamCodec<FriendlyByteBuf, Select> CODEC = CustomPacketPayload.<FriendlyByteBuf, Select>codec(Select::write, Select::new);

        Select(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(ability);
        }

        @Override
        public Type<Select> type() {
            return TYPE;
        }
    }

    /** Artifacts screen: 0 = unbind slot, 1 = toggle cloak flight. */
    public record ArtifactAction(int action, int slot) implements CustomPacketPayload {
        public static final int UNBIND = 0;
        public static final int TOGGLE_FLIGHT = 1;

        public static final Type<ArtifactAction> TYPE = payloadType("artifact_action");
        public static final StreamCodec<FriendlyByteBuf, ArtifactAction> CODEC = CustomPacketPayload.<FriendlyByteBuf, ArtifactAction>codec(ArtifactAction::write, ArtifactAction::new);

        ArtifactAction(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeVarInt(slot);
        }

        @Override
        public Type<ArtifactAction> type() {
            return TYPE;
        }
    }

    // ============================================================================================ server -> client

    /** The local player's own meters, cooldowns and selections. */
    public record PowerSync(float mystic, float cosmic, float ultimate, float maxMystic, float maxCosmic, int tiers, int souls, int activeSource,
                            int[] selected, int[] cooldowns, int flags, int charging, int windupAbility, int windupTicks) implements CustomPacketPayload {
        public static final int F_SHIELD = 1;
        public static final int F_DOME = 2;
        public static final int F_ASTRAL = 4;
        public static final int F_ABSORB = 8;
        public static final int F_KINETIC = 16;
        public static final int F_SUPERCHARGE = 32;
        public static final int F_DEFLECT = 64;
        public static final int F_ASTRAL_FORM = 128;
        public static final int F_MIRROR = 256;
        public static final int F_UNLIMITED = 512;
        public static final int F_CLOAK_FLIGHT = 1024;
        public static final int F_SLING_ANCHOR = 2048;
        public static final int F_SPACE_ANCHOR = 4096;
        public static final int F_REALITY_ANCHOR = 8192;
        public static final int F_SAVED_STATE = 16384;

        public static final Type<PowerSync> TYPE = payloadType("power_sync");
        public static final StreamCodec<FriendlyByteBuf, PowerSync> CODEC = CustomPacketPayload.<FriendlyByteBuf, PowerSync>codec(PowerSync::write, PowerSync::new);

        PowerSync(FriendlyByteBuf buf) {
            this(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(64), buf.readVarIntArray(512), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeFloat(mystic);
            buf.writeFloat(cosmic);
            buf.writeFloat(ultimate);
            buf.writeFloat(maxMystic);
            buf.writeFloat(maxCosmic);
            buf.writeVarInt(tiers);
            buf.writeVarInt(souls);
            buf.writeVarInt(activeSource);
            buf.writeVarIntArray(selected);
            buf.writeVarIntArray(cooldowns);
            buf.writeVarInt(flags);
            buf.writeVarInt(charging + 1);
            buf.writeVarInt(windupAbility + 1);
            buf.writeVarInt(windupTicks);
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        @Override
        public Type<PowerSync> type() {
            return TYPE;
        }
    }

    /**
     * What everyone watching a caster needs to draw them: shield, whip/telekinesis target, beam, casting pose and bound
     * artifacts (bit per {@code Artifact} ordinal).
     */
    public record CasterState(int entity, int shieldMode, int held, int holdKind, int beam, int pose, int poseTicks, int artifacts, int flags)
            implements CustomPacketPayload {
        public static final int F_FLYING = 1;
        public static final int F_ASTRAL = 2;
        public static final int F_ASTRAL_FORM = 4;
        public static final int F_CHARGING = 8;
        public static final int F_GAUNTLET_GLOW = 16;
        public static final int F_ABSORB = 32;
        public static final int F_KINETIC = 64;
        public static final int F_DEFLECT = 128;

        public static final Type<CasterState> TYPE = payloadType("caster_state");
        public static final StreamCodec<FriendlyByteBuf, CasterState> CODEC = CustomPacketPayload.<FriendlyByteBuf, CasterState>codec(CasterState::write, CasterState::new);

        CasterState(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeVarInt(shieldMode);
            buf.writeVarInt(held + 1);
            buf.writeVarInt(holdKind);
            buf.writeVarInt(beam + 1);
            buf.writeVarInt(pose);
            buf.writeVarInt(poseTicks);
            buf.writeVarInt(artifacts);
            buf.writeVarInt(flags);
        }

        @Override
        public Type<CasterState> type() {
            return TYPE;
        }
    }

    /** One-shot visual/audio event. Meaning of the vector, colour and parameters depends on {@code kind} (see FxKind). */
    public record Fx(int kind, double x, double y, double z, float dx, float dy, float dz, int color, float a, float b, int entity)
            implements CustomPacketPayload {
        public static final Type<Fx> TYPE = payloadType("fx");
        public static final StreamCodec<FriendlyByteBuf, Fx> CODEC = CustomPacketPayload.<FriendlyByteBuf, Fx>codec(Fx::write, Fx::new);

        Fx(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(dx);
            buf.writeFloat(dy);
            buf.writeFloat(dz);
            buf.writeInt(color);
            buf.writeFloat(a);
            buf.writeFloat(b);
            buf.writeVarInt(entity + 1);
        }

        @Override
        public Type<Fx> type() {
            return TYPE;
        }
    }

    /** Per-entity status marks in the player's dimension (time freeze, slow, binding, control...). Full snapshot. */
    public record Marks(List<Entry> entries) implements CustomPacketPayload {
        public record Entry(int entity, int kind, int ticks, int param) {}

        public static final Type<Marks> TYPE = payloadType("marks");
        public static final StreamCodec<FriendlyByteBuf, Marks> CODEC = CustomPacketPayload.<FriendlyByteBuf, Marks>codec(Marks::write, Marks::new);

        Marks(FriendlyByteBuf buf) {
            this(readEntries(buf));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Entry e : entries) {
                buf.writeVarInt(e.entity);
                buf.writeVarInt(e.kind);
                buf.writeVarInt(e.ticks);
                buf.writeVarInt(e.param);
            }
        }

        private static List<Entry> readEntries(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 4096);
            List<Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            return list;
        }

        @Override
        public Type<Marks> type() {
            return TYPE;
        }
    }
}
