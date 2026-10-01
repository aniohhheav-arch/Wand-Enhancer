package dev.riftverse.entity.ai;

import java.util.EnumSet;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Flying ranged behaviour: circle the target at a set radius and altitude, periodically charging and firing.
 * The fire callback receives the shooter and target; {@code chargeTicks} before firing the mob is flagged as charging.
 */
public class OrbitAndFireGoal extends Goal {
    private final Mob mob;
    private final double radius;
    private final double altitude;
    private final double speed;
    private final int interval;
    private final int chargeTicks;
    private final BiConsumer<Mob, LivingEntity> fire;
    private final BiConsumer<Mob, Boolean> charging;
    private int cooldown;
    private double angle;
    private int direction = 1;

    public OrbitAndFireGoal(Mob mob, double radius, double altitude, double speed, int interval, int chargeTicks,
                            BiConsumer<Mob, LivingEntity> fire, BiConsumer<Mob, Boolean> charging) {
        this.mob = mob;
        this.radius = radius;
        this.altitude = altitude;
        this.speed = speed;
        this.interval = interval;
        this.chargeTicks = chargeTicks;
        this.fire = fire;
        this.charging = charging;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public void start() {
        LivingEntity t = mob.getTarget();
        if (t != null) angle = Math.atan2(mob.getZ() - t.getZ(), mob.getX() - t.getX());
        cooldown = interval / 2;
        direction = mob.getRandom().nextBoolean() ? 1 : -1;
    }

    @Override
    public void stop() {
        charging.accept(mob, false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        angle += direction * 0.025 * speed;
        if (mob.getRandom().nextInt(200) == 0) direction = -direction;
        Vec3 goal = t.position().add(Math.cos(angle) * radius, altitude + Math.sin(mob.tickCount * 0.05) * 1.5, Math.sin(angle) * radius);
        mob.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
        mob.getLookControl().setLookAt(t, 30f, 30f);
        cooldown--;
        if (cooldown == chargeTicks) charging.accept(mob, true);
        if (cooldown <= 0) {
            charging.accept(mob, false);
            if (mob.hasLineOfSight(t)) fire.accept(mob, t);
            cooldown = interval + mob.getRandom().nextInt(Math.max(1, interval / 3));
        }
    }
}
