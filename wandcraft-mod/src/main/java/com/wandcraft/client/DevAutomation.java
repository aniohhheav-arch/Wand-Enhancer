package com.wandcraft.client;

import com.wandcraft.magic.Spells;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Developer-only scripted run (enabled with -Dwandcraft.dev=true) that joins a world, shows the wand, fires a few
 * spells and saves screenshots to run/screenshots. Never active in normal play.
 */
final class DevAutomation {
    static final boolean ON = Boolean.getBoolean("wandcraft.dev");
    private static int tick;

    private DevAutomation() {}

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        tick++;
        switch (tick) {
            case 80 -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "wandcraft level 30");
                cmd(mc, "wandcraft master all");
                cmd(mc, "give @s wandcraft:wand");
                cmd(mc, "time set noon");
                cmd(mc, "tp @s 0 -60 0 0 4");
                cmd(mc, "summon zombie 0 -60 9 {NoAI:1b,PersistenceRequired:1b}");
                cmd(mc, "summon zombie 2 -60 11 {NoAI:1b,PersistenceRequired:1b}");
                cmd(mc, "summon zombie -2 -60 12 {NoAI:1b,PersistenceRequired:1b}");
            }
            case 130 -> { mc.player.getInventory().selected = 0; mc.options.hideGui = false; }
            case 150 -> shot(mc, "01_wand_first_person");
            case 160 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 175 -> shot(mc, "02_wand_third_person");
            case 180 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); SpellView.select(Spells.get("arcane_beam")); }
            case 190 -> mc.options.keyUse.setDown(true);
            case 230 -> shot(mc, "03_beam_first_person");
            case 235 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 255 -> shot(mc, "04_beam_third_person");
            case 260 -> { mc.options.keyUse.setDown(false); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 275 -> SpellView.select(Spells.get("seismic_wave"));
            case 285 -> mc.options.keyUse.setDown(true);
            case 287 -> mc.options.keyUse.setDown(false);
            case 296 -> shot(mc, "05_seismic_a");
            case 304 -> shot(mc, "06_seismic_b");
            case 330 -> mc.setScreen(new WheelScreen());
            case 350 -> shot(mc, "07_wheel");
            case 355 -> mc.setScreen(null);
            case 360 -> SpellView.select(Spells.get("firestorm"));
            case 365 -> mc.options.keyUse.setDown(true);
            case 385 -> mc.options.keyUse.setDown(false);
            case 420 -> shot(mc, "08_firestorm");
            case 430 -> SpellView.select(Spells.get("shadow_clone"));
            case 432 -> mc.options.keyUse.setDown(true);
            case 434 -> mc.options.keyUse.setDown(false);
            case 450 -> SpellView.select(Spells.get("eclipsing_beam"));
            case 455 -> mc.options.keyUse.setDown(true);
            case 480 -> shot(mc, "09_eclipsing_beam");
            case 485 -> mc.options.keyUse.setDown(false);
            case 495 -> SpellView.select(Spells.get("dark_nova"));
            case 500 -> mc.options.keyUse.setDown(true);
            case 530 -> shot(mc, "10_dark_nova_charge");
            case 533 -> mc.options.keyUse.setDown(false);
            case 538 -> shot(mc, "11_dark_nova_blast");
            case 560 -> mc.stop();
            default -> {}
        }
    }
}
