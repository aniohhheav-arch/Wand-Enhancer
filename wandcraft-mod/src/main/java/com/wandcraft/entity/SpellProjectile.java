package com.wandcraft.entity;

import com.wandcraft.Config;
import com.wandcraft.WandCraft;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.ProjBehavior;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.util.Combat;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One entity type for every projectile spell; the spell's {@link ProjBehavior} decides what it does. */
public class SpellProjectile extends Projectile {
    private static final EntityDataAccessor<String> SPELL = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);

    public float damage = 4;
    public float power = 1;
    public float charge;
    public int life = 60;
    public int age;
    public double gravity;
    public int pierce;
    /** Free-form counters for behaviours. */
    public final int[] data = new int[4];
    public UUID homingTarget;
    public double homingStrength = 0.25;
    public boolean hitCredited;

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public SpellProjectile(Level level, Spell spell) {
        this(WandCraft.SPELL_PROJECTILE.get(), level);
        setSpell(spell);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SPELL, "");
        b.define(SIZE, 0.3f);
    }

    public void setSpell(Spell s) { entityData.set(SPELL, s.id); }
    public Spell spell() { return Spells.get(entityData.get(SPELL)); }
    public float size() { return entityData.get(SIZE); }
    public void setSize(float s) { entityData.set(SIZE, s); }

    public ServerPlayer caster() {
        return getOwner() instanceof ServerPlayer sp ? sp : null;
    }

    /** Mastery credit for the caster the first time this projectile affects something. */
    public void credit(int hits) {
        ServerPlayer c = caster();
        Spell s = spell();
        if (c != null && s != null && hits > 0) Magic.credit(c, s, hits);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 96 * 96; }

    @Override
    public boolean isNoGravity() { return true; }

    @Override
    public void tick() {
        super.tick();
        Spell s = spell();
        if (s == null || s.proj == null) {
            discard();
            return;
        }
        ProjBehavior b = s.proj;
        age++;
        if (level().isClientSide) {
            b.trail(this);
        } else {
            if (age > life || !level().hasChunkAt(blockPosition())) {
                b.expire(this);
                discard();
                return;
            }
            b.tick(this);
            if (isRemoved()) return;
            homing();
        }
        Vec3 v = getDeltaMovement();
        if (gravity != 0) v = v.add(0, -gravity, 0);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !level().isClientSide && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            onHit(hit);
        }
        if (isRemoved()) return;
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        updateRotation();
    }

    private void homing() {
        if (homingTarget == null || !(level() instanceof ServerLevel sl)) return;
        Entity t = sl.getEntity(homingTarget);
        if (!(t instanceof LivingEntity le) || !le.isAlive()) {
            homingTarget = null;
            return;
        }
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        if (speed < 1e-3) return;
        Vec3 want = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(position()).normalize();
        Vec3 nv = v.normalize().scale(1 - homingStrength).add(want.scale(homingStrength)).normalize().scale(speed);
        setDeltaMovement(nv);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        return super.canHitEntity(e) && Combat.canTarget(getOwner(), le) && age > 1;
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (level().isClientSide || !(r.getEntity() instanceof LivingEntity le)) return;
        Spell s = spell();
        if (s == null || s.proj == null) {
            discard();
            return;
        }
        boolean consume = s.proj.hitEntity(this, le);
        if (consume) {
            if (pierce > 0) pierce--;
            else discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide) return;
        Spell s = spell();
        if (s == null || s.proj == null || s.proj.hitBlock(this, r)) discard();
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    /** Number of live spell entities owned by {@code owner}; used to cap simultaneous instances. */
    public static boolean canSpawn(ServerPlayer owner) {
        int cap = Config.MAX_ENTITIES_PER_CASTER.get();
        ServerLevel l = owner.serverLevel();
        int n = 0;
        for (Entity e : l.getAllEntities()) {
            if (e instanceof SpellProjectile p && p.getOwner() == owner) n++;
            else if (e instanceof SpellZone z && z.ownerId != null && z.ownerId.equals(owner.getUUID())) n++;
            if (n >= cap) return false;
        }
        return true;
    }
}
