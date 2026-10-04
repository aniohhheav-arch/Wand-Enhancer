package dev.finalframe.finisher;

import com.mojang.logging.LogUtils;
import dev.finalframe.FFConfig;
import dev.finalframe.network.FFNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Server-authoritative finisher state machine: activation, per-tick choreography enforcement,
 * interruption handling and guaranteed restoration of AI, tick rate and player control.
 */
public final class FinisherManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Integer, FinisherSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, FinisherSession> BY_PERFORMER = new HashMap<>();
    private static final Map<Integer, FinisherSession> BY_TARGET = new HashMap<>();
    private static int nextId = 1;

    private FinisherManager() {
    }

    public static boolean isPerforming(Player player) {
        return BY_PERFORMER.containsKey(player.getUUID());
    }

    public static boolean isTargeted(Entity entity) {
        return BY_TARGET.containsKey(entity.getId());
    }

    // ---------------------------------------------------------------- activation

    /** Activation input: using a finisher weapon on a living entity. Runs on both sides; only the server starts. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && isPerforming(player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FinisherWeaponItem weapon)) {
            return;
        }
        if (FinisherRules.check(player, target, stack) != FinisherRules.Verdict.OK) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            tryStart(serverPlayer, target, weapon.finisher());
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }

    public static boolean tryStart(ServerPlayer player, LivingEntity target, FinisherDefinition definition) {
        if (isPerforming(player) || isTargeted(player) || isTargeted(target) || target instanceof Player p && isPerforming(p)) {
            return false;
        }
        if (FinisherRules.check(player, target, player.getMainHandItem()) != FinisherRules.Verdict.OK) {
            return false;
        }
        Vec3 origin = player.position();
        Vec3 delta = target.position().subtract(origin);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
        AnchorFrame frame = new AnchorFrame(origin, yaw);
        Vec3 end = definition.planTargetEnd(player.serverLevel(), player, target, frame);
        FinisherSnapshot snapshot = new FinisherSnapshot(nextId++, definition.id(), player.getId(), target.getId(), origin, yaw,
            target.position(), end, target.yBodyRot, target.getBbHeight());
        FinisherSession session = new FinisherSession(definition, snapshot, player, target);

        SESSIONS.put(snapshot.sessionId(), session);
        BY_PERFORMER.put(player.getUUID(), session);
        BY_TARGET.put(target.getId(), session);

        player.stopUsingItem();
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(origin.x, origin.y, origin.z, yaw, 6f);
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        target.setDeltaMovement(Vec3.ZERO);
        broadcast(session, new FFNetwork.Start(snapshot, 0));
        LOGGER.debug("Finisher {} started by {} on {}", definition.id(), player.getName().getString(), target);
        return true;
    }

    // ---------------------------------------------------------------- per tick

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        for (FinisherSession session : new ArrayList<>(SESSIONS.values())) {
            String interruption = interruption(session);
            if (interruption != null) {
                LOGGER.debug("Finisher {} interrupted: {}", session.snapshot.sessionId(), interruption);
                stop(session, true);
                continue;
            }
            tickSession(event.getServer(), session);
        }
    }

    private static void tickSession(MinecraftServer server, FinisherSession session) {
        int t = session.tick;
        FinisherDefinition def = session.definition;

        // Hold the performer on their mark. Clients lock input; this corrects any drift.
        ServerPlayer player = session.performer;
        player.setDeltaMovement(Vec3.ZERO);
        if (player.position().distanceToSqr(session.snapshot.anchor()) > 0.36) {
            Vec3 a = session.snapshot.anchor();
            player.connection.teleport(a.x, a.y, a.z, session.snapshot.anchorYaw(), player.getXRot());
        }

        // Keep the target on its choreographed path until it is defeated.
        LivingEntity target = session.target;
        if (!session.targetDefeated && target.isAlive()) {
            TargetMotion m = def.targetMotion(session.snapshot, t);
            target.setDeltaMovement(Vec3.ZERO);
            target.setPos(m.position());
            target.setYRot(m.bodyYaw());
            target.setYBodyRot(m.bodyYaw());
            target.setYHeadRot(m.headYaw());
            target.fallDistance = 0;
        }

        updateSlowMotion(server, session, t);
        def.serverTick(session, t);

        if (t > 0 && t % 20 == 0) {
            broadcast(session, new FFNetwork.Sync(session.snapshot.sessionId(), t));
        }
        session.tick++;
        if (session.tick >= def.timeline().length()) {
            stop(session, false);
        }
    }

    private static String interruption(FinisherSession session) {
        ServerPlayer p = session.performer;
        if (p.isRemoved() || !p.isAlive() || p.hasDisconnected()) {
            return "performer gone";
        }
        if (p.level() != session.level) {
            return "performer changed dimension";
        }
        if (!(p.getMainHandItem().getItem() instanceof FinisherWeaponItem)) {
            return "weapon no longer held";
        }
        if (!session.targetDefeated && session.tick < session.definition.fatalTick()) {
            LivingEntity t = session.target;
            if (t.isRemoved() || !t.isAlive() || t.level() != session.level) {
                return "target gone";
            }
        }
        return null;
    }

    private static void updateSlowMotion(MinecraftServer server, FinisherSession session, int t) {
        int[] window = session.definition.slowMotionWindow();
        boolean want = window != null && t >= window[0] && t < window[1]
            && FFConfig.get(FFConfig.WORLD_SLOW_MOTION) && server.getPlayerCount() == 1 && !otherSessionSlowing(session);
        if (want && !session.slowMotion) {
            ServerTickRateManager rates = server.tickRateManager();
            session.savedTickRate = rates.tickrate();
            rates.setTickRate(session.savedTickRate * FFConfig.get(FFConfig.SLOW_MOTION_FACTOR).floatValue());
            session.slowMotion = true;
        } else if (!want && session.slowMotion) {
            restoreTickRate(server, session);
        }
    }

    private static boolean otherSessionSlowing(FinisherSession session) {
        for (FinisherSession other : SESSIONS.values()) {
            if (other != session && other.slowMotion) {
                return true;
            }
        }
        return false;
    }

    private static void restoreTickRate(MinecraftServer server, FinisherSession session) {
        if (session.slowMotion) {
            server.tickRateManager().setTickRate(session.savedTickRate);
            session.slowMotion = false;
        }
    }

    /** Ends a session and restores every piece of gameplay state it touched. */
    private static void stop(FinisherSession session, boolean aborted) {
        if (SESSIONS.remove(session.snapshot.sessionId()) == null) {
            return;
        }
        BY_PERFORMER.remove(session.performer.getUUID());
        BY_TARGET.remove(session.target.getId());
        restoreTickRate(session.level.getServer(), session);

        LivingEntity target = session.target;
        if (target instanceof Mob mob && !target.isRemoved()) {
            mob.setNoAi(session.targetHadNoAi);
        }
        ServerPlayer player = session.performer;
        if (!player.hasDisconnected()) {
            int cooldown = FFConfig.get(FFConfig.COOLDOWN_TICKS);
            ItemStack held = player.getMainHandItem();
            if (cooldown > 0 && held.getItem() instanceof FinisherWeaponItem) {
                player.getCooldowns().addCooldown(held.getItem(), aborted ? cooldown / 3 : cooldown);
            }
        }
        broadcast(session, new FFNetwork.End(session.snapshot.sessionId(), aborted));
    }

    private static void broadcast(FinisherSession session, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (!session.performer.hasDisconnected()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(session.performer, payload);
        } else {
            PacketDistributor.sendToPlayersTrackingEntity(session.performer, payload);
        }
    }

    // ---------------------------------------------------------------- protection & interruption

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        FinisherSession asTarget = BY_TARGET.get(entity.getId());
        if (asTarget != null && !asTarget.isLastWord(event.getSource()) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }
        if (entity instanceof Player player && isPerforming(player) && FFConfig.get(FFConfig.INVULNERABLE_DURING_FINISHER)
            && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isPerforming(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isPerforming(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isPerforming(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isPerforming(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FinisherSession s = BY_PERFORMER.get(player.getUUID());
            if (s != null) {
                stop(s, true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FinisherSession s = BY_PERFORMER.get(event.getEntity().getUUID());
        if (s != null) {
            stop(s, true);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        FinisherSession s = BY_PERFORMER.get(event.getEntity().getUUID());
        if (s != null) {
            stop(s, true);
        }
    }

    /** Players who start tracking a performer mid-sequence join the cinematic in progress. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player performer && event.getEntity() instanceof ServerPlayer watcher) {
            FinisherSession s = BY_PERFORMER.get(performer.getUUID());
            if (s != null) {
                PacketDistributor.sendToPlayer(watcher, new FFNetwork.Start(s.snapshot, s.tick));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Iterator<FinisherSession> it = new ArrayList<>(SESSIONS.values()).iterator(); it.hasNext(); ) {
            stop(it.next(), true);
        }
        SESSIONS.clear();
        BY_PERFORMER.clear();
        BY_TARGET.clear();
    }
}
