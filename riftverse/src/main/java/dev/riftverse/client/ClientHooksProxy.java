package dev.riftverse.client;

import dev.riftverse.entity.BlackHoleEntity;

/**
 * Entry points that common code calls only on the logical client. Kept free of client imports so the class is safe to
 * load on a dedicated server; the referenced client classes are resolved lazily on first call.
 */
public final class ClientHooksProxy {
    private ClientHooksProxy() {}

    public static void startBlackHoleSound(BlackHoleEntity entity) {
        ClientEffects.startBlackHoleSound(entity);
    }

    public static void blackHoleClientTick(BlackHoleEntity entity) {
        ClientEffects.blackHoleTick(entity);
    }

    public static void bossAura(net.minecraft.world.entity.Entity entity, int color) {
        ClientEffects.bossAura(entity, color);
    }
}
