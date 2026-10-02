package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → owning client: full snapshot of the player's multiverse state for the HUD and menu. */
public record SyncPlayerDataPayload(
        float[] energies,
        float maxEnergy,
        Map<ResourceLocation, Integer> cooldowns,
        List<Optional<ResourceLocation>> loadout,
        Set<ResourceLocation> active,
        Set<ResourceLocation> unlocked,
        Optional<ResourceLocation> standType,
        Map<String, ResourceLocation> powers
) implements CustomPacketPayload {
    public static final Type<SyncPlayerDataPayload> TYPE = new Type<>(InfiniteMultiverse.id("sync_player_data"));
    public static final StreamCodec<FriendlyByteBuf, SyncPlayerDataPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncPlayerDataPayload::write, SyncPlayerDataPayload::read);

    public static SyncPlayerDataPayload of(PlayerMultiverseData data, float maxEnergy) {
        return new SyncPlayerDataPayload(
                clampNonNegative(data.energiesView()),
                maxEnergy,
                Map.copyOf(data.cooldownsView()),
                data.loadoutView(),
                Set.copyOf(data.activeView()),
                Set.copyOf(data.unlockedView()),
                data.standType(),
                Map.copyOf(data.powersView()));
    }

    private static float[] clampNonNegative(float[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.max(0f, values[i]);
        }
        return values;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(energies.length);
        for (float value : energies) {
            buf.writeFloat(value);
        }
        buf.writeFloat(maxEnergy);
        buf.writeVarInt(cooldowns.size());
        cooldowns.forEach((id, ticks) -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(ticks);
        });
        buf.writeVarInt(loadout.size());
        for (Optional<ResourceLocation> slot : loadout) {
            buf.writeBoolean(slot.isPresent());
            slot.ifPresent(buf::writeResourceLocation);
        }
        writeIds(buf, active);
        writeIds(buf, unlocked);
        buf.writeBoolean(standType.isPresent());
        standType.ifPresent(buf::writeResourceLocation);
        buf.writeVarInt(powers.size());
        powers.forEach((system, set) -> {
            buf.writeUtf(system);
            buf.writeResourceLocation(set);
        });
    }

    private static SyncPlayerDataPayload read(FriendlyByteBuf buf) {
        float[] energies = new float[buf.readVarInt()];
        for (int i = 0; i < energies.length; i++) {
            energies[i] = buf.readFloat();
        }
        float maxEnergy = buf.readFloat();
        int cooldownCount = buf.readVarInt();
        Map<ResourceLocation, Integer> cooldowns = new HashMap<>(cooldownCount);
        for (int i = 0; i < cooldownCount; i++) {
            cooldowns.put(buf.readResourceLocation(), buf.readVarInt());
        }
        int slotCount = buf.readVarInt();
        List<Optional<ResourceLocation>> loadout = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            loadout.add(buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty());
        }
        Set<ResourceLocation> active = readIds(buf);
        Set<ResourceLocation> unlocked = readIds(buf);
        Optional<ResourceLocation> standType = buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty();
        int powerCount = buf.readVarInt();
        Map<String, ResourceLocation> powers = new HashMap<>(powerCount);
        for (int i = 0; i < powerCount; i++) {
            powers.put(buf.readUtf(), buf.readResourceLocation());
        }
        return new SyncPlayerDataPayload(energies, maxEnergy, cooldowns, loadout, active, unlocked, standType, powers);
    }

    private static void writeIds(FriendlyByteBuf buf, Set<ResourceLocation> ids) {
        buf.writeVarInt(ids.size());
        for (ResourceLocation id : ids) {
            buf.writeResourceLocation(id);
        }
    }

    private static Set<ResourceLocation> readIds(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Set<ResourceLocation> ids = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readResourceLocation());
        }
        return ids;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
