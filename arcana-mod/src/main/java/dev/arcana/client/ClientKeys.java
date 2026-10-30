package dev.arcana.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
    public static final String CATEGORY = "key.categories.arcana";
    public static final KeyMapping SECONDARY = new KeyMapping("key.arcana.secondary", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SPECIAL = new KeyMapping("key.arcana.special", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping ULTIMATE = new KeyMapping("key.arcana.ultimate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping WHEEL = new KeyMapping("key.arcana.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping MENU = new KeyMapping("key.arcana.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private ClientKeys() {}
}
