package dev.finalframe.finisher;

/** States of a cinematic finisher. Every finisher walks these in order, skipping any it does not use. */
public enum FinisherPhase {
    IDLE,
    ACTIVATION,
    SHOVE,
    DRAW,
    FLOURISH,
    THROW,
    CATCH,
    AIM,
    FIRE,
    FINISH,
    RESTORE
}
