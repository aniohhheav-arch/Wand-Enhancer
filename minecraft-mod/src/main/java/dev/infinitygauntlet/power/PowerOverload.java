package dev.infinitygauntlet.power;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Power Overload: for a while the caster's bare blows carry the Power Stone. Every melee hit lands with a bonus
 * shockwave and a cleave, sprinting tears the ground up in a wake of shockwaves, and when the overload runs out it
 * vents in a final burst.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class PowerOverload {
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    public static void start(ServerPlayer player, int ticks) {
        ACTIVE.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
    }

    public static boolean active(Player player) {
        Long end = ACTIVE.get(player.getUUID());
        return end != null && end > player.level().getGameTime();
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof ServerPlayer player) || !source.is(DamageTypes.PLAYER_ATTACK) || !active(player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        event.setAmount(event.getAmount() + CombatFx.damage(7f));
        ServerLevel level = player.serverLevel();
        Vec3 dir = target.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        Vec3 c = target.getBoundingBox().getCenter();
        // the shockwave rides out from the fist a tick later, once the vanilla hit has resolved
        dev.infinitygauntlet.util.ServerScheduler.schedule(1, () -> {
            if (target.isAlive()) {
                target.setDeltaMovement(dir.scale(1.5).add(0, 0.55, 0));
                target.hurtMarked = true;
                CombatFx.react(level, target, CombatFx.Reaction.RECOIL, dir, 12);
            }
            CombatFx.hit(level, target, dir, 1.2f);
            Vfx.send(level, VfxType.SHOCKWAVE, c, dir, PowerFx.PURPLE, PowerFx.PINK, 3.2f, 9);
            Vfx.send(level, VfxType.SPARK_BURST, c, Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 0.8f, 8);
            Vfx.sound(level, c, ModSounds.POWER_HIT, 1.6f, 0.9f + level.random.nextFloat() * 0.2f);
            Vfx.camera(level, c, 12, 0.35f, 0, 0, 0, 0, 0);
            for (LivingEntity e : CombatFx.targets(player, c, 3)) {
                if (e != target) {
                    CombatFx.strike(player, e, 4f, e.position().subtract(c).normalize().scale(0.8).add(0, 0.35, 0), 0.6f,
                            CombatFx.DeathStyle.RAGDOLL, false);
                }
            }
        });
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long end = ACTIVE.get(player.getUUID());
        if (end == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now >= end || !player.isAlive()) {
            ACTIVE.remove(player.getUUID());
            if (player.isAlive()) {
                vent(player, level);
            }
            return;
        }
        if (player.isSprinting() && player.onGround() && now % 7 == 0) {
            Vec3 feet = player.position();
            Vfx.send(level, VfxType.SHOCKWAVE, feet.add(0, 0.1, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, 4f, 10);
            PowerFx.cracks(level, feet.add(0, 0.04, 0), 2.2f, 50, PowerFx.PURPLE);
            Vfx.sound(level, feet, ModSounds.POWER_HIT, 0.9f, 0.6f);
            for (LivingEntity e : CombatFx.targets(player, feet.add(0, 1, 0), 3.5)) {
                Vec3 away = e.position().subtract(feet).multiply(1, 0, 1).normalize();
                CombatFx.strike(player, e, 5f, away.scale(1.2).add(0, 0.6, 0), 0.8f, CombatFx.DeathStyle.RAGDOLL, false);
                CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, away, 14);
            }
        }
    }

    private static void vent(ServerPlayer player, ServerLevel level) {
        Vec3 c = player.position().add(0, 1, 0);
        Vfx.send(level, VfxType.SPHERE_PULSE, c, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 6f, 14);
        Vfx.send(level, VfxType.SHOCKWAVE, player.position().add(0, 0.1, 0), new Vec3(0, 1, 0), PowerFx.MAGENTA, PowerFx.PINK, 8f, 14);
        Vfx.sound(level, c, ModSounds.POWER_EXPLOSION, 2f, 1.4f);
        Vfx.camera(level, c, 20, 0.6f, 0, 0, 0xE0A0FF, 0.25f, 5);
        for (LivingEntity e : CombatFx.targets(player, c, 5.5)) {
            Vec3 away = e.position().subtract(c).normalize();
            CombatFx.strike(player, e, 8f, away.scale(1.3).add(0, 0.5, 0), 1f, CombatFx.DeathStyle.RAGDOLL, false);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private PowerOverload() {
    }
}
