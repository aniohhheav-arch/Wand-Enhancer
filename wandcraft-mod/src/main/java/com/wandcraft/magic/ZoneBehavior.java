package com.wandcraft.magic;

import com.wandcraft.entity.SpellZone;

/** Per-spell persistent area logic. {@link #tick} runs on the server, {@link #fx} on the client. */
public interface ZoneBehavior {
    default void start(SpellZone z) {}
    default void tick(SpellZone z) {}
    default void fx(SpellZone z) {}
    default void end(SpellZone z) {}
}
