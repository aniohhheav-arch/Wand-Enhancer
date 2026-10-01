package dev.riftverse.entity.boss;

import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.entity.ai.Flight;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.util.Advancements;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** A sea-serpent the size of a mountain range, at home in ocean and sky alike. */
public class AbyssalLeviathanEntity extends Monster {
    private static final EntityDataAccessor<Integer> ROAR = SynchedEntityData.defineId(AbyssalLeviathanEntity.class, EntityDataSerializers.INT);
    public static final int COLOR = 0x40FFE0;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.riftverse.abyssal_leviathan"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private int state;
    private int stateTicks;
    private double angle;
    private Vec3 diveTarget = Vec3.ZERO;
    private boolean introduced;

    public AbyssalLeviathanEntity(EntityType<? extends AbyssalLeviathanEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 8, true);
        this.setNoGravity(true);
        this.noCulling = true;
        this.xpReward = 200;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FLYING_SPEED, 0.7)
                .add(Attributes.FOLLOW_RANGE, 72.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROAR, 0);
    }

    public int roarTicks() {
        return entityData.get(ROAR);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return Flight.navigation(this, level);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (roarTicks() > 0) entityData.set(ROAR, roarTicks() - 1);
        LivingEntity target = getTarget();
        stateTicks++;
        if (target == null || !target.isAlive()) {
            angle += 0.008;
            moveControl.setWantedPosition(getX() + Math.cos(angle) * 30, getY() + Math.sin(tickCount * 0.01) * 4, getZ() + Math.sin(angle) * 30, 0.6);
            return;
        }
        if (!introduced) {
            introduced = true;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 120 * 120) {
                    PacketDistributor.sendToPlayer(p, new Payloads.BossIntro(getId(), "THE ABYSSAL LEVIATHAN", "Sovereign of the Endless Deep", COLOR));
                }
            }
            level.playSound(null, getX(), getY(), getZ(), RvSounds.LEVIATHAN_ROAR.get(), SoundSource.HOSTILE, 6.0f, 0.7f);
        }
        switch (state) {
            case 0 -> {
                angle += 0.025;
                Vec3 goal = target.position().add(Math.cos(angle) * 24, 10 + Math.sin(tickCount * 0.03) * 4, Math.sin(angle) * 24);
                moveControl.setWantedPosition(goal.x, goal.y, goal.z, 1.0);
                if (stateTicks > 110) {
                    state = random.nextFloat() < 0.35f ? 2 : 1;
                    stateTicks = 0;
                    Vec3 through = target.position().subtract(position());
                    diveTarget = target.position().add(through.normalize().scale(14));
                    if (state == 1) level.playSound(null, getX(), getY(), getZ(), RvSounds.LEVIATHAN_ROAR.get(), SoundSource.HOSTILE, 4.0f, 1.1f);
                }
            }
            case 1 -> {
                moveControl.setWantedPosition(diveTarget.x, diveTarget.y, diveTarget.z, 2.6);
                for (Entity e : level.getEntities(this, getBoundingBox().inflate(1.5), e -> e instanceof LivingEntity)) {
                    if (e.hurt(damageSources().mobAttack(this), 16f)) {
                        e.setDeltaMovement(getDeltaMovement().scale(1.2).add(0, 0.8, 0));
                        e.hurtMarked = true;
                    }
                }
                if (random.nextInt(2) == 0) level.sendParticles(RvParticles.STREAK.get().with(COLOR, 0.7f, 12), getX(), getY() + 1, getZ(), 4, 1, 1, 1, 0.1);
                if (stateTicks > 45 || distanceToSqr(diveTarget) < 9) {
                    state = 0;
                    stateTicks = 0;
                }
            }
            case 2 -> {
                getLookControl().setLookAt(target, 30, 30);
                moveControl.setWantedPosition(getX(), getY() + 0.05, getZ(), 0.2);
                if (stateTicks == 1) {
                    entityData.set(ROAR, 50);
                    level.playSound(null, getX(), getY(), getZ(), RvSounds.LEVIATHAN_ROAR.get(), SoundSource.HOSTILE, 6.0f, 0.6f);
                    for (ServerPlayer p : level.players()) {
                        if (p.distanceToSqr(this) < 80 * 80) PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.8f, 40, 0f, 0));
                    }
                }
                if (stateTicks > 15 && stateTicks % 5 == 0 && stateTicks < 50) {
                    Vec3 to = target.getEyePosition().subtract(getEyePosition());
                    for (int i = 0; i < 4; i++) {
                        EnergyBoltEntity bolt = EnergyBoltEntity.create(level, this, i % 2 == 0 ? COLOR : 0x3070FF, 0.7f, 8f);
                        bolt.setPos(getX(), getEyeY(), getZ());
                        bolt.shoot(to.x, to.y, to.z, 1.4f, 9f);
                        level.addFreshEntity(bolt);
                    }
                }
                if (stateTicks > 60) {
                    state = 0;
                    stateTicks = 0;
                }
            }
            default -> state = 0;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            Vec3 c = position();
            level.sendParticles(RvParticles.RING.get().with(COLOR, 26f, 40), c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(RvParticles.SPARK.get().with(0xB0FFF4, 0.9f, 40), c.x, c.y, c.z, 200, 4, 3, 4, 0.8);
            level.playSound(null, c.x, c.y, c.z, RvSounds.LEVIATHAN_ROAR.get(), SoundSource.HOSTILE, 6.0f, 0.45f);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(c) < 96 * 96) Advancements.award(p, "leviathan_slayer");
            }
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RvSounds.LEVIATHAN_ROAR.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.CREATURE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.LEVIATHAN_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4.0f;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f + random.nextFloat() * 0.15f;
    }
}
