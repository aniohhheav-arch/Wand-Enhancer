package dev.riftverse.entity.boss;

import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.entity.ai.Flight;
import dev.riftverse.entity.creature.RiftWraithEntity;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.util.Advancements;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Rift Warden: ancient custodian of the boundaries between universes. Three phases of escalating spectacle -
 * bolt barrages and aerial slams, then summoned wraiths and void singularities, then a reality-cutting beam.
 */
public class RiftWardenEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RiftWardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEAM = SynchedEntityData.defineId(RiftWardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_YAW = SynchedEntityData.defineId(RiftWardenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_PITCH = SynchedEntityData.defineId(RiftWardenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SLAM = SynchedEntityData.defineId(RiftWardenEntity.class, EntityDataSerializers.INT);

    public static final int BEAM_CHARGE = 40;
    public static final int BEAM_TOTAL = 110;
    public static final int COLOR = 0xFFC14D;

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(Component.translatable("entity.riftverse.rift_warden"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
    private int barrageCd = 60;
    private int slamCd = 220;
    private int summonCd = 200;
    private int voidCd = 260;
    private int beamCd = 160;
    private float beamSweep = 2.2f;
    private double orbit;

    public RiftWardenEntity(EntityType<? extends RiftWardenEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 12, true);
        this.setNoGravity(true);
        this.xpReward = 250;
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static void summon(ServerLevel level, BlockPos altar, Player summoner) {
        RiftWardenEntity warden = new RiftWardenEntity(RvEntities.RIFT_WARDEN.get(), level);
        warden.moveTo(altar.getX() + 0.5, altar.getY() + 8, altar.getZ() + 0.5, summoner.getYRot() + 180f, 0);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(warden, level, level.getCurrentDifficultyAt(altar), MobSpawnType.EVENT, null);
        warden.setPersistenceRequired();
        level.addFreshEntity(warden);
        Vec3 c = warden.position();
        level.sendParticles(RvParticles.RING.get().with(COLOR, 14f, 30), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.SPARK.get().with(0x8F6BFF, 0.8f, 40), c.x, c.y, c.z, 150, 2, 3, 2, 0.6);
        level.playSound(null, c.x, c.y, c.z, RvSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 5.0f, 0.8f);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) < 96 * 96) {
                PacketDistributor.sendToPlayer(p, new Payloads.BossIntro(warden.getId(), "THE RIFT WARDEN", "Custodian of the Boundaries", COLOR));
                PacketDistributor.sendToPlayer(p, new Payloads.Shake(1.0f, 50, 0.7f, COLOR));
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1);
        builder.define(BEAM, 0);
        builder.define(BEAM_YAW, 0f);
        builder.define(BEAM_PITCH, 0f);
        builder.define(SLAM, 0);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public int beamTicks() {
        return entityData.get(BEAM);
    }

    public float beamYaw() {
        return entityData.get(BEAM_YAW);
    }

    public float beamPitch() {
        return entityData.get(BEAM_PITCH);
    }

    public boolean isSlamming() {
        return entityData.get(SLAM) > 0;
    }

    public Vec3 beamDirection() {
        float yaw = beamYaw() * Mth.DEG_TO_RAD;
        float pitch = beamPitch() * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
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
        updatePhase(level);
        LivingEntity target = getTarget();

        int beam = beamTicks();
        if (beam > 0) {
            tickBeam(level, beam, target);
            return;
        }
        int slam = entityData.get(SLAM);
        if (slam > 0) {
            tickSlam(level, slam);
            return;
        }
        if (target == null || !target.isAlive()) {
            Vec3 home = position();
            moveControl.setWantedPosition(home.x, home.y + Math.sin(tickCount * 0.05) * 0.3, home.z, 0.3);
            return;
        }
        orbit += 0.02;
        Vec3 goal = target.position().add(Math.cos(orbit) * 11, 6 + Math.sin(tickCount * 0.04) * 2, Math.sin(orbit) * 11);
        moveControl.setWantedPosition(goal.x, goal.y, goal.z, 1.0);
        getLookControl().setLookAt(target, 30f, 30f);

        if (--barrageCd <= 0) {
            barrage(level, target);
            barrageCd = phase() >= 2 ? 45 : 65;
        }
        if (--slamCd <= 0) {
            startSlam(level, target);
            slamCd = 260 + random.nextInt(80);
        }
        if (phase() >= 2 && --summonCd <= 0) {
            summonWraiths(level);
            summonCd = 420;
        }
        if (phase() >= 2 && --voidCd <= 0) {
            voidSingularity(level, target);
            voidCd = 340;
        }
        if (phase() >= 3 && --beamCd <= 0) {
            entityData.set(BEAM, BEAM_TOTAL);
            Vec3 to = target.getEyePosition().subtract(getEyePosition());
            entityData.set(BEAM_YAW, (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG) - 35f);
            entityData.set(BEAM_PITCH, (float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG));
            beamSweep = random.nextBoolean() ? 1.4f : -1.4f;
            if (beamSweep < 0) entityData.set(BEAM_YAW, beamYaw() + 70f);
            level.playSound(null, getX(), getY(), getZ(), RvSounds.WARDEN_CHARGE.get(), SoundSource.HOSTILE, 3.0f, 0.6f);
            beamCd = 300;
        }
    }

    private void updatePhase(ServerLevel level) {
        float ratio = getHealth() / getMaxHealth();
        int target = ratio < 0.25f ? 3 : ratio < 0.6f ? 2 : 1;
        if (target > phase()) {
            entityData.set(PHASE, target);
            level.playSound(null, getX(), getY(), getZ(), RvSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 5.0f, target == 3 ? 0.6f : 0.75f);
            level.sendParticles(RvParticles.RING.get().with(target == 3 ? 0xFF4040 : 0x8F6BFF, 20f, 30), getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 80 * 80) PacketDistributor.sendToPlayer(p, new Payloads.Shake(1.1f, 40, 0.8f, target == 3 ? 0xFF6040 : 0xB090FF));
            }
            bossEvent.setColor(target == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PINK);
        }
    }

    private void barrage(ServerLevel level, LivingEntity target) {
        int count = phase() >= 2 ? 9 : 7;
        Vec3 to = target.getEyePosition().subtract(getEyePosition());
        Vec3 side = to.cross(new Vec3(0, 1, 0)).normalize();
        for (int i = 0; i < count; i++) {
            double spread = (i - (count - 1) / 2.0) * 0.09 * to.length();
            Vec3 aim = to.add(side.scale(spread)).add(0, Math.abs(spread) * 0.15, 0);
            EnergyBoltEntity bolt = EnergyBoltEntity.create(level, this, i % 2 == 0 ? COLOR : 0x8F6BFF, 0.55f, 7f);
            bolt.setPos(getX(), getEyeY() - 0.4, getZ());
            bolt.shoot(aim.x, aim.y, aim.z, 1.25f, 0.4f);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, getX(), getY(), getZ(), RvSounds.ENERGY_FIRE.get(), SoundSource.HOSTILE, 2.0f, 0.7f);
    }

    private void startSlam(ServerLevel level, LivingEntity target) {
        Vec3 above = target.position().add(0, 9, 0);
        level.sendParticles(RvParticles.STREAK.get().with(COLOR, 0.8f, 10), getX(), getY() + 2, getZ(), 30, 1, 2, 1, 0.3);
        teleportTo(above.x, above.y, above.z);
        level.sendParticles(RvParticles.RING.get().with(COLOR, 5f, 14), above.x, above.y, above.z, 1, 0, 0, 0, 0);
        level.playSound(null, above.x, above.y, above.z, RvSounds.BLADE_DASH.get(), SoundSource.HOSTILE, 2.0f, 0.5f);
        entityData.set(SLAM, 26);
    }

    private void tickSlam(ServerLevel level, int slam) {
        entityData.set(SLAM, slam - 1);
        if (slam > 14) {
            setDeltaMovement(0, 0.02, 0);
            return;
        }
        setDeltaMovement(0, -1.6, 0);
        if (verticalCollisionBelow || slam - 1 == 0) {
            entityData.set(SLAM, 0);
            Vec3 c = position();
            level.sendParticles(RvParticles.RING.get().with(COLOR, 12f, 20), c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(RvParticles.SPARK.get().with(0xFFE0A0, 0.8f, 30), c.x, c.y + 0.5, c.z, 100, 4, 0.5, 4, 0.4);
            level.playSound(null, c.x, c.y, c.z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.HOSTILE, 3.0f, 0.5f);
            for (Entity e : level.getEntities(this, new AABB(c, c).inflate(8, 3, 8), e -> e instanceof LivingEntity)) {
                e.hurt(damageSources().mobAttack(this), 14f);
                e.setDeltaMovement(e.position().subtract(c).normalize().scale(1.4).add(0, 0.9, 0));
                e.hurtMarked = true;
            }
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(c) < 40 * 40) PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.9f, 20, 0.2f, COLOR));
            }
            setDeltaMovement(0, 0.6, 0);
        }
    }

    private void summonWraiths(ServerLevel level) {
        long nearby = level.getEntitiesOfClass(RiftWraithEntity.class, getBoundingBox().inflate(40)).size();
        if (nearby >= 6) return;
        for (int i = 0; i < 3; i++) {
            RiftWraithEntity wraith = new RiftWraithEntity(RvEntities.RIFT_WRAITH.get(), level);
            double a = random.nextDouble() * Math.PI * 2;
            wraith.moveTo(getX() + Math.cos(a) * 6, getY() + 1, getZ() + Math.sin(a) * 6, 0, 0);
            wraith.setTarget(getTarget());
            level.addFreshEntity(wraith);
            level.sendParticles(RvParticles.RING.get().with(0xFF2BD6, 2.5f, 14), wraith.getX(), wraith.getY() + 1, wraith.getZ(), 1, 0, 0, 0, 0);
        }
        level.playSound(null, getX(), getY(), getZ(), RvSounds.RIFT_OPEN.get(), SoundSource.HOSTILE, 2.0f, 0.6f);
    }

    private void voidSingularity(ServerLevel level, LivingEntity target) {
        BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), level);
        Vec3 at = target.position().add((random.nextDouble() - 0.5) * 6, 3.5, (random.nextDouble() - 0.5) * 6);
        hole.moveTo(at.x, at.y, at.z, 0, 0);
        hole.setHorizonRadius(1.3f);
        hole.setCaptures(false);
        hole.setLifetime(100);
        hole.setStyle(BlackHoleEntity.STYLE_WARDEN);
        hole.setOwner(this);
        level.addFreshEntity(hole);
    }

    private void tickBeam(ServerLevel level, int beam, LivingEntity target) {
        entityData.set(BEAM, beam - 1);
        setDeltaMovement(getDeltaMovement().scale(0.5));
        int elapsed = BEAM_TOTAL - beam;
        if (elapsed < BEAM_CHARGE) {
            if (target != null) getLookControl().setLookAt(target, 10f, 10f);
            return;
        }
        if (elapsed == BEAM_CHARGE) level.playSound(null, getX(), getY(), getZ(), RvSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 4.0f, 1.3f);
        entityData.set(BEAM_YAW, beamYaw() + beamSweep);
        if (elapsed % 4 != 0) return;
        Vec3 origin = getEyePosition();
        Vec3 dir = beamDirection();
        double len = 48;
        List<Entity> hits = level.getEntities(this, new AABB(origin, origin.add(dir.scale(len))).inflate(1.5), e -> e instanceof LivingEntity && !(e instanceof RiftWraithEntity));
        for (Entity e : hits) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(origin);
            double t = rel.dot(dir);
            if (t < 0 || t > len) continue;
            if (rel.subtract(dir.scale(t)).length() < 1.4 + e.getBbWidth() * 0.5) e.hurt(damageSources().indirectMagic(this, this), 10f);
        }
        if (elapsed % 8 == 0) {
            Vec3 end = origin.add(dir.scale(len));
            net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(origin, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            Vec3 p = hit.getLocation();
            level.sendParticles(RvParticles.SPARK.get().with(0xFFE0A0, 0.6f, 14), p.x, p.y, p.z, 14, 0.5, 0.3, 0.5, 0.25);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof RiftWardenEntity) return false;
        return super.hurt(source, amount * (beamTicks() > 0 ? 0.6f : 1f));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            Vec3 c = position().add(0, 2, 0);
            level.sendParticles(RvParticles.RING.get().with(COLOR, 30f, 40), c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(RvParticles.RING.get().with(0x8F6BFF, 45f, 50), c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(RvParticles.SPARK.get().with(0xFFE0A0, 1.0f, 50), c.x, c.y, c.z, 300, 3, 3, 3, 1.0);
            level.playSound(null, c.x, c.y, c.z, RvSounds.BLACK_HOLE_COLLAPSE.get(), SoundSource.HOSTILE, 6.0f, 0.7f);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(c) < 96 * 96) {
                    PacketDistributor.sendToPlayer(p, new Payloads.Shake(1.5f, 60, 1.0f, 0xFFF0D0));
                    Advancements.award(p, "warden_slayer");
                }
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
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RvSounds.WARDEN_CHARGE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.ENERGY_HIT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.WARDEN_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 3.0f;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, Math.max(1, tag.getInt("phase")));
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("phase", phase());
    }
}
