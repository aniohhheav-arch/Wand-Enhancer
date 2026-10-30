package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.ability.TargetRules;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A region of slowed time. Everything inside that is not the caster or an ally runs at {@link #timeScale} through
 * {@link TimeManager}; stepping out restores normal speed within two ticks.
 * <ul>
 *     <li>{@link #SHAPE_DISC}: Time Slow, a ground field (position = ground centre, height {@link #DISC_HEIGHT}).</li>
 *     <li>{@link #SHAPE_SPHERE}: Temporal Bubble (position = sphere centre).</li>
 * </ul>
 */
public class TimeFieldEntity extends TimedEffectEntity {
    public static final byte SHAPE_DISC = 0;
    public static final byte SHAPE_SPHERE = 1;
    public static final double DISC_HEIGHT = 4.5;
    public static final int GROW_TICKS = 16;
    public static final int COLLAPSE_TICKS = 14;

    private byte shape = SHAPE_DISC;
    private float timeScale = 0.25f;

    public TimeFieldEntity(EntityType<? extends TimeFieldEntity> type, Level level) {
        super(type, level);
    }

    public void configure(byte shape, float timeScale) {
        this.shape = shape;
        this.timeScale = timeScale;
    }

    public byte shape() {
        return shape;
    }

    /** 0..1 visual size: grows in, holds, then collapses. */
    public float size(float pt) {
        float t = tickCount + pt;
        float grow = Math.min(1f, t / GROW_TICKS);
        grow = 1 - (1 - grow) * (1 - grow) * (1 - grow);
        float collapse = Math.max(0f, (t - (maxLife - COLLAPSE_TICKS)) / COLLAPSE_TICKS);
        return grow * (1 - collapse * collapse);
    }

    public boolean contains(Entity e) {
        Vec3 c = e.getBoundingBox().getCenter();
        double r = visualRadius * size(0);
        if (shape == SHAPE_SPHERE) {
            return c.distanceToSqr(position()) <= r * r;
        }
        double dx = c.x - getX(), dz = c.z - getZ();
        return dx * dx + dz * dz <= r * r && e.getY() >= getY() - 1 && e.getY() <= getY() + DISC_HEIGHT;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.TIME_FIELD_OPEN, 2f, shape == SHAPE_SPHERE ? 0.8f : 1f);
        }
        if (tickCount % 80 == 2) {
            Vfx.sound(level, position(), ModSounds.TIME_FIELD_LOOP, 1.2f, shape == SHAPE_SPHERE ? 0.8f : 1f);
        }
        ServerPlayer owner = owner();
        double r = visualRadius + 1;
        AABB box = shape == SHAPE_SPHERE ? new AABB(position(), position()).inflate(r)
                : new AABB(getX() - r, getY() - 1, getZ() - r, getX() + r, getY() + DISC_HEIGHT, getZ() + r);
        for (Entity e : level.getEntities(this, box, e -> affectedBy(owner, e))) {
            if (contains(e)) {
                TimeManager.apply(e, timeScale, 3);
            }
        }
    }

    public static boolean affectedBy(Player owner, Entity e) {
        if (e instanceof TimedEffectEntity || e instanceof RupturePortalEntity || e.isSpectator()) {
            return false;
        }
        if (owner == null) {
            return !(e instanceof Player);
        }
        if (e instanceof Player) {
            return TargetRules.canTarget(owner, e);
        }
        return !TargetRules.isAlly(owner, e);
    }

    @Override
    protected void onExpire(ServerLevel level) {
        Vfx.send(level, VfxType.IMPLOSION, position().add(0, shape == SHAPE_SPHERE ? 0 : 1, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                InfinityStone.TIME.highlight(), visualRadius * 0.5f, 10);
        Vfx.shockwave(level, position().add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.TIME, visualRadius * 1.6f, 16);
        Vfx.sound(level, position(), ModSounds.TIME_RESUME, 1.6f, 1f);
    }

    @Override
    protected void clientTick() {
        ClientEffects.timeFieldTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeByte(shape);
        buf.writeFloat(timeScale);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        shape = buf.readByte();
        timeScale = buf.readFloat();
    }
}
