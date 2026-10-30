package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.EntityHistory;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A small time anomaly. The server records the exact state (position, rotation, velocity) of every creature that
 * enters it; once per loop period each captured creature is snapped back to that state, so it keeps re-living the
 * same few seconds until the loop expires or it escapes far enough away.
 */
public class TemporalLoopEntity extends TimedEffectEntity {
    private record Capture(Entity entity, EntityHistory.Frame anchor) {
    }

    private final Map<Integer, Capture> captured = new HashMap<>();
    private int period = 50;

    public TemporalLoopEntity(EntityType<? extends TemporalLoopEntity> type, Level level) {
        super(type, level);
    }

    public int period() {
        return period;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (tickCount == 1) {
            period = ServerConfig.LOOP_PERIOD_TICKS.get();
            Vfx.sound(level, position(), ModSounds.TIME_FIELD_OPEN, 1.5f, 1.3f);
        }
        ServerPlayer owner = owner();
        double r = visualRadius;
        for (Entity e : level.getEntities(this, new AABB(position(), position()).inflate(r, 3, r),
                e -> e instanceof LivingEntity && TimeFieldEntity.affectedBy(owner, e))) {
            if (!captured.containsKey(e.getId()) && horizontalDistSqr(e) <= r * r) {
                captured.put(e.getId(), new Capture(e, EntityHistory.snapshot(e)));
                Vfx.send(level, VfxType.CLOCK_RINGS, e.position().add(0, e.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                        InfinityStone.TIME.highlight(), Math.max(0.7f, e.getBbWidth()), 20);
            }
        }
        if (tickCount % period != 0) {
            return;
        }
        double escape = r * 3;
        Iterator<Capture> it = captured.values().iterator();
        boolean looped = false;
        while (it.hasNext()) {
            Capture c = it.next();
            Entity e = c.entity;
            if (e.isRemoved() || !e.isAlive() || e.level() != level || e.position().distanceToSqr(position()) > escape * escape) {
                it.remove();
                continue;
            }
            EntityHistory.Frame a = c.anchor;
            if (e.position().distanceToSqr(a.pos()) < 0.25) {
                continue;
            }
            Vec3 safe = TeleportUtil.findSafeSpot(level, e, a.pos(), 1);
            if (safe == null) {
                continue;
            }
            Vfx.send(level, VfxType.AFTERIMAGES, e.position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 1f, 16, e.getId());
            Vfx.send(level, VfxType.LINK, e.position().add(0, e.getBbHeight() / 2, 0), safe.add(0, e.getBbHeight() / 2, 0),
                    InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 0.2f, 10);
            TeleportUtil.teleport(e, level, safe, a.yRot(), a.xRot(), a.velocity());
            e.setYHeadRot(a.headRot());
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            Vfx.send(level, VfxType.CLOCK_RINGS, safe.add(0, e.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                    InfinityStone.TIME.highlight(), Math.max(0.7f, e.getBbWidth()), 14);
            looped = true;
        }
        if (looped) {
            Vfx.sound(level, position(), ModSounds.TIME_LOOP, 1.5f, 1f);
            Vfx.shockwave(level, position().add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.TIME, (float) r * 1.4f, 10);
        }
    }

    private double horizontalDistSqr(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return dx * dx + dz * dz;
    }

    @Override
    protected void onExpire(ServerLevel level) {
        Vfx.send(level, VfxType.IMPLOSION, position().add(0, 1, 0), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(),
                visualRadius * 0.6f, 10);
        Vfx.sound(level, position(), ModSounds.TIME_RESUME, 1.2f, 1.2f);
    }

    @Override
    protected void clientTick() {
        ClientEffects.loopTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(ServerConfig.LOOP_PERIOD_TICKS.get());
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        period = buf.readVarInt();
    }
}
