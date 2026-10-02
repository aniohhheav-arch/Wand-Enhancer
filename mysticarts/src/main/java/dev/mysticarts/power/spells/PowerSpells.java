package dev.mysticarts.power.spells;

import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.MysticBoltEntity;
import dev.mysticarts.entity.ShockwaveEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Blast;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.Sustained;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** The Power Stone: raw, barely contained force. */
public final class PowerSpells {
    public static final int PURPLE = 0x9B2CFF;

    private PowerSpells() {}

    public static void register() {
        Spells.register(Ability.POWER_BLAST, c -> {
            MysticBoltEntity.shoot(c.level(), c.player(), c.hands(), c.look(), MysticBoltEntity.POWER, PURPLE, 0.55f, 1.9f, c.damage(10f)).blast(3f, true);
            Fx.sound(c.player(), MaSounds.BLAST_FIRE.get(), 1.2f, 0.7f);
            c.data().pose(Poses.PUSH, 8);
            return true;
        });
        Spells.register(Ability.POWER_SHOCKWAVE, c -> {
            ServerPlayer p = c.player();
            ShockwaveEntity.spawn(c.level(), p, p.position().add(0, 0.1, 0), ShockwaveEntity.GROUND, PURPLE, 12f, 18, c.damage(9f), 1.8f, 0.8f);
            Fx.screen(c.level(), p.position(), 24, 1.2f, 0.1f, PURPLE);
            Fx.sound(p, MaSounds.SHOCKWAVE.get(), 1.4f, 1f);
            c.data().pose(Poses.SLAM, 10);
            return true;
        });
        Spells.register(Ability.POWER_SUPERCHARGE, c -> {
            c.data().superchargeHits = 5;
            c.data().casterDirty = true;
            Fx.send(c.level(), FxKind.CHARGE_UP, c.player().position().add(0, 1, 0), Vec3.ZERO, PURPLE, 0.8f, 20, c.player().getId());
            Fx.sound(c.player(), MaSounds.ULTIMATE_CHARGE.get(), 0.8f, 1.6f);
            return true;
        });
        Spells.register(Ability.POWER_DESTRUCTION, PowerSpells::destruction);
        Spells.register(Ability.POWER_BEAM, c -> {
            Sustained.startBeam(c.player(), c.data(), c.ability(), 60);
            Fx.sound(c.player(), MaSounds.BEAM_COSMIC.get(), 1.2f, 1f);
            return true;
        });
        Spells.register(Ability.POWER_ORB, c -> {
            float ch = c.charge();
            MysticBoltEntity.shoot(c.level(), c.player(), c.hands(), c.look(), MysticBoltEntity.POWER_ORB, PURPLE, 0.5f + ch * 1.1f, 1.1f, c.damage(8f + 16f * ch))
                    .blast(2f + 4f * ch, true);
            Fx.sound(c.player(), MaSounds.BLAST_FIRE.get(), 1.4f, 0.9f - ch * 0.4f);
            Fx.screen(c.level(), c.eye(), 10, 0.5f * ch, 0, PURPLE);
            c.data().pose(Poses.PUSH, 10);
            return true;
        });
        Spells.register(Ability.POWER_ABSORB, c -> {
            c.data().kineticTicks = 120;
            c.data().kineticStored = 0;
            c.data().casterDirty = true;
            Fx.send(c.level(), FxKind.ABSORB, c.player().position().add(0, 1, 0), Vec3.ZERO, PURPLE, 2.5f, 120, c.player().getId());
            Fx.sound(c.player(), MaSounds.ABSORB.get(), 1f, 0.7f);
            return true;
        });
        Spells.register(Ability.POWER_PULSE, c -> {
            ServerPlayer p = c.player();
            ShockwaveEntity.spawn(c.level(), p, p.position().add(0, 1, 0), ShockwaveEntity.SPHERE, PURPLE, 8f, 8, c.damage(3f), 2.6f, 0.5f);
            Fx.sound(p, MaSounds.PUSH.get(), 1.3f, 0.7f);
            c.data().pose(Poses.RAISE, 8);
            return true;
        });
        Spells.register(Ability.POWER_STRENGTH, c -> {
            ServerPlayer p = c.player();
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 2));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
            Fx.send(c.level(), FxKind.CHARGE_UP, p.position().add(0, 1, 0), Vec3.ZERO, PURPLE, 1f, 30, p.getId());
            Fx.sound(p, MaSounds.STONE_POWER.get(), 1.3f, 0.8f);
            return true;
        });
        Spells.register(Ability.POWER_EXPLOSION, c -> {
            Vec3 at = Aim.point(c.player(), 32);
            SpellFieldEntity f = SpellFieldEntity.spawn(c.level(), c.player(), at.add(0, 0.5, 0), FieldKind.DELAYED_EXPLOSION, 6f, 60, PURPLE);
            f.damage = c.damage(22f);
            Fx.sound(c.level(), at, MaSounds.ULTIMATE_CHARGE.get(), 1.2f, 0.8f);
            return true;
        });
        Spells.windup(Ability.POWER_ULTIMATE, 70, c -> {
            SpellFieldEntity.spawn(c.level(), c.player(), c.player().position().add(0, 1, 0), FieldKind.POWER_CHARGE, 3f, 75, PURPLE).follow();
            Fx.send(c.level(), FxKind.CHARGE_UP, c.player().position().add(0, 1, 0), Vec3.ZERO, PURPLE, 2f, 70, c.player().getId());
            Fx.sound(c.player(), MaSounds.ULTIMATE_CHARGE.get(), 1.6f, 0.8f);
            return true;
        }, PowerSpells::annihilation);
    }

    private static boolean destruction(Cast c) {
        Vec3 eye = c.eye();
        Vec3 dir = c.look();
        for (int i = 0; i < 4; i++) {
            Vec3 at = eye.add(dir.scale(5 + i * 4));
            var hit = c.level().clip(new net.minecraft.world.level.ClipContext(eye, at, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, c.player()));
            Vec3 point = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at : hit.getLocation();
            final int delay = i * 3;
            dev.mysticarts.world.Scheduler.schedule(delay, server -> Blast.detonate(c.level(), c.player(), point, 3.2f, c.damage(9f), 1.4f, PURPLE, true));
        }
        Fx.screen(c.level(), eye, 20, 1.2f, 0, PURPLE);
        c.data().pose(Poses.SLAM, 14);
        return true;
    }

    /** Kinetic absorption ends: everything soaked up comes back out as a blast wave. */
    public static void releaseKinetic(ServerPlayer p, PowerData d) {
        float stored = d.kineticStored;
        d.kineticStored = 0;
        if (stored <= 0.5f) return;
        float radius = Math.min(14f, 4f + stored * 0.4f);
        ShockwaveEntity.spawn(p.serverLevel(), p, p.position().add(0, 1, 0), ShockwaveEntity.SPHERE, PURPLE, radius, 12,
                Spells.damage(p, Ability.POWER_ABSORB, stored * 1.5f), 2f, 0.6f);
        Fx.screen(p.serverLevel(), p.position(), radius * 2, 1f, 0.3f, PURPLE);
        Fx.sound(p, MaSounds.COSMIC_EXPLOSION.get(), 1.2f, 1.2f);
    }

    private static boolean annihilation(Cast c) {
        ServerPlayer p = c.player();
        Vec3 at = p.position().add(0, 1, 0);
        ShockwaveEntity.spawn(c.level(), p, at, ShockwaveEntity.SPHERE, PURPLE, 28f, 26, c.damage(30f), 3f, 1.1f);
        ShockwaveEntity.spawn(c.level(), p, p.position().add(0, 0.1, 0), ShockwaveEntity.GROUND, 0xE0A8FF, 22f, 22, 0f, 1f, 0.4f);
        if (dev.mysticarts.MaConfig.TERRAIN_DESTRUCTION.get()) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Blast.carve(c.level(), p, at.add(Math.cos(a) * 9, -1.5, Math.sin(a) * 9), 3.5f);
            }
        }
        Fx.send(c.level(), FxKind.WAVE, at, Vec3.ZERO, PURPLE, 28f, 26, -1);
        Fx.screen(c.level(), at, 96, 2.4f, 1f, PURPLE);
        Fx.sound(p, MaSounds.ULTIMATE_RELEASE.get(), 2f, 0.8f);
        Fx.sound(p, MaSounds.COSMIC_EXPLOSION.get(), 2f, 0.7f);
        return true;
    }
}
