package dev.riftverse.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.riftverse.network.Payloads;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final KeyMapping ABILITY = new KeyMapping("key.riftverse.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.riftverse");

    private KeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ABILITY);
    }

    public static void tick() {
        while (ABILITY.consumeClick()) {
            PacketDistributor.sendToServer(new Payloads.Ability(Payloads.Ability.ARMOR_SET));
        }
    }
}
