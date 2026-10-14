package com.stickyfingers.network;

/** Client-side camera / screen effect kinds sent by the server. */
public enum ScreenFx {
    SHAKE, FLASH, ZIP_TRANSITION, SLOWMO, SPEED_LINES, FOV_PUNCH, VIGNETTE;

    public static final ScreenFx[] VALUES = values();
}
