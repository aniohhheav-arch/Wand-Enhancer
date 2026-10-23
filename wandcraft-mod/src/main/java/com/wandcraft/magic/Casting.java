package com.wandcraft.magic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The server-authoritative cast pipeline: validate, spend, run, cooldown, credit. */
public final class Casting {
    private Casting() {}

    /** Reason a spell cannot start now, or null. */
    /** Works on both logical sides; the client uses it only to decide whether to start charging. */
    public static String blocker(net.minecraft.world.entity.player.Player p, Spell s) {
        if (!Magic.isUnlocked(p, s)) return "Locked — " + Magic.lockReason(p, s);
        long now = p.level().getGameTime();
        boolean recast = recasting(p, s);
        if (!recast && Magic.onCooldown(p, s, now)) return s.name + " is recharging";
        if (!Magic.infinite(p)) {
            PlayerMagic m = Magic.of(p);
            float need = recast ? s.recastCost : s.mode == CastMode.CHANNEL ? Magic.cost(p, s) * 0.25f : Magic.cost(p, s);
            if (m.mana < need) return "Not enough mana";
        }
        return null;
    }

    public static boolean recasting(net.minecraft.world.entity.player.Player p, Spell s) {
        return s.recast != null && s.recast.test(p);
    }

    private static CastCtx ctx(ServerPlayer p, Spell s, float charge, boolean echo, float extraPower) {
        PlayerMagic m = Magic.of(p);
        Rank r = m.rank(s.id);
        float power = r.power * extraPower;
        long now = p.level().getGameTime();
        if (m.pactUntil > now) power *= 1.25f;
        if (m.overloadCharges > 0 && m.overloadUntil > now && !echo) power *= 1.35f;
        return new CastCtx(p, s, charge, r, power, echo, null);
    }

    public static void fail(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal("§c" + why), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** Instant or released-charge cast. Returns true if the spell fired. */
    public static boolean cast(ServerPlayer p, Spell s, float charge) {
        String why = blocker(p, s);
        if (why != null) {
            fail(p, why);
            return false;
        }
        return run(p, s, charge, false, 1f);
    }

    public static boolean run(ServerPlayer p, Spell s, float charge, boolean echo, float extraPower) {
        PlayerMagic m = Magic.of(p);
        float chargeCost = s.mode == CastMode.CHARGE ? 1f + 0.5f * charge : 1f;
        boolean recast = recasting(p, s);
        int cost = recast ? s.recastCost : Math.round(Magic.cost(p, s) * chargeCost);
        if (!echo && !Magic.infinite(p) && m.mana < cost) {
            fail(p, "Not enough mana");
            return false;
        }
        CastCtx c = ctx(p, s, charge, echo, extraPower);
        int result = s.cast.cast(c);
        if (result < 0) return false;
        if (!echo) {
            Magic.spend(p, cost);
            if (s.healthCost > 0 && !Magic.infinite(p)) {
                p.hurt(p.damageSources().magic(), s.healthCost);
            }
            if (!recast) Magic.setCooldown(p, s, s.cooldown);
            m.lastCastTick = p.level().getGameTime();
            if (m.overloadCharges > 0) {
                m.overloadCharges--;
                m.dirty = true;
            }
            if (s.echoable) {
                m.lastEchoSpell = s.id;
                m.lastEchoCharge = charge;
            }
        }
        Magic.credit(p, s, Math.min(c.hits(), 4));
        return true;
    }

    // --- channelling ---------------------------------------------------------------------------------------------

    public static boolean startChannel(ServerPlayer p, Spell s) {
        String why = blocker(p, s);
        if (why != null) {
            fail(p, why);
            return false;
        }
        PlayerMagic m = Magic.of(p);
        m.channelSpell = s.id;
        m.channelTicks = 0;
        m.channelState = new java.util.HashMap<>();
        return true;
    }

    /** Returns false when the channel should end (out of mana, spell asked to stop). */
    public static boolean tickChannel(ServerPlayer p, Spell s) {
        PlayerMagic m = Magic.of(p);
        if (m.channelSpell == null || !m.channelSpell.equals(s.id)) return false;
        float perTick = Magic.cost(p, s) / 20f;
        if (!Magic.infinite(p)) {
            if (m.mana < perTick) {
                fail(p, "Out of mana");
                return false;
            }
            Magic.spend(p, perTick);
        }
        CastCtx c = ctx(p, s, 1f, false, 1f);
        CastCtx withState = new CastCtx(p, s, 1f, c.rank, c.power, false, m.channelState);
        int r = s.channel.tick(withState, m.channelTicks++);
        Magic.credit(p, s, Math.min(withState.hits(), 4));
        return r >= 0;
    }

    public static void endChannel(ServerPlayer p) {
        PlayerMagic m = Magic.of(p);
        if (m.channelSpell == null) return;
        Spell s = Spells.get(m.channelSpell);
        if (s != null) {
            if (s.end != null) {
                CastCtx c = ctx(p, s, 1f, false, 1f);
                s.end.end(new CastCtx(p, s, 1f, c.rank, c.power, false, m.channelState));
            }
            Magic.setCooldown(p, s, s.cooldown);
        }
        m.channelSpell = null;
        m.channelState = null;
        m.channelTicks = 0;
    }

    public static void cancelAll(ServerPlayer p) {
        endChannel(p);
    }
}
