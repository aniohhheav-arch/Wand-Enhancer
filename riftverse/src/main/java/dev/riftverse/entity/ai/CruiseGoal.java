package dev.riftverse.entity.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Long, graceful flights toward far-away waypoints, staying a comfortable height above the terrain. */
public class CruiseGoal extends Goal {
    private final Mob mob;
    private final double speed;
    private final double range;
    private final int minHeight;
    private Vec3 waypoint;

    public CruiseGoal(Mob mob, double speed, double range, int minHeight) {
        this.mob = mob;
        this.speed = speed;
        this.range = range;
        this.minHeight = minHeight;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return waypoint != null && mob.getTarget() == null && mob.distanceToSqr(waypoint) > 9;
    }

    @Override
    public void start() {
        pick();
    }

    private void pick() {
        double a = mob.getRandom().nextDouble() * Math.PI * 2;
        double d = range * (0.5 + mob.getRandom().nextDouble() * 0.5);
        double x = mob.getX() + Math.cos(a) * d;
        double z = mob.getZ() + Math.sin(a) * d;
        int ground = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
        double floor = Math.max(ground, mob.level().getMinBuildHeight() + 40) + minHeight;
        double y = Math.max(floor, mob.getY() + (mob.getRandom().nextDouble() - 0.5) * 16);
        y = Math.min(y, mob.level().getMaxBuildHeight() - 20);
        waypoint = new Vec3(x, y, z);
    }

    @Override
    public void tick() {
        if (waypoint == null) return;
        mob.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, speed);
        if (mob.tickCount % 200 == 0 && mob.getNavigation().isStuck()) pick();
    }
}
