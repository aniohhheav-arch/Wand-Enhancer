package dev.riftverse.player;

import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.event.CommonEvents;
import dev.riftverse.item.ArmorSet;
import dev.riftverse.item.RiftArmorItem;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.UniverseTravel;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Passive set bonuses and the active ability bound to the Riftverse ability key. */
public final class ArmorAbilities {
    private static final String FLIGHT_TAG = "riftverse:astral_flight";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorAbilities() {}

    @Nullable
    public static ArmorSet fullSet(LivingEntity entity) {
        ArmorSet found = null;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof RiftArmorItem armor)) return null;
            if (found == null) found = armor.set();
            else if (found != armor.set()) return null;
        }
        return found;
    }

    public static boolean hasVoyagerHelmet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(RvItems.VOYAGER_HELMET.get());
    }

    public static void tick(ServerPlayer player) {
        ArmorSet set = fullSet(player);
        updateFlight(player, set);
        long time = player.level().getGameTime();
        if (hasVoyagerHelmet(player) && time % 80 == 0) {
            UniverseSpec spec = CommonEvents.specOf(player);
            boolean dark = spec != null && (spec.time == TimeMode.ETERNAL_NIGHT || spec.archetype == Archetype.HOLLOW);
            if (dark) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false));
        }
        if (set == null) return;
        switch (set) {
            case RIFT_WALKER -> {
                if (player.fallDistance > 3f) player.resetFallDistance();
            }
            case VOYAGER -> {
                if (time % 40 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, true, false));
                }
            }
            case EVENT_HORIZON -> eventHorizonTick(player, time);
            case ASTRAL -> {
                if (time % 40 == 0) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            }
        }
    }

    private static void eventHorizonTick(ServerPlayer player, long time) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position().add(0, 1, 0);
        if (time % 2 == 0) {
            for (Entity e : level.getEntities(player, new AABB(c, c).inflate(8), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                Vec3 to = c.subtract(e.position());
                e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(to.normalize().scale(0.12)));
                e.hurtMarked = true;
            }
        }
        for (Entity e : level.getEntities(player, new AABB(c, c).inflate(3.5), e -> e instanceof Projectile p && p.getOwner() != player)) {
            Vec3 v = e.getDeltaMovement();
            Vec3 to = c.subtract(e.position());
            if (v.dot(to) <= 0 || e.getPersistentData().getBoolean("riftverse:deflected")) continue;
            e.getPersistentData().putBoolean("riftverse:deflected", true);
            if (player.getRandom().nextFloat() < 0.4f) {
                e.setDeltaMovement(v.scale(-0.8));
                e.hurtMarked = true;
                level.sendParticles(RvParticles.RING.get().with(0xFF8A3A, 1.2f, 8), e.getX(), e.getY(), e.getZ(), 1, 0, 0, 0, 0);
            }
        }
        if (time % 40 == 0) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
    }

    private static void updateFlight(ServerPlayer player, @Nullable ArmorSet set) {
        boolean shouldFly = set == ArmorSet.ASTRAL && UniverseTravel.isRiftverseDimension(player.level());
        boolean granted = player.getPersistentData().getBoolean(FLIGHT_TAG);
        if (shouldFly && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.getPersistentData().putBoolean(FLIGHT_TAG, true);
            player.onUpdateAbilities();
        } else if (!shouldFly && granted) {
            player.getPersistentData().putBoolean(FLIGHT_TAG, false);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    public static void activate(ServerPlayer player, int ability) {
        ArmorSet set = fullSet(player);
        if (set == null) {
            player.displayClientMessage(Component.translatable("message.riftverse.no_set"), true);
            return;
        }
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        long now = player.level().getGameTime();
        if (now < data.abilityReadyAt) {
            player.displayClientMessage(Component.translatable("message.riftverse.ability_cooldown", (data.abilityReadyAt - now) / 20 + 1), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        int cooldown = switch (set) {
            case RIFT_WALKER -> phaseShift(player, level);
            case VOYAGER -> scan(player, level);
            case EVENT_HORIZON -> collapse(player, level);
            case ASTRAL -> starfall(player, level);
        };
        data.abilityReadyAt = now + cooldown;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), RvSounds.ABILITY_ACTIVATE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static int phaseShift(ServerPlayer player, ServerLevel level) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(start, start.add(dir.scale(12)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? start.add(dir.scale(12)) : hit.getLocation().subtract(dir.scale(0.7));
        Vec3 feet = target.subtract(0, player.getEyeHeight(), 0);
        level.sendParticles(RvParticles.RING.get().with(0xB070FF, 2.0f, 12), player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 12; i++) {
            Vec3 p = player.position().lerp(feet, i / 12.0).add(0, 1, 0);
            level.sendParticles(RvParticles.STREAK.get().with(0x40E0FF, 0.4f, 10), p.x, p.y, p.z, 2, 0.2, 0.4, 0.2, 0.02);
        }
        player.connection.teleport(feet.x, feet.y, feet.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.sendParticles(RvParticles.RING.get().with(0x40E0FF, 2.0f, 12), feet.x, feet.y + 1, feet.z, 1, 0, 0, 0, 0);
        level.playSound(null, feet.x, feet.y, feet.z, RvSounds.BLADE_DASH.get(), SoundSource.PLAYERS, 0.9f, 1.3f);
        PacketDistributor.sendToPlayer(player, new Payloads.Shake(0.2f, 8, 0.3f, 0xB070FF));
        return 60;
    }

    private static int scan(ServerPlayer player, ServerLevel level) {
        Vec3 c = player.position();
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(40), e -> e != player);
        for (LivingEntity e : found) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        level.sendParticles(RvParticles.RING.get().with(0x7DF9FF, 40f, 30), c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        player.displayClientMessage(Component.translatable("message.riftverse.scan", found.size()), true);
        return 300;
    }

    private static int collapse(ServerPlayer player, ServerLevel level) {
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(6));
        BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), level);
        hole.moveTo(at.x, at.y, at.z, 0, 0);
        hole.setHorizonRadius(0.9f);
        hole.setCaptures(false);
        hole.setLifetime(90);
        hole.setOwner(player);
        level.addFreshEntity(hole);
        return 600;
    }

    private static int starfall(ServerPlayer player, ServerLevel level) {
        Vec3 c = player.position();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(16), e -> e instanceof Enemy && e.isAlive());
        int n = 0;
        for (LivingEntity target : targets) {
            if (n++ >= 8) break;
            EnergyBoltEntity star = EnergyBoltEntity.create(level, player, 0xFFE6A0, 0.9f, 12f);
            star.setPos(target.getX() + (level.random.nextDouble() - 0.5) * 4, target.getY() + 22, target.getZ() + (level.random.nextDouble() - 0.5) * 4);
            Vec3 v = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(star.position()).normalize().scale(1.8);
            star.setDeltaMovement(v);
            level.addFreshEntity(star);
        }
        if (n == 0) player.displayClientMessage(Component.translatable("message.riftverse.no_targets"), true);
        level.sendParticles(RvParticles.SPARK.get().with(0xFFE6A0, 0.6f, 40), c.x, c.y + 2, c.z, 60, 3, 2, 3, 0.1);
        return n == 0 ? 40 : 500;
    }
}
