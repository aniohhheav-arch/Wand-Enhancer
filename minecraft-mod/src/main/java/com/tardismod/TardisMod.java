package com.tardismod;

import net.fabricmc.api.ModInitializer;

/** Common entrypoint. Planned: TARDIS block/entity, interior dimension, console, flight state machine (port of tardis/src/ship.js). */
public class TardisMod implements ModInitializer {
    public static final String MOD_ID = "tardismod";

    @Override
    public void onInitialize() {
        // TODO: register blocks/entities/dimension types here (APIs differ per Minecraft version — verify against 26.2).
    }
}
