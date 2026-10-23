package com.wandcraft.magic;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Everything a spell needs while being cast. Server side only. */
public final class CastCtx {
    public final ServerPlayer player;
    public final ServerLevel level;
    public final Spell spell;
    /** 0..1 for charge spells, 1 otherwise. */
    public final float charge;
    public final Rank rank;
    /** Rank power multiplied by external modifiers (echo, overload...). */
    public final float power;
    public final boolean echo;
    /** Scratch space that survives for the duration of a channel. */
    public final Map<String, Object> state;
    int hits;

    public CastCtx(ServerPlayer player, Spell spell, float charge, Rank rank, float power, boolean echo, Map<String, Object> state) {
        this.player = player;
        this.level = player.serverLevel();
        this.spell = spell;
        this.charge = charge;
        this.rank = rank;
        this.power = power;
        this.echo = echo;
        this.state = state == null ? new HashMap<>() : state;
    }

    public Vec3 eye() { return player.getEyePosition(); }
    public Vec3 look() { return player.getLookAngle(); }
    public float dmg(double base) { return (float) (base * power); }
    public boolean master() { return rank == Rank.MASTER; }
    public void hit() { hits++; }
    public void hit(int n) { hits += n; }
    public int hits() { return hits; }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T def) {
        Object o = state.get(key);
        return o == null ? def : (T) o;
    }
}
