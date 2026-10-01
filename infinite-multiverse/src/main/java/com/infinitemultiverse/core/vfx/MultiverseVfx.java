package com.infinitemultiverse.core.vfx;

import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.network.VfxPayload;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side entry point for presentation: broadcasts effects to nearby players and plays varied positional audio. */
public final class MultiverseVfx {
    private MultiverseVfx() {
    }

    public static void broadcast(ServerLevel level, ResourceLocation effect, Vec3 origin, Vec3 vector, float scale) {
        double range = MultiverseConfig.SERVER.vfxBroadcastRange.get();
        PacketDistributor.sendToPlayersNear(level, null, origin.x, origin.y, origin.z, range,
                new VfxPayload(effect, origin, vector, scale));
    }

    public static void broadcast(ServerLevel level, ResourceLocation effect, Vec3 origin) {
        broadcast(level, effect, origin, Vec3.ZERO, 1f);
    }

    /** Plays a sound with a small random pitch spread so repeated casts never sound identical. */
    public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        float spread = (level.random.nextFloat() - 0.5f) * 0.12f;
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch + spread);
    }
}
