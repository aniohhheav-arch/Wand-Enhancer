package com.wandcraft.magic;

import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Scheduler;
import com.wandcraft.entity.SpellProp;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server state for the Time school: position history for Rewind, Stasis damage banking and frozen projectiles. */
public final class TimeEvents {
    private static final String STASIS = "wc_stasis", FROZEN = "wc_frozen";
    private static final int HISTORY_TICKS = 160, SAMPLE = 4;

    private record Snap(long tick, ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch, float health) {}

    private static final Map<UUID, ArrayDeque<Snap>> HISTORY = new HashMap<>();
    private static final Set<UUID> STASIS_ACTIVE = new HashSet<>();
    private static final Map<UUID, Vec3> FROZEN_VEL = new HashMap<>();

    private TimeEvents() {}

    // ------------------------------------------------------------------------------------ rewind
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && p.tickCount % SAMPLE == 0 && p.isAlive()) record(p, p.level().getGameTime());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { HISTORY.remove(e.getEntity().getUUID()); }

    public static void record(ServerPlayer p, long tick) {
        ArrayDeque<Snap> q = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        q.addLast(new Snap(tick, p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), p.getHealth()));
        while (!q.isEmpty() && tick - q.peekFirst().tick() > HISTORY_TICKS) q.pollFirst();
    }

    /** Test hook: pretend the player stood here {@code ago} ticks ago. */
    public static void seed(ServerPlayer p, int ago) { record(p, p.level().getGameTime() - ago); }

    public static boolean rewind(ServerPlayer p, int ago) {
        ArrayDeque<Snap> q = HISTORY.get(p.getUUID());
        if (q == null || q.isEmpty()) return false;
        long want = p.level().getGameTime() - ago;
        Snap best = null;
        for (Snap s : q) if (s.dim() == p.level().dimension() && (best == null || Math.abs(s.tick() - want) < Math.abs(best.tick() - want))) best = s;
        if (best == null || best.pos().distanceToSqr(p.position()) < 0.5 && best.health() <= p.getHealth()) return false;
        ServerLevel l = (ServerLevel) p.level();
        Vec3 from = p.position();
        ghostTrail(l, from, best.pos());
        p.teleportTo(l, best.pos().x, best.pos().y, best.pos().z, best.yaw(), best.pitch());
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        if (best.health() > p.getHealth()) p.setHealth(Math.min(p.getMaxHealth(), best.health()));
        Fx.burst(l, Fx.glow(0x3DE0C8, 1.1f), best.pos().add(0, 1, 0), 30, 0.4, 0.2);
        Fx.sound(l, best.pos(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        Fx.sound(l, from, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
        return true;
    }

    private static void ghostTrail(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = Math.max(4, (int) (d.length() * 2));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n)).add(0, 1, 0);
            l.sendParticles(Fx.glow(i % 2 == 0 ? 0x3DE0C8 : 0xFFD678, 0.6f), p.x, p.y, p.z, 1, 0.1, 0.3, 0.1, 0);
        }
    }

    // ------------------------------------------------------------------------------------ stasis
    public static void stasis(ServerLevel l, ServerPlayer caster, LivingEntity t, int ticks, float interest) {
        if (STASIS_ACTIVE.contains(t.getUUID())) return;
        STASIS_ACTIVE.add(t.getUUID());
        var tag = t.getPersistentData();
        tag.putFloat(STASIS + "_dmg", 0f);
        tag.putBoolean(STASIS, true);
        boolean noAi = t instanceof Mob m && m.isNoAi();
        tag.putBoolean(STASIS + "_noai", noAi);
        if (t instanceof Mob m) {
            m.setNoAi(true);
            m.setTarget(null);
        }
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 5, 9, false, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks + 5, 9, false, false, false));
        SpellProp ring = SpellProp.spawn(l, "time_ring", t.position().add(0, t.getBbHeight() * 0.5, 0), (float) Math.max(0.12, t.getBbHeight() * 0.075), ticks + 10)
                .follow(t, new Vec3(0, t.getBbHeight() * 0.5, 0));
        Fx.sound(l, t.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.5f);
        Scheduler.repeat(l, 0, 4, ticks / 4, i -> {
            if (!t.isAlive()) return;
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            Fx.burst(l, Fx.spark(i % 2 == 0 ? 0x3DE0C8 : 0xFFD678, 0.5f), t.position().add(0, t.getBbHeight() * 0.5, 0), 3, t.getBbWidth() * 0.5, 0.01);
        });
        Scheduler.after(l, ticks, () -> {
            float stored = t.getPersistentData().getFloat(STASIS + "_dmg");
            endStasis(t, noAi);
            ring.dismiss();
            Fx.sound(l, t.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
            if (t.isAlive()) {
                Fx.shockwave(l, Fx.glow(0x3DE0C8, 1f), t.position().add(0, 0.2, 0), 2.5, 4);
                if (stored > 0) {
                    Combat.hurt(l, caster, caster, t, stored * interest);
                    Fx.burst(l, Fx.spark(0xFFFFFF, 0.9f), t.position().add(0, t.getBbHeight() * 0.5, 0), 24, 0.4, 0.3);
                }
            }
        });
    }

    private static void endStasis(LivingEntity t, boolean noAiBefore) {
        STASIS_ACTIVE.remove(t.getUUID());
        t.getPersistentData().remove(STASIS);
        t.getPersistentData().remove(STASIS + "_dmg");
        t.getPersistentData().remove(STASIS + "_noai");
        if (t instanceof Mob m) m.setNoAi(noAiBefore);
    }

    /** Damage dealt during stasis is stored, not applied. */
    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (!t.getPersistentData().getBoolean(STASIS) || !STASIS_ACTIVE.contains(t.getUUID())) return;
        var tag = t.getPersistentData();
        tag.putFloat(STASIS + "_dmg", tag.getFloat(STASIS + "_dmg") + e.getAmount());
        e.setCanceled(true);
        if (t.level() instanceof ServerLevel l) Fx.burst(l, Fx.spark(0xFFD678, 0.6f), t.position().add(0, t.getBbHeight() * 0.6, 0), 6, 0.25, 0.1);
    }

    // ------------------------------------------------------------------------------------ entropy
    public static void entropy(ServerLevel l, ServerPlayer caster, LivingEntity t, float power) {
        boolean boss = Combat.isBoss(t);
        SpellProp ring = SpellProp.spawn(l, "time_ring", t.position().add(0, t.getBbHeight() * 0.5, 0), (float) Math.max(0.1, t.getBbHeight() * 0.06), 70)
                .follow(t, new Vec3(0, t.getBbHeight() * 0.5, 0));
        Fx.sound(l, t.position(), net.minecraft.sounds.SoundEvents.SAND_BREAK, 1f, 0.5f);
        Scheduler.repeat(l, 0, 3, 20, i -> {
            if (!t.isAlive()) return;
            Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
            Fx.burst(l, Fx.glow(0xD8B070, 0.6f), c, 4, t.getBbWidth() * 0.5, 0.02);
            l.sendParticles(Fx.spark(0xFFD678, 0.5f), c.x, t.getY(), c.z, 2, t.getBbWidth() * 0.4, 0.05, t.getBbWidth() * 0.4, 0.01);
        });
        Scheduler.after(l, 60, () -> {
            ring.dismiss();
            if (!t.isAlive()) return;
            float dmg = (float) Math.min(boss ? 30 : 60, t.getMaxHealth() * (boss ? 0.06 : 0.25) * power);
            Combat.hurt(l, caster, caster, t, Math.max(dmg, 2f));
            Fx.burst(l, Fx.wisp(0xB89A60, 1.3f), t.position().add(0, t.getBbHeight() * 0.5, 0), 28, 0.4, 0.08);
            Fx.sound(l, t.position(), net.minecraft.sounds.SoundEvents.SAND_BREAK, 1.2f, 0.3f);
        });
    }

    // ------------------------------------------------------------------------------------ frozen projectiles
    public static void freeze(Projectile p) {
        if (!FROZEN_VEL.containsKey(p.getUUID())) {
            FROZEN_VEL.put(p.getUUID(), p.getDeltaMovement());
            p.getPersistentData().putBoolean(FROZEN, true);
            p.setNoGravity(true);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
    }

    public static void releaseFrozen(ServerLevel l, double boost) {
        for (Map.Entry<UUID, Vec3> en : new HashMap<>(FROZEN_VEL).entrySet()) {
            Entity e = l.getEntity(en.getKey());
            if (e == null) continue;
            FROZEN_VEL.remove(en.getKey());
            e.getPersistentData().remove(FROZEN);
            e.setNoGravity(false);
            e.setDeltaMovement(en.getValue().scale(boost));
            e.hurtMarked = true;
        }
    }

    /** Mobs or projectiles saved mid-effect (server stopped) are returned to normal on load. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        var tag = en.getPersistentData();
        if (tag.getBoolean(STASIS) && !STASIS_ACTIVE.contains(en.getUUID()) && en instanceof LivingEntity le) endStasis(le, tag.getBoolean(STASIS + "_noai"));
        if (tag.getBoolean(FROZEN) && !FROZEN_VEL.containsKey(en.getUUID())) {
            tag.remove(FROZEN);
            en.setNoGravity(false);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (ServerLevel l : e.getServer().getAllLevels()) releaseFrozen(l, 1.0);
        STASIS_ACTIVE.clear();
        HISTORY.clear();
    }
}
