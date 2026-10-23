package com.wandcraft.entity;

import com.wandcraft.WandCraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A purely visual chunk of ground thrown into the air. It never touches the world: it is a copy of a block
 * that tumbles for a moment and then shatters into particles.
 */
public class DebrisEntity extends Entity {
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final int LIFE = 40;

    public final float spinX = (float) (Math.random() - 0.5) * 18f;
    public final float spinZ = (float) (Math.random() - 0.5) * 18f;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
    }

    public DebrisEntity(Level level, BlockState state) {
        this(WandCraft.DEBRIS.get(), level);
        entityData.set(STATE, state);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STATE, Blocks.DIRT.defaultBlockState());
    }

    public BlockState state() { return entityData.get(STATE); }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(getDeltaMovement().add(0, -0.045, 0).scale(0.99));
        move(MoverType.SELF, getDeltaMovement());
        if (level().isClientSide && tickCount % 3 == 0) {
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state()), getX(), getY() + 0.3, getZ(), 0, 0, 0);
        }
        if (!level().isClientSide && (tickCount > LIFE || (onGround() && tickCount > 6))) {
            ((net.minecraft.server.level.ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state()),
                    getX(), getY() + 0.3, getZ(), 10, 0.25, 0.25, 0.25, 0.05);
            discard();
        }
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
