package dev.riftverse.entity.boss;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Cosmic Deity: a titan in the shape of a person whose skin is the night sky. It hovers over its prey and, every
 * so often, inhales: its mouth tears wide open and everything nearby — air, light, creatures, loose terrain — is
 * dragged into it, while the world visibly warps for anyone close (the client reads {@link #inhale()} for the
 * distortion). Between breaths it hurls star-bolts. In "devourer" mode (End Protocols) it never fights; it only
 * hangs over a doomed universe and breathes it in.
 */
public class CosmicDeityEntity extends Monster {
    public static final int COLOR = 0xC070FF;
    public static final int MAX_INHALE = 60;
    private static final EntityDataAccessor<Integer> INHALE = SynchedEntityData.defineId(CosmicDeityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEVOURER = SynchedEntityData.defineId(CosmicDeityEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.riftverse.cosmic_deity"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int cycle;
    private boolean breathing;
    private boolean harmless;

    public CosmicDeityEntity(EntityType<? extends CosmicDeityEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noCulling = true;
        xpReward = 500;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.SCALE, 6.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(INHALE, 0);
        builder.define(DEVOURER, false);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** 0..1 how wide the mouth is open (synced). */
    public float inhale(float partial) {
        return Mth.clamp(entityData.get(INHALE) / (float) MAX_INHALE, 0f, 1f);
    }

    public float inhale() {
        return inhale(0f);
    }

    public boolean isDevourer() {
        return entityData.get(DEVOURER);
    }

    /** Devourer mode: no AI, no combat, it only breathes in. Harmless devourers (previews) touch nothing. */
    public void makeDevourer(boolean harmless) {
        entityData.set(DEVOURER, true);
        this.harmless = harmless;
        setNoAi(true);
        setInvulnerable(true);
    }

    public void setBreathing(boolean breathing) {
        this.breathing = breathing;
    }

    /** World position of the mouth. */
    public Vec3 mouth() {
        Vec3 fwd = Vec3.directionFromRotation(Math.min(getXRot(), 50f), getYHeadRot());
        return position().add(0, getBbHeight() * 0.86, 0).add(fwd.scale(getBbWidth() * 0.35));
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        int in = entityData.get(INHALE);
        entityData.set(INHALE, breathing ? Math.min(MAX_INHALE, in + 2) : Math.max(0, in - 3));
        if (isDevourer()) {
            setDeltaMovement(Vec3.ZERO);
            breathe(level, inhale());
            return;
        }
        cycle++;
        LivingEntity target = getTarget();
        if (!breathing && cycle > 220 && target != null) {
            breathing = true;
            cycle = 0;
            level.playSound(null, getX(), getY(), getZ(), RvSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 8f, 0.4f);
        } else if (breathing && cycle > 150) {
            breathing = false;
            cycle = 0;
        }
        if (target != null) {
            // hover above and in front of the prey, always facing it
            Vec3 want = target.position().add(0, 10, 0).add(target.getLookAngle().multiply(1, 0, 1).scale(18));
            Vec3 to = want.subtract(position());
            setDeltaMovement(getDeltaMovement().scale(0.85).add(to.scale(0.012)));
            getLookControl().setLookAt(target, 30f, 30f);
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            if (!breathing && cycle % 40 == 20 && hasLineOfSight(target)) starBolts(level, target);
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.8));
        }
        if (breathing) breathe(level, inhale());
    }

    private void starBolts(ServerLevel level, LivingEntity target) {
        for (int i = 0; i < 3; i++) {
            EnergyBoltEntity bolt = EnergyBoltEntity.create(level, this, i == 1 ? 0xFFC14D : COLOR, 0.8f, 10f);
            Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
            bolt.setPos(from.x, from.y, from.z);
            Vec3 to = target.getEyePosition().subtract(from);
            bolt.shoot(to.x, to.y, to.z, 1.4f, 4f);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, getX(), getY(), getZ(), RvSounds.ENERGY_FIRE.get(), SoundSource.HOSTILE, 4f, 0.5f);
    }

    /** The inhale: pull, devour, tear terrain loose and stream light into the mouth. */
    private void breathe(ServerLevel level, float k) {
        if (k <= 0.05f) return;
        Vec3 mouth = mouth();
        double reach = (isDevourer() ? 60 : 40) * k;
        boolean heavy = RiftverseConfig.get(RiftverseConfig.HEAVY_EFFECTS, true);
        if (tickCount % 2 == 0) {
            for (int i = 0; i < (heavy ? 8 : 3); i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = random.nextDouble() * Math.PI - Math.PI / 2;
                double r = reach * (0.5 + random.nextDouble() * 0.5);
                Vec3 p = mouth.add(Math.cos(a) * Math.cos(b) * r, Math.sin(b) * r * 0.6, Math.sin(a) * Math.cos(b) * r);
                level.sendParticles(RvParticles.INFALL.get().with(random.nextBoolean() ? COLOR : 0xFFF0C8, 1.3f, 30), p.x, p.y, p.z, 1, 0, 0, 0, 0.02);
            }
            level.sendParticles(RvParticles.RING.get().with(COLOR, 3f + 6f * k, 10), mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
        }
        if (tickCount % 30 == 0) level.playSound(null, mouth.x, mouth.y, mouth.z, RvSounds.BLACK_HOLE_PULL.get(), SoundSource.HOSTILE, 6f, 0.4f);
        if (harmless) return;
        AABB area = new AABB(mouth, mouth).inflate(reach);
        List<Entity> caught = level.getEntities(this, area, e -> e.isAlive() && !e.isSpectator() && !(e instanceof CosmicDeityEntity));
        for (Entity e : caught) {
            if (e instanceof Player p && (p.isCreative() || dev.riftverse.transit.TransitManager.inTransit(p))) continue;
            Vec3 to = mouth.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = to.length();
            if (d > reach || d < 1e-3) continue;
            if (d < 2.5 + getBbWidth() * 0.2) {
                if (e instanceof Player p) {
                    if (tickCount % 10 == 0) p.hurt(damageSources().mobAttack(this), 8f);
                } else if (e instanceof FallingBlockEntity || !(e instanceof LivingEntity)) {
                    e.discard();
                } else if (tickCount % 5 == 0) {
                    e.hurt(damageSources().mobAttack(this), 40f);
                }
                continue;
            }
            double pull = (0.06 + 0.3 * k) * Math.min(1.0, 14.0 / d + 0.3);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.9).add(to.scale(pull / d)));
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
        boolean terrain = isDevourer() || (RiftverseConfig.get(RiftverseConfig.EVENTS_ALTER_TERRAIN, true) && level.dimension() == RvWorldgen.EXPANSE);
        if (terrain && k > 0.5f && tickCount % 2 == 0) {
            for (int i = 0; i < (heavy ? 3 : 1); i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * reach * 0.8;
                int x = (int) Math.floor(mouth.x + Math.cos(a) * r);
                int z = (int) Math.floor(mouth.z + Math.sin(a) * r);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (y <= level.getMinBuildHeight() + 2) continue;
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(level, p) < 0 || s.is(Blocks.BEDROCK)) continue;
                FallingBlockEntity fb = FallingBlockEntity.fall(level, p, s);
                fb.dropItem = false;
                fb.setNoGravity(true);
                fb.time = -2000;
                fb.setDeltaMovement(mouth.subtract(Vec3.atCenterOf(p)).normalize().scale(0.6 + k));
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDevourer()) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isDevourer()) bossEvent.addPlayer(player);
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
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("devourer", isDevourer());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("devourer")) discard(); // devourers only exist for the length of their protocol
        bossEvent.setName(getDisplayName());
    }
}
