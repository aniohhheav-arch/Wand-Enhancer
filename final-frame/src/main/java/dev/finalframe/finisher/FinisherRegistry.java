package dev.finalframe.finisher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Registry of finisher definitions. Future finisher weapons add themselves here. */
public final class FinisherRegistry {
    private static final Map<ResourceLocation, FinisherDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private FinisherRegistry() {
    }

    public static void register(FinisherDefinition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate finisher " + definition.id());
        }
    }

    public static FinisherDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }
}
