package dev.mysticarts.entity;

import dev.mysticarts.power.PowerManager;
import dev.mysticarts.power.spells.MysticSpells;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A conjured double of its owner, drawn with the owner's own skin. Three modes: a fighting clone (Magical Cloning), a
 * decoy that draws aggression (Mind Stone illusion), and the empty body left behind during astral projection.
 */
public class MysticCloneEntity extends SummonEntity {
    public static final int CLONE = 0;
    public static final int DECOY = 1;
    public static final int BODY = 2;

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(MysticCloneEntity.class, EntityDataSerializers.INT);

    public MysticCloneEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, CLONE);
    }

    public void mode(int mode) {
        entityData.set(MODE, mode);
        if (mode != CLONE) setNoAi(true);
        if (mode == DECOY) setHealth(1f);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    @Override
    protected boolean fights() {
        return mode() == CLONE;
    }

    @Override
    protected int glowColor() {
        return switch (mode()) {
            case DECOY -> 0xFFC81E;
            case BODY -> 0x9FD4FF;
            default -> 0xFF9A2E;
        };
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (mode() == BODY) {
            if (!level().isClientSide && ownerEntity() instanceof ServerPlayer owner && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                // the soul is yanked back into a body under attack
                MysticSpells.endAstral(owner, PowerManager.data(owner));
                owner.hurt(source, amount);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        if (mode() == CLONE) super.doPush(entity);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) living.knockback(0.5, getX() - target.getX(), getZ() - target.getZ());
        return hit;
    }
}
