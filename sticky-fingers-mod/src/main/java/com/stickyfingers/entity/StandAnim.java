package com.stickyfingers.entity;

import software.bernie.geckolib.animation.RawAnimation;

/** Stand animation states; the server picks one and GeckoLib plays it on every client. */
public enum StandAnim {
    IDLE(RawAnimation.begin().thenLoop("animation.sticky_fingers.idle"), 0),
    SUMMON(RawAnimation.begin().thenPlay("animation.sticky_fingers.summon").thenLoop("animation.sticky_fingers.idle"), 16),
    DISMISS(RawAnimation.begin().thenPlayAndHold("animation.sticky_fingers.dismiss"), 12),
    BARRAGE(RawAnimation.begin().thenLoop("animation.sticky_fingers.barrage"), 0),
    HEAVY(RawAnimation.begin().thenPlay("animation.sticky_fingers.heavy_punch").thenLoop("animation.sticky_fingers.idle"), 14),
    SLAM(RawAnimation.begin().thenPlay("animation.sticky_fingers.slam").thenLoop("animation.sticky_fingers.idle"), 24),
    CAST(RawAnimation.begin().thenPlay("animation.sticky_fingers.cast").thenLoop("animation.sticky_fingers.idle"), 12),
    UNZIP(RawAnimation.begin().thenPlay("animation.sticky_fingers.unzip").thenLoop("animation.sticky_fingers.idle"), 16);

    public static final StandAnim[] VALUES = values();
    public final RawAnimation raw;
    /** Ticks after which the server returns to idle (0 = until changed). */
    public final int length;

    StandAnim(RawAnimation raw, int length) {
        this.raw = raw;
        this.length = length;
    }
}
