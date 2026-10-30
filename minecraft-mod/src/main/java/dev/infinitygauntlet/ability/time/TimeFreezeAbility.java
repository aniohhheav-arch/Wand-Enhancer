package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.entity.TimeFieldEntity;
import dev.infinitygauntlet.entity.TimedEffectEntity;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Stops time for everything around the caster: creatures, projectiles and falling objects hang motionless. */
public final class TimeFreezeAbility extends Ability {
    public TimeFreezeAbility() {
        super("time_freeze", InfinityStone.TIME, 600);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerLevel level = ctx.level();
        double radius = ServerConfig.FREEZE_RADIUS.get();
        int ticks = ServerConfig.FREEZE_SECONDS.get() * 20;
        Vec3 center = ctx.player().position();
        List<Entity> targets = level.getEntities(ctx.player(), ctx.player().getBoundingBox().inflate(radius),
                e -> !(e instanceof TimedEffectEntity) && !(e instanceof RupturePortalEntity) && TimeFieldEntity.affectedBy(ctx.player(), e)
                        && e.position().distanceTo(center) <= radius);
        for (Entity e : targets) {
            // the freeze lands as the wave reaches each entity
            int delay = 1 + (int) (e.position().distanceTo(center) / radius * 8);
            ServerScheduler.schedule(delay, () -> {
                if (!e.isRemoved()) {
                    TimeManager.apply(e, 0f, ticks - delay);
                }
            });
        }
        Vfx.send(level, VfxType.SPHERE_PULSE, ctx.handPos(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), (float) radius, 12);
        Vfx.shockwave(level, center.add(0, 0.15, 0), new Vec3(0, 1, 0), InfinityStone.TIME, (float) radius, 12);
        Vfx.send(level, VfxType.CLOCK_GLYPH, center.add(0, 0.05, 0), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(),
                (float) radius * 0.9f, ticks);
        Vfx.send(level, VfxType.SUSPENDED_FIELD, center.add(0, 1.5, 0), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(),
                (float) radius, ticks);
        Vfx.sound(level, center, ModSounds.TIME_FREEZE, 3f, 1f);
        Vfx.camera(level, center, (float) radius * 2, 0.3f, 1.08f, 16, 0xB4FFC8, 0.25f, 6);

        ServerScheduler.schedule(ticks, () -> {
            for (Entity e : targets) {
                if (!e.isRemoved() && e instanceof LivingEntity) {
                    Vfx.send(level, VfxType.SPARK_BURST, e.getBoundingBox().getCenter(), Vec3.ZERO, InfinityStone.TIME.color(),
                            InfinityStone.TIME.highlight(), 0.6f, 10);
                }
            }
            Vfx.shockwave(level, center.add(0, 0.15, 0), new Vec3(0, 1, 0), InfinityStone.TIME, (float) radius * 1.2f, 14);
            Vfx.sound(level, center, ModSounds.TIME_RESUME, 2.5f, 1f);
        });
        return CastResult.success(Component.translatable("message.infinitygauntlet.time_freeze", targets.size()));
    }
}
