package com.wandcraft.entity;

import com.wandcraft.WandCraft;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A purely visual, animated 3D object tied to a spell (portal, mirror, guardian vine...). It spawns with a "spawn"
 * animation, loops "idle", plays "despawn" while dying and can be poked with "action" (strike, hit flash, arm...).
 * It never affects gameplay and is removed by the spell that created it, or when its own life runs out.
 */
public class SpellProp extends Entity implements GeoEntity {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(SpellProp.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SpellProp.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DYING = SynchedEntityData.defineId(SpellProp.class, EntityDataSerializers.BOOLEAN);

    private static final int FADE = 12;

    public enum Follow { NONE, ENTITY, OWNER_FRONT }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life = 200;
    private Follow follow = Follow.NONE;
    private UUID followId;
    private Vec3 offset = Vec3.ZERO;
    private double frontDistance = 1.5;

    public SpellProp(EntityType<? extends SpellProp> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Spawns a prop on the server. {@code life} is in ticks; the despawn animation plays during the last 12. */
    public static SpellProp spawn(ServerLevel l, String kind, Vec3 pos, float scale, int life) {
        SpellProp p = new SpellProp(WandCraft.SPELL_PROP.get(), l);
        p.entityData.set(KIND, kind);
        p.entityData.set(SCALE, scale);
        p.life = life;
        p.setPos(pos);
        l.addFreshEntity(p);
        return p;
    }

    public SpellProp follow(Entity target, Vec3 offset) {
        this.follow = Follow.ENTITY;
        this.followId = target.getUUID();
        this.offset = offset;
        return this;
    }

    /** Hovers in front of {@code owner}'s eyes, turned to match their view (shields, mirrors). */
    public SpellProp frontOf(Entity owner, double distance, double dy) {
        this.follow = Follow.OWNER_FRONT;
        this.followId = owner.getUUID();
        this.frontDistance = distance;
        this.offset = new Vec3(0, dy, 0);
        return this;
    }

    public SpellProp facing(float yaw, float pitch) {
        setYRot(yaw);
        yRotO = yaw;
        setXRot(pitch);
        xRotO = pitch;
        return this;
    }

    /** Ends the prop with its despawn animation. */
    public void dismiss() { life = Math.min(life, FADE); }

    public void setLife(int ticks) { life = ticks; }

    /** Plays the kind's "action" animation (strike, flash...). */
    public void act() { triggerAnim("action", "action"); }

    public String kind() { return entityData.get(KIND); }
    public float scale() { return entityData.get(SCALE); }
    public boolean dying() { return entityData.get(DYING); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, "mirror");
        b.define(SCALE, 1f);
        b.define(DYING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (follow != Follow.NONE) {
            Entity f = ((ServerLevel) level()).getEntity(followId);
            if (f == null || f.isRemoved()) {
                dismiss();
            } else if (follow == Follow.ENTITY) {
                Vec3 p = f.position().add(offset);
                setPos(p.x, p.y, p.z);
            } else {
                Vec3 look = f.getLookAngle();
                Vec3 p = f.getEyePosition().add(look.scale(frontDistance)).add(offset);
                setPos(p.x, p.y, p.z);
                // Face away from the caster so the front of the model points back at them.
                setYRot(f.getYRot());
                setXRot(0f);
            }
        }
        if (--life <= FADE && !dying()) entityData.set(DYING, true);
        if (life <= 0) discard();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            String k = kind();
            if (dying()) {
                return state.setAndContinue(RawAnimation.begin().then("animation." + k + ".despawn", Animation.LoopType.HOLD_ON_LAST_FRAME));
            }
            return state.setAndContinue(RawAnimation.begin().then("animation." + k + ".spawn", Animation.LoopType.PLAY_ONCE)
                    .thenLoop("animation." + k + ".idle"));
        }));
        controllers.add(new AnimationController<SpellProp>(this, "action", 0, state -> PlayState.STOP) {
            @Override
            public boolean tryTriggerAnimation(String name) {
                // The action animation name depends on the kind, so build it on demand.
                triggerableAnim("action", RawAnimation.begin().then("animation." + kind() + ".action", Animation.LoopType.PLAY_ONCE));
                return super.tryTriggerAnimation(name);
            }
        }.triggerableAnim("action", RawAnimation.begin().then("animation.mirror.action", Animation.LoopType.PLAY_ONCE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128 * 128; }
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
