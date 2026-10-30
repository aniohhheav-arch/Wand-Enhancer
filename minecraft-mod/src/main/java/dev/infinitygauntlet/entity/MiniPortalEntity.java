package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * One portal of the Portal Barrage: orbits its caster, swivels to track a target, fires a volley of space bolts
 * through itself and then collapses. The orbit is computed identically on both sides so it stays smooth.
 */
public class MiniPortalEntity extends TimedEffectEntity {
    public static final int OPEN_TICKS = 10;
    public static final int CLOSE_TICKS = 8;
    private static final int SHOTS = 3;
    private static final int SHOT_INTERVAL = 7;
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(MiniPortalEntity.class, EntityDataSerializers.INT);

    private int ownerEntityId = -1;
    private float orbitAngle;
    private int slot;
    private int openDelay;

    public MiniPortalEntity(EntityType<? extends MiniPortalEntity> type, Level level) {
        super(type, level);
    }

    public void configure(ServerPlayer owner, int slot, int slots, int openDelay) {
        this.ownerEntityId = owner.getId();
        this.slot = slot;
        this.orbitAngle = (float) (Math.PI * 2 * slot / slots);
        this.openDelay = openDelay;
        setup(owner, openDelay + OPEN_TICKS + 8 + SHOTS * SHOT_INTERVAL + CLOSE_TICKS, 0.55f);
        setPos(orbitPosition(owner, 0));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET, -1);
    }

    @Override
    public void tick() {
        Entity owner = level().getEntity(ownerEntityId);
        if (owner != null) {
            Vec3 p = orbitPosition(owner, tickCount);
            setOldPosAndRot();
            setPos(p);
        }
        super.tick();
    }

    private Vec3 orbitPosition(Entity owner, float t) {
        double angle = orbitAngle + t * 0.045;
        double bob = Math.sin(t * 0.12 + slot) * 0.25;
        return owner.position().add(Math.cos(angle) * 2.6, 1.7 + bob + (slot % 2) * 0.6, Math.sin(angle) * 2.6);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null) {
            tickCount = Math.max(tickCount, maxLife - CLOSE_TICKS);
            return;
        }
        int active = tickCount - openDelay;
        if (active == 0) {
            Vfx.sound(level, position(), ModSounds.SPACE_BLINK, 0.8f, 1.6f);
        }
        if (active == OPEN_TICKS - 2 || entityData.get(TARGET) < 0 && active > OPEN_TICKS && tickCount % 5 == 0) {
            LivingEntity target = pickTarget(level, owner);
            entityData.set(TARGET, target == null ? -1 : target.getId());
        }
        int sinceOpen = active - OPEN_TICKS - 8;
        if (sinceOpen >= 0 && sinceOpen % SHOT_INTERVAL == 0 && sinceOpen / SHOT_INTERVAL < SHOTS) {
            fire(level, owner);
        }
    }

    @Nullable
    private LivingEntity pickTarget(ServerLevel level, ServerPlayer owner) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(28),
                e -> TargetRules.isHostileTo(owner, e) && e.hasLineOfSight(this));
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(owner)));
        // spread the portals over the closest few enemies
        return candidates.get(slot % Math.min(candidates.size(), 3));
    }

    private void fire(ServerLevel level, ServerPlayer owner) {
        Entity target = target();
        Vec3 dir = target != null
                ? target.getBoundingBox().getCenter().subtract(position()).normalize()
                : owner.getLookAngle();
        SpaceBoltEntity bolt = new SpaceBoltEntity(level, owner, position().add(dir.scale(0.5)), dir, target);
        level.addFreshEntity(bolt);
        Vfx.sound(level, position(), ModSounds.SPACE_BOLT_FIRE, 1f, 0.9f + random.nextFloat() * 0.3f);
    }

    @Nullable
    public Entity target() {
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    /** 0..1 aperture of the portal (open → hold → close), relative to its staggered open time. */
    public float aperture(float partialTick) {
        float t = tickCount + partialTick - openDelay;
        if (t < 0) {
            return 0;
        }
        float open = Math.min(1f, t / OPEN_TICKS);
        float close = Math.max(0f, (tickCount + partialTick - (maxLife - CLOSE_TICKS)) / CLOSE_TICKS);
        return open * (1 - close);
    }

    @Override
    protected void clientTick() {
        ClientEffects.miniPortalTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(ownerEntityId);
        buf.writeFloat(orbitAngle);
        buf.writeVarInt(slot);
        buf.writeVarInt(openDelay);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        ownerEntityId = buf.readVarInt();
        orbitAngle = buf.readFloat();
        slot = buf.readVarInt();
        openDelay = buf.readVarInt();
    }
}
