package com.infinitemultiverse.power.ability;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Toggle: seize the creature, item or projectile you look at (16 blocks) and hold it 4 blocks in front of you while you
 * move and aim. Toggling off hurls it along your gaze.
 */
public final class TelekineticGripAbility extends PowerAbility {
    private static final double REACH = 16;
    private static final double HOLD_DISTANCE = 4;
    private static final double THROW_SPEED = 2.2;
    private static final Map<UUID, Integer> HELD = new HashMap<>();
    private final int color;

    public TelekineticGripAbility(MultiverseSystem system, float upkeep, int color) {
        super(system, ActivationType.TOGGLE, upkeep);
        this.color = color;
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        AABB sweep = player.getBoundingBox().expandTowards(player.getLookAngle().scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(ctx.level(), player, eye, end, sweep,
                e -> e != player && e.isAlive() && !(e instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other))));
        if (hit == null) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
            return false;
        }
        HELD.put(player.getUUID(), hit.getEntity().getId());
        bands(ctx.level(), hit.getEntity(), player);
        return true;
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        ServerPlayer player = ctx.player();
        Integer id = HELD.get(player.getUUID());
        Entity held = id == null ? null : ctx.level().getEntity(id);
        if (held == null || !held.isAlive() || held.distanceTo(player) > REACH * 1.5) {
            AbilityManager.deactivate(player, ctx.data(), this, DeactivationReason.MANUAL);
            return;
        }
        Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(HOLD_DISTANCE)).subtract(0, held.getBbHeight() * 0.5, 0);
        held.setDeltaMovement(target.subtract(held.position()).scale(0.5));
        held.fallDistance = 0;
        held.hurtMarked = true;
        if (activeTicks % 20 == 0) {
            bands(ctx.level(), held, player);
        }
    }

    private void bands(net.minecraft.server.level.ServerLevel level, Entity target, ServerPlayer caster) {
        com.infinitemultiverse.core.cinematic.Cinematics.scene(level, system() == com.infinitemultiverse.core.MultiverseSystem.MYSTIC_ARTS
                        ? com.infinitemultiverse.core.cinematic.SceneIds.MYSTIC_BANDS : com.infinitemultiverse.core.cinematic.SceneIds.GRIP,
                target.position(), new Vec3(1, 0, 0), color, 24, target, caster.getId(), com.infinitemultiverse.core.cinematic.Cinematics.FOLLOW);
    }

    @Override
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
        Integer id = HELD.remove(ctx.player().getUUID());
        Entity held = id == null ? null : ctx.level().getEntity(id);
        if (held != null && held.isAlive() && reason == DeactivationReason.MANUAL) {
            held.setDeltaMovement(ctx.player().getLookAngle().scale(THROW_SPEED));
            held.hurtMarked = true;
            MultiverseVfx.fx(ctx.level(), VfxIds.BURST, held.position(), Vec3.ZERO, color);
        }
    }
}
