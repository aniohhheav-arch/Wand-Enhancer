package com.infinitemultiverse.power.mystic;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModEntities;
import com.infinitemultiverse.power.ability.Flight;
import com.infinitemultiverse.stand.StandScheduler;
import net.minecraft.world.effect.MobEffects;
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
    private static final String MIRROR_TAG = "infinitemultiverse_mirror_return";
    private static final int PORTAL_COLOR = 0xFF9A2A;

    private MysticArts() {
    }

    // ===================== Sling Ring portals =====================

    private record Portal(ServerLevel level, Vec3 center, Vec3 normal, ServerLevel targetLevel, Vec3 target, long open, long expiry) {
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
            // The gateway is drawn first (16 ticks of the ring being traced), then becomes passable.
            long open = ctx.level().getGameTime() + 14;
            long expiry = open + LIFETIME;
            Vec3 far = exit.add(look.scale(2.5)).add(0, 1.2, 0);
            PORTALS.add(new Portal(ctx.level(), here, look, targetLevel, exit, open, expiry));
            PORTALS.add(new Portal(targetLevel, far, look.scale(-1), ctx.level(), player.position(), open, expiry));
            Cinematics.attached(ctx.level(), SceneIds.SPELL_CIRCLE, player, PORTAL_COLOR, 24, 1f);
            Cinematics.scene(ctx.level(), SceneIds.SLING_PORTAL, here, look, PORTAL_COLOR, LIFETIME + 14, player, 1.7f);
            Cinematics.scene(targetLevel, SceneIds.SLING_PORTAL, far, look.scale(-1), PORTAL_COLOR, LIFETIME + 14, null, 1.7f);
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
                it.remove();
                continue;
            }
            if (now < portal.open) {
                continue;
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

    /**
     * Astral Projection: your body stays behind (a still copy with your skin and gear) while your spirit floats free —
     * same game mode, flying, passing through blocks, unable to touch blocks, items or creatures, and unseen by mobs.
     * Toggle again to return; you are also pulled back if your body is struck, your mana runs out, or you stray too far.
     */
    public static final class AstralProjection extends PowerAbility {
        private static final double LEASH = 64;

        public AstralProjection() {
            super(MYSTIC, ActivationType.TOGGLE, 4f);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            if (player.isSpectator()) {
                return false;
            }
            PhysicalBodyEntity body = ModEntities.PHYSICAL_BODY.get().create(ctx.level());
            if (body == null) {
                return false;
            }
            body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 25f);
            body.setOwner(player);
            ctx.level().addFreshEntity(body);

            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", ctx.level().dimension().location().toString());
            tag.putDouble("x", player.getX());
            tag.putDouble("y", player.getY());
            tag.putDouble("z", player.getZ());
            tag.putUUID("body", body.getUUID());
            player.getPersistentData().put(AstralState.TAG, tag);
            Flight.grant(player, id());
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
            player.setDeltaMovement(0, 0.25, 0);
            player.hurtMarked = true;
            Cinematics.scene(ctx.level(), SceneIds.ASTRAL_EXIT, player.position(), player.getLookAngle(), 0xFFE6A0, 32, player, 1f);
            Cinematics.attached(ctx.level(), SceneIds.ASTRAL_SPIRIT, player, 0xFFE6A0, 24, 1f);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_SUMMON, 1f, 1.6f);
            return true;
        }

        @Override
        public void tickActive(AbilityContext ctx, int activeTicks) {
            ServerPlayer player = ctx.player();
            CompoundTag tag = player.getPersistentData().getCompound(AstralState.TAG);
            if (tag.isEmpty()) {
                AbilityManager.deactivate(player, ctx.data(), this, DeactivationReason.MANUAL);
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
            player.fallDistance = 0;
            if (!player.getAbilities().flying) {
                // A spirit always floats; without this it would sink straight through the ground.
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            if (activeTicks % 20 == 0) {
                Cinematics.attached(ctx.level(), SceneIds.ASTRAL_SPIRIT, player, 0xFFE6A0, 24, 1f);
            }
            Vec3 body = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            boolean sameLevel = player.level().dimension().location().toString().equals(tag.getString("dimension"));
            if (!sameLevel) {
                AbilityManager.deactivate(player, ctx.data(), this, DeactivationReason.MANUAL);
                return;
            }
            if (player.position().distanceTo(body) > LEASH) {
                Vec3 back = body.add(player.position().subtract(body).normalize().scale(LEASH - 1));
                player.teleportTo(back.x, back.y, back.z);
                player.displayClientMessage(Component.translatable("message.infinitemultiverse.astral_leash").withStyle(ChatFormatting.GOLD), true);
            }
        }

        @Override
        public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
            returnFromAstral(ctx.player());
        }
    }

    /** Ends a projection from outside the ability (body struck, admin...). */
    public static void forceReturn(ServerPlayer player) {
        var data = AbilityManager.data(player);
        var ability = AbilityManager.lookup(AstralState.ABILITY);
        if (ability != null && data.isActive(AstralState.ABILITY)) {
            AbilityManager.deactivate(player, data, ability, DeactivationReason.MANUAL);
            AbilityManager.syncNow(player);
        } else {
            returnFromAstral(player);
        }
    }

    /** Puts the player back in their body. Safe to call when not projecting. */
    public static void returnFromAstral(ServerPlayer player) {
        if (!player.getPersistentData().contains(AstralState.TAG)) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(AstralState.TAG);
        player.getPersistentData().remove(AstralState.TAG);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            level = player.server.overworld();
        }
        if (tag.hasUUID("body") && level.getEntity(tag.getUUID("body")) instanceof net.minecraft.world.entity.Entity body
                && (body instanceof PhysicalBodyEntity || body instanceof ArmorStand)) {
            body.discard();
        }
        Vec3 spirit = player.position();
        Vec3 home = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        player.noPhysics = false;
        Flight.revoke(player, AstralState.ABILITY);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.teleportTo(level, home.x, home.y, home.z, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        if (tag.contains("mode") && player.isSpectator()) {
            // Projections made by older versions switched to spectator; restore the original mode.
            GameType mode = GameType.byName(tag.getString("mode"), GameType.SURVIVAL);
            player.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        Cinematics.stop(level, SceneIds.ASTRAL_SPIRIT, home, player);
        Cinematics.scene(level, SceneIds.ASTRAL_RETURN, home, spirit.subtract(home), 0xFFE6A0, 16, player, 1f);
        MultiverseVfx.sound(level, home, ModSounds.STAND_DISMISS, 1f, 1.4f);
    }

    // ===================== Eye of Agamotto: Time Reversal =====================

    /** Eye of Agamotto: a short cutscene of the eye opening and a backward-spinning clock, then time is turned back on you. */
    public static final class TimeReversal extends PowerAbility {
        public TimeReversal() {
            super(MYSTIC);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            Cinematics.scene(ctx.level(), SceneIds.TIME_EYE, player.position(), player.getLookAngle(), 0x5CFF8A, 50, player, 1f);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_REWIND, 1f, 1.2f);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 4, false, false));
            StandScheduler.later(28, () -> {
                if (!player.isAlive()) {
                    return;
                }
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
                MultiverseVfx.broadcast(player.serverLevel(), VfxIds.REWIND, player.position().add(0, 1, 0), Vec3.ZERO, 0x5CFF8A);
                MultiverseVfx.shout(player.serverLevel(), player.position(), Component.translatable("message.infinitemultiverse.shout.agamotto")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 16);
            });
            return true;
        }
    }

    // ===================== Mirror Dimension =====================

    /**
     * Mirror Dimension: a 40-tick cutscene — spell circles, reality cracking like glass, mirrored shards peeling off
     * the world — then on the shatter you, and every creature near you, are pulled into a mirrored copy of the area.
     * Inside, the sky folds into kaleidoscope rings and mirror panels hang around you. Toggle off to return (the
     * shards fly home and the cracks heal).
     */
    public static final class MirrorDimension extends PowerAbility {
        private static final int RADIUS = 12;
        private static final int DOWN = 6;
        private static final int UP = 12;
        private static final int TINT = 0xB070FF;
        private static final int SHATTER = 28;

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
            ServerLevel home = ctx.level();
            BlockPos origin = player.blockPosition();
            copyRegion(home, mirror, origin);
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", home.dimension().location().toString());
            tag.putDouble("x", player.getX());
            tag.putDouble("y", player.getY());
            tag.putDouble("z", player.getZ());
            player.getPersistentData().put(MIRROR_TAG, tag);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHATTER + 10, 4, false, false));
            Cinematics.scene(home, SceneIds.MIRROR_ENTER, player.position(), player.getLookAngle(), TINT, 40, player, 12f);
            MultiverseVfx.sound(home, player.position(), ModSounds.STAND_EPITAPH, 1.2f, 1.5f);
            StandScheduler.later(SHATTER - 6, () -> MultiverseVfx.sound(home, player.position(), ModSounds.STAND_TIME_ERASE, 1.4f, 0.7f));
            StandScheduler.later(SHATTER, () -> {
                if (!player.isAlive() || player.level() != home || !player.getPersistentData().contains(MIRROR_TAG)) {
                    return;
                }
                for (LivingEntity foe : AbilityTargeting.hostilesInRadius(player, 10)) {
                    if (!(foe instanceof Player)) {
                        foe.teleportTo(mirror, foe.getX(), foe.getY(), foe.getZ(), Set.<RelativeMovement>of(), foe.getYRot(), foe.getXRot());
                    }
                }
                player.teleportTo(mirror, player.getX(), player.getY(), player.getZ(), Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
                Cinematics.scene(mirror, SceneIds.MIRROR_WORLD, player.position(), Vec3.ZERO, TINT, 400, player, 20f);
                MultiverseVfx.sound(mirror, player.position(), ModSounds.TIME_STOP, 1.2f, 1.4f);
            });
            return true;
        }

        @Override
        public void tickActive(AbilityContext ctx, int activeTicks) {
            if (activeTicks < SHATTER + 10) {
                return;
            }
            if (ctx.player().level().dimension() != MIRROR) {
                AbilityManager.deactivate(ctx.player(), ctx.data(), this, DeactivationReason.MANUAL);
                return;
            }
            if (activeTicks % 360 == 0) {
                Cinematics.scene(ctx.level(), SceneIds.MIRROR_WORLD, ctx.player().position(), Vec3.ZERO, TINT, 400, ctx.player(), 20f);
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
                            state = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
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
            Cinematics.stop(mirror, SceneIds.MIRROR_WORLD, player.position(), player);
            for (LivingEntity creature : mirror.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(48), e -> !(e instanceof Player))) {
                creature.teleportTo(home, creature.getX(), creature.getY(), creature.getZ(), Set.<RelativeMovement>of(), creature.getYRot(), creature.getXRot());
            }
            Vec3 safe = SafeTeleport.findSafeSpot(home, player, back, 2, 4).orElse(back);
            player.teleportTo(home, safe.x, safe.y, safe.z, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
            Cinematics.scene(home, SceneIds.MIRROR_EXIT, player.position(), player.getLookAngle(), 0xB070FF, 36, player, 12f);
            MultiverseVfx.sound(home, player.position(), ModSounds.STAND_TIME_ERASE, 1.2f, 1.3f);
        }
    }

    // ===================== lifecycle =====================

    public static void tick(MinecraftServer server) {
        if (!PORTALS.isEmpty()) {
            tickPortals(server);
        }
    }

    /** Repairs a player who logged out or crashed while projecting or inside the mirror. */
    public static void onLogin(ServerPlayer player) {
        forceReturn(player);
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
