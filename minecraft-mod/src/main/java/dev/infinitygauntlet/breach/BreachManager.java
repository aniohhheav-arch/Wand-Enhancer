package dev.infinitygauntlet.breach;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.PlayerPowerData;
import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative Reality Breach sessions (Mind + Space).
 * <p>
 * Casting opens a rift that clings to the target. The caster walks into it and arrives in their own white
 * observation chamber, where a containment unit mirrors the real target live and a floating panel manipulates it.
 * Every control acts on the real entity in its own world; the chamber only ever holds representations. The
 * caster leaves through the return rift and may re-enter as long as the breach stands. Sealing the breach
 * (Release, re-cast, timeout, logout, target lost) reverts every manipulation and removes every chamber object.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class BreachManager {
    private static final ResourceLocation SCALE_MOD = InfinityGauntlet.id("breach_scale");
    private static final ResourceLocation VISION_MOD = InfinityGauntlet.id("breach_vision");
    private static final float[] SCALES = {0.3f, 0.55f, 1f, 1.6f, 2.4f};
    private static final int MAX_TICKS = 20 * 60 * 10;
    private static final int ENTRY_COOLDOWN = 30;
    private static final String COOLDOWN_KEY = "infinitygauntlet_breach_cd";
    private static final double PANEL_REACH = 6;
    /**
     * Keeps the target's chunk loaded and entity-ticking while its breach is open. Without it, the target unloads
     * as soon as its world has no player nearby (the caster is in the chamber) and the link is lost.
     */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> ANCHOR =
            net.minecraft.server.level.TicketType.create("infinitygauntlet_breach",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 80);

    static final class Session {
        final UUID player;
        final LivingEntity target;
        final int cell;
        final long started;
        DimensionalRiftEntity rift;
        DimensionalRiftEntity returnRift;
        ObservationPanelEntity panel;
        SpecimenEntity specimen;
        @Nullable
        GlobalPos returnPos;
        float returnYaw;
        boolean lost;
        int flags;
        int scaleStep = 2;
        /** Until the caster first steps through, the target is held motionless at the tear. */
        boolean entered;
        long lastSurvey;

        Session(UUID player, LivingEntity target, int cell, long started) {
            this.player = player;
            this.target = target;
            this.cell = cell;
            this.started = started;
        }

        boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    @Nullable
    static Session session(UUID player) {
        return SESSIONS.get(player);
    }

    public static boolean hasBreach(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** The player's chamber panel, if their breach has one (tests and commands). */
    @Nullable
    public static ObservationPanelEntity panelOf(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.panel;
    }

    @Nullable
    public static DimensionalRiftEntity riftOf(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.rift;
    }

    /** Testing command: pass through the player's breach without walking into it. */
    public static boolean forceTraverse(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return false;
        }
        if (ChamberDimension.isChamber(player.level())) {
            leave(player, s);
        } else if (!s.lost) {
            enter(player, s);
        }
        return true;
    }

    // ------------------------------------------------------------------ opening

    public static CastResult open(ServerPlayer player, LivingEntity target) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            close(player.server, existing, "sealed");
            return CastResult.utility(Component.translatable("message.infinitygauntlet.breach.sealed"));
        }
        if (ChamberDimension.isChamber(target.level()) || target instanceof Player p && !TargetRules.canTarget(player, p)
                || target.getBbHeight() > 4.5f) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.breach.refused"));
        }
        ServerLevel level = (ServerLevel) target.level();
        Session s = new Session(player.getUUID(), target, ChamberDimension.cellFor(player.server, player.getUUID()), level.getGameTime());
        s.rift = DimensionalRiftEntity.breach(level, target, player);
        SESSIONS.put(player.getUUID(), s);
        anchor(s);
        TimeManager.apply(target, 0f, 3);

        Vec3 c = target.getBoundingBox().getCenter();
        Vfx.sound(level, c, ModSounds.BREACH_OPEN, 3f, 1f);
        Vfx.glitch(level, c, 40, 1f);
        Vfx.send(level, VfxType.SHOCKWAVE, c, new Vec3(0, 1, 0), 0xFF46C8, 0x5AE6FF, s.rift.radius() * 3, 14);
        Vfx.send(level, VfxType.DISTORTION, c, Vec3.ZERO, 0x5AE6FF, 0xFFFFFF, s.rift.radius() * 1.4f, 30);
        Vfx.camera(level, c, 32, 0.6f, 0, 0, 0xFFFFFF, 0.25f, 6);
        return CastResult.success(Component.translatable("message.infinitygauntlet.breach.opened", target.getDisplayName()));
    }

    // ------------------------------------------------------------------ passing through

    /** Called by open rifts every other tick: moves the owner through if they stepped into the core. */
    static void riftTouch(DimensionalRiftEntity rift) {
        UUID ownerId = rift.ownerId();
        if (ownerId == null || !(rift.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        Session s = SESSIONS.get(ownerId);
        if (owner == null || s == null || owner.level() != level || !rift.isInside(owner) || owner.isSpectator()
                || owner.getPersistentData().getLong(COOLDOWN_KEY) > level.getGameTime()) {
            return;
        }
        if (rift.isReturn()) {
            leave(owner, s);
            if (s.lost) {
                close(level.getServer(), s, "lost");
            }
        } else if (rift == s.rift) {
            enter(owner, s);
        }
    }

    static void enter(ServerPlayer player, Session s) {
        MinecraftServer server = player.server;
        ServerLevel chamber = server.getLevel(ChamberDimension.KEY);
        anchor(s);
        if (chamber == null) {
            InfinityGauntlet.LOGGER.warn("Observation chamber dimension is not loaded in this world");
            player.getPersistentData().putLong(COOLDOWN_KEY, player.serverLevel().getGameTime() + 100);
            player.displayClientMessage(Component.translatable("message.infinitygauntlet.breach.no_chamber"), true);
            return;
        }
        ServerLevel from = player.serverLevel();
        Vec3 riftPos = s.rift.position();
        Vec3 away = player.position().subtract(riftPos).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(-1, 0, -1).normalize() : away.normalize();
        Vec3 back = TeleportUtil.findSafeSpot(from, player, riftPos.add(away.scale(s.rift.radius() + 1.4)).subtract(0, s.rift.radius() * 0.5, 0), 4);
        s.returnPos = GlobalPos.of(from.dimension(), BlockPos.containing(back == null ? player.position() : back));
        s.returnYaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
        player.getData(ModAttachments.POWER_DATA).chamberReturn = s.returnPos;

        ChamberDimension.ensureBuilt(chamber, s.cell);
        s.entered = true;
        spawnChamberObjects(chamber, s);
        Vfx.dissolve(from, player, InfinityStone.SPACE);
        Vfx.sound(from, riftPos, ModSounds.BREACH_ENTER, 1.5f, 1f);
        Vfx.transition(player, 30, InfinityStone.MIND);
        Vec3 arrival = ChamberDimension.arrival(s.cell);
        player.getPersistentData().putLong(COOLDOWN_KEY, chamber.getGameTime() + ENTRY_COOLDOWN);
        TeleportUtil.teleport(player, chamber, arrival, 0f, 8f, Vec3.ZERO);
        player.getPersistentData().putLong(COOLDOWN_KEY, chamber.getGameTime() + ENTRY_COOLDOWN);
        Vfx.sound(chamber, arrival, ModSounds.BREACH_ENTER, 1.5f, 0.8f);
        Vfx.glitch(chamber, arrival, 16, 1f);
        sendSurvey(player, s);
    }

    static void leave(ServerPlayer player, Session s) {
        MinecraftServer server = player.server;
        ServerLevel to = s.returnPos == null ? null : server.getLevel(s.returnPos.dimension());
        Vec3 dest;
        if (to == null) {
            to = server.overworld();
            BlockPos spawn = to.getSharedSpawnPos();
            dest = Vec3.atBottomCenterOf(spawn);
        } else {
            dest = Vec3.atBottomCenterOf(s.returnPos.pos());
        }
        Vec3 safe = TeleportUtil.findSafeSpot(to, player, dest, 6);
        Vfx.sound(player.serverLevel(), player.position(), ModSounds.BREACH_ENTER, 1.5f, 1.3f);
        Vfx.transition(player, 26, InfinityStone.SPACE);
        TeleportUtil.teleport(player, to, safe == null ? dest : safe, s.returnYaw, 0f, Vec3.ZERO);
        player.getPersistentData().putLong(COOLDOWN_KEY, to.getGameTime() + ENTRY_COOLDOWN);
        Vfx.glitch(to, player.position(), 16, 0.8f);
        Vfx.sound(to, player.position(), ModSounds.BREACH_ENTER, 1.2f, 1.1f);
    }

    private static void spawnChamberObjects(ServerLevel chamber, Session s) {
        if (s.returnRift == null || s.returnRift.isRemoved()) {
            s.returnRift = DimensionalRiftEntity.returning(chamber, ChamberDimension.returnRift(s.cell), s.player);
        }
        if (s.specimen == null || s.specimen.isRemoved()) {
            s.specimen = new SpecimenEntity(ModEntities.SPECIMEN.get(), chamber);
            Vec3 c = ChamberDimension.containment(s.cell);
            s.specimen.moveTo(c.x, c.y, c.z, 180f, 0f);
            if (s.target.isAlive()) {
                s.specimen.mirror(s.target, s.flags);
            }
            chamber.addFreshEntity(s.specimen);
        }
        if (s.panel == null || s.panel.isRemoved()) {
            s.panel = new ObservationPanelEntity(ModEntities.OBSERVATION_PANEL.get(), chamber);
            Vec3 p = ChamberDimension.panel(s.cell);
            s.panel.moveTo(p.x, p.y, p.z, 180f, 0f); // faces north, toward the arrival point
            s.panel.bindSpecimen(s.specimen);
            chamber.addFreshEntity(s.panel);
        }
    }

    // ------------------------------------------------------------------ controls

    public static void press(ServerPlayer player, int panelId, int controlIndex) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.panel == null || s.panel.getId() != panelId || controlIndex < 0 || controlIndex >= PanelControl.values().length
                || player.level() != s.panel.level() || player.distanceTo(s.panel) > PANEL_REACH) {
            return;
        }
        PanelControl control = PanelControl.values()[controlIndex];
        ServerLevel chamber = (ServerLevel) s.panel.level();
        s.panel.triggerAnim("press", "press");
        Vfx.sound(chamber, s.panel.position(), ModSounds.PANEL_PRESS, 1f, 0.9f + controlIndex * 0.03f);
        if (control == PanelControl.RELEASE) {
            close(player.server, s, "released");
            return;
        }
        if (control == PanelControl.RESET) {
            revert(s);
            Vfx.glitch(chamber, s.specimen.position(), 24, 0.7f);
            return;
        }
        if (s.lost || !s.target.isAlive()) {
            player.displayClientMessage(Component.translatable("message.infinitygauntlet.breach.no_specimen"), true);
            return;
        }
        apply(s, control);
        Vfx.send(chamber, VfxType.SPARK_BURST, s.specimen.position().add(0, 1.4, 0), Vec3.ZERO, 0x5AE6FF, 0xFF46C8, 0.8f, 10);
        Vfx.glitch(chamber, s.specimen.position(), 24, 0.35f);
        ServerLevel real = (ServerLevel) s.target.level();
        Vfx.send(real, VfxType.SPARK_BURST, s.target.getBoundingBox().getCenter(), Vec3.ZERO, 0x5AE6FF, 0xFF46C8, 0.8f, 10);
        Vfx.glitch(real, s.target.position(), 20, 0.4f);
        s.specimen.mirror(s.target, s.flags);
    }

    private static void apply(Session s, PanelControl control) {
        LivingEntity t = s.target;
        ServerLevel level = (ServerLevel) t.level();
        switch (control) {
            case MOVE_WEST -> shift(t, level, new Vec3(-2, 0, 0));
            case MOVE_NORTH -> shift(t, level, new Vec3(0, 0, -2));
            case MOVE_SOUTH -> shift(t, level, new Vec3(0, 0, 2));
            case MOVE_EAST -> shift(t, level, new Vec3(2, 0, 0));
            case LIFT -> toggle(s, SpecimenEntity.FLAG_LEVITATING, () -> t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 30, 1)),
                    () -> t.removeEffect(MobEffects.LEVITATION));
            case DROP -> {
                if (s.has(SpecimenEntity.FLAG_LEVITATING)) {
                    s.flags &= ~SpecimenEntity.FLAG_LEVITATING;
                    t.removeEffect(MobEffects.LEVITATION);
                }
                t.setDeltaMovement(t.getDeltaMovement().add(0, -1.2, 0));
                t.hurtMarked = true;
            }
            case BLIND -> toggle(s, SpecimenEntity.FLAG_BLIND, () -> {
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 600, 0));
                t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 600, 0));
                modifier(t, Attributes.FOLLOW_RANGE, VISION_MOD, -0.9);
                if (t instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }, () -> {
                t.removeEffect(MobEffects.BLINDNESS);
                t.removeEffect(MobEffects.DARKNESS);
                modifier(t, Attributes.FOLLOW_RANGE, VISION_MOD, 0);
            });
            case GLOW -> toggle(s, SpecimenEntity.FLAG_GLOWING, () -> t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0)),
                    () -> t.removeEffect(MobEffects.GLOWING));
            case TURN_LEFT -> turn(t, -45);
            case TURN_RIGHT -> turn(t, 45);
            case PACIFY -> toggle(s, SpecimenEntity.FLAG_PACIFIED, () -> {
                if (t instanceof Mob mob) {
                    mob.targetSelector.disableControlFlag(Goal.Flag.TARGET);
                    mob.setTarget(null);
                }
            }, () -> {
                if (t instanceof Mob mob) {
                    mob.targetSelector.enableControlFlag(Goal.Flag.TARGET);
                }
            });
            case SHRINK -> rescale(s, s.scaleStep - 1);
            case GROW -> rescale(s, s.scaleStep + 1);
            case FREEZE -> toggle(s, SpecimenEntity.FLAG_FROZEN, () -> TimeManager.apply(t, 0f, MAX_TICKS), () -> TimeManager.clear(t));
            case PHASE -> toggle(s, SpecimenEntity.FLAG_PHASED, () -> t.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 600, 0)),
                    () -> t.removeEffect(MobEffects.INVISIBILITY));
            default -> {
            }
        }
    }

    private static void toggle(Session s, int flag, Runnable on, Runnable off) {
        if (s.has(flag)) {
            s.flags &= ~flag;
            off.run();
        } else {
            s.flags |= flag;
            on.run();
        }
    }

    private static void shift(LivingEntity t, ServerLevel level, Vec3 offset) {
        Vec3 safe = TeleportUtil.findSafeSpot(level, t, t.position().add(offset), 2);
        if (safe == null) {
            return;
        }
        Vfx.send(level, VfxType.DISSOLVE, t.position(), new Vec3(t.getBbWidth(), t.getBbHeight(), 0), 0x5AE6FF, 0xFF46C8, 1f, 12);
        TeleportUtil.teleport(t, level, safe, t.getYRot(), t.getXRot(), Vec3.ZERO);
        if (t instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private static void turn(LivingEntity t, float degrees) {
        float yaw = t.getYRot() + degrees;
        t.setYRot(yaw);
        t.setYHeadRot(yaw);
        t.setYBodyRot(yaw);
        if (t instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(t.getEyePosition().add(Vec3.directionFromRotation(0, yaw).scale(8)));
        }
    }

    private static void rescale(Session s, int step) {
        int clamped = Math.max(0, Math.min(SCALES.length - 1, step));
        if (clamped == s.scaleStep) {
            return;
        }
        s.scaleStep = clamped;
        modifier(s.target, Attributes.SCALE, SCALE_MOD, SCALES[clamped] - 1.0);
        Vfx.sound((ServerLevel) s.target.level(), s.target.position(), ModSounds.REALITY_GLITCH, 1f, clamped < 2 ? 1.5f : 0.7f);
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Undoes every manipulation this breach applied to its target. */
    private static void revert(Session s) {
        LivingEntity t = s.target;
        removeIf(s, SpecimenEntity.FLAG_LEVITATING, t, MobEffects.LEVITATION);
        removeIf(s, SpecimenEntity.FLAG_GLOWING, t, MobEffects.GLOWING);
        removeIf(s, SpecimenEntity.FLAG_PHASED, t, MobEffects.INVISIBILITY);
        if (s.has(SpecimenEntity.FLAG_BLIND)) {
            t.removeEffect(MobEffects.BLINDNESS);
            t.removeEffect(MobEffects.DARKNESS);
        }
        modifier(t, Attributes.FOLLOW_RANGE, VISION_MOD, 0);
        modifier(t, Attributes.SCALE, SCALE_MOD, 0);
        if (s.has(SpecimenEntity.FLAG_FROZEN)) {
            TimeManager.clear(t);
        }
        if (s.has(SpecimenEntity.FLAG_PACIFIED) && t instanceof Mob mob) {
            mob.targetSelector.enableControlFlag(Goal.Flag.TARGET);
        }
        s.flags = 0;
        s.scaleStep = 2;
    }

    private static void removeIf(Session s, int flag, LivingEntity t, Holder<MobEffect> effect) {
        if (s.has(flag)) {
            t.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    static void close(MinecraftServer server, Session s, String reason) {
        SESSIONS.remove(s.player);
        if (!s.lost && s.target.isAlive()) {
            revert(s);
        }
        if (s.rift != null && !s.rift.isRemoved()) {
            s.rift.startClosing();
            if (s.rift.level() instanceof ServerLevel level) {
                Vfx.sound(level, s.rift.position(), ModSounds.SPACE_PORTAL_CLOSE, 2f, 1.3f);
                Vfx.glitch(level, s.rift.position(), 30, 0.8f);
            }
        }
        ServerPlayer player = server.getPlayerList().getPlayer(s.player);
        if (player != null && ChamberDimension.isChamber(player.level())) {
            leave(player, s);
        }
        for (Entity e : new Entity[]{s.returnRift, s.panel, s.specimen}) {
            if (e != null && !e.isRemoved()) {
                if (e instanceof DimensionalRiftEntity r) {
                    r.startClosing();
                } else {
                    e.discard();
                }
            }
        }
        if (player != null) {
            player.getData(ModAttachments.POWER_DATA).chamberReturn = null;
            player.displayClientMessage(Component.translatable("message.infinitygauntlet.breach." + reason), true);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            long now = server.overworld().getGameTime();
            if (player == null) {
                close(server, s, "sealed");
                continue;
            }
            if (now - s.started > MAX_TICKS) {
                close(server, s, "collapsed");
                continue;
            }
            if (!s.lost && (!s.target.isAlive() || s.target.isRemoved())) {
                s.lost = true;
                if (s.rift != null) {
                    s.rift.startClosing();
                }
                if (s.specimen != null && !s.specimen.isRemoved()) {
                    s.specimen.markLost();
                    Vfx.sound((ServerLevel) s.specimen.level(), s.specimen.position(), ModSounds.SPECIMEN_LOST, 2f, 1f);
                    Vfx.glitch((ServerLevel) s.specimen.level(), s.specimen.position(), 32, 1f);
                }
                player.displayClientMessage(Component.translatable("message.infinitygauntlet.breach.lost"), true);
                if (!ChamberDimension.isChamber(player.level())) {
                    close(server, s, "lost");
                }
                continue;
            }
            boolean inside = ChamberDimension.isChamber(player.level());
            if (!s.lost) {
                upkeep(s);
                if (s.specimen != null && !s.specimen.isRemoved()) {
                    s.specimen.mirror(s.target, s.flags);
                }
                if (inside && now - s.lastSurvey >= 40) {
                    s.lastSurvey = now;
                    sendSurvey(player, s);
                }
                if (inside && now % 2 == 0) {
                    sendView(player, s);
                }
            }
            if (inside) {
                Vec3 origin = ChamberDimension.origin(s.cell);
                if (player.getY() < origin.y - 8 || Math.abs(player.getX() - origin.x) > 40 || Math.abs(player.getZ() - origin.z) > 40) {
                    player.teleportTo(ChamberDimension.arrival(s.cell).x, ChamberDimension.arrival(s.cell).y, ChamberDimension.arrival(s.cell).z);
                }
            }
        }
    }

    private static void anchor(Session s) {
        if (s.target.level() instanceof ServerLevel level) {
            // forceTicks: the target keeps ticking (moving, being manipulated, mirrored) with no player in its world
            level.getChunkSource().addRegionTicket(ANCHOR, s.target.chunkPosition(), 2, s.target.chunkPosition(), true);
        }
    }

    /** Keeps timed manipulations alive while their toggle is on. */
    private static void upkeep(Session s) {
        LivingEntity t = s.target;
        // refresh by server time: the target's own tick counter stops if its chunk ever stops ticking
        if (t.level().getGameTime() % 20 == 0 || !s.entered) {
            anchor(s);
        }
        if (!s.entered) {
            TimeManager.apply(t, 0f, 3); // held at the tear until the caster passes through
        } else if (t.level() instanceof ServerLevel level) {
            // Vanilla stops ticking every entity in a level that has had no players for 15 seconds. With the caster in
            // the chamber the target's world is usually empty, so keep it awake: the target must stay live, not frozen.
            level.resetEmptyTime();
        }
        if (t.tickCount % 100 == 0) {
            if (s.has(SpecimenEntity.FLAG_LEVITATING)) {
                t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 30, 1));
            }
        }
        if (s.has(SpecimenEntity.FLAG_PACIFIED) && t instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    private static final int SLICE_RADIUS = 16, SLICE_DOWN = 8, SLICE_UP = 10;

    /** Streams the blocks around the target (the live view's world, and the survey hologram). */
    static void sendSurvey(ServerPlayer player, Session s) {
        if (s.specimen == null || s.lost) {
            return;
        }
        Level level = s.target.level();
        BlockPos base = s.target.blockPosition().offset(-SLICE_RADIUS, -SLICE_DOWN, -SLICE_RADIUS);
        int sx = 2 * SLICE_RADIUS + 1, sy = SLICE_DOWN + SLICE_UP + 1, sz = 2 * SLICE_RADIUS + 1;
        int[] states = new int[sx * sy * sz];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    m.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    states[i++] = Block.getId(level.getBlockState(m));
                }
            }
        }
        ModNetwork.sendTo(player, new dev.infinitygauntlet.network.SlicePayload(s.specimen.getId(), base, sx, sy, sz, states));
    }

    /** Streams the target's eyes and the creatures around it. */
    static void sendView(ServerPlayer player, Session s) {
        if (s.specimen == null || s.lost) {
            return;
        }
        LivingEntity t = s.target;
        List<dev.infinitygauntlet.network.ViewPayload.Creature> list = new ArrayList<>();
        for (Entity e : t.level().getEntities(t, t.getBoundingBox().inflate(SLICE_RADIUS), e -> e instanceof LivingEntity && e.isAlive())) {
            if (list.size() >= 32) {
                break;
            }
            LivingEntity l = (LivingEntity) e;
            double dx = l.getX() - l.xo, dz = l.getZ() - l.zo;
            list.add(new dev.infinitygauntlet.network.ViewPayload.Creature(l.getId(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(l.getType()), l.position(), l.yBodyRot, l.yHeadRot, l.getXRot(),
                    (float) Math.sqrt(dx * dx + dz * dz), l.isBaby(), l.getScale(), l instanceof Player p ? p.getGameProfile().getName() : ""));
        }
        ModNetwork.sendTo(player, new dev.infinitygauntlet.network.ViewPayload(s.specimen.getId(), t.getEyePosition(), t.getYHeadRot(), t.getXRot(), list));
    }

    // ------------------------------------------------------------------ safety nets

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ChamberDimension.isChamber(player.level())
                || SESSIONS.containsKey(player.getUUID())) {
            return;
        }
        // stranded in a chamber without a live breach (relog or restart): send them home
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        ServerLevel to = data.chamberReturn == null ? null : player.server.getLevel(data.chamberReturn.dimension());
        Vec3 dest;
        if (to == null) {
            to = player.server.overworld();
            dest = Vec3.atBottomCenterOf(to.getSharedSpawnPos());
        } else {
            dest = Vec3.atBottomCenterOf(data.chamberReturn.pos());
        }
        Vec3 safe = TeleportUtil.findSafeSpot(to, player, dest, 8);
        TeleportUtil.teleport(player, to, safe == null ? dest : safe, player.getYRot(), 0, Vec3.ZERO);
        data.chamberReturn = null;
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && ChamberDimension.isChamber(level) && !(event.getEntity() instanceof Player p && p.isCreative())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (!s.lost && s.target.isAlive()) {
                revert(s);
            }
        }
        SESSIONS.clear();
    }


    private BreachManager() {
    }
}
