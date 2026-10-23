package com.wandcraft.magic;

public enum Rank {
    NOVICE("Novice", 1.0f, 1.0f, 1.0f, 0),
    ADEPT("Adept", 1.1f, 0.9f, 0.95f, 25),
    MASTER("Master", 1.25f, 0.85f, 0.9f, 100);

    public final String display;
    public final float power;
    public final float costMul;
    public final float cooldownMul;
    /** Successful uses needed to reach this rank. */
    public final int uses;

    Rank(String display, float power, float costMul, float cooldownMul, int uses) {
        this.display = display;
        this.power = power;
        this.costMul = costMul;
        this.cooldownMul = cooldownMul;
        this.uses = uses;
    }

    public static Rank forUses(int uses) {
        return uses >= MASTER.uses ? MASTER : uses >= ADEPT.uses ? ADEPT : NOVICE;
    }
}
