package dev.arcana.client;

import dev.arcana.magic.Spell;

/** Latest server snapshot plus client-only presentation state (cinematics, smoothing). */
public final class ClientState {
    public static float mana, max = 100, shownMana;
    public static int[] xp = new int[8];
    public static int[] cooldowns = new int[Spell.values().length];
    public static int[] readyFlash = new int[Spell.values().length];
    public static int recentMask;
    public static int tick;
    private static int syncTick;

    public static int cinematicRgb, cinematicStart = -1000, cinematicTicks, cinematicKind, cinematicSpell = -1, cinematicCaster = -1;

    public static void applySync(float m, float mx, int[] x, int[] cd, int mask) {
        mana = m;
        max = mx;
        xp = x;
        if (cd.length == cooldowns.length) {
            for (int i = 0; i < cd.length; i++) if (cooldowns[i] - (tick - syncTick) > 0 && cd[i] == 0) readyFlash[i] = 12;
            cooldowns = cd;
        }
        syncTick = tick;
        recentMask = mask;
    }

    public static int cooldownLeft(Spell s) {
        return Math.max(0, cooldowns[s.ordinal()] - (tick - syncTick));
    }

    public static void startCinematic(int rgb, int ticks, int kind, int spell, int caster) {
        cinematicRgb = rgb;
        cinematicTicks = ticks;
        cinematicStart = tick;
        cinematicKind = kind;
        cinematicSpell = spell;
        cinematicCaster = caster;
    }

    /** 0..1 progress through the active cinematic (0 when none). */
    public static float cinematicProgress(float partial) {
        float t = tick + partial - cinematicStart;
        return t < 0 || t > cinematicTicks ? 0f : t / cinematicTicks;
    }

    /** 0..1 remaining strength of the active cinematic. */
    public static float cinematicStrength(float partial) {
        float t = tick + partial - cinematicStart;
        if (t < 0 || t > cinematicTicks) return 0f;
        float x = t / cinematicTicks;
        return x < 0.12f ? x / 0.12f : 1f - (x - 0.12f) / 0.88f;
    }

    public static void clientTick() {
        tick++;
        shownMana += (mana - shownMana) * 0.18f;
        for (int i = 0; i < readyFlash.length; i++) if (readyFlash[i] > 0) readyFlash[i]--;
    }

    private ClientState() {}
}
