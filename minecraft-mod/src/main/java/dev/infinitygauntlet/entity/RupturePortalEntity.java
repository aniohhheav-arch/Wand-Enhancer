package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.registry.ModTriggers;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Space Stone's Dimensional Rupture: a turbulent spherical tear in space. The entity position is the sphere's
 * centre. Portals are linked in pairs through {@link PortalNetwork}; anything that enters the core of an open, linked
 * rupture is carried to its partner (any dimension), keeping its momentum.
 */
public class RupturePortalEntity extends Entity {
    public static final int OPEN_TICKS = 50;
    public static final int CLOSE_TICKS = 34;
    public static final byte PHASE_OPENING = 0;
    public static final byte PHASE_OPEN = 1;
    public static final byte PHASE_CLOSING = 2;
    public static final byte STYLE_PORTAL = 0;
    public static final byte STYLE_GATEWAY = 1;

    private static final String TRAVEL_COOLDOWN_KEY = "infinitygauntlet_portal_cd";
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(RupturePortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(RupturePortalEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> LINKED = SynchedEntityData.defineId(RupturePortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> STYLE = SynchedEntityData.defineId(RupturePortalEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> PHASE_AGE = SynchedEntityData.defineId(RupturePortalEntity.class, EntityDataSerializers.INT);

    /** Ticks since the current phase started; the server value is synced on phase change, then both sides count. */
    private int phaseAge;
    private int lifetime = -1;
    private UUID owner = new UUID(0, 0);
    private boolean exitDown;

    public RupturePortalEntity(EntityType<? extends RupturePortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static RupturePortalEntity create(ServerLevel level, Vec3 center, float radius, UUID owner, int lifetimeTicks, byte style, boolean exitDown) {
        RupturePortalEntity portal = new RupturePortalEntity(ModEntities.RUPTURE_PORTAL.get(), level);
        portal.moveTo(center.x, center.y, center.z, 0, 0);
        portal.entityData.set(RADIUS, radius);
        portal.entityData.set(STYLE, style);
        portal.owner = owner;
        portal.lifetime = lifetimeTicks;
        portal.exitDown = exitDown;
        level.addFreshEntity(portal);
        PortalNetwork.get(level.getServer()).put(new PortalNetwork.Entry(portal.getUUID(), level.dimension(), center, radius, null, owner,
                exitDown, level.getGameTime()));
        Vfx.sound(level, center, ModSounds.SPACE_PORTAL_OPEN, radius > 3 ? 4f : 2f, radius > 3 ? 0.75f : 1f);
        Vfx.camera(level, center, 24 + radius * 4, 0.35f + radius * 0.05f, 0, 0, 0, 0, 0);
        return portal;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 1.6f);
        builder.define(PHASE, PHASE_OPENING);
        builder.define(LINKED, false);
        builder.define(STYLE, STYLE_PORTAL);
        builder.define(PHASE_AGE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key) || PHASE_AGE.equals(key)) {
            phaseAge = entityData.get(PHASE_AGE);
        }
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public byte phase() {
        return entityData.get(PHASE);
    }

    public boolean isLinked() {
        return entityData.get(LINKED);
    }

    public byte style() {
        return entityData.get(STYLE);
    }

    public int phaseAge() {
        return phaseAge;
    }

    public boolean isOpen() {
        return phase() == PHASE_OPEN;
    }

    private void setPhase(byte phase) {
        phaseAge = 0;
        entityData.set(PHASE_AGE, 0);
        entityData.set(PHASE, phase);
    }

    public void startClosing() {
        if (phase() == PHASE_CLOSING || level().isClientSide) {
            return;
        }
        setPhase(PHASE_CLOSING);
        ServerLevel level = (ServerLevel) level();
        Vfx.sound(level, position(), ModSounds.SPACE_PORTAL_CLOSE, radius() > 3 ? 4f : 2f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        phaseAge++;
        if (level().isClientSide) {
            ClientEffects.portalTick(this);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        byte phase = phase();
        if (phase == PHASE_OPENING && phaseAge >= OPEN_TICKS) {
            setPhase(PHASE_OPEN);
            Vfx.send(level, VfxType.SHOCKWAVE, position(), new Vec3(0, 1, 0), 0x2A6EFF, 0x9FE8FF, radius() * 2.6f, 18);
        } else if (phase == PHASE_CLOSING) {
            if (phaseAge == CLOSE_TICKS - 4) {
                Vfx.send(level, VfxType.IMPLOSION, position(), Vec3.ZERO, 0x2A6EFF, 0xDDF6FF, radius() * 0.6f, 10);
                Vfx.camera(level, position(), 20 + radius() * 3, 0.5f, 0, 0, 0x9FE8FF, 0.25f, 6);
            }
            if (phaseAge >= CLOSE_TICKS) {
                PortalNetwork.get(level.getServer()).close(level.getServer(), getUUID());
                discard();
            }
            return;
        }

        if (lifetime > 0 && --lifetime == 0) {
            PortalNetwork.get(level.getServer()).close(level.getServer(), getUUID());
            startClosing();
            return;
        }

        if (tickCount % 10 == 0 || phase == PHASE_OPEN) {
            PortalNetwork network = PortalNetwork.get(level.getServer());
            PortalNetwork.Entry self = network.get(getUUID());
            if (self == null) {
                startClosing();
                return;
            }
            PortalNetwork.Entry partner = self.partner() == null ? null : network.get(self.partner());
            if (self.partner() != null && partner == null) {
                network.close(level.getServer(), getUUID());
                return;
            }
            if (isLinked() != (partner != null)) {
                entityData.set(LINKED, partner != null);
            }
            if (phase == PHASE_OPEN && partner != null) {
                transportEntities(level, partner);
            }
        }
    }

    private void transportEntities(ServerLevel level, PortalNetwork.Entry partner) {
        float r = radius();
        double core = r * 0.72;
        long now = level.getGameTime();
        AABB box = new AABB(position(), position()).inflate(r + 1);
        for (Entity e : level.getEntities(this, box, this::canTransport)) {
            Vec3 nearest = clampToBox(position(), e.getBoundingBox());
            if (nearest.distanceToSqr(position()) > core * core) {
                continue;
            }
            if (e.getPersistentData().getLong(TRAVEL_COOLDOWN_KEY) > now) {
                continue;
            }
            transport(level, e, partner, now);
        }
    }

    private boolean canTransport(Entity e) {
        if (!e.isAlive() || e.isSpectator() || e.isPassenger() || e instanceof RupturePortalEntity || e instanceof TimedEffectEntity) {
            return false;
        }
        if (e instanceof Projectile) {
            return ServerConfig.PORTALS_TRANSPORT_PROJECTILES.get();
        }
        if (e instanceof ItemEntity) {
            return ServerConfig.PORTALS_TRANSPORT_ITEMS.get();
        }
        if (e instanceof ServerPlayer) {
            return true;
        }
        return e instanceof LivingEntity ? ServerConfig.PORTALS_TRANSPORT_MOBS.get() : e.canUsePortal(false);
    }

    private void transport(ServerLevel level, Entity e, PortalNetwork.Entry partner, long now) {
        ServerLevel dest = level.getServer().getLevel(partner.dimension());
        if (dest == null || dest != level && !ServerConfig.CROSS_DIMENSION_PORTALS.get()) {
            return;
        }
        Vec3 velocity = e.getDeltaMovement();
        Vec3 dir;
        if (partner.exitDown()) {
            dir = new Vec3(0, -1, 0);
        } else {
            Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);
            if (horizontal.lengthSqr() < 0.0025) {
                // walking in slowly or standing: continue through along the line from entry side to core
                horizontal = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
            }
            if (horizontal.lengthSqr() < 1.0E-4) {
                horizontal = Vec3.directionFromRotation(0, e.getYRot());
            }
            dir = horizontal.normalize();
        }
        double half = e.getBbHeight() / 2;
        Vec3 exitCenter = partner.exitDown()
                ? partner.pos().add(0, -(partner.radius() + half + 0.6), 0)
                : partner.pos().add(dir.scale(partner.radius() + Math.max(0.6, e.getBbWidth()) * 0.5 + 0.7));
        Vec3 feet = exitCenter.subtract(0, half, 0);
        Vec3 safe = TeleportUtil.findSafeSpot(dest, e, feet, e instanceof ServerPlayer ? 6 : 3);
        if (safe == null) {
            // nowhere to stand at the destination: bounce the entity back out instead of trapping it
            e.getPersistentData().putLong(TRAVEL_COOLDOWN_KEY, now + 20);
            e.setDeltaMovement(e.position().subtract(position()).normalize().scale(0.6).add(0, 0.2, 0));
            e.hurtMarked = true;
            return;
        }
        double speed = Math.max(velocity.length(), 0.3);
        Vec3 newVelocity = partner.exitDown() ? new Vec3(velocity.x * 0.3, -speed, velocity.z * 0.3)
                : dir.scale(speed).add(0, Math.max(velocity.y, 0.05), 0);
        float yaw = partner.exitDown() ? e.getYRot() : (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;

        Vfx.send(level, VfxType.DISSOLVE, e.position(), new Vec3(e.getBbWidth(), e.getBbHeight(), 0), 0x2A6EFF, 0x9FE8FF, 1f, 14);
        Vfx.sound(level, position(), ModSounds.SPACE_TELEPORT, 1.2f, 0.9f + level.random.nextFloat() * 0.2f);
        e.getPersistentData().putLong(TRAVEL_COOLDOWN_KEY, now + 30);
        boolean crossDimension = dest != level;
        if (e instanceof ServerPlayer player) {
            Vfx.transition(player, crossDimension ? 34 : 22, InfinityStone.SPACE);
            ModTriggers.PORTAL_TRAVEL.get().trigger(player, crossDimension ? "cross_dimension" : "same_dimension");
        }
        if (TeleportUtil.teleport(e, dest, safe, yaw, e.getXRot(), newVelocity)) {
            Vfx.send(dest, VfxType.SPARK_BURST, safe.add(0, half, 0), Vec3.ZERO, 0x2A6EFF, 0x9FE8FF, 1.2f, 16);
            Vfx.send(dest, VfxType.SHOCKWAVE, partner.pos(), dir, 0x2A6EFF, 0x9FE8FF, partner.radius() * 1.8f, 12);
            Vfx.sound(dest, safe, ModSounds.SPACE_TELEPORT, 1.2f, 1.2f);
        }
    }

    private static Vec3 clampToBox(Vec3 p, AABB box) {
        return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
    }

    // ------------------------------------------------------------------ rendering helpers (shared by renderer + particles)

    /** 0..1 opening progress (1 once open). */
    public float openProgress(float partialTick) {
        return switch (phase()) {
            case PHASE_OPENING -> Math.min(1f, (phaseAge + partialTick) / OPEN_TICKS);
            default -> 1f;
        };
    }

    /** 0..1 closing progress (0 unless collapsing). */
    public float closeProgress(float partialTick) {
        return phase() == PHASE_CLOSING ? Math.min(1f, (phaseAge + partialTick) / CLOSE_TICKS) : 0f;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(radius() * 2.2 + 1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 160 + radius() * 16;
        return distance < d * d;
    }

    // ------------------------------------------------------------------ entity plumbing

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(RADIUS, tag.getFloat("radius"));
        entityData.set(STYLE, tag.getByte("style"));
        lifetime = tag.getInt("lifetime");
        exitDown = tag.getBoolean("exitDown");
        if (tag.hasUUID("owner")) {
            owner = tag.getUUID("owner");
        }
        // a reloaded portal re-opens with its animation rather than popping in
        setPhase(PHASE_OPENING);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("radius", radius());
        tag.putByte("style", style());
        tag.putInt("lifetime", lifetime);
        tag.putBoolean("exitDown", exitDown);
        tag.putUUID("owner", owner);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
