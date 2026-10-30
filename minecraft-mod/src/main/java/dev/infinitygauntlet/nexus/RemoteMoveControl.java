package dev.infinitygauntlet.nexus;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Replaces a possessed mob's move control. Vanilla's control zeroes the mob's movement every tick unless a goal
 * steers it and caps strafing at a quarter of its speed; this one simply applies the operator's input each tick.
 */
public class RemoteMoveControl extends MoveControl {
    private float forward;
    private float strafe;
    private float speedMultiplier = 1f;

    public RemoteMoveControl(Mob mob) {
        super(mob);
    }

    public void input(float forward, float strafe, float speedMultiplier) {
        this.forward = forward;
        this.strafe = strafe;
        this.speedMultiplier = speedMultiplier;
    }

    @Override
    public void tick() {
        if (forward == 0 && strafe == 0) {
            mob.setSpeed(0);
            mob.setZza(0);
            mob.setXxa(0);
            return;
        }
        mob.setSpeed((float) (mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedMultiplier));
        mob.setZza(forward);
        mob.setXxa(strafe);
    }

    @Override
    public boolean hasWanted() {
        return forward != 0 || strafe != 0;
    }
}
