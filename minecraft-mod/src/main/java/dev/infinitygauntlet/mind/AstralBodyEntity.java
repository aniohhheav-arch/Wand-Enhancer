package dev.infinitygauntlet.mind;

import dev.infinitygauntlet.entity.TimedEffectEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The body an astral projection leaves behind: stands where the player was, drawn as the player, breathing slowly.
 * Striking it throws the consciousness straight back into it, and the player takes the blow.
 */
public class AstralBodyEntity extends TimedEffectEntity {
    private float bodyYaw;
    private float headYaw;

    public AstralBodyEntity(EntityType<? extends AstralBodyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    void pose(float body, float head) {
        this.bodyYaw = body;
        this.headYaw = head;
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    public float headYaw() {
        return headYaw;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (owner() == null && tickCount > 40) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() != null) {
            MindManager.bodyStruck(this, source, amount);
        }
        return !level().isClientSide;
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(bodyYaw);
        buf.writeFloat(headYaw);
        buf.writeUUID(ownerId == null ? new java.util.UUID(0, 0) : ownerId);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        bodyYaw = buf.readFloat();
        headYaw = buf.readFloat();
        ownerId = buf.readUUID();
    }

    public java.util.UUID ownerUuid() {
        return ownerId;
    }
}
