package dev.telephoneorder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/** Registers one sound event for every generated .ogg listed in the catalog. */
public final class ModSounds {
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();

    private ModSounds() {}

    public static void register() {
        for (String name : Catalog.SOUNDS) {
            Identifier id = Identifier.fromNamespaceAndPath(TelephoneOrder.MOD_ID, name);
            EVENTS.put(name, Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
        }
    }

    public static SoundEvent get(String name) {
        SoundEvent e = EVENTS.get(name);
        if (e == null) throw new IllegalArgumentException("Unknown Telephone Order sound: " + name);
        return e;
    }

    public static boolean has(String name) { return EVENTS.containsKey(name); }
}
