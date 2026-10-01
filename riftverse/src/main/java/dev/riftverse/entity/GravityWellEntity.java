package dev.riftverse.entity;

import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Gravity Gauntlet ability: hoists every creature nearby into a spinning knot, then slams them into the ground. */
public class GravityWellEntity extends Entity {
    public static final int DURATION = 70;
    public static final double RADIUS = 10.0;

    @Nullable
    private UUID ownerId;

    public GravityWellEntity(EntityType<? extends GravityWellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setOwner(Entity owner) {
        this.ownerId = owner.getUUID();
    }

    public float progress(float partial) {
        return Math.min(1f, (tickCount + partial) / DURATION);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 c = position();
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 2 + random.nextDouble() * RADIUS * 0.7;
                level().addParticle(RvParticles.INFALL.get().with(0x6FD8FF, 0.3f, 25), c.x + Math.cos(a) * r, c.y + (random.nextDouble() - 0.5) * 3, c.z + Math.sin(a) * r, c.x, c.y, c.z);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (tickCount < DURATION) {
            for (Entity e : level.getEntities(this, new AABB(c, c).inflate(RADIUS), e -> e instanceof LivingEntity && !e.getUUID().equals(ownerId) && !e.isSpectator())) {
                Vec3 to = c.subtract(e.position());
                double d = to.length();
                if (d > RADIUS) continue;
                Vec3 swirl = new Vec3(-to.z, 0, to.x).normalize().scale(0.18);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.7).add(to.normalize().scale(Math.min(0.35, d * 0.06))).add(swirl).add(0, 0.06, 0));
                e.hurtMarked = true;
                e.resetFallDistance();
            }
            if (tickCount % 20 == 0) level.playSound(null, c.x, c.y, c.z, RvSounds.GRAVITY_BEAM.get(), SoundSource.PLAYERS, 0.8f, 0.7f + tickCount / (float) DURATION);
            return;
        }
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        DamageSource src = owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic();
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(RADIUS), e -> e instanceof LivingEntity && !e.getUUID().equals(ownerId))) {
            e.setDeltaMovement(0, -2.4, 0);
            e.hurtMarked = true;
            e.hurt(src, 9.0f);
        }
        level.sendParticles(RvParticles.RING.get().with(0x6FD8FF, (float) RADIUS, 18), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.SPARK.get().with(0xD0F4FF, 0.7f, 20), c.x, c.y, c.z, 60, 2, 1, 2, 0.4);
        level.playSound(null, c.x, c.y, c.z, RvSounds.GRAVITY_PULSE.get(), SoundSource.PLAYERS, 1.6f, 0.6f);
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) ownerId = tag.getUUID("owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("owner", ownerId);
    }
}
