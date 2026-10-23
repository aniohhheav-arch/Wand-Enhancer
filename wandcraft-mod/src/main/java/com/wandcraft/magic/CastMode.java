package com.wandcraft.magic;

public enum CastMode {
    /** Cast the moment right-click is pressed. */
    INSTANT,
    /** Hold to charge, release to cast. A tap casts at minimum charge. */
    CHARGE,
    /** Hold to maintain; mana is drained every tick until released. */
    CHANNEL
}
