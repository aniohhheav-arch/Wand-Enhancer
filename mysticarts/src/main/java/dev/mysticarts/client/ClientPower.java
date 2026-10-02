package dev.mysticarts.client;

import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Source;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The local player's power state, mirrored from the server; cooldowns count down locally between syncs. */
public final class ClientPower {
    @Nullable private static Payloads.PowerSync sync;
    private static long syncTick;
    private static long tick;
    /** Previous ready-state per ability, to flash an icon when it comes off cooldown. */
    private static final int[] PREV = new int[Ability.values().length];
    public static final float[] READY_FLASH = new float[Ability.values().length];

    private ClientPower() {}

    public static void update(Payloads.PowerSync p) {
        sync = p;
        syncTick = tick;
    }

    public static void reset() {
        sync = null;
    }

    public static void tick() {
        tick++;
        for (int i = 0; i < READY_FLASH.length; i++) {
            READY_FLASH[i] = Math.max(0, READY_FLASH[i] - 0.08f);
            int cd = cooldown(Ability.values()[i]);
            if (PREV[i] > 0 && cd == 0) READY_FLASH[i] = 1f;
            PREV[i] = cd;
        }
    }

    public static boolean ready() {
        return sync != null;
    }

    public static float mystic() {
        return sync == null ? 0 : sync.mystic();
    }

    public static float cosmic() {
        return sync == null ? 0 : sync.cosmic();
    }

    public static float ultimate() {
        return sync == null ? 0 : sync.ultimate();
    }

    public static float maxMystic() {
        return sync == null ? 100 : sync.maxMystic();
    }

    public static float maxCosmic() {
        return sync == null ? 200 : sync.maxCosmic();
    }

    public static int souls() {
        return sync == null ? 0 : sync.souls();
    }

    public static int tiers() {
        return sync == null ? 0 : sync.tiers();
    }

    public static boolean has(int flag) {
        return sync != null && sync.has(flag);
    }

    public static int cooldown(Ability a) {
        if (sync == null || a.ordinal() >= sync.cooldowns().length) return 0;
        return (int) Math.max(0, sync.cooldowns()[a.ordinal()] - (tick - syncTick));
    }

    public static float cooldownFraction(Ability a) {
        int total = Math.max(1, dev.mysticarts.power.PowerManager.cooldownTicksClient(a));
        return Math.min(1f, cooldown(a) / (float) total);
    }

    public static Source activeSource() {
        return sync == null ? Source.MYSTIC : Source.byId(sync.activeSource());
    }

    public static Ability selected(Source s) {
        List<Ability> list = Ability.of(s);
        if (sync == null || s.ordinal() >= sync.selected().length) return list.get(0);
        return list.get(Math.floorMod(sync.selected()[s.ordinal()], list.size()));
    }

    /** Ticks charged so far for a CHARGE ability, or -1. */
    public static int charging() {
        return sync == null ? -1 : sync.charging() - 1;
    }

    public static int windupAbility() {
        return sync == null ? -1 : sync.windupAbility() - 1;
    }

    /** Optimistic local selection so the HUD updates before the server answers. */
    public static void selectLocal(Ability a) {
        if (sync == null) return;
        int[] sel = sync.selected().clone();
        sel[a.source.ordinal()] = Ability.of(a.source).indexOf(a);
        sync = new Payloads.PowerSync(sync.mystic(), sync.cosmic(), sync.ultimate(), sync.maxMystic(), sync.maxCosmic(), sync.tiers(), sync.souls(),
                a.source.ordinal(), sel, sync.cooldowns(), sync.flags(), sync.charging(), sync.windupAbility(), sync.windupTicks());
    }

    public static long clientTick() {
        return tick;
    }
}
