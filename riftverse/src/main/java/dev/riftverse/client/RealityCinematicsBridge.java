package dev.riftverse.client;

import dev.riftverse.client.cinematic.RealityCinematics;

/** Lets the screen-effect composer read the reality cinematics without a package cycle. */
final class RealityCinematicsBridge {
    private RealityCinematicsBridge() {}

    static float blackHoleWarp(float partial) {
        return RealityCinematics.blackHoleWarp(partial);
    }
}
