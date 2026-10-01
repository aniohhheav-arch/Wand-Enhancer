package dev.riftverse.entity;

import dev.riftverse.registry.RvParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The energy packet fired by the Portal Gun; opens a portal where it strikes a surface. */
public class PortalBoltEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(PortalBoltEntity.class, EntityDataSerializers.INT);

    public PortalBoltEntity(EntityType<? extends PortalBoltEntity> type, Level level) {
        super(type, level);
    }

    public PortalBoltEntity(EntityType<? extends PortalBoltEntity> type, LivingEntity shooter, Level level, int slot) {
        super(type, shooter, level);
        entityData.set(SLOT, slot);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SLOT, 0);
    }

    public int slot() {
        return entityData.get(SLOT);
    }

    public int color() {
        return slot() == RIFT_SLOT ? 0x8F6BFF : slot() == 0 ? PortalEntity.COLOR_A : PortalEntity.COLOR_B;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                double t = i / 3.0;
                level().addParticle(RvParticles.SPARK.get().with(color(), 0.22f, 10), getX() - v.x * t, getY() - v.y * t, getZ() - v.z * t, 0, 0, 0);
            }
        } else if (tickCount > 60) {
            discard();
        }
    }

    /** Slot 2 bolts tear a rift instead of opening a portal. */
    public static final int RIFT_SLOT = 2;
    @org.jetbrains.annotations.Nullable
    private dev.riftverse.transit.Destination riftDestination;

    public void setRiftDestination(dev.riftverse.transit.Destination destination) {
        this.riftDestination = destination;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel server)) return;
        Entity owner = getOwner();
        Direction face = hit.getDirection();
        BlockPos pos = hit.getBlockPos();
        if (slot() == RIFT_SLOT) {
            BlockPos at = pos.relative(face);
            if (face.getAxis().isHorizontal() || face == Direction.UP) at = at.above(face == Direction.UP ? 1 : 0);
            if (server.getBlockState(at).canBeReplaced()) {
                server.setBlockAndUpdate(at, dev.riftverse.registry.RvBlocks.RIFT.get().defaultBlockState()
                        .setValue(dev.riftverse.block.RiftBlock.TYPE, dev.riftverse.block.RiftType.STELLAR));
                if (server.getBlockEntity(at) instanceof dev.riftverse.block.RiftBlockEntity rift) {
                    rift.configure(riftDestination != null ? riftDestination : dev.riftverse.transit.Destination.random(), server.getGameTime() + 1200, false);
                }
                server.playSound(null, at, dev.riftverse.registry.RvSounds.RIFT_OPEN.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 1.0f);
                server.sendParticles(RvParticles.RING.get().with(0x8F6BFF, 3.0f, 18), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
            discard();
            return;
        }
        if (owner instanceof Player player && server.getBlockState(pos).isFaceSturdy(server, pos, face)) {
            Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.52));
            Direction up = Direction.UP;
            if (face.getAxis().isHorizontal()) {
                BlockPos above = pos.above();
                if (server.getBlockState(above).isFaceSturdy(server, above, face) && server.getBlockState(above.relative(face)).isAir()) center = center.add(0, 0.5, 0);
                else center = center.add(0, -0.5 + PortalEntity.HALF_H, 0).add(0, -0.45, 0);
            } else {
                up = player.getDirection();
                if (face == Direction.DOWN) up = up.getOpposite();
            }
            PortalEntity.place(server, player, slot(), center, face, up);
        } else {
            server.sendParticles(RvParticles.SPARK.get().with(color(), 0.4f, 12), hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 16, 0.1, 0.1, 0.1, 0.15);
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(RvParticles.SPARK.get().with(color(), 0.4f, 12), getX(), getY(), getZ(), 10, 0.1, 0.1, 0.1, 0.15);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 128 * 128;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("slot", slot());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SLOT, tag.getInt("slot"));
    }
}
