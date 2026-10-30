package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A wall of clockwork rings held in front of the caster. Incoming projectiles decelerate as they approach and are
 * suspended in place when they reach it. Dismissing the barrier (re-cast) or letting it expire sends every suspended
 * projectile back the way it came, now owned by the caster.
 */
public class ChronoBarrierEntity extends TimedEffectEntity {
    public static final double DISTANCE = 3.4;
    private static final double SLOW_RANGE = 10;

    private record Held(Projectile projectile, Vec3 velocity) {
    }

    private final Map<Integer, Held> suspended = new HashMap<>();
    private int ownerEntityId = -1;

    public ChronoBarrierEntity(EntityType<? extends ChronoBarrierEntity> type, Level level) {
        super(type, level);
    }

    public void bind(ServerPlayer owner) {
        this.ownerEntityId = owner.getId();
        follow(owner, 1f);
    }

    @Nullable
    public Entity ownerEntity() {
        return ownerEntityId < 0 ? null : level().getEntity(ownerEntityId);
    }

    /** The barrier faces along the owner's horizontal look direction. */
    public Vec3 facing(float pt) {
        Entity owner = ownerEntity();
        if (owner == null) {
            return Vec3.directionFromRotation(0, getYRot());
        }
        return Vec3.directionFromRotation(0, owner.getViewYRot(pt));
    }

    private void follow(Entity owner, float pt) {
        Vec3 f = Vec3.directionFromRotation(0, owner.getViewYRot(pt));
        Vec3 p = owner.getPosition(pt).add(f.scale(DISTANCE)).add(0, owner.getBbHeight() * 0.55, 0);
        setOldPosAndRot();
        setPos(p);
        setYRot(owner.getViewYRot(pt));
    }

    @Override
    public void tick() {
        Entity owner = ownerEntity();
        if (owner != null) {
            follow(owner, 1f);
        }
        super.tick();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            tickCount = maxLife;
            return;
        }
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.TIME_BARRIER, 1.5f, 1f);
        }
        for (Held h : suspended.values()) {
            if (!h.projectile.isRemoved()) {
                TimeManager.apply(h.projectile, 0f, 3);
            }
        }
        Vec3 facing = facing(1f);
        for (Entity e : level.getEntities(this, new AABB(position(), position()).inflate(SLOW_RANGE),
                e -> e instanceof Projectile p && p.getOwner() != owner && !suspended.containsKey(e.getId()))) {
            Projectile p = (Projectile) e;
            Vec3 to = position().subtract(p.position());
            double dist = to.length();
            Vec3 v = p.getDeltaMovement();
            boolean incoming = v.dot(to) > 0 || dist < visualRadius;
            boolean inFront = p.position().subtract(owner.position()).dot(facing) > 0.5;
            if (!incoming || !inFront) {
                continue;
            }
            // the plane distance to the barrier face decides when it is caught
            double planeDist = Math.abs(p.position().subtract(position()).dot(facing));
            Vec3 lateral = p.position().subtract(position()).subtract(facing.scale(p.position().subtract(position()).dot(facing)));
            if (planeDist < 1.2 && lateral.length() < visualRadius * 1.15) {
                suspended.put(p.getId(), new Held(p, v));
                TimeManager.apply(p, 0f, 3);
                Vfx.send(level, VfxType.CLOCK_RINGS, p.position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 0.45f, 16);
                Vfx.sound(level, p.position(), ModSounds.TIME_SUSPEND, 1f, 0.9f + random.nextFloat() * 0.3f);
            } else if (dist < SLOW_RANGE) {
                float scale = (float) Math.max(0.08, Math.min(1, (planeDist - 1.2) / (SLOW_RANGE - 1.2)));
                TimeManager.apply(p, scale, 2);
            }
        }
    }

    public int suspendedCount() {
        return suspended.size();
    }

    /** Releases suspended projectiles back toward where they came from. */
    public void release() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = owner();
        for (Held h : suspended.values()) {
            Projectile p = h.projectile;
            if (p.isRemoved()) {
                continue;
            }
            TimeManager.clear(p);
            p.setDeltaMovement(h.velocity.scale(-1.1));
            p.hurtMarked = true;
            if (owner != null) {
                p.setOwner(owner);
            }
            Vfx.send(level, VfxType.SPARK_BURST, p.position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 0.5f, 8);
        }
        suspended.clear();
    }

    public void dismiss() {
        tickCount = Math.max(tickCount, maxLife - 1);
    }

    @Override
    protected void onExpire(ServerLevel level) {
        release();
        Vfx.shockwave(level, position(), facing(1f), InfinityStone.TIME, visualRadius * 2f, 12);
        Vfx.sound(level, position(), ModSounds.TIME_RESUME, 1.4f, 1.1f);
    }

    @Override
    protected void clientTick() {
        ClientEffects.barrierTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(ownerEntityId);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        ownerEntityId = buf.readVarInt();
    }
}
