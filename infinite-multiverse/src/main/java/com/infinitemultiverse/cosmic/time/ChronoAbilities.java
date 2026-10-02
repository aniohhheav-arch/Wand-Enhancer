package com.infinitemultiverse.cosmic.time;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.stand.RewindTracker;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Chronokinesis: bend the flow of time around you. */
public final class ChronoAbilities {
    static final MultiverseSystem HEROES = MultiverseSystem.SUPERHEROES;
    static final int COLOR = 0x2EE0C0;

    private ChronoAbilities() {
    }

    /** Time Dilation (toggle): within 10 blocks foes crawl and projectiles drift as if in bullet-time. */
    public static final class TimeDilation extends PowerAbility {
        public TimeDilation() {
            super(HEROES, ActivationType.TOGGLE, 5f);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            Cinematics.attached(ctx.level(), SceneIds.TIME_FIELD, ctx.player(), COLOR, 24, 10f);
            MultiverseVfx.sound(ctx.level(), ctx.player().position(), ModSounds.TEMPORAL_DRAG, 1f, 0.8f);
            return true;
        }

        @Override
        public void tickActive(AbilityContext ctx, int activeTicks) {
            ServerPlayer player = ctx.player();
            for (Projectile p : ctx.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(10), p -> p.getOwner() != player)) {
                p.setDeltaMovement(p.getDeltaMovement().scale(0.6));
                p.hurtMarked = true;
            }
            if (activeTicks % 10 == 0) {
                for (LivingEntity e : AbilityTargeting.hostilesInRadius(player, 10)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4, false, false), player);
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 3, false, false), player);
                }
            }
            if (activeTicks % 20 == 0) {
                Cinematics.attached(ctx.level(), SceneIds.TIME_FIELD, player, COLOR, 24, 10f);
            }
        }

        @Override
        public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
            Cinematics.stop(ctx.level(), SceneIds.TIME_FIELD, ctx.player().position(), ctx.player());
        }
    }

    /** Rewind Step: snap back to where you were a few seconds ago, healed to what you were then. */
    public static final class RewindStep extends PowerAbility {
        public RewindStep() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            RewindTracker.Snapshot snap = RewindTracker.oldest(player);
            if (snap == null) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_history"));
                return false;
            }
            Vec3 from = player.position();
            player.teleportTo(ctx.level(), snap.position().x, snap.position().y, snap.position().z, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
            player.setHealth(Math.max(player.getHealth(), snap.health()));
            RewindTracker.clear(player);
            Cinematics.scene(ctx.level(), SceneIds.TIME_REWIND, from, snap.position().subtract(from), COLOR, 24, player, 1f);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_REWIND, 1f, 1.4f);
            return true;
        }
    }

    /** Stasis: lock the creature you look at inside a frozen moment for 6 seconds. */
    public static final class Stasis extends PowerAbility {
        public Stasis() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Vec3 eye = player.getEyePosition();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(ctx.level(), player, eye, eye.add(player.getLookAngle().scale(24)),
                    player.getBoundingBox().expandTowards(player.getLookAngle().scale(24)).inflate(1),
                    e -> e instanceof LivingEntity && e.isAlive() && AbilityTargeting.isHostileTarget(player, e));
            if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
                return false;
            }
            Cinematics.scene(ctx.level(), SceneIds.STASIS, target.position(), Vec3.ZERO, COLOR, 120, target, target.getBbHeight(), Cinematics.FOLLOW);
            boolean hadAi = target instanceof Mob m && !m.isNoAi();
            if (target instanceof Mob m) {
                m.setNoAi(true);
            }
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 10, false, false), player);
            StandScheduler.repeat(1, 1, 120, i -> {
                if (!target.isAlive()) {
                    return false;
                }
                target.setDeltaMovement(Vec3.ZERO);
                return true;
            });
            StandScheduler.later(120, () -> {
                if (hadAi && target instanceof Mob m && target.isAlive()) {
                    m.setNoAi(false);
                }
            });
            MultiverseVfx.sound(ctx.level(), target.position(), ModSounds.TIME_STOP, 1f, 1.6f);
            return true;
        }
    }
}
