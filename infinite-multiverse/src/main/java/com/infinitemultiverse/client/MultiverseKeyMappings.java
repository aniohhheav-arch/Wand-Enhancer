package com.infinitemultiverse.client;

import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/** All key bindings live under one "Infinite Multiverse" category in Options → Controls and are fully rebindable. */
public final class MultiverseKeyMappings {
    public static final String CATEGORY = "key.categories.infinitemultiverse";

    private static final int[] DEFAULT_SLOT_KEYS = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_G};

    public static final Lazy<KeyMapping> OPEN_MENU = Lazy.of(() -> new KeyMapping(
            "key.infinitemultiverse.open_menu", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

    public static final Lazy<KeyMapping> TOGGLE_STAND = Lazy.of(() -> new KeyMapping(
            "key.infinitemultiverse.toggle_stand", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

    public static final List<Lazy<KeyMapping>> ABILITY_SLOTS = IntStream.range(0, PlayerMultiverseData.LOADOUT_SIZE)
            .mapToObj(slot -> Lazy.of(() -> new KeyMapping(
                    "key.infinitemultiverse.ability_slot_" + (slot + 1), KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, DEFAULT_SLOT_KEYS[slot], CATEGORY)))
            .toList();

    private MultiverseKeyMappings() {
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU.get());
        event.register(TOGGLE_STAND.get());
        ABILITY_SLOTS.forEach(key -> event.register(key.get()));
    }
}
