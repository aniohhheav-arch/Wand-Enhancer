package dev.infinitygauntlet.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    public static final String CATEGORY = "key.categories.infinitygauntlet";

    public static final KeyMapping NEXT_ABILITY = new KeyMapping("key.infinitygauntlet.next_ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping PREVIOUS_ABILITY = new KeyMapping("key.infinitygauntlet.previous_ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping NEXT_STONE = new KeyMapping("key.infinitygauntlet.next_stone", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping ABILITY_WHEEL = new KeyMapping("key.infinitygauntlet.ability_wheel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public static final KeyMapping REMOTE_BLINK = new KeyMapping("key.infinitygauntlet.remote_blink", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping NEURAL_OVERRIDE = new KeyMapping("key.infinitygauntlet.neural_override", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping PORTAL_EXTRACTION = new KeyMapping("key.infinitygauntlet.portal_extraction", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping SWITCH_FEED = new KeyMapping("key.infinitygauntlet.switch_feed", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping EMERGENCY_DISCONNECT = new KeyMapping("key.infinitygauntlet.emergency_disconnect", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private Keybinds() {
    }
}
