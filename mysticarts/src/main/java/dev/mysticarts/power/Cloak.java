package dev.mysticarts.power;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Cloak of Levitation: grants controlled flight (double-tap jump, like creative flight but slower), catches its
 * wearer when they fall, and lashes out at anything that strikes them.
 */
public final class Cloak {
    private static final String FLIGHT_TAG = "mysticarts:cloak_flight";
    private static final String LASH_TAG = "mysticarts:cloak_lash";
    private static final float FLY_SPEED = 0.065f;

    private Cloak() {}

    public static void tick(ServerPlayer player, PowerData data) {
        boolean worn = PowerManager.hasArtifact(data, Artifact.CLOAK);
        boolean shouldFly = worn && data.cloakFlight && !player.isSpectator();
        boolean granted = player.getPersistentData().getBoolean(FLIGHT_TAG);
        var abilities = player.getAbilities();
        if (shouldFly && !granted) {
            player.getPersistentData().putBoolean(FLIGHT_TAG, true);
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                abilities.setFlyingSpeed(FLY_SPEED);
                player.onUpdateAbilities();
            }
        } else if (!shouldFly && granted) {
            player.getPersistentData().putBoolean(FLIGHT_TAG, false);
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                abilities.setFlyingSpeed(0.05f);
                player.onUpdateAbilities();
            }
        }
        if (!worn) return;

        boolean flying = abilities.flying;
        if (flying != player.getPersistentData().getBoolean("mysticarts:was_flying")) {
            player.getPersistentData().putBoolean("mysticarts:was_flying", flying);
            data.casterDirty = true;
            Fx.sound(player, MaSounds.CLOAK_FLAP.get(), 0.8f, flying ? 1.1f : 0.85f);
            if (!flying && player.onGround()) Fx.ring(player.serverLevel(), player.position().add(0, 0.05, 0), new Vec3(0, 1, 0), Artifact.CLOAK.color, 1.4f, 12);
        }
        // the cloak catches a falling wearer (never while sneaking, so they can still drop on purpose)
        if (!flying && !player.onGround() && !player.isShiftKeyDown() && player.fallDistance > 3.5f && player.getDeltaMovement().y < -0.3) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, true));
            player.resetFallDistance();
            if (player.tickCount % 6 == 0) Fx.sound(player, MaSounds.CLOAK_FLAP.get(), 0.5f, 1.3f);
        }
        long lash = player.getPersistentData().getLong(LASH_TAG);
        if (lash > 0 && player.level().getGameTime() > lash) player.getPersistentData().remove(LASH_TAG);
    }

    /** Autonomous defence: the cloak strikes back at an attacker (at most every two seconds). */
    public static void onWearerHurt(ServerPlayer player, LivingEntity attacker) {
        if (!PowerManager.hasArtifact(player, Artifact.CLOAK) || attacker == player || player.distanceToSqr(attacker) > 36) return;
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(LASH_TAG) > now) return;
        player.getPersistentData().putLong(LASH_TAG, now + 40);
        attacker.hurt(player.damageSources().indirectMagic(player, player), 3f);
        attacker.knockback(1.2, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        Vec3 from = player.position().add(0, 1.2, 0).subtract(player.getLookAngle().scale(0.3));
        Fx.send(player.serverLevel(), FxKind.ARC, from, attacker.position().add(0, attacker.getBbHeight() * 0.6, 0).subtract(from), Artifact.CLOAK.color, 0.6f, 0, player.getId());
        Fx.sound(player, MaSounds.CLOAK_FLAP.get(), 1f, 0.7f);
        Fx.sound(player, MaSounds.WHIP_CRACK.get(), 0.6f, 1.4f);
    }
}
