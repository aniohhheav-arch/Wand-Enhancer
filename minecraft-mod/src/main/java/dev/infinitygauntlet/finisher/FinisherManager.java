package dev.infinitygauntlet.finisher;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.network.FinisherPayload;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.time.TimeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs cinematic finishers on the server. While one plays, the caster follows the scripted path (their own client
 * moves them; the server only corrects drift) and takes no damage, and the target is frozen in place and shielded
 * from outside damage until the script itself finishes it. A session ends cleanly if the caster disconnects, dies or
 * changes dimension, or the target is unloaded: everything is released and clients are told to stop.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class FinisherManager {
    /** Server half of one finisher type: the gameplay beats of its timeline. */
    public interface Script {
        /** Where the caster should be at {@code t}. */
        Vec3 casterPos(Session s, float t);

        void tick(Session s, int t);

        /** Until this tick the caster moves fast and lags behind the server's expectation on real connections. */
        default int fastUntil() {
            return 0;
        }
    }

    public static final class Session {
        public final FinisherType type;
        public final ServerPlayer caster;
        public final LivingEntity target;
        public final ServerLevel level;
        public final Vec3 casterStart;
        public final Vec3 targetPos;
        final Script script;
        int age;
        /** Set by the script right before it deals its own finishing damage. */
        public boolean allowDamage;
        /** Set by the script once the target is gone (killed or consumed): it is no longer held. */
        public boolean released;

        Session(FinisherType type, ServerPlayer caster, LivingEntity target, Script script) {
            this.type = type;
            this.caster = caster;
            this.target = target;
            this.level = caster.serverLevel();
            this.casterStart = caster.position();
            this.targetPos = target.position();
            this.script = script;
        }

        public int age() {
            return age;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean enabled() {
        return ServerConfig.FINISHERS_ENABLED.get();
    }

    /** Whether {@code target} is weak enough to be finished by {@code caster}. */
    public static boolean canFinish(ServerPlayer caster, LivingEntity target, float pendingDamage) {
        if (!enabled() || busy(caster) || involved(target) || !target.isAlive() || target.level() != caster.level()) {
            return false;
        }
        if (target instanceof Player && !ServerConfig.FINISHERS_ON_PLAYERS.get()) {
            return false;
        }
        float threshold = ServerConfig.FINISHER_HEALTH_THRESHOLD.get().floatValue();
        return target.getHealth() <= pendingDamage || target.getHealth() / target.getMaxHealth() <= threshold;
    }

    public static boolean busy(Player caster) {
        return SESSIONS.containsKey(caster.getUUID());
    }

    public static boolean involved(LivingEntity entity) {
        for (Session s : SESSIONS.values()) {
            if (s.target == entity) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Session session(Player caster) {
        return SESSIONS.get(caster.getUUID());
    }

    public static Session start(ServerPlayer caster, LivingEntity target, FinisherType type) {
        Script script = switch (type) {
            case COSMIC_PUNCH -> new CosmicPunchScript();
            case DIMENSIONAL_ERASURE -> new StoneFinisherScripts.Erasure();
            case PSYCHIC_OVERLOAD -> new StoneFinisherScripts.Psychic();
            case REALITY_REWRITE -> new StoneFinisherScripts.Rewrite();
            case TEMPORAL_COLLAPSE -> new StoneFinisherScripts.Collapse();
            case SOUL_EXTRACTION -> new StoneFinisherScripts.Extraction();
        };
        Session s = new Session(type, caster, target, script);
        SESSIONS.put(caster.getUUID(), s);
        TimeManager.apply(target, 0f, 3, true);
        target.setDeltaMovement(Vec3.ZERO);
        broadcast(s, false);
        script.tick(s, 0);
        return s;
    }

    private static void broadcast(Session s, boolean ended) {
        ModNetwork.sendNear(s.level, s.targetPos, 160,
                new FinisherPayload(s.type.ordinal(), s.caster.getId(), s.target.getId(), s.casterStart, s.targetPos, ended));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        List<Session> done = new ArrayList<>();
        List<Session> broken = new ArrayList<>();
        for (Session s : SESSIONS.values()) {
            s.age++;
            ServerPlayer c = s.caster;
            if (!c.isAlive() || c.hasDisconnected() || c.level() != s.level
                    || (!s.released && (s.target.isRemoved() || s.target.level() != s.level))) {
                broken.add(s);
                continue;
            }
            // the caster's own client moves them along the path; only drift (lag, knockback) is corrected
            Vec3 expected = s.script.casterPos(s, s.age);
            double tolerance = s.age <= s.script.fastUntil() ? 2.5 : 0.6; // fast moves get slack for latency
            if (c.position().distanceToSqr(expected) > tolerance * tolerance) {
                c.teleportTo(expected.x, expected.y, expected.z);
            }
            c.fallDistance = 0;
            if (!s.released && s.target.isAlive()) {
                TimeManager.apply(s.target, 0f, 3, true);
                s.target.setDeltaMovement(Vec3.ZERO);
                if (s.target.position().distanceToSqr(s.targetPos) > 0.01) {
                    s.target.teleportTo(s.targetPos.x, s.targetPos.y, s.targetPos.z);
                }
            }
            s.script.tick(s, s.age);
            if (s.age >= s.type.duration) {
                done.add(s);
            }
        }
        for (Session s : done) {
            finish(s, false);
        }
        for (Session s : broken) {
            finish(s, true);
        }
    }

    private static void finish(Session s, boolean interrupted) {
        SESSIONS.remove(s.caster.getUUID());
        if (s.target.isAlive()) {
            TimeManager.clear(s.target);
        }
        if (interrupted) {
            broadcast(s, true);
            InfinityGauntlet.LOGGER.debug("Finisher {} interrupted", s.type);
        }
    }

    /** Ends the caster's finisher right away (logout, death, external reset). */
    public static void cancel(Player caster) {
        Session s = SESSIONS.get(caster.getUUID());
        if (s != null) {
            finish(s, true);
        }
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        LivingEntity e = event.getEntity();
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return; // /kill and the void still apply; the session then ends as interrupted
        }
        for (Session s : SESSIONS.values()) {
            // the caster is untouchable mid-sequence; the target only takes the script's own blow
            if (e == s.caster || e == s.target && !s.allowDamage && !s.released) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private FinisherManager() {
    }
}
