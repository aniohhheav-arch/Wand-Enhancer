package com.wandcraft.entity;

import com.wandcraft.WandCraft;
import com.wandcraft.magic.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A continuous beam from a caster's wand to an aim point, rendered as a 3D beacon-style shaft on the client.
 * The server keeps it alive by calling {@link #update}; if updates stop for a few ticks it removes itself.
 */
public class SpellBeam extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.FLOAT);

    private long lastUpdate;
    public float renderLength;
    public float prevRenderLength;

    public SpellBeam(EntityType<? extends SpellBeam> type, Level level) {
        super(type, level);
        noPhysics = true;
        setInvulnerable(true);
    }

    public SpellBeam(Level level, Entity owner, int rgb) {
        this(WandCraft.SPELL_BEAM.get(), level);
        entityData.set(OWNER, owner.getId());
        entityData.set(COLOR, rgb);
        setPos(owner.getEyePosition());
        lastUpdate = level.getGameTime();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER, -1);
        b.define(LENGTH, 0f);
        b.define(COLOR, 0xFFFFFF);
        b.define(POWER, 0f);
    }

    public int ownerId() { return entityData.get(OWNER); }
    public float length() { return entityData.get(LENGTH); }
    public int color() { return entityData.get(COLOR); }
    /** 0..1 intensity; widens and brightens the beam. */
    public float power() { return entityData.get(POWER); }

    public void update(float length, float power) {
        entityData.set(LENGTH, length);
        entityData.set(POWER, power);
        lastUpdate = level().getGameTime();
        Entity o = level().getEntity(ownerId());
        if (o != null) setPos(o.getEyePosition());
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = level().getEntity(ownerId());
        if (!level().isClientSide) {
            if (owner == null || !owner.isAlive() || level().getGameTime() - lastUpdate > 3) discard();
            return;
        }
        prevRenderLength = renderLength;
        renderLength += (length() - renderLength) * 0.6f;
        if (owner == null) return;
        Vec3 start = Vec3.ZERO;
        Vec3 end = tip(owner, 1f);
        int rgb = color();
        float p = power();
        for (int i = 0; i < 2; i++) {
            Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.12);
            Fx.c(level(), Fx.spark(0xFFFFFF, 0.8f + p), end.x + jitter.x, end.y + jitter.y, end.z + jitter.z, jitter.x * 0.4, jitter.y * 0.4 + 0.03, jitter.z * 0.4);
        }
        Fx.c(level(), Fx.glow(rgb, 1.4f + p), end.x, end.y, end.z, 0, 0, 0);
        if (random.nextInt(2) == 0) {
            Fx.c(level(), Fx.wisp(rgb, 0.8f), end.x, end.y, end.z, (random.nextDouble() - .5) * .1, random.nextDouble() * .06, (random.nextDouble() - .5) * .1);
        }
        // sparks drifting along the shaft
        Vec3 from = start(owner, 1f);
        Vec3 dir = end.subtract(from);
        double len = dir.length();
        if (len > 0.5) {
            Vec3 n = dir.scale(1 / len);
            double t = random.nextDouble() * len;
            Vec3 q = from.add(n.scale(t));
            Fx.c(level(), Fx.glow(rgb, 0.7f), q.x, q.y, q.z, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01);
        }
    }

    /** Set by the client: true when {@code e} is the local player seen in first person. */
    public static java.util.function.Predicate<Entity> firstPerson = e -> false;

    /** World position where the beam leaves the wand tip. */
    public static Vec3 start(Entity owner, float pt) {
        Vec3 eye = owner.getEyePosition(pt);
        Vec3 dir = owner.getViewVector(pt);
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        if (firstPerson.test(owner)) return eye.add(dir.scale(0.8)).add(right.scale(0.42)).add(0, -0.02, 0);
        // Third person: from the right hand, in front of the body.
        float yaw = (owner instanceof net.minecraft.world.entity.LivingEntity le ? net.minecraft.util.Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : owner.getYRot()) * ((float) Math.PI / 180f);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 rgt = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return owner.getPosition(pt).add(0, owner.getEyeHeight() - 0.35, 0).add(fwd.scale(0.7)).add(rgt.scale(0.38));
    }

    /** World position of the end of the beam. */
    public Vec3 tip(Entity owner, float pt) {
        Vec3 eye = owner.getEyePosition(pt);
        float len = prevRenderLength + (renderLength - prevRenderLength) * pt;
        return eye.add(owner.getViewVector(pt).scale(len));
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
