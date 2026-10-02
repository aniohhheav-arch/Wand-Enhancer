package com.infinitemultiverse.cosmic.gauntlet;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.Summons;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.world.Blink;
import com.infinitemultiverse.core.world.TemporaryBlocks;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.stand.RewindTracker;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/** Infinity Stone techniques with bespoke mechanics (the simple beams and bursts are built in CosmicContent). */
public final class StoneAbilities {
    static final MultiverseSystem GAUNTLET = MultiverseSystem.INFINITY_GAUNTLET;

    private StoneAbilities() {
    }

    /** Base: gear-gated by a set of stones in a held gauntlet; damage grows with every stone set. */
    public abstract static class StoneAbility extends PowerAbility {
        private final int requiredMask;

        protected StoneAbility(int requiredMask) {
            this(requiredMask, ActivationType.INSTANT, 0f);
        }

        protected StoneAbility(int requiredMask, ActivationType type, float upkeep) {
            super(GAUNTLET, type, upkeep, true);
            this.requiredMask = requiredMask;
        }

        @Override
        public final boolean activate(AbilityContext ctx) {
            if (!meets(ctx.player(), requiredMask)) {
                AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.needs_stones"));
                return false;
            }
            return cast(ctx);
        }

        protected abstract boolean cast(AbilityContext ctx);

        protected static float power(ServerPlayer player) {
            return 1f + 0.12f * (Gauntlet.heldCount(player) - 1);
        }
    }

    public static boolean meets(ServerPlayer player, int mask) {
        ItemStack g = Gauntlet.held(player);
        return !g.isEmpty() && (Gauntlet.stones(g) & mask) == mask;
    }

    public static Predicate<ServerPlayer> needs(InfinityStone... stones) {
        int mask = 0;
        for (InfinityStone s : stones) {
            mask |= s.bit();
        }
        int m = mask;
        return p -> meets(p, m);
    }

    @Nullable
    static LivingEntity lookTarget(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, eye.add(player.getLookAngle().scale(reach)),
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && e != player);
        return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
    }

    static Vec3 fist(ServerPlayer player) {
        return player.getEyePosition().add(player.getLookAngle().scale(0.8)).subtract(0, 0.3, 0);
    }

    // ============================== SPACE ==============================

    /** Space Warp: fold space and step through to where you look (64 blocks). */
    public static final class SpaceWarp extends StoneAbility {
        public SpaceWarp() {
            super(InfinityStone.SPACE.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            Vec3 from = ctx.player().position();
            if (!Blink.perform(ctx, 64)) {
                return false;
            }
            Cinematics.scene(ctx.level(), SceneIds.SPACE_WARP, from, ctx.player().position().subtract(from), InfinityStone.SPACE.color(), 24, ctx.player(), 1f);
            return true;
        }
    }

    /** Tesseract Fold (ultimate): space collapses into a cube in front of you, dragging every foe in 24 blocks inside it. */
    public static final class SpaceFold extends StoneAbility {
        public SpaceFold() {
            super(InfinityStone.SPACE.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Vec3 point = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(7)).add(0, 1, 0);
            Cinematics.scene(ctx.level(), SceneIds.TESSERACT, point, Vec3.ZERO, InfinityStone.SPACE.color(), 50, player, 24f);
            float damage = 18f * power(player);
            List<LivingEntity> victims = AbilityTargeting.hostilesInRadius(player, 24);
            StandScheduler.repeat(4, 1, 20, i -> {
                for (LivingEntity e : victims) {
                    if (e.isAlive()) {
                        e.setDeltaMovement(point.subtract(e.position()).scale(0.25));
                        e.hurtMarked = true;
                    }
                }
                return true;
            });
            StandScheduler.later(26, () -> {
                for (LivingEntity e : victims) {
                    if (e.isAlive() && e.position().distanceTo(point) < 6) {
                        e.invulnerableTime = 0;
                        e.hurt(player.damageSources().indirectMagic(player, player), damage);
                    }
                }
                MultiverseVfx.sound(ctx.level(), point, ModSounds.SHOCKWAVE, 1.6f, 0.6f);
            });
            MultiverseVfx.sound(ctx.level(), point, ModSounds.TIME_STOP, 1.2f, 1.6f);
            return true;
        }
    }

    // ============================== MIND ==============================

    /** Mind Control: the creature you look at becomes your thrall for 30 seconds and hunts your enemies. */
    public static final class MindControl extends StoneAbility {
        public MindControl() {
            super(InfinityStone.MIND.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            LivingEntity target = lookTarget(player, 24);
            if (!(target instanceof Mob mob) || target.getType().is(Tags.EntityTypes.BOSSES)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
                return false;
            }
            enthrall(player, mob, 600);
            return true;
        }
    }

    static void enthrall(ServerPlayer player, Mob mob, int ticks) {
        mob.getPersistentData().putUUID(Summons.CREATOR_TAG, player.getUUID());
        mob.setTarget(null);
        Cinematics.scene(player.serverLevel(), SceneIds.MIND_THRALL, mob.position(), Vec3.ZERO, InfinityStone.MIND.color(), ticks, mob, 1f, Cinematics.FOLLOW);
        UUID owner = player.getUUID();
        StandScheduler.repeat(1, 10, ticks / 10, i -> {
            if (!mob.isAlive()) {
                return false;
            }
            ServerPlayer p = mob.getServer().getPlayerList().getPlayer(owner);
            if (p == null) {
                return false;
            }
            LivingEntity prey = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(16),
                    e -> e != mob && AbilityTargeting.isHostileTarget(p, e) && !(e instanceof Player)).stream().findFirst().orElse(null);
            mob.setTarget(prey);
            return true;
        });
        StandScheduler.later(ticks, () -> {
            if (mob.isAlive()) {
                mob.getPersistentData().remove(Summons.CREATOR_TAG);
                mob.setTarget(null);
            }
        });
    }

    /** Psionic Storm (ultimate): every mind within 16 blocks is shattered — they reel, weaken, and turn on each other. */
    public static final class PsionicStorm extends StoneAbility {
        public PsionicStorm() {
            super(InfinityStone.MIND.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            List<LivingEntity> foes = AbilityTargeting.hostilesInRadius(player, 16);
            Cinematics.scene(ctx.level(), SceneIds.PSIONIC_STORM, player.position(), Vec3.ZERO, InfinityStone.MIND.color(), 50, player, 16f);
            for (LivingEntity e : foes) {
                e.invulnerableTime = 0;
                e.hurt(player.damageSources().indirectMagic(player, player), 10f * power(player));
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0), player);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2), player);
                if (e instanceof Mob mob && foes.size() > 1) {
                    LivingEntity other = foes.get((foes.indexOf(e) + 1) % foes.size());
                    mob.setTarget(other);
                }
            }
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_EPITAPH, 1.6f, 1.6f);
            return true;
        }
    }

    // ============================== REALITY ==============================

    private static final BlockState[] REALITY_BLOCKS = {
            Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.CRIMSON_NYLIUM.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(),
            Blocks.RED_CONCRETE.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState()};

    /** Rewrites the surface around {@code center} into a red-black reality pattern for {@code ticks}. */
    static void transmute(ServerLevel level, Vec3 center, int radius, int ticks, String group) {
        BlockPos c = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-radius, -2, -radius), c.offset(radius, 2, radius))) {
            if (pos.distSqr(c) > radius * radius) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !level.getBlockState(pos.above()).isAir() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
                continue;
            }
            BlockState into = REALITY_BLOCKS[Math.floorMod(pos.getX() * 7 + pos.getZ() * 13 + pos.getY(), REALITY_BLOCKS.length)];
            TemporaryBlocks.place(level, pos.immutable(), into, ticks, group, true);
        }
    }

    /** Reality Warp: the world around your target is rewritten; foes inside are bound and disoriented. */
    public static final class RealityWarp extends StoneAbility {
        public RealityWarp() {
            super(InfinityStone.REALITY.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            LivingEntity target = lookTarget(player, 32);
            Vec3 at = target != null ? target.position() : player.position().add(player.getLookAngle().scale(8));
            Cinematics.scene(ctx.level(), SceneIds.REALITY_WARP, at, Vec3.ZERO, InfinityStone.REALITY.color(), 40, player, 6f);
            if (MultiverseConfig.SERVER.allowTerrainModification.get()) {
                transmute(ctx.level(), at, 6, 400, "reality:" + player.getUUID());
            }
            for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6), e -> AbilityTargeting.isHostileTarget(player, e))) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 4), player);
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), player);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), player);
            }
            MultiverseVfx.sound(ctx.level(), at, ModSounds.STAND_TIME_ERASE, 1.2f, 0.8f);
            return true;
        }
    }

    /** Reality Shatter (ultimate): reality around you breaks apart in red shards; everything nearby is torn. */
    public static final class RealityShatter extends StoneAbility {
        public RealityShatter() {
            super(InfinityStone.REALITY.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Cinematics.scene(ctx.level(), SceneIds.REALITY_SHATTER, player.position(), player.getLookAngle(), InfinityStone.REALITY.color(), 50, player, 14f);
            if (MultiverseConfig.SERVER.allowTerrainModification.get()) {
                transmute(ctx.level(), player.position(), 12, 600, "reality:" + player.getUUID());
            }
            StandScheduler.later(10, () -> {
                for (LivingEntity e : AbilityTargeting.hostilesInRadius(player, 14)) {
                    e.invulnerableTime = 0;
                    e.hurt(player.damageSources().indirectMagic(player, player), 22f * power(player));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), player);
                }
            });
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.SHOCKWAVE, 1.6f, 0.7f);
            return true;
        }
    }

    // ============================== TIME ==============================

    /** Time Rewind: return to where you stood (and how healthy you were) a few seconds ago. */
    public static final class TimeRewind extends StoneAbility {
        public TimeRewind() {
            super(InfinityStone.TIME.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            RewindTracker.Snapshot snap = RewindTracker.oldest(player);
            if (snap == null) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_history"));
                return false;
            }
            Vec3 from = player.position();
            player.teleportTo(ctx.level(), snap.position().x, snap.position().y, snap.position().z, java.util.Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
            player.setHealth(Math.max(player.getHealth(), snap.health()));
            RewindTracker.clear(player);
            Cinematics.scene(ctx.level(), SceneIds.TIME_REWIND, from, snap.position().subtract(from), InfinityStone.TIME.color(), 24, player, 1f);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_REWIND, 1f, 1.3f);
            return true;
        }
    }

    /** Time Freeze (ultimate): every creature within 16 blocks is locked in a single moment for 10 seconds. */
    public static final class TimeFreeze extends StoneAbility {
        public TimeFreeze() {
            super(InfinityStone.TIME.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            List<LivingEntity> frozen = AbilityTargeting.hostilesInRadius(player, 16);
            Cinematics.scene(ctx.level(), SceneIds.TIME_FREEZE, player.position(), Vec3.ZERO, InfinityStone.TIME.color(), 200, player, 16f);
            List<Mob> stopped = new ArrayList<>();
            for (LivingEntity e : frozen) {
                if (e instanceof Mob mob && !mob.isNoAi()) {
                    mob.setNoAi(true);
                    stopped.add(mob);
                }
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 10, false, false), player);
            }
            StandScheduler.repeat(1, 1, 200, i -> {
                for (LivingEntity e : frozen) {
                    if (e.isAlive()) {
                        e.setDeltaMovement(Vec3.ZERO);
                    }
                }
                return true;
            });
            StandScheduler.later(200, () -> stopped.forEach(m -> m.setNoAi(false)));
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.TIME_STOP, 1.4f, 1.2f);
            return true;
        }
    }

    // ============================== SOUL ==============================

    /** Soul Siphon: tear the life out of what you look at and take it for yourself. */
    public static final class SoulSiphon extends StoneAbility {
        public SoulSiphon() {
            super(InfinityStone.SOUL.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            LivingEntity target = lookTarget(player, 24);
            if (target == null || !AbilityTargeting.isHostileTarget(player, target)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
                return false;
            }
            float amount = 10f * power(player);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().indirectMagic(player, player), amount);
            player.heal(amount * 0.6f);
            Cinematics.scene(ctx.level(), SceneIds.SOUL_DRAIN, target.getBoundingBox().getCenter(), fist(player).subtract(target.getBoundingBox().getCenter()),
                    InfinityStone.SOUL.color(), 24, player, 1f);
            MultiverseVfx.sound(ctx.level(), target.position(), ModSounds.STAND_LIFE, 1f, 0.7f);
            return true;
        }
    }

    /** Soul Harvest (ultimate): drain every soul nearby and raise three spectral servants from what you took. */
    public static final class SoulHarvest extends StoneAbility {
        public SoulHarvest() {
            super(InfinityStone.SOUL.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            float total = 0;
            for (LivingEntity e : AbilityTargeting.hostilesInRadius(player, 12)) {
                e.invulnerableTime = 0;
                e.hurt(player.damageSources().indirectMagic(player, player), 12f * power(player));
                total += 4;
                Cinematics.scene(ctx.level(), SceneIds.SOUL_DRAIN, e.getBoundingBox().getCenter(), player.getEyePosition().subtract(e.getBoundingBox().getCenter()),
                        InfinityStone.SOUL.color(), 30, player, 1f);
            }
            player.heal(Math.min(20, total));
            Cinematics.scene(ctx.level(), SceneIds.SOUL_HARVEST, player.position(), Vec3.ZERO, InfinityStone.SOUL.color(), 40, player, 12f);
            for (int i = 0; i < 3; i++) {
                double a = Math.PI * 2 * i / 3;
                Vex vex = Summons.spawnAlly(player, EntityType.VEX, player.position().add(Math.cos(a) * 1.5, 1.5, Math.sin(a) * 1.5), 600, InfinityStone.SOUL.color());
                if (vex != null) {
                    vex.setLimitedLife(600);
                }
            }
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_SUMMON, 1.4f, 0.6f);
            return true;
        }
    }

    // ============================== COMBINATIONS ==============================

    /** Chrono Jump (Space + Time): leap through space and arrive a moment before you left — foes near the exit are slowed to a crawl. */
    public static final class ChronoJump extends StoneAbility {
        public ChronoJump() {
            super(InfinityStone.SPACE.bit() | InfinityStone.TIME.bit());
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            Vec3 from = ctx.player().position();
            if (!Blink.perform(ctx, 48)) {
                return false;
            }
            Cinematics.scene(ctx.level(), SceneIds.SPACE_WARP, from, ctx.player().position().subtract(from), InfinityStone.TIME.color(), 24, ctx.player(), 1f);
            Cinematics.scene(ctx.level(), SceneIds.TIME_FREEZE, ctx.player().position(), Vec3.ZERO, InfinityStone.TIME.color(), 60, ctx.player(), 8f);
            for (LivingEntity e : AbilityTargeting.hostilesInRadius(ctx.player(), 8)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4), ctx.player());
            }
            ctx.player().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 2));
            return true;
        }
    }

    /**
     * The Snap (all six stones). A cutscene: the gauntlet is raised, the stones ignite one by one, and the fingers snap.
     * Server config decides the effect: SAFE (half of nearby hostile, non-boss, unnamed, unowned creatures turn to dust;
     * players are never affected), VISUAL (cinematic only), or OFF. The gauntlet's power burns its wielder.
     */
    public static final class Snap extends StoneAbility {
        public static final int SNAP_AT = 62;

        public Snap() {
            super(Gauntlet.FULL);
        }

        @Override
        protected boolean cast(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            MultiverseConfig.SnapMode mode = MultiverseConfig.SERVER.snapMode.get();
            if (mode == MultiverseConfig.SnapMode.OFF) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.snap_disabled"));
                return false;
            }
            ServerLevel level = ctx.level();
            Cinematics.scene(level, SceneIds.SNAP, player.getEyePosition(), player.getLookAngle(), 0xFFE27A, 110, player, 64f);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SNAP_AT + 10, 4, false, false));
            StandScheduler.later(SNAP_AT, () -> {
                if (!player.isAlive()) {
                    return;
                }
                MultiverseVfx.sound(level, player.position(), ModSounds.STAND_HEAVY, 2f, 1.8f);
                MultiverseVfx.sound(level, player.position(), ModSounds.TIME_STOP, 2f, 0.5f);
                MultiverseVfx.shout(level, player.position(), Component.translatable("message.infinitemultiverse.shout.snap")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 64);
                if (mode == MultiverseConfig.SnapMode.SAFE) {
                    List<LivingEntity> pool = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64),
                            e -> e instanceof Mob && !(e instanceof Player) && AbilityTargeting.isHostileTarget(player, e) && !e.hasCustomName()
                                    && !e.getType().is(Tags.EntityTypes.BOSSES) && !(e instanceof OwnableEntity o && o.getOwnerUUID() != null)));
                    Collections.shuffle(pool, new java.util.Random(level.getGameTime()));
                    for (int i = 0; i < pool.size() / 2 + pool.size() % 2; i++) {
                        LivingEntity victim = pool.get(i);
                        int delay = 10 + level.random.nextInt(50);
                        StandScheduler.later(delay, () -> {
                            if (victim.isAlive()) {
                                Cinematics.scene(level, SceneIds.DUST, victim.position(), Vec3.ZERO, 0x6A5040, 50, victim, victim.getBbHeight());
                                if (victim instanceof Mob m) {
                                    m.setNoAi(true);
                                }
                            }
                        });
                        StandScheduler.later(delay + 40, () -> {
                            if (victim.isAlive()) {
                                victim.discard();
                            }
                        });
                    }
                }
                if (!player.isCreative()) {
                    player.hurt(player.damageSources().magic(), 10f);
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                }
            });
            return true;
        }
    }
}
