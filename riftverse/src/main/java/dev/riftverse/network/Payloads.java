package dev.riftverse.network;

import dev.riftverse.Riftverse;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** All Riftverse network payloads. */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(Riftverse.id(name));
    }

    /** Server → client: which reality the player is standing in (mode 0 = vanilla, 1 = expanse universe, 2 = nexus). */
    public record UniverseSync(int mode, CompoundTag spec) implements CustomPacketPayload {
        public static final Type<UniverseSync> TYPE = payloadType("universe_sync");
        public static final StreamCodec<FriendlyByteBuf, UniverseSync> CODEC = CustomPacketPayload.<FriendlyByteBuf, UniverseSync>codec(UniverseSync::write, UniverseSync::new);

        UniverseSync(FriendlyByteBuf buf) {
            this(buf.readVarInt(), readTag(buf));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeNbt(spec);
        }

        @Override
        public Type<UniverseSync> type() {
            return TYPE;
        }
    }

    /** Server → client: begin a travel cinematic. */
    public record Cinematic(int kind, int teleportTick, double fx, double fy, double fz, int colorA, int colorB, float strength) implements CustomPacketPayload {
        public static final Type<Cinematic> TYPE = payloadType("cinematic");
        public static final StreamCodec<FriendlyByteBuf, Cinematic> CODEC = CustomPacketPayload.<FriendlyByteBuf, Cinematic>codec(Cinematic::write, Cinematic::new);

        Cinematic(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readInt(), buf.readFloat());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeVarInt(teleportTick);
            buf.writeDouble(fx);
            buf.writeDouble(fy);
            buf.writeDouble(fz);
            buf.writeInt(colorA);
            buf.writeInt(colorB);
            buf.writeFloat(strength);
        }

        @Override
        public Type<Cinematic> type() {
            return TYPE;
        }
    }

    /** Server → client: the player has been moved; play the emergence and title card. */
    public record Arrival(String title, String subtitle, int color, int kind) implements CustomPacketPayload {
        public static final Type<Arrival> TYPE = payloadType("arrival");
        public static final StreamCodec<FriendlyByteBuf, Arrival> CODEC = CustomPacketPayload.<FriendlyByteBuf, Arrival>codec(Arrival::write, Arrival::new);

        Arrival(FriendlyByteBuf buf) {
            this(buf.readUtf(256), buf.readUtf(512), buf.readInt(), buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(title, 256);
            buf.writeUtf(subtitle, 512);
            buf.writeInt(color);
            buf.writeVarInt(kind);
        }

        @Override
        public Type<Arrival> type() {
            return TYPE;
        }
    }

    /** Server → client: camera shake and optional flash. */
    public record Shake(float intensity, int duration, float flash, int color) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = payloadType("shake");
        public static final StreamCodec<FriendlyByteBuf, Shake> CODEC = CustomPacketPayload.<FriendlyByteBuf, Shake>codec(Shake::write, Shake::new);

        Shake(FriendlyByteBuf buf) {
            this(buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeFloat(intensity);
            buf.writeVarInt(duration);
            buf.writeFloat(flash);
            buf.writeInt(color);
        }

        @Override
        public Type<Shake> type() {
            return TYPE;
        }
    }

    /** Server → client: open the Multiverse browser with the player's catalogue. */
    public record OpenBrowser(BlockPos console, List<UniverseEntry> entries, int manifestsLeft) implements CustomPacketPayload {
        public static final Type<OpenBrowser> TYPE = payloadType("open_browser");
        public static final StreamCodec<FriendlyByteBuf, OpenBrowser> CODEC = CustomPacketPayload.<FriendlyByteBuf, OpenBrowser>codec(OpenBrowser::write, OpenBrowser::new);

        OpenBrowser(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), readEntries(buf), buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(console);
            buf.writeVarInt(entries.size());
            for (UniverseEntry e : entries) e.write(buf);
            buf.writeVarInt(manifestsLeft);
        }

        @Override
        public Type<OpenBrowser> type() {
            return TYPE;
        }
    }

    /** Client → server: a choice made in the Multiverse browser. */
    public record BrowserAction(BlockPos console, int action, long universe, int archetype, String prompt) implements CustomPacketPayload {
        public static final int TRAVEL_UNIVERSE = 0;
        public static final int TRAVEL_ARCHETYPE = 1;
        public static final int TRAVEL_RANDOM = 2;
        public static final int MANIFEST = 3;
        public static final int IMPRINT_KEY = 4;
        public static final int OPEN_GATE = 5;

        public static final Type<BrowserAction> TYPE = payloadType("browser_action");
        public static final StreamCodec<FriendlyByteBuf, BrowserAction> CODEC = CustomPacketPayload.<FriendlyByteBuf, BrowserAction>codec(BrowserAction::write, BrowserAction::new);

        BrowserAction(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readUtf(160));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(console);
            buf.writeVarInt(action);
            buf.writeLong(universe);
            buf.writeVarInt(archetype);
            buf.writeUtf(prompt, 160);
        }

        @Override
        public Type<BrowserAction> type() {
            return TYPE;
        }
    }

    /** Server → client: the outcome of manifesting a universe from a description. */
    public record ManifestResult(UniverseEntry entry, List<String> notes) implements CustomPacketPayload {
        public static final Type<ManifestResult> TYPE = payloadType("manifest_result");
        public static final StreamCodec<FriendlyByteBuf, ManifestResult> CODEC = CustomPacketPayload.<FriendlyByteBuf, ManifestResult>codec(ManifestResult::write, ManifestResult::new);

        ManifestResult(FriendlyByteBuf buf) {
            this(UniverseEntry.read(buf), readStrings(buf));
        }

        void write(FriendlyByteBuf buf) {
            entry.write(buf);
            buf.writeVarInt(notes.size());
            for (String s : notes) buf.writeUtf(s, 256);
        }

        @Override
        public Type<ManifestResult> type() {
            return TYPE;
        }
    }

    /** Client → server: armour / equipment ability key pressed. */
    public record Ability(int ability) implements CustomPacketPayload {
        public static final int ARMOR_SET = 0;
        public static final int DASH = 1;

        public static final Type<Ability> TYPE = payloadType("ability");
        public static final StreamCodec<FriendlyByteBuf, Ability> CODEC = CustomPacketPayload.<FriendlyByteBuf, Ability>codec(Ability::write, Ability::new);

        Ability(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(ability);
        }

        @Override
        public Type<Ability> type() {
            return TYPE;
        }
    }

    /** Server → client: a boss has arrived; frame it cinematically. */
    public record BossIntro(int entityId, String name, String subtitle, int color) implements CustomPacketPayload {
        public static final Type<BossIntro> TYPE = payloadType("boss_intro");
        public static final StreamCodec<FriendlyByteBuf, BossIntro> CODEC = CustomPacketPayload.<FriendlyByteBuf, BossIntro>codec(BossIntro::write, BossIntro::new);

        BossIntro(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readUtf(128), buf.readUtf(256), buf.readInt());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(name, 128);
            buf.writeUtf(subtitle, 256);
            buf.writeInt(color);
        }

        @Override
        public Type<BossIntro> type() {
            return TYPE;
        }
    }

    private static CompoundTag readTag(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return t == null ? new CompoundTag() : t;
    }

    private static List<UniverseEntry> readEntries(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 2048);
        List<UniverseEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(UniverseEntry.read(buf));
        return list;
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(256));
        return list;
    }
}
