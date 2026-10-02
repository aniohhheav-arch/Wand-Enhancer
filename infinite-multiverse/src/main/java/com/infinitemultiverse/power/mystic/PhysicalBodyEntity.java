package com.infinitemultiverse.power.mystic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The body left behind during Astral Projection: a still, head-bowed copy of the player with their skin and armour.
 * Anything that hurts it snaps the spirit back and passes the damage on. It cleans itself up if its owner is back.
 */
public final class PhysicalBodyEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PhysicalBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);

    public PhysicalBodyEntity(EntityType<? extends PhysicalBodyEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    public void setOwner(ServerPlayer owner) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                armor.set(slot.getIndex(), owner.getItemBySlot(slot).copy());
            } else if (slot.getType() == EquipmentSlot.Type.HAND) {
                hands.set(slot.getIndex(), owner.getItemBySlot(slot).copy());
            }
        }
        setYRot(owner.getYRot());
        yBodyRot = owner.getYRot();
        yHeadRot = owner.getYRot();
        setXRot(25f);
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        yHeadRot = getYRot();
        yBodyRot = getYRot();
        if (!level().isClientSide && tickCount % 20 == 0) {
            UUID owner = ownerId();
            ServerPlayer player = owner == null ? null : level().getServer().getPlayerList().getPlayer(owner);
            if (owner == null || (player != null && !AstralState.isProjecting(player))) {
                discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        UUID owner = ownerId();
        ServerPlayer player = owner == null ? null : level().getServer().getPlayerList().getPlayer(owner);
        if (player != null && AstralState.isProjecting(player)) {
            MysticArts.forceReturn(player);
            player.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HUMANOID_ARMOR -> armor.get(slot.getIndex());
            case HAND -> hands.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            armor.set(slot.getIndex(), stack);
        } else if (slot.getType() == EquipmentSlot.Type.HAND) {
            hands.set(slot.getIndex(), stack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = ownerId();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
    }

}
