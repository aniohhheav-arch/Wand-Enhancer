package dev.finalframe.finisher;

import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Activation validation, shared by the server (authoritative) and the client (prompt and prediction). */
public final class FinisherRules {
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, FinalFrame.id("finisher_immune"));

    public enum Verdict {
        OK,
        NO_WEAPON,
        COOLDOWN,
        INELIGIBLE,
        TOO_FAR,
        NOT_BEHIND,
        NEEDS_SNEAK,
        BUSY
    }

    private FinisherRules() {
    }

    public static Verdict check(Player player, LivingEntity target, ItemStack stack) {
        if (!(stack.getItem() instanceof FinisherWeaponItem)) {
            return Verdict.NO_WEAPON;
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return Verdict.COOLDOWN;
        }
        if (!isEligible(player, target)) {
            return Verdict.INELIGIBLE;
        }
        if (player.isPassenger() || player.isSpectator() || player.isSleeping()) {
            return Verdict.INELIGIBLE;
        }
        Vec3 toPlayer = player.position().subtract(target.position());
        double horizontal = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
        if (horizontal > FFConfig.get(FFConfig.ACTIVATION_RANGE) || Math.abs(toPlayer.y) > 1.5) {
            return Verdict.TOO_FAR;
        }
        if (!isBehind(player, target)) {
            return Verdict.NOT_BEHIND;
        }
        if (FFConfig.get(FFConfig.REQUIRE_SNEAK) && !player.isShiftKeyDown()) {
            return Verdict.NEEDS_SNEAK;
        }
        return Verdict.OK;
    }

    public static boolean isEligible(Player player, LivingEntity target) {
        if (target == player || !target.isAlive() || target.isRemoved() || target.isPassenger() || target.isVehicle()) {
            return false;
        }
        if (target instanceof ArmorStand || target.getType().is(IMMUNE) || target.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player other && (!FFConfig.get(FFConfig.ALLOW_PLAYER_TARGETS) || other.isCreative() || other.isSpectator())) {
            return false;
        }
        if (target.getBbHeight() > FFConfig.get(FFConfig.MAX_TARGET_HEIGHT)) {
            return false;
        }
        double maxHealth = FFConfig.get(FFConfig.MAX_TARGET_HEALTH);
        return maxHealth <= 0 || target.getMaxHealth() <= maxHealth;
    }

    /** True when the player stands inside the configured cone behind the target's body. */
    public static boolean isBehind(Player player, LivingEntity target) {
        Vec3 toPlayer = player.position().subtract(target.position());
        double len = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
        if (len < 1.0E-4) {
            return true;
        }
        float bodyYaw = target.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(bodyYaw);
        double fz = Mth.cos(bodyYaw);
        double cos = (fx * toPlayer.x + fz * toPlayer.z) / len;
        double angleFromBehind = 180.0 - Math.toDegrees(Math.acos(Mth.clamp(cos, -1.0, 1.0)));
        return angleFromBehind <= FFConfig.get(FFConfig.BEHIND_ANGLE) / 2.0;
    }
}
