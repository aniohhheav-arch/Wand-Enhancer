package com.wandcraft.magic;

import com.wandcraft.Config;
import com.wandcraft.ModSounds;
import com.wandcraft.entity.AshPile;
import com.wandcraft.entity.DarkClone;
import com.wandcraft.entity.ShadowClone;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.ShadowFx;
import com.wandcraft.net.PossessionPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server authority for the Dark Clone's possession: which mobs may be taken, who is hunting whom, and the timeline of
 * a possession (flicker, disintegration, implosion) after which the original mob is removed exactly once and an ash pile
 * is left behind. Clients only receive the elapsed time, they never decide anything.
 */
public final class Possession {
    public static final int FLICKER = 20, DISSOLVE = 70, IMPLODE = 18, TOTAL = FLICKER + DISSOLVE + IMPLODE;
    private static final String TAG = "wc_possessed";

    private static final class P {
        UUID owner;
        boolean started;
        long start;
        boolean noAiBefore, invulBefore;
        Vec3 pos;
    }

    /** target uuid -> state. */
    private static final Map<UUID, P> ACTIVE = new HashMap<>();
    /** target uuid -> hunting clone, and owner uuid -> clone: enforces one target at a time. */
    private static final Map<UUID, UUID> CLAIMED = new HashMap<>();
    private static final Map<UUID, UUID> BY_OWNER = new HashMap<>();
    /** Client mirror: entity id -> game time the possession started. */
    private static final Map<Integer, Long> CLIENT = new HashMap<>();

    private Possession() {}

    // ------------------------------------------------------------------------------------ rules
    public static boolean valid(Entity owner, LivingEntity t) {
        if (!(t instanceof Mob) || t instanceof Player || !t.isAlive() || t.isRemoved()) return false;
        if (t instanceof DarkClone || t instanceof ShadowClone) return false;
        if (Combat.isBoss(t) || !Combat.canTarget(owner, t)) return false;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()).toString();
        if (Config.DARK_CLONE_BLACKLIST.get().contains(id)) return false;
        return !ACTIVE.containsKey(t.getUUID()) && !t.getPersistentData().getBoolean(TAG);
    }

    public static boolean claimedByOther(LivingEntity t, UUID clone) {
        UUID c = CLAIMED.get(t.getUUID());
        return c != null && !c.equals(clone);
    }

    public static boolean hasClone(Player owner) { return BY_OWNER.containsKey(owner.getUUID()); }

    public static void claim(UUID owner, UUID clone) { BY_OWNER.put(owner, clone); }
    public static void claimTarget(UUID target, UUID clone) { CLAIMED.put(target, clone); }
    public static void releaseTarget(UUID target, UUID clone) { CLAIMED.remove(target, clone); }

    public static void releaseClone(UUID owner, UUID clone) {
        BY_OWNER.remove(owner, clone);
        CLAIMED.values().removeIf(clone::equals);
    }

    /** Nearest mob around {@code from} that could be taken, preferring ones the owner is looking at. */
    public static Mob pick(ServerPlayer owner, double range, Mob exclude) {
        Vec3 look = owner.getLookAngle();
        Mob best = null;
        double bestScore = Double.MAX_VALUE;
        for (Mob m : owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(range), m -> m != exclude && valid(owner, m) && !claimedByOther(m, null))) {
            double d = m.distanceTo(owner);
            if (d > range) continue;
            Vec3 to = m.position().subtract(owner.position()).normalize();
            double score = d * (1.6 - Math.max(0, look.dot(to)));
            if (score < bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------ lifecycle
    /** Freezes the target the moment the clone reaches for it. */
    public static void lock(Mob t, UUID owner) {
        if (ACTIVE.containsKey(t.getUUID())) return;
        P p = new P();
        p.owner = owner;
        p.noAiBefore = t.isNoAi();
        p.invulBefore = t.isInvulnerable();
        p.pos = t.position();
        t.setNoAi(true);
        t.setTarget(null);
        t.getNavigation().stop();
        t.setInvulnerable(true);
        t.getPersistentData().putBoolean(TAG, true);
        ACTIVE.put(t.getUUID(), p);
    }

    public static void unlock(Mob t) {
        P p = ACTIVE.remove(t.getUUID());
        restore(t, p);
        broadcast(t, -1);
    }

    private static void restore(Mob t, P p) {
        t.getPersistentData().remove(TAG);
        t.setNoAi(p != null && p.noAiBefore);
        t.setInvulnerable(p != null && p.invulBefore);
    }

    /** The clone has merged: start the visible possession. */
    public static void begin(Mob t, ServerLevel l) {
        P p = ACTIVE.get(t.getUUID());
        if (p == null) {
            lock(t, null);
            p = ACTIVE.get(t.getUUID());
        }
        p.started = true;
        p.start = l.getGameTime();
        broadcast(t, 0);
        Fx.sound(l, t.position(), ModSounds.DC_POSSESS.get(), 1.4f, 1f);
        Fx.sound(l, t.position(), ModSounds.DC_WHISPER.get(), 1.0f, 0.8f);
    }

    public static boolean isPossessed(Entity e) { return ACTIVE.containsKey(e.getUUID()); }

    private static void broadcast(Entity t, int elapsed) {
        if (t.level() instanceof ServerLevel) PacketDistributor.sendToPlayersTrackingEntityAndSelf(t, new PossessionPayload(t.getId(), elapsed));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (ACTIVE.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, P>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, P> en = it.next();
            P p = en.getValue();
            Mob t = find(e.getServer(), en.getKey());
            if (t == null) continue; // unloaded: restored when it joins a level again
            if (!t.isAlive() || t.isRemoved()) {
                it.remove();
                broadcast(t, -1);
                continue;
            }
            ServerLevel l = (ServerLevel) t.level();
            t.getNavigation().stop();
            Vec3 v = t.getDeltaMovement();
            t.setDeltaMovement(0, Math.min(v.y, 0), 0);
            if (!p.started) continue;
            int el = (int) (l.getGameTime() - p.start);
            if (el % 10 == 0) broadcast(t, el);
            if (el == FLICKER) Fx.sound(l, t.position(), ModSounds.DC_DISINTEGRATE.get(), 1.2f, 1f);
            if (el == FLICKER + DISSOLVE) Fx.sound(l, t.position(), ModSounds.DC_IMPLODE.get(), 1.6f, 1f);
            if (el < FLICKER + DISSOLVE) {
                // violent shaking while the soul is torn out
                t.setYRot(t.getYRot() + (l.random.nextFloat() - 0.5f) * (el < FLICKER ? 16 : 6));
                if (el % 7 == 0) Fx.sound(l, t.position(), ModSounds.DC_WHISPER.get(), 0.5f, 0.6f + l.random.nextFloat() * 0.5f);
            } else {
                double h = t.getBbHeight();
                int k = el - FLICKER - DISSOLVE;
                ShadowFx.implode(l, t.position().add(0, h * 0.4, 0), 1.6 + h * 0.3, 10 + k);
            }
            if (el >= TOTAL) {
                it.remove();
                finish(t, l);
            }
        }
    }

    private static Mob find(net.minecraft.server.MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity en = l.getEntity(id);
            if (en instanceof Mob m) return m;
        }
        return null;
    }

    /** Removes the original mob exactly once (no drops, no experience) and leaves ash where it stood. */
    private static void finish(Mob t, ServerLevel l) {
        Vec3 at = t.position();
        float width = t.getBbWidth();
        double h = t.getBbHeight();
        t.getPersistentData().remove(TAG);
        t.stopRiding();
        t.ejectPassengers();
        t.dropLeash(true, false);
        t.discard();
        broadcast(t, -1);
        ShadowFx.puff(l, at.add(0, h * 0.4, 0), 40, 0.6);
        Fx.burst(l, ShadowFx.ash(1.2f), at.add(0, h * 0.4, 0), 36, 0.5, 0.18);
        Fx.shockwave(l, ShadowFx.smoke(0.8f), at.add(0, 0.15, 0), 3.5, 8);
        AshPile.spawn(l, at, Math.max(0.9f, Math.min(2.8f, width * 1.6f)));
    }

    /** Anything left locked when the server stops is released, and mobs that were saved while possessed are restored on load. */
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Map.Entry<UUID, P> en : new ArrayList<>(ACTIVE.entrySet())) {
            Mob t = find(e.getServer(), en.getKey());
            if (t != null) restore(t, en.getValue());
        }
        ACTIVE.clear();
        CLAIMED.clear();
        BY_OWNER.clear();
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m)) return;
        if (m.getPersistentData().getBoolean(TAG) && !ACTIVE.containsKey(m.getUUID())) {
            m.getPersistentData().remove(TAG);
            m.setNoAi(false);
            m.setInvulnerable(false);
        }
    }

    // ------------------------------------------------------------------------------------ client mirror
    public static void clientSet(int id, int elapsed, long gameTime) {
        if (elapsed < 0) CLIENT.remove(id);
        else CLIENT.put(id, gameTime - elapsed);
    }

    public static Map<Integer, Long> clientView() { return CLIENT; }

    public static void clientClear() { CLIENT.clear(); }
}
