package dev.infinitygauntlet.ability;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of a cast.
 *
 * @param success       whether the ability did something (plays the cast animation, fires advancements)
 * @param startCooldown whether the ability's cooldown starts (utility casts such as placing a marker skip it)
 * @param message       optional action-bar feedback
 */
public record CastResult(boolean success, boolean startCooldown, @Nullable Component message) {
    public static final CastResult SUCCESS = new CastResult(true, true, null);

    public static CastResult success(Component message) {
        return new CastResult(true, true, message);
    }

    public static CastResult utility(Component message) {
        return new CastResult(true, false, message);
    }

    public static CastResult fail(Component message) {
        return new CastResult(false, false, message);
    }
}
