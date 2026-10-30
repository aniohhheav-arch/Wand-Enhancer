package dev.infinitygauntlet.nexus;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.PlayerPowerData;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.network.ControlInputPayload;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.network.PossessionActionPayload;
import dev.infinitygauntlet.network.PossessionStatePayload;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative remote possession (Mind + Space). A player binds a Consciousness Nexus, links creatures to it
 * with the Remote Consciousness combination, and operates them from the Nexus console while their own body stays at
 * the station. All movement, attacks and abilities are executed here from the operator's validated input; the
 * possessed creature's own goals are suspended for the duration and fully restored when the link ends (death,
 * unloading, range, expiry, extraction or disconnect).
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class PossessionManager {
    public static final double CONSOLE_RANGE = 6;
    private static final EnumSet<Goal.Flag> SUSPENDED = EnumSet.allOf(Goal.Flag.class);
    private static final int BLINK_COOLDOWN = 60, OVERRIDE_COOLDOWN = 600, EXTRACT_COOLDOWN = 400;

    static final class Link {
        final Mob mob;
        final MoveControl savedMove;
        final long started;
        float yaw;
        float pitch;
        float stability = 1f;

        Link(Mob mob, long started) {
            this.mob = mob;
            this.savedMove = mob.moveControl;
            this.started = started;
            this.yaw = mob.getYRot();
            this.pitch = mob.getXRot();
        }
    }

    static final class Session {
        final UUID player;
        final ResourceKey<Level> dimension;
        final BlockPos nexus;
        final List<Link> links = new ArrayList<>();
        int active;
        boolean controlling;
        float forward, strafe;
        int heldFlags;
        int pendingFlags;
        long lastInput;
        int attackCooldown, specialCooldown;
        long blinkReady, overrideReady, extractReady, overrideUntil;

        Session(UUID player, ResourceKey<Level> dimension, BlockPos nexus) {
            this.player = player;
            this.dimension = dimension;
            this.nexus = nexus;
        }

        @Nullable
        Link activeLink() {
            return links.isEmpty() ? null : links.get(Math.min(active, links.size() - 1));
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<Integer> POSSESSED = new HashSet<>();

    // ------------------------------------------------------------------ queries

    public static boolean isPossessed(Entity e) {
        return POSSESSED.contains(e.getId());
    }

    public static boolean isControlling(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.controlling;
    }

    @Nullable
    public static Mob activeMob(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        Link l = s == null ? null : s.activeLink();
        return l == null ? null : l.mob;
    }

    public static int linkCount(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? 0 : s.links.size();
    }

    @Nullable
    private static NexusBlockEntity nexus(MinecraftServer server, Session s) {
        ServerLevel level = server.getLevel(s.dimension);
        if (level == null || !level.isLoaded(s.nexus)) {
            return null;
        }
        return level.getBlockEntity(s.nexus) instanceof NexusBlockEntity n ? n : null;
    }

    // ------------------------------------------------------------------ linking

    /** Links {@code mob} to the player's bound Nexus. */
    public static CastResult link(ServerPlayer player, Mob mob) {
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        if (data.nexus == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.nexus.none"));
        }
        ServerLevel level = player.server.getLevel(data.nexus.dimension());
        if (level == null || !level.isLoaded(data.nexus.pos()) || !(level.getBlockEntity(data.nexus.pos()) instanceof NexusBlockEntity nexus)
                || !player.getUUID().equals(nexus.owner())) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.nexus.missing"));
        }
        if (mob.level() != level) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.nexus.other_dimension"));
        }
        if (!ControlProfiles.isCompatible(mob) || isPossessed(mob)) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.nexus.incompatible"));
        }
        int range = ServerConfig.POSSESSION_RANGE.get();
        if (mob.position().distanceTo(nexus.screenCenter()) > range) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.nexus.too_far", range));
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && (!s.nexus.equals(nexus.getBlockPos()) || s.dimension != level.dimension())) {
            disconnectAll(player.server, s, "RELINKED");
            s = null;
        }
        if (s == null) {
            s = new Session(player.getUUID(), level.dimension(), nexus.getBlockPos());
            SESSIONS.put(player.getUUID(), s);
        }
        if (s.links.size() >= ServerConfig.POSSESSION_MAX_FEEDS.get()) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.nexus.full", s.links.size()));
        }
        Link link = new Link(mob, level.getGameTime());
        possess(mob);
        s.links.add(link);
        s.active = s.links.size() - 1;
        publish(level, s, nexus);
        nexus.setState(NexusBlockEntity.CONNECTING);

        Vec3 head = mob.getEyePosition().add(0, 0.6 + mob.getBbHeight() * 0.2, 0);
        Vfx.send(level, VfxType.RUPTURE_BURST, head, Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.MIND.highlight(), 0.6f, 24);
        for (int i = 0; i < 3; i++) {
            Vfx.send(level, VfxType.ARC, head, mob.getEyePosition().add(level.random.nextGaussian() * 0.2, 0, level.random.nextGaussian() * 0.2),
                    InfinityStone.MIND.color(), InfinityStone.MIND.highlight(), 0.5f, 20);
        }
        Vfx.link(level, head, nexus.screenCenter(), InfinityStone.SPACE, 30);
        Vfx.sound(level, mob.position(), ModSounds.NEXUS_LINK, 1.5f, 1f);
        Vfx.sound(level, Vec3.atCenterOf(nexus.getBlockPos()), ModSounds.NEXUS_POWER_ON, 1.5f, 1f);
        if (s.controlling) {
            sendState(player, s);
        }
        return CastResult.success(Component.translatable("message.infinitygauntlet.nexus.linked", mob.getDisplayName(), s.links.size()));
    }

    private static void possess(Mob mob) {
        POSSESSED.add(mob.getId());
        mob.moveControl = new RemoteMoveControl(mob);
        for (Goal.Flag flag : SUSPENDED) {
            mob.goalSelector.disableControlFlag(flag);
            mob.targetSelector.disableControlFlag(flag);
        }
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    private static void release(Link link) {
        Mob mob = link.mob;
        POSSESSED.remove(mob.getId());
        mob.moveControl = link.savedMove;
        for (Goal.Flag flag : SUSPENDED) {
            mob.goalSelector.enableControlFlag(flag);
            mob.targetSelector.enableControlFlag(flag);
        }
        mob.setZza(0);
        mob.setXxa(0);
    }

    private static void dropLink(MinecraftServer server, Session s, Link link, String signal) {
        release(link);
        s.links.remove(link);
        s.active = s.links.isEmpty() ? 0 : Math.floorMod(s.active, s.links.size());
        NexusBlockEntity nexus = nexus(server, s);
        Mob mob = link.mob;
        if (mob.level() instanceof ServerLevel level && !mob.isRemoved()) {
            Vec3 head = mob.getEyePosition().add(0, 0.6 + mob.getBbHeight() * 0.2, 0);
            Vfx.send(level, VfxType.IMPLOSION, head, Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.MIND.highlight(), 0.5f, 10);
            Vfx.sound(level, mob.position(), ModSounds.SPACE_PORTAL_CLOSE, 0.8f, 1.5f);
        }
        if (nexus != null) {
            nexus.beginDisconnect(mob.getId(), signal);
            publish((ServerLevel) nexus.getLevel(), s, nexus);
            Vfx.sound((ServerLevel) nexus.getLevel(), Vec3.atCenterOf(nexus.getBlockPos()), ModSounds.NEXUS_DISCONNECT, 1.5f, 1f);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(s.player);
        if (s.links.isEmpty()) {
            SESSIONS.remove(s.player);
            if (nexus != null) {
                nexus.setController(-1);
            }
            s.controlling = false;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.infinitygauntlet.nexus.signal", mob.getDisplayName(), signal), true);
            sendState(player, s);
        }
    }

    private static void disconnectAll(MinecraftServer server, Session s, String signal) {
        for (Link link : new ArrayList<>(s.links)) {
            dropLink(server, s, link, signal);
        }
        SESSIONS.remove(s.player);
    }

    /** Mirrors the session into the Nexus block entity for every client. */
    private static void publish(ServerLevel level, Session s, NexusBlockEntity nexus) {
        List<NexusBlockEntity.Link> list = new ArrayList<>();
        for (Link l : s.links) {
            list.add(new NexusBlockEntity.Link(l.mob.getId(), l.started, l.stability));
        }
        nexus.setLinks(list, s.active);
        nexus.setCooldowns(s.blinkReady, s.overrideReady, s.extractReady, s.overrideUntil);
    }

    private static void sendState(ServerPlayer player, Session s) {
        Link l = s.activeLink();
        float anchor = 0;
        if (player.level() instanceof ServerLevel level && level.getBlockEntity(s.nexus) instanceof NexusBlockEntity n) {
            anchor = n.consoleYaw();
        }
        ModNetwork.sendTo(player, new PossessionStatePayload(s.controlling && l != null, s.nexus, l == null ? -1 : l.mob.getId(),
                l == null ? 0 : l.yaw, l == null ? 0 : l.pitch, anchor));
    }

    // ------------------------------------------------------------------ console

    /** Right-click on the Nexus by its owner: take or release the controls. */
    public static void toggleControl(ServerPlayer player, NexusBlockEntity nexus) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.links.isEmpty() || !s.nexus.equals(nexus.getBlockPos())) {
            player.displayClientMessage(Component.translatable("message.infinitygauntlet.nexus.no_link"), true);
            return;
        }
        s.controlling = !s.controlling;
        if (s.controlling) {
            Vec3 at = nexus.consolePosition();
            Vec3 safe = TeleportUtil.findSafeSpot(player.serverLevel(), player, at, 2);
            if (safe != null) {
                player.connection.teleport(safe.x, safe.y, safe.z, nexus.consoleYaw(), NexusBlockEntity.CONSOLE_PITCH);
            }
            Link l = s.activeLink();
            if (l != null) {
                l.yaw = l.mob.getYRot();
                l.pitch = l.mob.getXRot();
            }
            nexus.setController(player.getId());
            Vfx.sound(player.serverLevel(), Vec3.atCenterOf(nexus.getBlockPos()), ModSounds.GAUNTLET_SELECT, 1f, 0.7f);
        } else {
            nexus.setController(-1);
        }
        s.forward = s.strafe = 0;
        sendState(player, s);
    }

    public static void input(ServerPlayer player, ControlInputPayload p) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !s.controlling) {
            return;
        }
        s.forward = Mth.clamp(p.forward(), -1, 1);
        s.strafe = Mth.clamp(p.strafe(), -1, 1);
        s.heldFlags = p.flags();
        s.pendingFlags |= p.flags() & (ControlInputPayload.ATTACK | ControlInputPayload.SPECIAL);
        Link l = s.activeLink();
        if (l != null && Float.isFinite(p.yaw()) && Float.isFinite(p.pitch())) {
            l.yaw = Mth.wrapDegrees(p.yaw());
            l.pitch = Mth.clamp(p.pitch(), -90, 90);
        }
        s.lastInput = player.serverLevel().getGameTime();
    }

    public static void action(ServerPlayer player, int action) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return;
        }
        MinecraftServer server = player.server;
        ServerLevel level = server.getLevel(s.dimension);
        NexusBlockEntity nexus = nexus(server, s);
        Link l = s.activeLink();
        if (level == null || nexus == null || l == null) {
            return;
        }
        long now = level.getGameTime();
        switch (action) {
            case PossessionActionPayload.LEAVE -> {
                if (s.controlling) {
                    toggleControl(player, nexus);
                }
            }
            case PossessionActionPayload.DISCONNECT -> disconnectAll(server, s, "EMERGENCY DISCONNECT");
            case PossessionActionPayload.SWITCH -> {
                if (s.links.size() > 1) {
                    s.active = (s.active + 1) % s.links.size();
                    Link next = s.activeLink();
                    next.yaw = next.mob.getYRot();
                    next.pitch = next.mob.getXRot();
                    s.forward = s.strafe = 0;
                    publish(level, s, nexus);
                    sendState(player, s);
                    Vfx.sound(level, Vec3.atCenterOf(nexus.getBlockPos()), ModSounds.NEXUS_STATIC, 1f, 1.2f);
                }
            }
            case PossessionActionPayload.BLINK -> {
                if (!s.controlling || now < s.blinkReady) {
                    return;
                }
                Vec3 dir = Vec3.directionFromRotation(Math.min(l.pitch, 10), l.yaw);
                Vec3 target = l.mob.position().add(dir.scale(8));
                Vec3 safe = null;
                for (double back = 0; back < 8 && safe == null; back += 1) {
                    safe = TeleportUtil.findSafeSpot(level, l.mob, target.subtract(dir.scale(back)), 1);
                }
                if (safe == null) {
                    return;
                }
                Vec3 from = l.mob.position();
                Vfx.send(level, VfxType.RUPTURE_BURST, from.add(0, l.mob.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                        InfinityStone.SPACE.highlight(), Math.max(0.8f, l.mob.getBbHeight() * 0.6f), 20);
                TeleportUtil.teleport(l.mob, level, safe, l.yaw, l.pitch, Vec3.ZERO);
                Vfx.send(level, VfxType.RUPTURE_BURST, safe.add(0, l.mob.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                        InfinityStone.SPACE.highlight(), Math.max(0.8f, l.mob.getBbHeight() * 0.6f), 20);
                Vfx.sound(level, from, ModSounds.SPACE_BLINK, 1f, 1f);
                Vfx.sound(level, safe, ModSounds.SPACE_BLINK, 1f, 1.3f);
                s.blinkReady = now + BLINK_COOLDOWN;
                publish(level, s, nexus);
            }
            case PossessionActionPayload.OVERRIDE -> {
                if (!s.controlling || now < s.overrideReady) {
                    return;
                }
                int ticks = ServerConfig.NEURAL_OVERRIDE_SECONDS.get() * 20;
                l.mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1));
                l.mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1));
                l.mob.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1));
                l.mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
                Vfx.send(level, VfxType.SPARK_BURST, l.mob.getBoundingBox().getCenter(), Vec3.ZERO, InfinityStone.MIND.color(),
                        InfinityStone.MIND.highlight(), 1.2f, 16);
                Vfx.shockwave(level, l.mob.position().add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.MIND, 3.5f, 14);
                Vfx.sound(level, l.mob.position(), ModSounds.GAUNTLET_CAST, 1.2f, 1.4f);
                s.overrideReady = now + OVERRIDE_COOLDOWN;
                s.overrideUntil = now + ticks;
                publish(level, s, nexus);
            }
            case PossessionActionPayload.EXTRACT -> {
                if (!s.controlling || now < s.extractReady) {
                    return;
                }
                Direction side = nexus.facing().getClockWise();
                Vec3 dest = Vec3.atBottomCenterOf(nexus.getBlockPos()).add(side.getStepX() * 3.0, 0, side.getStepZ() * 3.0)
                        .add(nexus.facing().getStepX() * 1.5, 0, nexus.facing().getStepZ() * 1.5);
                Vec3 safe = TeleportUtil.findSafeSpot(level, l.mob, dest, 3);
                if (safe == null) {
                    player.displayClientMessage(Component.translatable("message.infinitygauntlet.nexus.extract_blocked"), true);
                    return;
                }
                Mob mob = l.mob;
                float r = Math.max(1f, mob.getBbHeight() * 0.8f);
                Vfx.send(level, VfxType.RUPTURE_BURST, mob.position().add(0, mob.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                        InfinityStone.MIND.highlight(), r, 30);
                Vfx.dissolve(level, mob, InfinityStone.SPACE);
                Vfx.sound(level, mob.position(), ModSounds.SPACE_PORTAL_OPEN, 1.2f, 1.3f);
                TeleportUtil.teleport(mob, level, safe, nexus.consoleYaw() + 180, 0, Vec3.ZERO);
                Vfx.send(level, VfxType.RUPTURE_BURST, safe.add(0, mob.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                        InfinityStone.MIND.highlight(), r, 30);
                Vfx.sound(level, safe, ModSounds.SPACE_TELEPORT, 1.2f, 1f);
                s.extractReady = now + EXTRACT_COOLDOWN;
                dropLink(server, s, l, "EXTRACTED");
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Pre event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int range = ServerConfig.POSSESSION_RANGE.get();
        long maxTicks = ServerConfig.POSSESSION_SECONDS.get() * 20L;
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            NexusBlockEntity nexus = nexus(server, s);
            if (player == null || nexus == null) {
                disconnectAll(server, s, nexus == null ? "NEXUS OFFLINE" : "OPERATOR LOST");
                continue;
            }
            ServerLevel level = (ServerLevel) nexus.getLevel();
            long now = level.getGameTime();
            if (s.controlling && (player.level() != level || player.position().distanceTo(nexus.consolePosition()) > CONSOLE_RANGE)) {
                s.controlling = false;
                nexus.setController(-1);
                sendState(player, s);
            }
            Iterator<Link> it = new ArrayList<>(s.links).iterator();
            while (it.hasNext()) {
                Link l = it.next();
                Mob mob = l.mob;
                String broken = null;
                if (mob.isRemoved() || !mob.isAlive() || mob.level() != level) {
                    broken = "SIGNAL LOST";
                } else {
                    double d = mob.position().distanceTo(nexus.screenCenter());
                    if (d > range) {
                        broken = "OUT OF RANGE";
                    } else if (now - l.started > maxTicks) {
                        broken = "LINK EXPIRED";
                    }
                    float strain = now < s.overrideUntil && l == s.activeLink() ? 0.2f : 0f;
                    l.stability = (float) Mth.clamp(1 - Math.pow(d / range, 2) - strain, 0, 1);
                }
                if (broken != null) {
                    dropLink(server, s, l, broken);
                }
            }
            if (!SESSIONS.containsKey(s.player)) {
                continue;
            }
            Link active = s.activeLink();
            boolean driving = s.controlling && now - s.lastInput < 10;
            for (Link l : s.links) {
                if (l.mob.moveControl instanceof RemoteMoveControl ctrl) {
                    boolean mine = driving && l == active;
                    float speed = (s.heldFlags & ControlInputPayload.SPRINT) != 0 ? 1.35f : 1f;
                    ctrl.input(mine ? s.forward : 0, mine ? s.strafe : 0, speed);
                    if (mine && (s.heldFlags & ControlInputPayload.JUMP) != 0 && (l.mob.onGround() || l.mob.isInWater())) {
                        l.mob.getJumpControl().jump();
                    }
                }
                l.mob.setTarget(null);
            }
            if (s.attackCooldown > 0) {
                s.attackCooldown--;
            }
            if (s.specialCooldown > 0) {
                s.specialCooldown--;
            }
            if (driving && active != null) {
                Vec3 look = Vec3.directionFromRotation(active.pitch, active.yaw);
                if ((s.pendingFlags & ControlInputPayload.ATTACK) != 0 && s.attackCooldown == 0) {
                    s.attackCooldown = ControlProfiles.attack(level, player, active.mob, look);
                }
                if ((s.pendingFlags & ControlInputPayload.SPECIAL) != 0 && s.specialCooldown == 0) {
                    s.specialCooldown = ControlProfiles.special(level, player, active.mob, look);
                }
            }
            s.pendingFlags = 0;
            if (now % 10 == 0) {
                publish(level, s, nexus);
            }
            if (active != null && active.stability < 0.3f && now % 20 == 0) {
                level.playSound(null, nexus.getBlockPos(), ModSounds.NEXUS_WARNING.get(), SoundSource.BLOCKS, 1f, 1f);
            }
        }
    }

    /** Keeps the possessed creature facing where the operator looks (applied around its own tick). */
    @SubscribeEvent
    static void onEntityTickPre(EntityTickEvent.Pre event) {
        orient(event.getEntity());
    }

    @SubscribeEvent
    static void onEntityTickPost(EntityTickEvent.Post event) {
        orient(event.getEntity());
    }

    private static void orient(Entity e) {
        if (e.level().isClientSide || POSSESSED.isEmpty() || !POSSESSED.contains(e.getId()) || !(e instanceof Mob mob)) {
            return;
        }
        for (Session s : SESSIONS.values()) {
            for (Link l : s.links) {
                if (l.mob == mob) {
                    mob.setYRot(l.yaw);
                    mob.setYHeadRot(l.yaw);
                    mob.setYBodyRot(l.yaw);
                    mob.setXRot(l.pitch);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    static void onDespawn(MobDespawnEvent event) {
        if (isPossessed(event.getEntity())) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session s = SESSIONS.get(player.getUUID());
            if (s != null) {
                disconnectAll(player.server, s, "OPERATOR DOWN");
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session s = SESSIONS.get(player.getUUID());
            if (s != null) {
                disconnectAll(player.server, s, "OPERATOR LOST");
            }
        }
    }

    static void nexusRemoved(NexusBlockEntity nexus) {
        if (nexus.getLevel() == null || nexus.getLevel().getServer() == null) {
            return;
        }
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (s.nexus.equals(nexus.getBlockPos()) && s.dimension == nexus.getLevel().dimension()) {
                for (Link link : new ArrayList<>(s.links)) {
                    release(link);
                }
                s.links.clear();
                SESSIONS.remove(s.player);
                ServerPlayer player = nexus.getLevel().getServer().getPlayerList().getPlayer(s.player);
                if (player != null) {
                    s.controlling = false;
                    sendState(player, s);
                }
            }
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        for (Session s : SESSIONS.values()) {
            s.links.forEach(PossessionManager::release);
        }
        SESSIONS.clear();
        POSSESSED.clear();
    }

    private PossessionManager() {
    }
}
