package com.infinitemultiverse.abilities.stand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Each Killer Queen user can hold one bomb: either a creature or a block. Transient by design. */
final class KillerQueenBombs {
    record Bomb(ResourceKey<Level> dimension, @Nullable UUID entity, @Nullable BlockPos block) {
    }

    private static final Map<UUID, Bomb> BOMBS = new HashMap<>();

    private KillerQueenBombs() {
    }

    static void set(UUID owner, Bomb bomb) {
        BOMBS.put(owner, bomb);
    }

    @Nullable
    static Bomb take(UUID owner) {
        return BOMBS.remove(owner);
    }
}
