package dev.infinitygauntlet.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent per-player gauntlet state: cooldowns (absolute game time), the half-built portal pair,
 * and the Space Stone's markers.
 */
public final class PlayerPowerData {
    public static final Codec<PlayerPowerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns),
            UUIDUtil.CODEC.optionalFieldOf("pending_portal").forGetter(d -> Optional.ofNullable(d.pendingPortal)),
            GlobalPos.CODEC.optionalFieldOf("swap_marker").forGetter(d -> Optional.ofNullable(d.swapMarker)),
            UUIDUtil.CODEC.optionalFieldOf("swap_entity").forGetter(d -> Optional.ofNullable(d.swapEntity)),
            GlobalPos.CODEC.optionalFieldOf("waypoint").forGetter(d -> Optional.ofNullable(d.waypoint)),
            GlobalPos.CODEC.optionalFieldOf("nexus").forGetter(d -> Optional.ofNullable(d.nexus))
    ).apply(i, (cd, portal, marker, entity, waypoint, nexus) -> {
        PlayerPowerData d = new PlayerPowerData();
        d.cooldowns.putAll(cd);
        d.pendingPortal = portal.orElse(null);
        d.swapMarker = marker.orElse(null);
        d.swapEntity = entity.orElse(null);
        d.waypoint = waypoint.orElse(null);
        d.nexus = nexus.orElse(null);
        return d;
    }));

    private final Map<String, Long> cooldowns = new HashMap<>();
    public UUID pendingPortal;
    public GlobalPos swapMarker;
    public UUID swapEntity;
    public GlobalPos waypoint;
    /** The Consciousness Nexus this player last bound. */
    public GlobalPos nexus;

    public long cooldownEnd(ResourceLocation ability) {
        return cooldowns.getOrDefault(ability.toString(), 0L);
    }

    public void setCooldownEnd(ResourceLocation ability, long gameTime) {
        cooldowns.put(ability.toString(), gameTime);
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    public Map<String, Long> cooldowns() {
        return cooldowns;
    }
}
