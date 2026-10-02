package com.infinitemultiverse.power.mastery;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.power.PowerManager;
import com.infinitemultiverse.power.PowerSet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Technique mastery (levels I–V per power set). Using a set's abilities earns experience; every level adds damage and
 * trims energy costs, level III empowers the visuals and scale of the set's techniques, and some sets unlock signature
 * upgrades (Limitless: Six Eyes → Maximum Output → Hollow Purple 200% → Domain Amplification; Shrine: World-Cutting Slash).
 */
public final class Mastery {
    public static final int MAX_LEVEL = 5;
    private static final int[] THRESHOLDS = {0, 150, 450, 1000, 2000};

    private Mastery() {
    }

    public static int levelFor(int xp) {
        int level = 1;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (xp >= THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    public static int thresholdFor(int level) {
        return THRESHOLDS[Mth.clamp(level, 1, MAX_LEVEL) - 1];
    }

    /** 0..1 progress toward the next level (1 at max). */
    public static float progress(int xp) {
        int level = levelFor(xp);
        if (level >= MAX_LEVEL) {
            return 1f;
        }
        int from = thresholdFor(level), to = thresholdFor(level + 1);
        return (xp - from) / (float) (to - from);
    }

    @Nullable
    private static PowerSet setOf(ServerPlayer player, MultiverseSystem system) {
        return PowerManager.powerOf(player, system);
    }

    public static int level(ServerPlayer player, MultiverseSystem system) {
        PowerSet set = setOf(player, system);
        return set == null ? 1 : levelFor(AbilityManager.data(player).masteryXp(set.id()));
    }

    /** Level of a specific set, or 0 if the player does not wield it. */
    public static int levelOf(ServerPlayer player, ResourceLocation set) {
        PowerSet current = setOf(player, setSystem(set));
        return current != null && current.id().equals(set) ? levelFor(AbilityManager.data(player).masteryXp(set)) : 0;
    }

    private static MultiverseSystem setSystem(ResourceLocation set) {
        PowerSet s = com.infinitemultiverse.core.registry.MultiverseRegistries.POWER_SETS.get(set);
        return s == null ? MultiverseSystem.CORE : s.system();
    }

    public static float damageMultiplier(ServerPlayer player, MultiverseSystem system) {
        return 1f + 0.12f * (level(player, system) - 1);
    }

    public static float costMultiplier(ServerPlayer player, MultiverseSystem system) {
        return 1f - 0.06f * (level(player, system) - 1);
    }

    /** Scene flag for empowered visuals (mastery III+). */
    public static int sceneFlags(ServerPlayer player, MultiverseSystem system) {
        return level(player, system) >= 3 ? Cinematics.EMPOWERED : 0;
    }

    /** Awarded after every successful activation of a power ability. */
    public static void onUsed(ServerPlayer player, Ability ability) {
        if (!(ability instanceof PowerAbility)) {
            return;
        }
        PowerSet set = setOf(player, ability.system());
        if (set == null || !set.abilities().contains(ability)) {
            return;
        }
        PlayerMultiverseData data = AbilityManager.data(player);
        int before = data.masteryXp(set.id());
        int gain = 2 + Math.round(ability.energyCost() / 4f);
        data.setMasteryXp(set.id(), before + gain);
        int oldLevel = levelFor(before), newLevel = levelFor(before + gain);
        if (newLevel > oldLevel) {
            announce(player, set, newLevel);
        }
    }

    public static void setLevel(ServerPlayer player, PowerSet set, int level) {
        AbilityManager.data(player).setMasteryXp(set.id(), thresholdFor(level));
        AbilityManager.syncNow(player);
    }

    private static void announce(ServerPlayer player, PowerSet set, int level) {
        Component name = set.displayName().copy().withStyle(style -> style.withColor(set.color()));
        player.sendSystemMessage(Component.translatable("message.infinitemultiverse.mastery_up", name, roman(level)).withStyle(ChatFormatting.GOLD));
        String perk = "mastery.infinitemultiverse." + set.id().getPath() + "." + level;
        player.sendSystemMessage(Component.translatable(perk).withStyle(ChatFormatting.YELLOW));
        Cinematics.attached(player.serverLevel(), SceneIds.AURA_STRENGTH, player, set.color(), 40, 1f);
        Cinematics.scene(player.serverLevel(), SceneIds.WAVE, player.position(), player.getLookAngle(), set.color(), 24, player, 6f);
        MultiverseVfx.sound(player.serverLevel(), player.position(), ModSounds.STAND_AWAKEN, 1f, 1.2f);
    }

    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }
}
