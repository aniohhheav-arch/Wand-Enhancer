package dev.finalframe.client.choreo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Maps finisher ids to their client presentation. */
public final class Choreographies {
    private static final Map<ResourceLocation, Choreography> BY_ID = new ConcurrentHashMap<>();

    private Choreographies() {
    }

    public static void register(ResourceLocation finisherId, Choreography choreography) {
        BY_ID.put(finisherId, choreography);
    }

    public static Choreography get(ResourceLocation finisherId) {
        return BY_ID.get(finisherId);
    }
}
