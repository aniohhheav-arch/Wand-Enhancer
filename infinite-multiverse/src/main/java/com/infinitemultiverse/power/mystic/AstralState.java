package com.infinitemultiverse.power.mystic;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Who is currently projecting. Server: a persistent-data tag. Client: the local player's synced active toggle. */
public final class AstralState {
    public static final String TAG = "infinitemultiverse_astral";
    public static final ResourceLocation ABILITY = InfiniteMultiverse.id("astral_projection");
    /** Set by the client from synced data; only meaningful for the local player. */
    public static volatile boolean localProjecting;

    private AstralState() {
    }

    public static boolean isProjecting(Player player) {
        if (player.level().isClientSide) {
            return player.isLocalPlayer() && localProjecting;
        }
        return player.getPersistentData().contains(TAG);
    }
}
