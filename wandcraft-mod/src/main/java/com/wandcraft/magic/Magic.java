package com.wandcraft.magic;

import com.wandcraft.WandCraft;
import com.wandcraft.net.SyncMagicPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side rules: mana, cooldowns, unlocks and mastery. All state changes go through here. */
public final class Magic {
    private Magic() {}

    public static PlayerMagic of(Player p) { return p.getData(WandCraft.MAGIC.get()); }

    public static boolean infinite(Player p) {
        return p instanceof ServerPlayer sp ? sp.gameMode.isCreative() : p.isCreative();
    }

    public static boolean isUnlocked(Player p, Spell s) {
        if (infinite(p)) return true;
        PlayerMagic m = of(p);
        if (s.evolvesFrom != null) return m.rank(s.evolvesFrom) == Rank.MASTER;
        return m.level() >= s.level && masterCount(m) >= s.masters;
    }

    public static String lockReason(Player p, Spell s) {
        if (s.evolvesFrom != null) {
            Spell parent = Spells.get(s.evolvesFrom);
            return "Reach Master rank with " + (parent == null ? s.evolvesFrom : parent.name);
        }
        return s.masters > 0 ? "Reach wizard level " + s.level + " and master " + s.masters + " spells" : "Reach wizard level " + s.level;
    }

    public static int masterCount(PlayerMagic m) {
        int n = 0;
        for (Spell sp : Spells.all()) if (m.rank(sp.id) == Rank.MASTER) n++;
        return n;
    }

    /** True when cooldowns do not apply to this player: the world setting or their personal override is off. */
    public static boolean cooldownsDisabled(Player p) {
        PlayerMagic m = of(p);
        if (m.cooldownsOff) return true;
        if (p.level().isClientSide) return m.worldCooldownsOff;
        return p.getServer() != null && WandSettings.get(p.getServer()).cooldownsDisabled();
    }

    public static boolean onCooldown(Player p, Spell s, long now) {
        if (cooldownsDisabled(p)) return false;
        return of(p).cooldownEnd.getOrDefault(s.id, 0L) > now;
    }

    public static int cost(Player p, Spell s) {
        PlayerMagic m = of(p);
        float c = s.cost * m.rank(s.id).costMul;
        if (m.overloadCharges > 0 && m.overloadUntil > p.level().getGameTime()) c *= 1.5f;
        return Math.max(1, Math.round(c));
    }

    public static boolean spend(ServerPlayer p, float amount) {
        if (infinite(p)) return true;
        PlayerMagic m = of(p);
        if (m.mana < amount) return false;
        m.mana -= amount;
        m.lastCastTick = p.level().getGameTime();
        m.dirty = true;
        return true;
    }

    public static void restore(ServerPlayer p, float amount) {
        PlayerMagic m = of(p);
        m.mana = Math.min(m.maxMana(), m.mana + amount);
        m.dirty = true;
    }

    public static void setCooldown(ServerPlayer p, Spell s, int ticks) {
        if (cooldownsDisabled(p)) return;
        PlayerMagic m = of(p);
        float mul = infinite(p) ? 0.25f : m.rank(s.id).cooldownMul;
        m.cooldownEnd.put(s.id, p.level().getGameTime() + Math.max(2, Math.round(ticks * mul)));
        m.dirty = true;
    }

    /** Credits successful uses and announces rank-ups / level-ups. */
    public static void credit(ServerPlayer p, Spell s, int hits) {
        if (hits <= 0) return;
        PlayerMagic m = of(p);
        Rank before = m.rank(s.id);
        int levelBefore = m.level();
        m.uses.merge(s.id, hits, Integer::sum);
        m.dirty = true;
        Rank after = m.rank(s.id);
        if (after != before) {
            String perk = after == Rank.MASTER ? s.masterPerk : s.adeptPerk;
            p.displayClientMessage(Component.literal("✦ " + s.name + " reached " + after.display + " — " + perk), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, after == Rank.MASTER ? 1.4f : 1.1f);
            for (Spell other : Spells.all()) {
                if (s.id.equals(other.evolvesFrom) && after == Rank.MASTER) {
                    p.sendSystemMessage(Component.literal("✦ New spell unlocked: " + other.name));
                }
            }
        }
        if (m.level() > levelBefore) {
            p.sendSystemMessage(Component.literal("✦ Wizard level " + m.level() + "! Max mana is now " + (int) m.maxMana() + "."));
            for (Spell other : Spells.all()) {
                if (other.evolvesFrom == null && other.level > levelBefore && other.level <= m.level()) {
                    p.sendSystemMessage(Component.literal("✦ New spell unlocked: " + other.name));
                }
            }
        }
    }

    public static void sync(ServerPlayer p) {
        PlayerMagic m = of(p);
        m.dirty = false;
        m.worldCooldownsOff = p.getServer() != null && WandSettings.get(p.getServer()).cooldownsDisabled();
        try {
            PacketDistributor.sendToPlayer(p, new SyncMagicPayload(m.serializeNBT(p.registryAccess())));
        } catch (UnsupportedOperationException ignored) {
            // Connections without our channel (vanilla clients, test fakes) simply get no HUD data.
        }
    }
}
