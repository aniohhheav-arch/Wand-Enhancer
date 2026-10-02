package dev.riftverse.entity;

import dev.riftverse.multiverse.MigrationManager;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A wound in the universe opened by the Reality Rupture: a jagged vertical fracture that drags creatures and loose
 * objects toward it, tears at whatever it catches and, like a rift, sends anything that falls fully inside to another
 * reality. It grows open, holds, and seals itself.
 */
public class RealityTearEntity extends Entity {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(RealityTearEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(RealityTearEntity.class, EntityDataSerializers.INT);
    @Nullable
    private UUID owner;
    private int maxLife = 140;

    public RealityTearEntity(EntityType<? extends RealityTearEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 4f);
        builder.define(LIFE, 0);
    }

    public void setup(float size, int life, @Nullable UUID owner) {
        entityData.set(SIZE, size);
        this.maxLife = life;
        this.owner = owner;
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int maxLife() {
        return maxLife;
    }

    /** 0..1 how open the tear is (opens over 20 ticks, closes over the last 20). */
    public float openness(float partial) {
        float t = life() + partial;
        return Math.max(0f, Math.min(1f, Math.min(t / 20f, (maxLife - t) / 20f)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int l = life() + 1;
        entityData.set(LIFE, l);
        if (l >= maxLife) {
            ServerLevel level = (ServerLevel) level();
            level.sendParticles(RvParticles.RING.get().with(0xFFFFFF, size() * 2, 20), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, getX(), getY(), getZ(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 3f, 0.6f);
            discard();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        float k = openness(0f);
        float reach = size() * 4f * k;
        Vec3 c = position();
        if (l % 2 == 0) {
            level.sendParticles(RvParticles.STREAK.get().with(l % 4 == 0 ? 0xFFFFFF : 0xA040FF, 1.2f, 16), c.x, c.y, c.z, 6, size() * 0.2, size() * 0.6, size() * 0.2, 0.15);
            level.sendParticles(RvParticles.INFALL.get().with(0x6A10C0, 1f, 24), c.x, c.y, c.z, 8, reach * 0.5, reach * 0.5, reach * 0.5, 0.05);
        }
        if (l % 30 == 0) level.playSound(null, c.x, c.y, c.z, RvSounds.BLACK_HOLE_PULL.get(), SoundSource.PLAYERS, 2f, 0.7f);
        List<Entity> caught = level.getEntities(this, new AABB(c, c).inflate(reach), e -> !(e instanceof RealityTearEntity) && e.isAlive() && !e.isSpectator());
        for (Entity e : caught) {
            if (e instanceof Player p && (p.getUUID().equals(owner) || p.isCreative())) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = to.length();
            if (d < 1e-3 || d > reach) continue;
            if (d < 1.5 + size() * 0.15) {
                if (e instanceof LivingEntity le) {
                    if (l % 10 == 0) le.hurt(damageSources().magic(), 8f);
                    if (!(e instanceof Player) && le.getMaxHealth() <= 120f && MigrationManager.enabled(level)) MigrationManager.migrate(e, Destination.random(), 0xA040FF);
                } else {
                    e.discard();
                }
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().scale(0.9).add(to.scale((0.05 + 0.2 * k) / d)));
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard(); // tears never outlive the session that opened them
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
