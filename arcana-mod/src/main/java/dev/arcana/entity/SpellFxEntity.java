package dev.arcana.entity;

import dev.arcana.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Short-lived, purely visual 3D effect (black hole, sun, ice spire, ...) shown during ultimates. It never collides, is never saved and
 * only ever exists for a few seconds; all gameplay is done by the spell itself. Spawned at most a handful of times per ultimate.
 */
public class SpellFxEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SpellFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpellFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PROFILE = SynchedEntityData.defineId(SpellFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SpellFxEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.arcana.fx.idle");

    /** Growth profiles: 0 ease in/out, 1 gather then burst (supernova), 2 expanding ring, 3 grow, sway and wither. */
    public static final int EASE = 0, GATHER = 1, EXPAND = 2, GROW = 3;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SpellFxEntity(EntityType<? extends SpellFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpellFxEntity spawn(ServerLevel level, FxKind kind, Vec3 pos, float scale, int life, int profile) {
        SpellFxEntity e = new SpellFxEntity(ModEntities.SPELL_FX.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(KIND, kind.ordinal());
        e.entityData.set(LIFE, Math.max(4, life));
        e.entityData.set(PROFILE, profile);
        e.entityData.set(SCALE, scale);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0).define(LIFE, 40).define(PROFILE, 0).define(SCALE, 1f);
    }

    public FxKind kind() { return FxKind.byOrdinal(entityData.get(KIND)); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= entityData.get(LIFE)) discard();
    }

    /** Current render scale (blocks of model-space multiplier) with the entrance/exit animation baked in. */
    public float visualScale(float partial) {
        float base = entityData.get(SCALE), life = entityData.get(LIFE), age = tickCount + partial;
        float t = Math.min(1f, age / life);
        float in = Math.min(1f, age / 16f), out = Math.min(1f, (life - age) / 12f);
        float eased = 1f - (1f - in) * (1f - in);
        switch (entityData.get(PROFILE)) {
            case GATHER: {
                float s = 0.15f + 0.85f * (float) Math.pow(t, 1.6);
                float burst = Math.max(0f, age - (life - 6f));
                return base * (s + burst * 0.55f) * (burst > 5f ? 0f : 1f);
            }
            case EXPAND:
                return base * (0.12f + 0.88f * t) * Math.min(1f, Math.max(0f, (life - age) / 3f));
            case GROW: {
                float g = 1f - (float) Math.pow(1f - Math.min(1f, age / 26f), 3);
                return base * g * Math.min(1f, Math.max(0f, (life - age) / 28f)) * (1f + 0.02f * (float) Math.sin(age * 0.25f));
            }
            default:
                return base * eased * Math.max(0f, Math.min(1f, out));
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = Math.max(8.0, entityData.get(SCALE) * 1.2);
        return new AABB(getX() - r, getY() - 2, getZ() - r, getX() + r, getY() + r * 1.5, getZ() + r);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
