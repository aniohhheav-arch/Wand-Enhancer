package com.wandcraft.magic;

import net.minecraft.network.chat.Component;

/** Immutable spell definition. Behaviour is supplied as lambdas so groups of spells stay compact. */
public final class Spell {
    /** Returns the number of targets successfully affected (mastery credit), or -1 if the cast failed and should be free. */
    @FunctionalInterface public interface Cast { int cast(CastCtx c); }
    /** Called every server tick while channelling. Return -1 to stop the channel. */
    @FunctionalInterface public interface Channel { int tick(CastCtx c, int ticks); }
    @FunctionalInterface public interface End { void end(CastCtx c); }

    public final String id;
    public final String name;
    public final Category category;
    public final CastMode mode;
    public String desc = "";
    public Glyph icon;
    /** Mana per cast (per second for channels). */
    public int cost = 10;
    public int cooldown = 20;
    /** Ticks to full charge for CHARGE spells. */
    public int chargeTicks = 20;
    /** Wizard level required. Ignored when {@link #evolvesFrom} is set. */
    public int level = 0;
    /** Spell id whose Master rank unlocks this spell. */
    public String evolvesFrom;
    public float healthCost = 0;
    /** Eligible for Spell Echo. */
    public boolean echoable = false;
    /** Casting this does not end stealth (the stealth spells themselves, utility). */
    public boolean stealthSafe = false;
    /** While this returns true the spell can be recast without waiting for its cooldown, for {@link #recastCost} mana. */
    public java.util.function.Predicate<net.minecraft.world.entity.player.Player> recast;
    public int recastCost;
    /** Optional per-few-ticks hook while a CHARGE spell is being held (e.g. warnings to bystanders). */
    public java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, Float> chargeFx;
    public Cast cast;
    public Channel channel;
    public End end;
    public ProjBehavior proj;
    public ZoneBehavior zone;
    /** Short rank-up flavour lines: [adept, master]. */
    public String adeptPerk = "Costs 10% less mana and hits 10% harder.";
    public String masterPerk = "Costs 15% less mana and hits 25% harder.";

    private Spell(String id, String name, Category category, CastMode mode) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.mode = mode;
        this.icon = category.icon;
    }

    public static Spell of(String id, String name, Category cat, CastMode mode) { return new Spell(id, name, cat, mode); }

    public Spell desc(String d) { desc = d; return this; }
    public Spell icon(Glyph g) { icon = g; return this; }
    public Spell cost(int c) { cost = c; return this; }
    public Spell cd(int ticks) { cooldown = ticks; return this; }
    public Spell charge(int ticks) { chargeTicks = ticks; return this; }
    public Spell level(int l) { level = l; return this; }
    public Spell evolvesFrom(String parent) { evolvesFrom = parent; return this; }
    public Spell health(float h) { healthCost = h; return this; }
    public Spell echoable() { echoable = true; return this; }
    public Spell stealthSafe() { stealthSafe = true; return this; }
    public Spell perks(String adept, String master) { adeptPerk = adept; masterPerk = master; return this; }
    public Spell recast(java.util.function.Predicate<net.minecraft.world.entity.player.Player> when, int mana) { recast = when; recastCost = mana; return this; }
    public Spell chargeFx(java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, Float> f) { chargeFx = f; return this; }
    public Spell onCast(Cast c) { cast = c; return this; }
    public Spell onChannel(Channel c) { channel = c; return this; }
    public Spell onEnd(End e) { end = e; return this; }
    public Spell projectile(ProjBehavior b) { proj = b; return this; }
    public Spell zone(ZoneBehavior b) { zone = b; return this; }

    public Component title() { return Component.literal(name); }
}
