package com.infinitemultiverse.power.mutant;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.Beams;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.core.world.Blink;
import com.infinitemultiverse.core.world.TemporaryBlocks;
import com.infinitemultiverse.power.PowerAbility;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Mutant and hero abilities that need bespoke logic. Simple beams, bursts and buffs are configured in PowerSets. */
public final class MutantAbilities {
    public static final String ICE_SHARD_TAG = "infinitemultiverse_ice_shard";
    private static final MultiverseSystem HEROES = MultiverseSystem.SUPERHEROES;

    private MutantAbilities() {
    }

    /** Telepathy: read the mind of whatever you look at. */
    public static final class MindScan extends PowerAbility {
        public MindScan() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Vec3 eye = player.getEyePosition();
            var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(ctx.level(), player, eye,
                    eye.add(player.getLookAngle().scale(32)), player.getBoundingBox().expandTowards(player.getLookAngle().scale(32)).inflate(1),
                    e -> e instanceof LivingEntity && e != player);
            if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
                return false;
            }
            String mindTarget = target instanceof Mob mob && mob.getTarget() != null ? mob.getTarget().getName().getString() : "-";
            player.sendSystemMessage(Component.translatable("message.infinitemultiverse.mind_scan", target.getDisplayName(),
                    String.format(java.util.Locale.ROOT, "%.1f/%.1f", target.getHealth(), target.getMaxHealth()),
                    target.getArmorValue(), mindTarget, target.getActiveEffects().size()).withStyle(ChatFormatting.LIGHT_PURPLE));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0, false, false), player);
            MultiverseVfx.fx(ctx.level(), VfxIds.BEAM, eye, target.getEyePosition().subtract(eye), 0xE07AFF);
            return true;
        }
    }

    /** Electrokinesis: lightning that jumps between up to five foes. */
    public static final class ChainLightning extends PowerAbility {
        public ChainLightning() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Beams.Result first = Beams.trace(player, 20, 1.2, 1);
            if (first.hits().isEmpty()) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
                return false;
            }
            Set<LivingEntity> struck = new HashSet<>();
            LivingEntity current = first.hits().get(0);
            Vec3 from = player.getEyePosition();
            for (int jump = 0; jump < 5 && current != null; jump++) {
                struck.add(current);
                Vec3 to = current.getBoundingBox().getCenter();
                MultiverseVfx.fx(ctx.level(), VfxIds.BOLT, from, to.subtract(from), 0x9FD8FF);
                current.invulnerableTime = 0;
                current.hurt(player.damageSources().indirectMagic(player, player), 6f);
                from = to;
                LivingEntity next = null;
                double best = 36;
                for (LivingEntity candidate : AbilityTargeting.hostilesInRadius(player, 32)) {
                    double d = candidate.distanceToSqr(current);
                    if (!struck.contains(candidate) && d < best) {
                        best = d;
                        next = candidate;
                    }
                }
                current = next;
            }
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_NAIL, 1f, 1.8f);
            return true;
        }
    }

    /** Cryokinesis: a fast ice shard; on impact it damages and freezes (handled in PowerEvents). */
    public static final class IceShard extends PowerAbility {
        public IceShard() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Snowball shard = new Snowball(ctx.level(), player);
            shard.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 2.6f, 0.2f);
            shard.getPersistentData().putBoolean(ICE_SHARD_TAG, true);
            ctx.level().addFreshEntity(shard);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_KNIFE, 0.8f, 1.8f);
            return true;
        }
    }

    /** Cryokinesis toggle: water and empty air beneath you freeze into a temporary ice path. */
    public static final class IcePath extends PowerAbility {
        public IcePath() {
            super(HEROES, ActivationType.TOGGLE, 3f);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            return true;
        }

        @Override
        public void tickActive(AbilityContext ctx, int activeTicks) {
            ServerPlayer player = ctx.player();
            ServerLevel level = ctx.level();
            BlockPos below = player.blockPosition().below();
            boolean bridging = player.isShiftKeyDown() || !level.getFluidState(below).isEmpty();
            if (!bridging && player.onGround()) {
                return;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = below.offset(dx, 0, dz);
                    boolean water = level.getFluidState(pos).is(Fluids.WATER);
                    boolean air = level.getBlockState(pos).isAir() && bridging;
                    if (water || air) {
                        TemporaryBlocks.place(level, pos, Blocks.PACKED_ICE.defaultBlockState(), 160, "ice:" + player.getUUID(), false);
                    }
                }
            }
            if (activeTicks % 5 == 0) {
                MultiverseVfx.fx(level, VfxIds.FROST, player.position(), new Vec3(1.2, 0, 0), 0xBFEFFF);
            }
        }
    }

    /** Magnetism: drag loose items, armoured creatures and golems toward you. */
    public static final class MagneticPull extends PowerAbility {
        public MagneticPull() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            for (var entity : ctx.level().getEntities(player, player.getBoundingBox().inflate(16))) {
                boolean metallic = entity instanceof ItemEntity
                        || entity instanceof IronGolem
                        || (entity instanceof LivingEntity living && living.getArmorValue() > 0 && AbilityTargeting.isHostileTarget(player, living));
                if (!metallic) {
                    continue;
                }
                Vec3 toward = player.position().subtract(entity.position());
                entity.setDeltaMovement(toward.normalize().scale(Math.min(1.6, 0.4 + toward.length() * 0.12)).add(0, 0.25, 0));
                entity.hurtMarked = true;
            }
            MultiverseVfx.fx(ctx.level(), VfxIds.DOMAIN_CLOSE, player.position().add(0, 1, 0), new Vec3(6, 0, 0), 0xC0C8D8);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_ACCELERATE, 0.8f, 0.6f);
            return true;
        }
    }

    /** Speedster: cross 16 blocks in a blink, striking everything along the way. */
    public static final class SpeedForceDash extends PowerAbility {
        public SpeedForceDash() {
            super(HEROES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            Beams.Result path = Beams.trace(ctx.player(), 16, 1.2, 8);
            if (!Blink.perform(ctx, 16)) {
                return false;
            }
            for (LivingEntity target : path.hits()) {
                target.invulnerableTime = 0;
                target.hurt(ctx.player().damageSources().playerAttack(ctx.player()), 6f);
            }
            MultiverseVfx.fx(ctx.level(), VfxIds.BOLT, path.start(), path.vector(), 0xFFD84A);
            return true;
        }
    }

    /** Grapple onto the block you look at (32 blocks) and launch toward it. */
    public static final class Grapple extends PowerAbility {
        private final Predicate<ServerPlayer> requirement;

        public Grapple(Predicate<ServerPlayer> requirement) {
            super(HEROES, ActivationType.INSTANT, 0f, true);
            this.requirement = requirement;
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            if (!requirement.test(player)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.needs_suit"));
                return false;
            }
            Vec3 eye = player.getEyePosition();
            BlockHitResult hit = ctx.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_anchor"));
                return false;
            }
            Vec3 toward = hit.getLocation().subtract(player.position());
            player.setDeltaMovement(toward.normalize().scale(Math.min(2.6, 0.8 + toward.length() * 0.09)).add(0, 0.4, 0));
            player.hurtMarked = true;
            ctx.data().grantFallProtection(80);
            MultiverseVfx.fx(ctx.level(), VfxIds.BEAM, eye, hit.getLocation().subtract(eye), 0x2A2A2A);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_NAIL, 0.8f, 0.8f);
            return true;
        }
    }

    /** A mighty leap along your gaze, with fall protection. */
    public static final class Leap extends PowerAbility {
        private final double strength;
        @Nullable
        private final Predicate<ServerPlayer> requirement;

        public Leap(double strength, @Nullable Predicate<ServerPlayer> requirement) {
            super(HEROES, ActivationType.INSTANT, 0f, requirement != null);
            this.strength = strength;
            this.requirement = requirement;
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            if (requirement != null && !requirement.test(player)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.needs_suit"));
                return false;
            }
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(new Vec3(look.x * strength, Math.max(look.y * strength, 0) + 0.7, look.z * strength));
            player.hurtMarked = true;
            ctx.data().grantFallProtection(160);
            MultiverseVfx.broadcast(ctx.level(), VfxIds.KINETIC_LEAP, player.position(), player.getDeltaMovement(), 1f);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.KINETIC_LEAP, 1f, 0.8f);
            return true;
        }
    }

    /** Electrokinesis toggle helper: zap hostiles within 4 blocks once a second. */
    public static void staticPulse(AbilityContext ctx, int seconds) {
        for (LivingEntity target : AbilityTargeting.hostilesInRadius(ctx.player(), 4.5)) {
            target.invulnerableTime = 0;
            target.hurt(ctx.player().damageSources().indirectMagic(ctx.player(), ctx.player()), 3f);
            Vec3 from = ctx.player().getBoundingBox().getCenter();
            MultiverseVfx.fx(ctx.level(), VfxIds.BOLT, from, target.getBoundingBox().getCenter().subtract(from), 0x9FD8FF);
        }
    }

    /** Pyrokinesis toggle helper: ignite hostiles within 3 blocks once a second. */
    public static void heatPulse(AbilityContext ctx, int seconds) {
        for (LivingEntity target : AbilityTargeting.hostilesInRadius(ctx.player(), 3.5)) {
            target.igniteForSeconds(3);
        }
        MultiverseVfx.fx(ctx.level(), VfxIds.BURST, ctx.player().position().add(0, 0.2, 0), Vec3.ZERO, 0xFF7A1A);
    }
}
