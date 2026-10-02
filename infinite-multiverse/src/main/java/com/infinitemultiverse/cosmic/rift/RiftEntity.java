package com.infinitemultiverse.cosmic.rift;

import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.world.SafeTeleport;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A dimensional rift: a crackling tear in space. It drags nearby creatures, items and players toward its core, and
 * anything that touches the core is flung through to a random place 100–300 blocks away. Three hits seal it.
 */
public final class RiftEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HITS = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    private static final double PULL = 7;

    public RiftEntity(EntityType<? extends RiftEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIFETIME, 600);
        builder.define(HITS, 0);
    }

    public void setLifetime(int ticks) {
        entityData.set(LIFETIME, ticks);
    }

    public int lifetime() {
        return entityData.get(LIFETIME);
    }

    public int hits() {
        return entityData.get(HITS);
    }

    public Vec3 core() {
        return position().add(0, 1.5, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount == 1) {
            Cinematics.scene(level, SceneIds.RIFT_OPEN, core(), Vec3.ZERO, 0xB040FF, 30, this, 1f);
            MultiverseVfx.sound(level, core(), ModSounds.STAND_TIME_ERASE, 1.4f, 0.6f);
        }
        if (tickCount >= lifetime()) {
            close(level);
            return;
        }
        float strength = Math.min(1f, tickCount / 30f) * Math.min(1f, (lifetime() - tickCount) / 30f);
        Vec3 core = core();
        for (Entity e : level.getEntities(this, getBoundingBox().inflate(PULL), e -> e.isAlive() && !(e instanceof RiftEntity) && !(e instanceof ArmorStand)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            Vec3 to = core.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d < 1.1 && tickCount > 20) {
                fling(level, e);
                continue;
            }
            if (d < PULL) {
                e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(0.035 * strength * (1 + (PULL - d) / PULL))));
                e.hurtMarked = true;
            }
        }
        if (tickCount % 40 == 0) {
            MultiverseVfx.sound(level, core, ModSounds.TEMPORAL_DRAG, 0.7f, 0.5f);
        }
    }

    private void fling(ServerLevel level, Entity e) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 100 + random.nextDouble() * 200;
        int x = (int) (getX() + Math.cos(angle) * distance), z = (int) (getZ() + Math.sin(angle) * distance);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 desired = new Vec3(x + 0.5, y, z + 0.5);
        Vec3 dest = SafeTeleport.findSafeSpot(level, e, desired, 4, 6).orElse(desired.add(0, 1, 0));
        Cinematics.scene(level, SceneIds.RIFT_TRANSIT, e.getBoundingBox().getCenter(), Vec3.ZERO, 0xB040FF, 20, null, 1f);
        e.teleportTo(level, dest.x, dest.y, dest.z, Set.<RelativeMovement>of(), e.getYRot(), e.getXRot());
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
        Cinematics.scene(level, SceneIds.RIFT_TRANSIT, dest.add(0, 1, 0), Vec3.ZERO, 0xB040FF, 20, null, 1f);
        MultiverseVfx.sound(level, dest, ModSounds.PHASE_STEP_ARRIVE, 1f, 0.6f);
    }

    private void close(ServerLevel level) {
        Cinematics.stop(level, SceneIds.RIFT_OPEN, core(), this);
        Cinematics.scene(level, SceneIds.RIFT_TRANSIT, core(), Vec3.ZERO, 0xB040FF, 20, null, 2f);
        MultiverseVfx.sound(level, core(), ModSounds.STAND_DISMISS, 1.2f, 0.6f);
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel level && source.getEntity() instanceof Player) {
            entityData.set(HITS, hits() + 1);
            MultiverseVfx.sound(level, core(), ModSounds.AEGIS_IMPACT, 1f, 0.7f);
            if (hits() >= 3) {
                close(level);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(LIFETIME, Math.max(60, tag.getInt("Lifetime") - tag.getInt("Age")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", lifetime());
        tag.putInt("Age", tickCount);
    }
}
