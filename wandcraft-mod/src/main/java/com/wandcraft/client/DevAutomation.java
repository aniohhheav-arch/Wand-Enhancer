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
    static final boolean SHOW = Boolean.getBoolean("wandcraft.show");
    static final boolean PROPS = Boolean.getBoolean("wandcraft.props");
    static final boolean CAST_ALL = Boolean.getBoolean("wandcraft.castall");
    private static int tick;

    private DevAutomation() {}

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        tick++;
        if (SHOW) {
            showcase(mc);
            return;
        }
        if (PROPS) {
            gallery(mc);
            return;
        }
        if (CAST_ALL) {
            castAll(mc);
            return;
        }
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
            case 438 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 446 -> shot(mc, "09a_clone_third_person");
            case 449 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
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

    /** Casts every spell in turn, holding the button briefly, to shake out client-side effect crashes. */
    private static void castAll(Minecraft mc) {
        if (tick == 80) {
            cmd(mc, "gamemode creative");
            cmd(mc, "wandcraft level 30");
            cmd(mc, "wandcraft master all");
            cmd(mc, "give @s wandcraft:wand");
            cmd(mc, "tp @s 0 -60 0 0 4");
            cmd(mc, "summon zombie 0 -60 9 {NoAI:1b,PersistenceRequired:1b}");
            cmd(mc, "summon zombie 3 -60 11 {NoAI:1b,PersistenceRequired:1b}");
        }
        if (tick < 140) return;
        if (tick == 140) mc.player.getInventory().selected = 0;
        var all = new java.util.ArrayList<>(Spells.all());
        int idx = (tick - 150) / 50, phase = (tick - 150) % 50;
        if (tick < 150) return;
        if (idx >= all.size()) {
            System.out.println("WANDCRAFT castall finished without a crash: " + all.size());
            mc.stop();
            return;
        }
        var s = all.get(idx);
        if (phase == 0) { SpellView.select(s); System.out.println("WANDCRAFT castall " + s.id); }
        if (phase == 3) mc.options.keyUse.setDown(true);
        if (phase == 14) mc.options.keyUse.setDown(false);
    }

    /** Spawns each prop kind in front of the camera and screenshots it. */
    private static void gallery(Minecraft mc) {
        var kinds = new java.util.ArrayList<String>();
        com.wandcraft.entity.Props.ids().forEach(kinds::add);
        if (tick == 80) {
            cmd(mc, "gamemode creative");
            cmd(mc, "time set noon");
            cmd(mc, "tp @s 0 -60 0 0 8");
            cmd(mc, "gamerule doDaylightCycle false");
        }
        if (tick < 100) return;
        int idx = (tick - 100) / 50, phase = (tick - 100) % 50;
        if (idx >= kinds.size()) {
            mc.stop();
            return;
        }
        String k = kinds.get(idx);
        if (phase == 0) {
            cmd(mc, "kill @e[type=wandcraft:spell_prop]");
            cmd(mc, "wandcraft prop " + k);
        }
        if (phase == 34) shot(mc, String.format("prop_%02d_%s", idx, k));
    }

    private static final String[] SHOWCASE = System.getProperty("wandcraft.showlist") != null ? System.getProperty("wandcraft.showlist").split(",") : new String[]{"guardian_vine", "portal_pair", "black_hole", "cataclysm", "mirror_ward", "magic_shield", "aegis_dome",
            "phantom_wall", "sanctuary", "gravity_well", "time_fracture", "reality_tear", "spectral_legion", "mirror_image", "dimensional_slash",
            "root_snare", "healing_bloom", "firestorm", "sand_vortex", "void_rift", "void_collapse", "radiant_lance", "beacon", "phantom_double"};

    /** Casts a curated set of prop-heavy spells and screenshots each one in action. */
    private static void showcase(Minecraft mc) {
        if (tick == 80) {
            cmd(mc, "gamemode creative");
            cmd(mc, "wandcraft level 30");
            cmd(mc, "wandcraft master all");
            cmd(mc, "give @s wandcraft:wand");
            cmd(mc, "time set noon");
            cmd(mc, "gamerule doDaylightCycle false");
        }
        if (tick < 120) return;
        if (tick == 120) mc.player.getInventory().selected = 0;
        int per = 270;
        int idx = (tick - 130) / per, phase = (tick - 130) % per;
        if (tick < 130) return;
        if (idx >= SHOWCASE.length) { mc.stop(); return; }
        var spell = Spells.get(SHOWCASE[idx]);
        if (phase == 0) {
            cmd(mc, "tp @s 0 -60 0 0 6");
            cmd(mc, "kill @e[type=!player]");
            cmd(mc, "summon zombie 0 -60 9 {NoAI:1b,PersistenceRequired:1b}");
            cmd(mc, "summon zombie 3 -60 11 {NoAI:1b,PersistenceRequired:1b}");
            cmd(mc, "summon zombie -3 -60 10 {NoAI:1b,PersistenceRequired:1b}");
            cmd(mc, "summon zombie 1 -60 13 {NoAI:1b,PersistenceRequired:1b}");
        }
        if (phase == 20) SpellView.select(spell);
        int hold = spell.mode == com.wandcraft.magic.CastMode.INSTANT ? 3 : spell.mode == com.wandcraft.magic.CastMode.CHARGE ? spell.chargeTicks + 3 : 40;
        if (phase == 30) mc.options.keyUse.setDown(true);
        if (phase == 30 + hold) mc.options.keyUse.setDown(false);
        int shotAt = spell.mode == com.wandcraft.magic.CastMode.CHANNEL ? 30 + hold - 4 : 30 + hold + (spell.id.equals("cataclysm") ? 105 : spell.id.equals("void_collapse") ? 35 : 22);
        if (phase == Math.min(shotAt, per - 2)) shot(mc, String.format("show_%02d_%s", idx, spell.id));
    }
}
