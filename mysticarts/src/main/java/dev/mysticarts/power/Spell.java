package dev.mysticarts.power;

/** A castable effect. Returns false when nothing happened (no target, unsafe destination...) so no cost is paid. */
@FunctionalInterface
public interface Spell {
    boolean cast(Cast c);
}
