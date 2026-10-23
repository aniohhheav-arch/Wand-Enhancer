package com.wandcraft.entity;

import com.wandcraft.Config;
import com.wandcraft.ModSounds;
import com.wandcraft.WandCraft;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.ShadowFx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The black ash left behind when a possession finishes. It sits flush on the terrain, glows faintly with embers,
 * drifts motes in the wind and scatters when someone walks through it. Saved with the world when {@code ashPersists},
 * and pruned oldest-first by the per-area and per-dimension limits.
 */
public class AshPile extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(AshPile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DYING = SynchedEntityData.defineId(AshPile.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int age;
    private int dyingTicks;
    private long born;
    private int scatterCd;

    public AshPile(EntityType<? extends AshPile> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ------------------------------------------------------------------------------------ spawning
    /** Spawns a pile on the ground at or below {@code at}, enforcing the cleanup limits. No pile appears over the void. */
    public static AshPile spawn(ServerLevel l, Vec3 at, float scale) {
        double floor = floorY(l, at.x, at.y + 1.0, at.z);
        if (Double.isNaN(floor)) return null;
        prune(l, new Vec3(at.x, floor, at.z));
        AshPile p = new AshPile(WandCraft.ASH_PILE.get(), l);
        p.entityData.set(SCALE, scale);
        p.born = l.getGameTime();
        p.setPos(at.x, floor, at.z);
        p.setYRot(l.random.nextFloat() * 360f);
        l.addFreshEntity(p);
        Fx.sound(l, p.position(), ModSounds.ASH_SCATTER.get(), 0.9f, 0.8f);
        return p;
    }

    /** Top surface of the first solid block at or below y (searching eight blocks down), or NaN. */
    static double floorY(Level l, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int i = 0; i <= 8; i++) {
            BlockPos p = base.below(i);
            VoxelShape s = l.getBlockState(p).getCollisionShape(l, p);
            if (!s.isEmpty()) return p.getY() + s.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    private static void prune(ServerLevel l, Vec3 at) {
        int perArea = Config.ASH_MAX_PER_AREA.get(), total = Config.ASH_MAX_TOTAL.get();
        List<AshPile> near = l.getEntitiesOfClass(AshPile.class, new AABB(at, at).inflate(16), a -> !a.dying());
        near.sort(Comparator.comparingLong(a -> a.born));
        for (int i = 0; near.size() - i >= perArea; i++) near.get(i).dissipate();
        List<AshPile> all = new ArrayList<>();
        for (Entity e : l.getAllEntities()) if (e instanceof AshPile a && !a.dying()) all.add(a);
        all.sort(Comparator.comparingLong(a -> a.born));
        for (int i = 0; all.size() - i >= total; i++) all.get(i).dissipate();
    }

    public void dissipate() {
        if (!dying()) entityData.set(DYING, true);
    }

    public float scale() { return entityData.get(SCALE); }
    public boolean dying() { return entityData.get(DYING); }

    // ------------------------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            ambient();
            return;
        }
        if (dying()) {
            if (++dyingTicks > 30) discard();
            return;
        }
        int dur = Config.ASH_DURATION_SECONDS.get();
        if (dur > 0 && age > dur * 20) {
            dissipate();
            return;
        }
        // stay flush with the terrain if the block underneath changes
        if (age % 40 == 0) {
            double f = floorY(level(), getX(), getY() + 0.6, getZ());
            if (Double.isNaN(f)) {
                dissipate();
            } else if (Math.abs(f - getY()) > 0.02) {
                setPos(getX(), f, getZ());
            }
        }
    }

    /** Wind-blown motes, rising embers and dust kicked up by passers-by. Client side only. */
    private void ambient() {
        Level l = level();
        float s = scale();
        long t = l.getGameTime();
        double wx = Math.sin(t * 0.013 + getId()) * 0.012 + 0.008, wz = Math.cos(t * 0.009) * 0.01;
        if (dying()) {
            if (t % 2 == 0) {
                for (int i = 0; i < 3; i++) Fx.c(l, ShadowFx.ash(0.8f), getX() + (random.nextDouble() - .5) * 1.6 * s, getY() + 0.1, getZ() + (random.nextDouble() - .5) * 1.6 * s, wx * 4, 0.04, wz * 4);
            }
            return;
        }
        if (t % 5 == 0) Fx.c(l, ShadowFx.smoke(0.5f), getX() + (random.nextDouble() - .5) * 1.2 * s, getY() + 0.25 * s, getZ() + (random.nextDouble() - .5) * 1.2 * s, wx, 0.012, wz);
        if (t % 17 == 0) Fx.c(l, Fx.wisp(0x3C3944, 1.1f * s), getX() + (random.nextDouble() - .5) * s, getY() + 0.3, getZ() + (random.nextDouble() - .5) * s, wx * 2.5, 0.02, wz * 2.5);
        if (t % 11 == (getId() & 7)) Fx.c(l, Fx.spark(random.nextBoolean() ? 0xFF8A36 : 0xC070FF, 0.35f), getX() + (random.nextDouble() - .5) * 0.9 * s, getY() + 0.2 * s, getZ() + (random.nextDouble() - .5) * 0.9 * s, wx * 3, 0.03, wz * 3);
        if (scatterCd > 0) scatterCd--;
        Player near = l.getNearestPlayer(this, 1.2 * s + 0.9);
        if (near != null && scatterCd == 0 && Math.hypot(near.getX() - near.xo, near.getZ() - near.zo) > 0.035 && Math.abs(near.getY() - getY()) < 1.6) {
            scatterCd = 3;
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double dx = near.getX() - getX(), dz = near.getZ() - getZ();
                double m = Math.max(0.1, Math.hypot(dx, dz));
                Fx.c(l, ShadowFx.ash(0.7f), near.getX() + Math.cos(a) * 0.3, getY() + 0.1, near.getZ() + Math.sin(a) * 0.3, dx / m * 0.08 + wx, 0.05 + random.nextDouble() * 0.05, dz / m * 0.08 + wz);
            }
            if (t % 12 == 0) l.playLocalSound(getX(), getY(), getZ(), ModSounds.ASH_SCATTER.get(), net.minecraft.sounds.SoundSource.AMBIENT, 0.5f, 0.9f + random.nextFloat() * 0.3f, false);
        }
    }

    // ------------------------------------------------------------------------------------ plumbing
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(dying()
                ? RawAnimation.begin().then("animation.ash_pile.dissipate", Animation.LoopType.HOLD_ON_LAST_FRAME)
                : RawAnimation.begin().then("animation.ash_pile.settle", Animation.LoopType.PLAY_ONCE).thenLoop("animation.ash_pile.idle"))));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SCALE, 1f);
        b.define(DYING, false);
    }

    @Override public boolean shouldBeSaved() { return Config.ASH_PERSISTS.get(); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 96 * 96; }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        age = t.getInt("age");
        born = t.getLong("born");
        entityData.set(SCALE, t.contains("scale") ? t.getFloat("scale") : 1f);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("age", age);
        t.putLong("born", born);
        t.putFloat("scale", scale());
    }
}
