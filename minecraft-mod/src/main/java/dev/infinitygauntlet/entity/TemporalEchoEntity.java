package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.EntityHistory;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A translucent duplicate of the caster that replays the caster's recorded movement. It is a living entity so hostile
 * mobs can be lured onto it; it cannot be hurt and dissolves when the replay ends.
 */
public class TemporalEchoEntity extends LivingEntity implements IEntityWithComplexSpawn {
    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private List<EntityHistory.Frame> frames = List.of();
    private int ownerEntityId = -1;
    private int index;
    private int length = 80;

    public TemporalEchoEntity(EntityType<? extends TemporalEchoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20);
    }

    /** {@code recorded} is oldest-first. */
    public void configure(Player owner, List<EntityHistory.Frame> recorded) {
        this.ownerEntityId = owner.getId();
        this.frames = new ArrayList<>(recorded);
        this.length = frames.size();
        EntityHistory.Frame first = frames.getFirst();
        moveTo(first.pos().x, first.pos().y, first.pos().z, first.yRot(), first.xRot());
        setYHeadRot(first.headRot());
        setYBodyRot(first.yRot());
    }

    @Nullable
    public Entity ownerEntity() {
        return ownerEntityId < 0 ? null : level().getEntity(ownerEntityId);
    }

    /** 0..1 replay progress. */
    public float progress(float pt) {
        return Math.min(1f, (tickCount + pt) / Math.max(1, length));
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (index >= frames.size()) {
            Vfx.send(level, VfxType.DISSOLVE, position(), new Vec3(getBbWidth(), getBbHeight(), 0), InfinityStone.TIME.color(),
                    InfinityStone.TIME.highlight(), 1f, 20);
            Vfx.sound(level, position(), ModSounds.TIME_ECHO, 1f, 1.4f);
            discard();
            return;
        }
        EntityHistory.Frame f = frames.get(index++);
        moveTo(f.pos().x, f.pos().y, f.pos().z, f.yRot(), f.xRot());
        setYHeadRot(f.headRot());
        if (tickCount % 10 == 1) {
            Entity owner = ownerEntity();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(20), m -> m instanceof Enemy && m.isAlive())) {
                if (mob.getTarget() == null || mob.getTarget() == owner) {
                    mob.setTarget(this);
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32), m -> m.getTarget() == this)) {
                mob.setTarget(null);
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel level && source.getEntity() != null && tickCount % 4 == 0) {
            Vfx.send(level, VfxType.SPARK_BURST, getBoundingBox().getCenter(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(),
                    0.6f, 8);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(ownerEntityId);
        buf.writeVarInt(length);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        ownerEntityId = buf.readVarInt();
        length = buf.readVarInt();
    }
}
