package dev.finalframe.client.choreo;

/**
 * Where a finisher weapon is and how its moving parts are posed.
 *
 * @param placement   which renderer draws the weapon this frame
 * @param spin        rotation around the trigger finger (degrees, lateral axis)
 * @param roll        wrist roll around the barrel axis (degrees)
 * @param flip        backward flip around the grip (degrees)
 * @param lift        vertical hop applied with the flip (blocks, in item space)
 * @param cylinder    cylinder rotation (degrees)
 * @param hammer      hammer cocking, 0 = down, 1 = fully cocked
 */
public record WeaponState(Placement placement, float spin, float roll, float flip, float lift, float cylinder, float hammer) {
    public static final WeaponState HOLSTERED = new WeaponState(Placement.HOLSTER, 0, 0, 0, 0, 0, 0);

    public enum Placement {
        HAND,
        AIR,
        HOLSTER
    }
}
