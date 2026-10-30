package dev.infinitygauntlet.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base for short-lived, server-driven ability entities (rifts, prisons, spheres...). The server owns all gameplay;
 * the spawn packet carries the current age and lifetime so late-joining clients render the same animation phase.
 */
public abstract class TimedEffectEntity extends Entity implements IEntityWithComplexSpawn {
    protected int maxLife = 100;
    @Nullable
    protected UUID ownerId;
    protected float visualRadius = 1.0f;

    protected TimedEffectEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(@Nullable Entity owner, int lifetime, float radius) {
        this.ownerId = owner == null ? null : owner.getUUID();
        this.maxLife = lifetime;
        this.visualRadius = radius;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (tickCount >= maxLife) {
            onExpire((ServerLevel) level());
            discard();
            return;
        }
        serverTick((ServerLevel) level());
    }

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {
    }

    protected void onExpire(ServerLevel level) {
    }

    /** 0..1 progress through the entity's lifetime, smoothed by the render partial tick. */
    public float life(float partialTick) {
        return Math.min(1f, (tickCount + partialTick) / Math.max(1, maxLife));
    }

    public int maxLife() {
        return maxLife;
    }

    public float visualRadius() {
        return visualRadius;
    }

    @Nullable
    public ServerPlayer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getServer().getPlayerList().getPlayer(ownerId);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(visualRadius * 2 + 2);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(tickCount);
        buf.writeVarInt(maxLife);
        buf.writeFloat(visualRadius);
        writeExtraSpawnData(buf);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        tickCount = buf.readVarInt();
        maxLife = buf.readVarInt();
        visualRadius = buf.readFloat();
        readExtraSpawnData(buf);
    }

    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
    }

    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 128 + visualRadius * 8;
        return distance < d * d;
    }
}
