package com.stickyfingers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    public static final String CATEGORY = "key.categories.stickyfingers";

    public static final KeyMapping SUMMON = key("summon", GLFW.GLFW_KEY_V);
    public static final KeyMapping WHEEL = key("wheel", GLFW.GLFW_KEY_R);
    public static final KeyMapping USE = key("use", GLFW.GLFW_KEY_G);
    public static final KeyMapping NETWORK = key("network", GLFW.GLFW_KEY_N);
    public static final KeyMapping ORIENT = key("orientation", GLFW.GLFW_KEY_Z);
    public static final KeyMapping CLOSE_ALL = key("close_all", InputConstants.UNKNOWN.getValue());
    /** Star Platinum: toggle Time Stop. Default T. */
    public static final KeyMapping TIME_STOP = key("time_stop", GLFW.GLFW_KEY_T);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.stickyfingers." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    private Keybinds() {}
}
