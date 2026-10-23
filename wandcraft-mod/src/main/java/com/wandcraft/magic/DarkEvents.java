package com.wandcraft.magic;

import com.wandcraft.magic.util.Fx;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Curse Mark bonus damage and stealth target loss for the Dark Arts and Illusion schools. */
public final class DarkEvents {
    public static final String CURSE = "wc_curse";

    private DarkEvents() {}

    public static void curse(ServerPlayer caster, LivingEntity target, int hits, float bonus, int ticks) {
        var tag = target.getPersistentData();
        tag.putUUID(CURSE, caster.getUUID());
        tag.putInt(CURSE + "_n", hits);
        tag.putFloat(CURSE + "_b", bonus);
        tag.putLong(CURSE + "_t", target.level().getGameTime() + ticks);
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        var tag = t.getPersistentData();
        if (!tag.hasUUID(CURSE) || !(t.level() instanceof ServerLevel sl)) return;
        if (tag.getLong(CURSE + "_t") < sl.getGameTime() || tag.getInt(CURSE + "_n") <= 0) {
            tag.remove(CURSE);
            return;
        }
        UUID owner = tag.getUUID(CURSE);
        if (e.getSource().getEntity() == null || !owner.equals(e.getSource().getEntity().getUUID())) return;
        e.setAmount(e.getAmount() + tag.getFloat(CURSE + "_b"));
        int left = tag.getInt(CURSE + "_n") - 1;
        tag.putInt(CURSE + "_n", left);
        Fx.burst(sl, Fx.wisp(0x1A0B2E, 1f), t.position().add(0, t.getBbHeight() * 0.6, 0), 8, 0.25, 0.05);
        Fx.burst(sl, Fx.spark(0xB06BFF, 0.7f), t.position().add(0, t.getBbHeight() * 0.6, 0), 6, 0.25, 0.1);
        if (e.getSource().getEntity() instanceof ServerPlayer sp) {
            Spell s = Spells.get("curse_mark");
            if (s != null) Magic.credit(sp, s, 1);
        }
        if (left <= 0) tag.remove(CURSE);
    }

    /** Mobs further than a few blocks cannot pick out a stealthed player. */
    @SubscribeEvent
    public static void retarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof ServerPlayer p && e.getEntity() instanceof Mob m
                && Magic.of(p).stealthUntil > p.level().getGameTime() && m.distanceToSqr(p) > 16) {
            e.setCanceled(true);
        }
    }
}
