package dev.mysticarts.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mysticarts.client.screen.ArtifactsScreen;
import dev.mysticarts.client.screen.RadialMenuScreen;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Source;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** All Mystic Arts controls (rebindable under Options > Controls > Mystic Arts). */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.mysticarts";
    public static final KeyMapping CAST = key("cast", GLFW.GLFW_KEY_V);
    public static final KeyMapping RADIAL = key("radial", GLFW.GLFW_KEY_R);
    public static final KeyMapping ULTIMATE = key("ultimate", GLFW.GLFW_KEY_B);
    public static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_G);
    public static final KeyMapping SOURCE = key("source", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ARTIFACTS = key("artifacts", GLFW.GLFW_KEY_J);

    private static boolean castHeld;
    private static int castAbility = -1;

    private KeyBindings() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.mysticarts." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : new KeyMapping[] {CAST, RADIAL, ULTIMATE, NEXT, SOURCE, ARTIFACTS}) event.register(k);
    }

    /** Sources the player can draw on right now: the Mystic Arts always, stones in a held gauntlet, combinations if two or more. */
    public static List<Source> available() {
        List<Source> out = new ArrayList<>();
        out.add(Source.MYSTIC);
        int stones = InfinityGauntletItem.heldStones(Minecraft.getInstance().player);
        int count = 0;
        for (Source s : Source.STONES) {
            if ((stones & s.bit()) != 0) {
                out.add(s);
                count++;
            }
        }
        if (count >= 2) out.add(Source.COMBO);
        return out;
    }

    public static Source currentSource() {
        Source s = ClientPower.activeSource();
        return available().contains(s) ? s : Source.MYSTIC;
    }

    public static void select(Ability a) {
        ClientPower.selectLocal(a);
        PacketDistributor.sendToServer(new Payloads.Select(a.ordinal()));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen == null) {
            while (RADIAL.consumeClick()) mc.setScreen(new RadialMenuScreen());
            while (ARTIFACTS.consumeClick()) mc.setScreen(new ArtifactsScreen());
            while (NEXT.consumeClick()) {
                Source s = currentSource();
                List<Ability> list = Ability.of(s);
                Ability next = list.get((list.indexOf(ClientPower.selected(s)) + 1) % list.size());
                select(next);
                mc.player.displayClientMessage(Component.translatable(next.translationKey()).withColor(next.color()), true);
            }
            while (SOURCE.consumeClick()) {
                List<Source> av = available();
                Source next = av.get((av.indexOf(currentSource()) + 1) % av.size());
                select(ClientPower.selected(next));
                mc.player.displayClientMessage(Component.translatable("source.mysticarts." + next.id).withColor(next.color), true);
            }
            while (ULTIMATE.consumeClick()) {
                Ability ult = ultimateFor(currentSource());
                if (ult == null) mc.player.displayClientMessage(Component.translatable("message.mysticarts.no_ultimate"), true);
                else {
                    PacketDistributor.sendToServer(new Payloads.Cast(ult.ordinal(), Payloads.Cast.PRESS));
                }
            }
        }
        boolean down = CAST.isDown() && mc.screen == null;
        while (CAST.consumeClick()) {
            if (!castHeld) {
                Ability a = ClientPower.selected(currentSource());
                castAbility = a.ordinal();
                PacketDistributor.sendToServer(new Payloads.Cast(castAbility, Payloads.Cast.PRESS));
                castHeld = true;
            }
        }
        if (castHeld && !down) {
            PacketDistributor.sendToServer(new Payloads.Cast(castAbility, Payloads.Cast.RELEASE));
            castHeld = false;
        }
    }

    /** The ultimate for a source: the stone's own, the Snap for the combination wheel. */
    public static Ability ultimateFor(Source s) {
        for (Ability a : Ability.of(s)) if (a.ultimate()) return a;
        return null;
    }
}
