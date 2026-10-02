package dev.mysticarts.entity;

import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaParticles;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An expanding wave that strikes each thing once as its front passes. Ground waves (ground slam, Power Stone shockwave)
 * hug the terrain and throw debris; sphere waves (pulses, ultimate releases) expand in all directions.
 */
public class ShockwaveEntity extends Entity {
    public static final int GROUND = 0;
    public static final int SPHERE = 1;

    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MAX_RADIUS = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.INT);

    @Nullable private UUID owner;
    private float damage;
    private float knockback;
    private float lift;
    private final Set<Integer> hit = new HashSet<>();

    public ShockwaveEntity(EntityType<? extends ShockwaveEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static ShockwaveEntity spawn(ServerLevel level, @Nullable Entity owner, Vec3 pos, int style, int color, float radius, int duration,
                                        float damage, float knockback, float lift) {
        ShockwaveEntity w = new ShockwaveEntity(MaEntities.SHOCKWAVE.get(), level);
        w.owner = owner == null ? null : owner.getUUID();
        w.entityData.set(STYLE, style);
        w.entityData.set(COLOR, color);
        w.entityData.set(MAX_RADIUS, radius);
        w.entityData.set(DURATION, duration);
        w.damage = damage;
        w.knockback = knockback;
        w.lift = lift;
        w.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addFreshEntity(w);
        return w;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, GROUND);
        builder.define(COLOR, 0x9B2CFF);
        builder.define(MAX_RADIUS, 8f);
        builder.define(DURATION, 16);
    }

    public int style() {
        return entityData.get(STYLE);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float maxRadius() {
        return entityData.get(MAX_RADIUS);
    }

    public int duration() {
        return entityData.get(DURATION);
    }

    /** Front radius at a (fractional) age: fast start, easing out. */
    public float radiusAt(float age) {
        float t = Mth.clamp(age / duration(), 0f, 1f);
        return maxRadius() * (1f - (1f - t) * (1f - t));
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > duration()) {
            discard();
            return;
        }
        float r0 = radiusAt(tickCount - 1), r1 = radiusAt(tickCount);
        if (level().isClientSide) {
            if (style() == GROUND) groundDebris(r1);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Entity src = owner == null ? null : level.getEntity(owner);
        double h = style() == GROUND ? 2.5 : r1;
        for (Entity e : level.getEntities(this, new AABB(position(), position()).inflate(r1 + 1, h + 1, r1 + 1))) {
            if (e == src || hit.contains(e.getId()) || e instanceof ShockwaveEntity || e instanceof SlingPortalEntity || e instanceof SpellFieldEntity) continue;
            if (src != null && e.isAlliedTo(src)) continue;
            Vec3 rel = e.position().subtract(position());
            double d = style() == GROUND ? Math.sqrt(rel.x * rel.x + rel.z * rel.z) : rel.length();
            if (style() == GROUND && Math.abs(rel.y) > 3) continue;
            if (d < r0 - 1.0 || d > r1 + 0.5) continue;
            hit.add(e.getId());
            Vec3 out = (style() == GROUND ? new Vec3(rel.x, 0, rel.z) : rel).normalize();
            if (out.lengthSqr() < 1e-6) out = new Vec3(0, 1, 0);
            float fall = 1f - (float) (d / maxRadius()) * 0.5f;
            if (e instanceof LivingEntity living) {
                if (src instanceof Player && living instanceof Player && !level.getServer().isPvpAllowed()) continue;
                if (damage > 0) living.hurt(level.damageSources().indirectMagic(src, src), damage * fall);
            }
            if (e instanceof Projectile || e instanceof LivingEntity || e instanceof net.minecraft.world.entity.item.ItemEntity) {
                e.setDeltaMovement(e.getDeltaMovement().add(out.scale(knockback * fall)).add(0, lift * fall, 0));
                e.hurtMarked = true;
            }
        }
    }

    private void groundDebris(float r) {
        int n = (int) (r * 2.5f);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(x, getY() - 0.5, z);
            BlockState s = level().getBlockState(below);
            if (!s.isAir() && random.nextInt(3) == 0) {
                level().addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, s),
                        x, getY() + 0.1, z, Math.cos(a) * 0.2, 0.3 + random.nextDouble() * 0.3, Math.sin(a) * 0.2);
            }
            if (random.nextInt(2) == 0) {
                level().addParticle(MaParticles.SPARK.get().with(color(), 0.35f, 12), x, getY() + 0.15, z, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 160 * 160;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
