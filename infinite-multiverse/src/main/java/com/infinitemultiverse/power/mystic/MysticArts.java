package com.infinitemultiverse.power.mystic;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.core.world.SafeTeleport;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.power.gear.ModGear;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Mystic Arts spells with bespoke mechanics: sling-ring portals, astral projection, time reversal and the Mirror Dimension. */
public final class MysticArts {
    public static final ResourceKey<Level> MIRROR = ResourceKey.create(Registries.DIMENSION, InfiniteMultiverse.id("mirror"));
    private static final MultiverseSystem MYSTIC = MultiverseSystem.MYSTIC_ARTS;
    private static final String ASTRAL_TAG = "infinitemultiverse_astral";
    private static final String MIRROR_TAG = "infinitemultiverse_mirror_return";
    private static final int PORTAL_COLOR = 0xFF9A2A;

    private MysticArts() {
    }

    // ===================== Sling Ring portals =====================

    private record Portal(ServerLevel level, Vec3 center, Vec3 normal, ServerLevel targetLevel, Vec3 target, long expiry) {
    }

    private static final List<Portal> PORTALS = new ArrayList<>();
    private static final java.util.Map<java.util.UUID, Long> PORTAL_COOLDOWN = new java.util.HashMap<>();

    public static final class SlingRingPortal extends PowerAbility {
        private static final int LIFETIME = 600;

        public SlingRingPortal() {
            super(MYSTIC);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            ItemStack ring = findRing(player);
            if (ring.isEmpty()) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.needs_sling_ring"));
                return false;
            }
            Optional<SlingRingItem.Destination> destination = SlingRingItem.destination(ring);
            if (destination.isEmpty()) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.sling_ring_unbound"));
                return false;
            }
            ServerLevel targetLevel = player.server.getLevel(destination.get().dimension());
            if (targetLevel == null) {
                return false;
            }
            Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 here = player.position().add(look.scale(2.5)).add(0, 1.2, 0);
            Vec3 there = Vec3.atBottomCenterOf(destination.get().pos());
            targetLevel.getChunk(destination.get().pos());
            Vec3 exit = SafeTeleport.findSafeSpot(targetLevel, player, there, 3, 4).orElse(there);
            long expiry = ctx.level().getGameTime() + LIFETIME;
            PORTALS.add(new Portal(ctx.level(), here, look, targetLevel, exit, expiry));
            PORTALS.add(new Portal(targetLevel, exit.add(look.scale(2.5)).add(0, 1.2, 0), look.scale(-1), ctx.level(), player.position(), expiry));
            MultiverseVfx.sound(ctx.level(), here, ModSounds.STAND_TIME_ERASE, 1.0f, 1.4f);
            return true;
        }
    }

    private static ItemStack findRing(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModGear.SLING_RING.get())) {
                return stack;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModGear.SLING_RING.get()) ? offhand : ItemStack.EMPTY;
    }

    static void tickPortals(MinecraftServer server) {
        Iterator<Portal> it = PORTALS.iterator();
        while (it.hasNext()) {
            Portal portal = it.next();
            long now = portal.level.getGameTime();
            if (now >= portal.expiry) {
                MultiverseVfx.fx(portal.level, VfxIds.BURST, portal.center, Vec3.ZERO, PORTAL_COLOR);
                it.remove();
                continue;
            }
            if (now % 3 == 0) {
                MultiverseVfx.fx(portal.level, VfxIds.PORTAL_RING, portal.center, portal.normal, PORTAL_COLOR);
            }
            AABB box = new AABB(portal.center, portal.center).inflate(1.2, 1.6, 1.2);
            for (Entity entity : portal.level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isPassenger() && !(e instanceof ArmorStand))) {
                Long last = PORTAL_COOLDOWN.get(entity.getUUID());
                if (last != null && now - last < 40) {
                    continue;
                }
                PORTAL_COOLDOWN.put(entity.getUUID(), now);
                Vec3 velocity = entity.getDeltaMovement();
                entity.teleportTo(portal.targetLevel, portal.target.x, portal.target.y, portal.target.z, Set.<RelativeMovement>of(), entity.getYRot(), entity.getXRot());
                entity.setDeltaMovement(velocity);
                MultiverseVfx.sound(portal.targetLevel, portal.target, ModSounds.PHASE_STEP_ARRIVE, 0.8f, 0.8f);
            }
        }
        if (PORTAL_COOLDOWN.size() > 512) {
            PORTAL_COOLDOWN.clear();
        }
    }

    // ===================== Astral Projection =====================

    public static final class AstralProjection extends PowerAbility {
        private static final double LEASH = 48;

        public AstralProjection() {
            super(MYSTIC, ActivationType.TOGGLE, 4f);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            if (player.isSpectator() || player.isCreative()) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.astral_survival_only"));
                return false;
            }
            ArmorStand body = new ArmorStand(ctx.level(), player.getX(), player.getY(), player.getZ());
            body.setYRot(player.getYRot());
            body.setShowArms(true);
            body.setInvulnerable(true);
            body.setNoGravity(true);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
            body.setCustomName(Component.translatable("entity.infinitemultiverse.physical_body", player.getName()));
            ctx.level().addFreshEntity(body);

            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", ctx.level().dimension().location().toString());
            tag.putDouble("x", player.getX());
            tag.putDouble("y", player.getY());
            tag.putDouble("z", player.getZ());
            tag.putString("mode", player.gameMode.getGameModeForPlayer().getName());
            tag.putUUID("body", body.getUUID());
            player.getPersistentData().put(ASTRAL_TAG, tag);
            player.setGameMode(GameType.SPECTATOR);
            MultiverseVfx.fx(ctx.level(), VfxIds.AURA, player.position(), Vec3.ZERO, 0xFFE6A0);
            MultiverseVfx.tint(ctx.level(), player.position(), 1, 0xFFE6A0, 0.18f, 72000);
            return true;
        }

        @Override
        public void tickActive(AbilityContext ctx, int activeTicks) {
            CompoundTag tag = ctx.player().getPersistentData().getCompound(ASTRAL_TAG);
            Vec3 body = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            if (ctx.player().position().distanceTo(body) > LEASH) {
                Vec3 back = body.add(ctx.player().position().subtract(body).normalize().scale(LEASH - 1));
                ctx.player().teleportTo(back.x, back.y, back.z);
                ctx.player().displayClientMessage(Component.translatable("message.infinitemultiverse.astral_leash").withStyle(ChatFormatting.GOLD), true);
            }
        }

        @Override
        public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
            returnFromAstral(ctx.player());
        }
    }

    /** Puts the player back in their body and restores their game mode. Safe to call when not projecting. */
    public static void returnFromAstral(ServerPlayer player) {
        if (!player.getPersistentData().contains(ASTRAL_TAG)) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(ASTRAL_TAG);
        player.getPersistentData().remove(ASTRAL_TAG);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            level = player.server.overworld();
        }
        if (tag.hasUUID("body") && level.getEntity(tag.getUUID("body")) instanceof ArmorStand body) {
            body.discard();
        }
        GameType mode = GameType.byName(tag.getString("mode"), GameType.SURVIVAL);
        player.teleportTo(level, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        player.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
        MultiverseVfx.tint(level, player.position(), 1, 0xFFE6A0, 0f, 0);
        MultiverseVfx.fx(level, VfxIds.AURA, player.position(), Vec3.ZERO, 0xFFE6A0);
    }

    // ===================== Eye of Agamotto: Time Reversal =====================

    public static final class TimeReversal extends PowerAbility {
        public TimeReversal() {
            super(MYSTIC);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(10f);
            player.clearFire();
            player.setTicksFrozen(0);
            List<MobEffectInstance> harmful = player.getActiveEffects().stream()
                    .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).toList();
            harmful.forEach(e -> player.removeEffect(e.getEffect()));
            ItemStack held = player.getMainHandItem();
            if (held.isDamageableItem()) {
                held.setDamageValue(0);
            }
            MultiverseVfx.broadcast(ctx.level(), VfxIds.REWIND, player.position().add(0, 1, 0), Vec3.ZERO, 0x5CFF8A);
            MultiverseVfx.fx(ctx.level(), VfxIds.MANDALA, player.getEyePosition().add(player.getLookAngle()), player.getLookAngle(), 0x5CFF8A);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_REWIND, 1f, 1.2f);
            MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable("message.infinitemultiverse.shout.agamotto")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 16);
            return true;
        }
    }

    // ===================== Mirror Dimension =====================

    public static final class MirrorDimension extends PowerAbility {
        private static final int RADIUS = 12;
        private static final int DOWN = 6;
        private static final int UP = 12;
        private static final int TINT = 0xB070FF;

        public MirrorDimension() {
            super(MYSTIC, ActivationType.TOGGLE, 2f);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            ServerLevel mirror = player.server.getLevel(MIRROR);
            if (mirror == null || ctx.level() == mirror) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.mirror_unavailable"));
                return false;
            }
            BlockPos origin = player.blockPosition();
            copyRegion(ctx.level(), mirror, origin);
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", ctx.level().dimension().location().toString());
            tag.putDouble("x", player.getX());
            tag.putDouble("y", player.getY());
            tag.putDouble("z", player.getZ());
            player.getPersistentData().put(MIRROR_TAG, tag);

            for (LivingEntity foe : AbilityTargeting.hostilesInRadius(player, 8)) {
                if (!(foe instanceof Player)) {
                    foe.teleportTo(mirror, foe.getX(), foe.getY(), foe.getZ(), Set.<RelativeMovement>of(), foe.getYRot(), foe.getXRot());
                }
            }
            MultiverseVfx.fx(ctx.level(), VfxIds.MANDALA, player.getEyePosition().add(player.getLookAngle()), player.getLookAngle(), TINT);
            player.teleportTo(mirror, player.getX(), player.getY(), player.getZ(), Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
            MultiverseVfx.tint(mirror, player.position(), 1, TINT, 0.22f, 200);
            MultiverseVfx.sound(mirror, player.position(), ModSounds.STAND_TIME_ERASE, 1f, 0.7f);
            return true;
        }

        @Override
        public void tickActive(AbilityContext ctx, int activeTicks) {
            if (ctx.player().level().dimension() != MIRROR) {
                AbilityManager.deactivate(ctx.player(), ctx.data(), this, DeactivationReason.MANUAL);
                return;
            }
            if (activeTicks % 180 == 0) {
                MultiverseVfx.tint(ctx.level(), ctx.player().position(), 1, TINT, 0.22f, 200);
            }
            if (activeTicks % 10 == 0) {
                MultiverseVfx.fx(ctx.level(), VfxIds.FROST, ctx.player().position().add(0, 2, 0), new Vec3(6, 0, 0), TINT);
            }
        }

        @Override
        public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
            leaveMirror(ctx.player());
        }

        private static void copyRegion(ServerLevel from, ServerLevel to, BlockPos origin) {
            BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dz * dz > RADIUS * RADIUS) {
                        continue;
                    }
                    for (int dy = -DOWN; dy <= UP; dy++) {
                        source.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (!to.isInWorldBounds(source)) {
                            continue;
                        }
                        BlockState state = from.getBlockState(source);
                        if (from.getBlockEntity(source) != null) {
                            state = state.getBlock().defaultBlockState().hasBlockEntity() ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
                        }
                        to.setBlock(source, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    /** Returns the player (and creatures near them) from the Mirror Dimension. Safe to call when not inside. */
    public static void leaveMirror(ServerPlayer player) {
        if (!player.getPersistentData().contains(MIRROR_TAG)) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(MIRROR_TAG);
        player.getPersistentData().remove(MIRROR_TAG);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel home = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (home == null) {
            home = player.server.overworld();
        }
        Vec3 back = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        if (player.level().dimension() == MIRROR) {
            ServerLevel mirror = player.serverLevel();
            for (LivingEntity creature : mirror.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(48), e -> !(e instanceof Player))) {
                creature.teleportTo(home, creature.getX(), creature.getY(), creature.getZ(), Set.<RelativeMovement>of(), creature.getYRot(), creature.getXRot());
            }
        }
        Vec3 safe = SafeTeleport.findSafeSpot(home, player, back, 2, 4).orElse(back);
        player.teleportTo(home, safe.x, safe.y, safe.z, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        MultiverseVfx.tint(home, player.position(), 1, 0xB070FF, 0f, 0);
    }

    // ===================== lifecycle =====================

    public static void tick(MinecraftServer server) {
        if (!PORTALS.isEmpty()) {
            tickPortals(server);
        }
    }

    /** Repairs a player who logged out or crashed while projecting or inside the mirror. */
    public static void onLogin(ServerPlayer player) {
        returnFromAstral(player);
        if (player.level().dimension() == MIRROR || player.getPersistentData().contains(MIRROR_TAG)) {
            if (!player.getPersistentData().contains(MIRROR_TAG)) {
                CompoundTag tag = new CompoundTag();
                BlockPos spawn = player.server.overworld().getSharedSpawnPos();
                tag.putString("dimension", Level.OVERWORLD.location().toString());
                tag.putDouble("x", spawn.getX() + 0.5);
                tag.putDouble("y", spawn.getY());
                tag.putDouble("z", spawn.getZ() + 0.5);
                player.getPersistentData().put(MIRROR_TAG, tag);
            }
            leaveMirror(player);
        }
    }
}
