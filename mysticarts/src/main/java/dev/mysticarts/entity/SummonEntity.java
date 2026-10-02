package dev.mysticarts.entity;

import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.registry.MaParticles;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Base for conjured allies: bound to an owner, fights the owner's foes, fades when its time runs out. */
public abstract class SummonEntity extends PathfinderMob {
    protected static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(SummonEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    protected static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SummonEntity.class, EntityDataSerializers.INT);

    protected SummonEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(LIFE, 400);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    public void bind(LivingEntity owner, int lifetime) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(LIFE, lifetime);
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    @Nullable
    public LivingEntity ownerEntity() {
        UUID id = ownerId();
        if (id == null) return null;
        if (level() instanceof ServerLevel s && s.getEntity(id) instanceof LivingEntity l) return l;
        Player p = level().getPlayerByUUID(id);
        return p;
    }

    public int life() {
        return entityData.get(LIFE);
    }

    /** Whether this summon actively hunts the owner's enemies. */
    protected boolean fights() {
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                level().addParticle(MaParticles.MOTE.get().with(glowColor(), 0.3f, 25), getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            }
            return;
        }
        int life = life() - 1;
        entityData.set(LIFE, life);
        if (life <= 0) {
            vanish();
            return;
        }
        LivingEntity owner = ownerEntity();
        if (!fights()) return;
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || target == owner || isAlliedTo(target))) {
            setTarget(null);
            target = null;
        }
        if (target == null && tickCount % 10 == 0) {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            AABB box = getBoundingBox().inflate(16);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> x != this && x.isAlive() && !isAlliedTo(x)
                    && (x instanceof Enemy || (owner != null && x instanceof net.minecraft.world.entity.Mob m && m.getTarget() == owner))
                    && !EntityMarks.has(x, MarkKind.CONTROLLED))) {
                double d = e.distanceToSqr(this);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) setTarget(best);
        }
        if (getTarget() == null && owner != null && distanceToSqr(owner) > 36) getNavigation().moveTo(owner, 1.2);
        if (owner != null && distanceToSqr(owner) > 48 * 48) teleportTo(owner.getX(), owner.getY(), owner.getZ());
    }

    protected abstract int glowColor();

    public void vanish() {
        if (level() instanceof ServerLevel s) {
            s.sendParticles(MaParticles.RUNE.get().with(glowColor(), 0.6f, 30), getX(), getY() + getBbHeight() * 0.5, getZ(), 16, 0.3, 0.5, 0.3, 0.05);
        }
        discard();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        UUID id = ownerId();
        if (id != null) {
            if (other.getUUID().equals(id)) return true;
            if (other instanceof SummonEntity s && id.equals(s.ownerId())) return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
