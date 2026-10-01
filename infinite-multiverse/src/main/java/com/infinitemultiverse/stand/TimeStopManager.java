package com.infinitemultiverse.stand;

import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.network.TimeStopPayload;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side time stop. Frozen entities are pinned in place every tick with AI and gravity suspended; their
 * velocity, fuse and AI flags are restored on release. Damage dealt to a frozen creature is held and applied in
 * one hit when time resumes. Every frozen entity carries a persistent marker so a crash mid-stop is repaired the
 * next time the entity loads.
 */
public final class TimeStopManager {
    private static final String MARKER = "infinitemultiverse_time_frozen";
    private static final double OWN_PROJECTILE_GRACE = 1.5;

    private static final List<TimeStop> ACTIVE = new ArrayList<>();
    private static final Map<UUID, TimeStop> FROZEN_BY = new HashMap<>();

    private TimeStopManager() {
    }

    private static final class Frozen {
        final Entity entity;
        final Vec3 position;
        final Vec3 velocity;
        final boolean hadNoAi;
        final boolean hadNoGravity;
        final int fuse;
        @Nullable
        DamageSource pendingSource;
        float pendingDamage;

        Frozen(Entity entity) {
            this.entity = entity;
            this.position = entity.position();
            this.velocity = entity.getDeltaMovement();
            this.hadNoAi = entity instanceof Mob mob && mob.isNoAi();
            this.hadNoGravity = entity.isNoGravity();
            this.fuse = entity instanceof PrimedTnt tnt ? tnt.getFuse() : 0;
        }
    }

    private static final class TimeStop {
        final UUID owner;
        final ServerLevel level;
        final double radius;
        int ticksLeft;
        int age;
        final Map<UUID, Frozen> frozen = new LinkedHashMap<>();

        TimeStop(ServerPlayer owner, double radius, int duration) {
            this.owner = owner.getUUID();
            this.level = owner.serverLevel();
            this.radius = radius;
            this.ticksLeft = duration;
        }
    }

    public static boolean isActive(ServerPlayer owner) {
        for (TimeStop stop : ACTIVE) {
            if (stop.owner.equals(owner.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFrozen(Entity entity) {
        return FROZEN_BY.containsKey(entity.getUUID());
    }

    public static void start(ServerPlayer owner, double radius, int duration, Component shout) {
        TimeStop stop = new TimeStop(owner, radius, duration);
        ACTIVE.add(stop);
        freezeNearby(stop, owner);
        Vec3 center = owner.position();
        MultiverseVfx.sound(stop.level, center, ModSounds.TIME_STOP, 1.4f, 1.0f);
        MultiverseVfx.broadcast(stop.level, VfxIds.TIME_STOP, center.add(0.0, 1.0, 0.0), Vec3.ZERO, (float) radius);
        broadcastState(stop, owner, true, duration);
        MultiverseVfx.shout(stop.level, center, shout, radius * 2.0);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<TimeStop> it = ACTIVE.iterator();
        while (it.hasNext()) {
            TimeStop stop = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(stop.owner);
            stop.age++;
            if (owner == null || !owner.isAlive() || owner.level() != stop.level || --stop.ticksLeft <= 0) {
                it.remove();
                release(stop, owner);
                continue;
            }
            if (stop.age % 2 == 0) {
                freezeNearby(stop, owner);
            }
            pin(stop);
        }
    }

    public static void endAll(ServerPlayer owner) {
        Iterator<TimeStop> it = ACTIVE.iterator();
        while (it.hasNext()) {
            TimeStop stop = it.next();
            if (stop.owner.equals(owner.getUUID())) {
                it.remove();
                release(stop, owner);
            }
        }
    }

    /** Returns true if the damage was held back because the target is frozen in time. */
    public static boolean holdDamage(LivingEntity target, DamageSource source, float amount) {
        TimeStop stop = FROZEN_BY.get(target.getUUID());
        if (stop == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        Frozen frozen = stop.frozen.get(target.getUUID());
        if (frozen == null) {
            return false;
        }
        frozen.pendingDamage += amount;
        frozen.pendingSource = source;
        return true;
    }

    /** Repairs entities that were frozen when the server stopped or crashed. */
    public static void repairIfStale(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(MARKER) || isFrozen(entity)) {
            return;
        }
        CompoundTag marker = data.getCompound(MARKER);
        if (entity instanceof Mob mob) {
            mob.setNoAi(marker.getBoolean("noAi"));
        }
        entity.setNoGravity(marker.getBoolean("noGravity"));
        data.remove(MARKER);
    }

    public static void clear() {
        for (TimeStop stop : ACTIVE) {
            release(stop, null);
        }
        ACTIVE.clear();
        FROZEN_BY.clear();
    }

    // ---- internals ----

    private static void freezeNearby(TimeStop stop, ServerPlayer owner) {
        double radiusSqr = stop.radius * stop.radius;
        boolean freezePlayers = MultiverseConfig.SERVER.timeStopFreezesPlayers.get();
        for (Entity entity : stop.level.getEntities((Entity) null, owner.getBoundingBox().inflate(stop.radius), e -> e.distanceToSqr(owner) <= radiusSqr)) {
            if (shouldFreeze(owner, entity, freezePlayers)) {
                freeze(stop, entity);
            }
        }
    }

    private static boolean shouldFreeze(ServerPlayer owner, Entity entity, boolean freezePlayers) {
        if (entity == owner || entity.isRemoved() || entity.isPassenger() || isFrozen(entity)) {
            return false;
        }
        if (entity instanceof StandEntity stand && owner.getUUID().equals(stand.ownerId())) {
            return false;
        }
        if (entity instanceof Player player) {
            return freezePlayers && !player.isCreative() && !player.isSpectator();
        }
        if (entity instanceof Projectile projectile && projectile.getOwner() == owner) {
            return projectile.distanceTo(owner) > OWN_PROJECTILE_GRACE;
        }
        return true;
    }

    private static void freeze(TimeStop stop, Entity entity) {
        Frozen frozen = new Frozen(entity);
        stop.frozen.put(entity.getUUID(), frozen);
        FROZEN_BY.put(entity.getUUID(), stop);

        CompoundTag marker = new CompoundTag();
        marker.putBoolean("noAi", frozen.hadNoAi);
        marker.putBoolean("noGravity", frozen.hadNoGravity);
        entity.getPersistentData().put(MARKER, marker);

        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
    }

    private static void pin(TimeStop stop) {
        Iterator<Frozen> it = stop.frozen.values().iterator();
        while (it.hasNext()) {
            Frozen frozen = it.next();
            Entity entity = frozen.entity;
            if (entity.isRemoved()) {
                it.remove();
                FROZEN_BY.remove(entity.getUUID());
                continue;
            }
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof ServerPlayer player) {
                if (player.position().distanceToSqr(frozen.position) > 0.0025) {
                    player.connection.teleport(frozen.position.x, frozen.position.y, frozen.position.z, player.getYRot(), player.getXRot());
                }
            } else {
                entity.setPos(frozen.position.x, frozen.position.y, frozen.position.z);
            }
            if (entity instanceof PrimedTnt tnt) {
                tnt.setFuse(frozen.fuse);
            }
        }
    }

    private static void release(TimeStop stop, @Nullable ServerPlayer owner) {
        List<Frozen> frozen = new ArrayList<>(stop.frozen.values());
        stop.frozen.clear();
        for (Frozen f : frozen) {
            FROZEN_BY.remove(f.entity.getUUID());
        }
        for (Frozen f : frozen) {
            Entity entity = f.entity;
            if (entity.isRemoved()) {
                continue;
            }
            entity.getPersistentData().remove(MARKER);
            if (entity instanceof Mob mob) {
                mob.setNoAi(f.hadNoAi);
            }
            entity.setNoGravity(f.hadNoGravity);
            entity.setDeltaMovement(f.velocity);
            entity.hurtMarked = true;
            if (f.pendingDamage > 0f && f.pendingSource != null && entity instanceof LivingEntity living && living.isAlive()) {
                living.invulnerableTime = 0;
                living.hurt(f.pendingSource, f.pendingDamage);
                MultiverseVfx.broadcast(stop.level, VfxIds.STAND_HEAVY, living.getEyePosition(), Vec3.ZERO, 1f);
            }
        }
        Vec3 center = owner != null ? owner.position() : Vec3.ZERO;
        if (owner != null) {
            MultiverseVfx.sound(stop.level, center, ModSounds.TIME_RESUME, 1.2f, 1.0f);
            MultiverseVfx.broadcast(stop.level, VfxIds.TIME_RESUME, center.add(0.0, 1.0, 0.0), Vec3.ZERO, (float) stop.radius);
            broadcastState(stop, owner, false, 0);
            MultiverseVfx.shout(stop.level, center, Component.translatable("message.infinitemultiverse.shout.time_resume")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), stop.radius * 2.0);
        }
    }

    private static void broadcastState(TimeStop stop, ServerPlayer owner, boolean active, int duration) {
        Vec3 c = owner.position();
        PacketDistributor.sendToPlayersNear(stop.level, null, c.x, c.y, c.z, stop.radius * 2.0,
                new TimeStopPayload(active, owner.getId(), c, (float) stop.radius, duration));
    }
}
