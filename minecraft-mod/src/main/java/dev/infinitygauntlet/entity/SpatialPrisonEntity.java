package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Six rotating portal rings that suspend a target in place. Every attempt by the target to move is snapped back by
 * the server and shows up as strain on the rings; the prison collapses when its time runs out or the target dies.
 */
public class SpatialPrisonEntity extends TimedEffectEntity {
    private static final EntityDataAccessor<Float> STRAIN = SynchedEntityData.defineId(SpatialPrisonEntity.class, EntityDataSerializers.FLOAT);
    private int targetId = -1;

    public SpatialPrisonEntity(EntityType<? extends SpatialPrisonEntity> type, Level level) {
        super(type, level);
    }

    public void setTarget(LivingEntity target) {
        this.targetId = target.getId();
    }

    @Nullable
    public Entity target() {
        return targetId < 0 ? null : level().getEntity(targetId);
    }

    public float strain() {
        return entityData.get(STRAIN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STRAIN, 0f);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Entity target = target();
        if (target == null || !target.isAlive() || target.level() != level) {
            tickCount = maxLife - 1;
            return;
        }
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.SPACE_PRISON, 2f, 1f);
        }
        Vec3 hold = position().subtract(0, target.getBbHeight() / 2, 0);
        double drift = target.position().distanceTo(hold);
        float strain = strain() * 0.9f;
        if (drift > 0.08) {
            strain = Math.min(1f, strain + (float) drift * 0.6f);
            if (target instanceof ServerPlayer player) {
                player.connection.teleport(hold.x, hold.y, hold.z, player.getYRot(), player.getXRot());
            } else {
                target.teleportTo(hold.x, hold.y, hold.z);
            }
            if (drift > 0.2 && tickCount % 4 == 0) {
                Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(visualRadius);
                Vfx.arc(level, position(), position().add(dir), InfinityStone.SPACE, 4);
                Vfx.send(level, VfxType.DISTORTION, position(), Vec3.ZERO, 0x2A6EFF, 0x9FE8FF, visualRadius * 1.1f, 8);
                Vfx.sound(level, position(), ModSounds.SPACE_ARC, 0.8f, 1.2f + random.nextFloat() * 0.4f);
            }
        }
        entityData.set(STRAIN, strain);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0;
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    @Override
    protected void onExpire(ServerLevel level) {
        Vfx.send(level, VfxType.IMPLOSION, position(), Vec3.ZERO, 0x2A6EFF, 0xDDF6FF, visualRadius * 1.2f, 10);
        Vfx.shockwave(level, position(), new Vec3(0, 1, 0), InfinityStone.SPACE, visualRadius * 3, 12);
        Vfx.sound(level, position(), ModSounds.SPACE_PORTAL_CLOSE, 1.5f, 1.4f);
    }

    @Override
    protected void clientTick() {
        ClientEffects.prisonTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(targetId);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        targetId = buf.readVarInt();
    }
}
