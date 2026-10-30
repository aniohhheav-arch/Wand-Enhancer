package dev.infinitygauntlet.client.nexus;

import dev.infinitygauntlet.client.Keybinds;
import dev.infinitygauntlet.nexus.ControlProfiles;
import dev.infinitygauntlet.nexus.NexusBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Operator HUD while at the Nexus controls: link banner, control hints and instability warnings. */
public final class RemoteControlHud {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!RemoteControlClient.isControlling() || mc.level == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(true);
        Entity mob = mc.level.getEntity(RemoteControlClient.activeEntity());
        NexusBlockEntity nexus = mc.level.getBlockEntity(RemoteControlClient.nexus()) instanceof NexusBlockEntity n ? n : null;
        float stability = 1f;
        if (nexus != null && nexus.activeLink() != null) {
            stability = nexus.activeLink().stability();
        }

        if (stability < 0.3f) {
            int a = (int) ((0.25f + 0.25f * Mth.sin(t * 0.5f)) * 255);
            int c = a << 24 | 0xC01818;
            g.fill(0, 0, w, 6, c);
            g.fill(0, h - 6, w, h, c);
            g.fill(0, 0, 6, h, c);
            g.fill(w - 6, 0, w, h, c);
            Component warn = Component.translatable("hud.infinitygauntlet.remote.unstable");
            g.drawCenteredString(font, warn, w / 2, 34, (Mth.sin(t * 0.5f) > 0 ? 0xFFFF4040 : 0xFFFFC040));
        }

        String dot = ((int) (t / 10)) % 2 == 0 ? "\u25CF " : "  ";
        Component banner = Component.literal(dot).withStyle(s -> s.withColor(0xFF3030))
                .append(Component.translatable("hud.infinitygauntlet.remote.banner", mob == null ? "—" : mob.getDisplayName()).withStyle(s -> s.withColor(0xFFD84A)));
        int bw = font.width(banner) + 16;
        g.fill(w / 2 - bw / 2, 6, w / 2 + bw / 2, 22, 0xB0101420);
        g.fill(w / 2 - bw / 2, 21, w / 2 + bw / 2, 22, 0xFF2A6EFF);
        g.drawCenteredString(font, banner, w / 2, 10, 0xFFFFFFFF);

        String atk = mob == null ? "none" : ControlProfiles.attackKey(mob);
        String sp = mob == null ? "none" : ControlProfiles.specialKey(mob);
        Component line0 = Component.translatable("hud.infinitygauntlet.remote.controls");
        Component line1 = Component.translatable("hud.infinitygauntlet.remote.actions",
                Component.translatable("control.infinitygauntlet." + atk), Component.translatable("control.infinitygauntlet." + sp));
        Component line2 = Component.translatable("hud.infinitygauntlet.remote.abilities",
                Keybinds.REMOTE_BLINK.getTranslatedKeyMessage(), Keybinds.NEURAL_OVERRIDE.getTranslatedKeyMessage(),
                Keybinds.PORTAL_EXTRACTION.getTranslatedKeyMessage(), Keybinds.SWITCH_FEED.getTranslatedKeyMessage(),
                Keybinds.EMERGENCY_DISCONNECT.getTranslatedKeyMessage());
        int y = h - 74;
        int lw = Math.max(font.width(line0), Math.max(font.width(line1), font.width(line2))) + 12;
        g.fill(w / 2 - lw / 2, y - 4, w / 2 + lw / 2, y + 33, 0xA0101420);
        g.drawCenteredString(font, line0, w / 2, y, 0xFFC8D8FF);
        g.drawCenteredString(font, line1, w / 2, y + 11, 0xFFFFFFFF);
        g.drawCenteredString(font, line2, w / 2, y + 22, 0xFFFFD86A);
    }

    private RemoteControlHud() {
    }
}
