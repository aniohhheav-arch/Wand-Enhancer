package com.infinitemultiverse.power.ability;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.ability.OwnerDamageInterceptor;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Toggled ward around the user. Incoming projectiles are stopped dead, reflected back, or simply blocked, and a share
 * of melee damage from the front (or from everywhere) is absorbed at an energy cost. Infinity, Kinetic Barrier,
 * Magnetic Shield, Bracer Deflect and the Shield of the Seraphim are all configurations of this.
 */
public final class ProjectileWardAbility extends PowerAbility implements OwnerDamageInterceptor {
    public enum Mode { STOP, REFLECT }

    private final Mode mode;
    private final double radius;
    private final float meleeReduction;
    private final boolean frontOnly;
    private final float energyPerDamage;
    private final int color;
    @Nullable
    private final Predicate<ServerPlayer> requirement;
    @Nullable
    private final String requirementMessage;

    public ProjectileWardAbility(MultiverseSystem system, float upkeep, Mode mode, double radius, float meleeReduction, boolean frontOnly,
                                 float energyPerDamage, int color, @Nullable Predicate<ServerPlayer> requirement, @Nullable String requirementMessage) {
        super(system, ActivationType.TOGGLE, upkeep, requirement != null);
        this.mode = mode;
        this.radius = radius;
        this.meleeReduction = meleeReduction;
        this.frontOnly = frontOnly;
        this.energyPerDamage = energyPerDamage;
        this.color = color;
        this.requirement = requirement;
        this.requirementMessage = requirementMessage;
    }

    private net.minecraft.resources.ResourceLocation scene = com.infinitemultiverse.core.cinematic.SceneIds.WARD_KINETIC;

    /** Rendered ward scene (see SceneIds.WARD_*). */
    public ProjectileWardAbility scene(net.minecraft.resources.ResourceLocation id) {
        this.scene = id;
        return this;
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (requirement != null && !requirement.test(ctx.player())) {
            AbilityManager.deny(ctx.player(), Component.translatable(requirementMessage));
            return false;
        }
        com.infinitemultiverse.core.cinematic.Cinematics.attached(ctx.level(), scene, ctx.player(), color, 24, (float) radius);
        return true;
    }

    private static Vec3 frontPoint(ServerPlayer player) {
        return player.getEyePosition().add(player.getLookAngle().scale(1.2)).subtract(0, 0.3, 0);
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        ServerPlayer player = ctx.player();
        if (requirement != null && !requirement.test(player)) {
            AbilityManager.deactivate(player, ctx.data(), this, DeactivationReason.MANUAL);
            return;
        }
        Vec3 center = player.getBoundingBox().getCenter();
        for (Projectile projectile : ctx.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius),
                p -> p.getOwner() != player)) {
            Vec3 velocity = projectile.getDeltaMovement();
            Vec3 toOwner = center.subtract(projectile.position());
            if (velocity.dot(toOwner) <= 0) {
                continue;
            }
            if (frontOnly && toOwner.normalize().dot(player.getLookAngle()) > -0.3) {
                continue;
            }
            if (mode == Mode.STOP) {
                projectile.setDeltaMovement(Vec3.ZERO);
                projectile.setNoGravity(false);
            } else {
                projectile.setDeltaMovement(velocity.scale(-1.1));
                projectile.setOwner(player);
            }
            projectile.hurtMarked = true;
            com.infinitemultiverse.core.cinematic.Cinematics.scene(ctx.level(), com.infinitemultiverse.core.cinematic.SceneIds.WARD_HIT, projectile.position(),
                    toOwner.normalize(), color, 12, null, 1f);
        }
        if (activeTicks % 20 == 0) {
            com.infinitemultiverse.core.cinematic.Cinematics.attached(ctx.level(), scene, player, color, 24, (float) radius);
        }
    }

    @Override
    public void onOwnerIncomingDamage(AbilityContext ctx, LivingIncomingDamageEvent event) {
        if (meleeReduction <= 0f || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().getEntity() == null) {
            return;
        }
        Vec3 sourcePos = event.getSource().getSourcePosition();
        if (frontOnly && sourcePos != null) {
            Vec3 toSource = sourcePos.subtract(ctx.player().position()).normalize();
            if (toSource.dot(ctx.player().getLookAngle()) < 0.2) {
                return;
            }
        }
        float absorbed = event.getAmount() * meleeReduction;
        if (!AbilityManager.isEnergyFree(ctx.player()) && !ctx.data().drain(energyPool(), absorbed * energyPerDamage)) {
            AbilityManager.deactivate(ctx.player(), ctx.data(), this, DeactivationReason.ENERGY_DEPLETED);
            return;
        }
        event.setAmount(event.getAmount() - absorbed);
        if (sourcePos != null) {
            Vec3 at = ctx.player().getEyePosition().lerp(sourcePos, 0.3);
            com.infinitemultiverse.core.cinematic.Cinematics.scene(ctx.level(), com.infinitemultiverse.core.cinematic.SceneIds.WARD_HIT, at,
                    sourcePos.subtract(at).normalize(), color, 12, null, 1f);
        }
    }
}
