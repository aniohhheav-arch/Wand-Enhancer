package dev.mysticarts.power.spells;

import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.MysticBoltEntity;
import dev.mysticarts.entity.SoulWispEntity;
import dev.mysticarts.entity.SpectralBeastEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.Sustained;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.Scheduler;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The Soul Stone: life, death and what lies between. */
public final class SoulSpells {
    public static final int ORANGE = 0xFF7A12;
    public static final int MAX_SOULS = 12;

    private SoulSpells() {}

    public static void register() {
        Spells.register(Ability.SOUL_BEAM, c -> {
            Sustained.startBeam(c.player(), c.data(), c.ability(), 60);
            Fx.sound(c.player(), MaSounds.BEAM_MYSTIC.get(), 1f, 0.7f);
            return true;
        });
        Spells.register(Ability.SOUL_DETECT, SoulSpells::detect);
        Spells.register(Ability.SOUL_TETHER, c -> {
            LivingEntity t = Aim.entity(c.player(), 28, e -> !MysticSpells.pvpBlocked(c, e));
            if (t == null) return false;
            EntityMarks.Mark m = EntityMarks.add(t, MarkKind.TETHERED, 240, c.player().getId());
            m.anchor = t.position();
            Fx.send(c.level(), FxKind.ARC, c.hands(), t.getBoundingBox().getCenter().subtract(c.hands()), ORANGE, 0.4f, 0, c.player().getId());
            Fx.sound(c.level(), t.position(), MaSounds.BINDING_CHAINS.get(), 1f, 0.7f);
            return true;
        });
        Spells.register(Ability.SOUL_PROJECTION, c -> {
            MysticBoltEntity.shoot(c.level(), c.player(), c.player().position().add(0, 0.1, 0).add(c.look().scale(0.5)), c.look(), MysticBoltEntity.SOUL_PROJECTION,
                    ORANGE, 0.9f, 0.9f, c.damage(9f)).life(45);
            Fx.sound(c.player(), MaSounds.ASTRAL_SEPARATE.get(), 1f, 1.3f);
            c.data().pose(Poses.PUSH, 10);
            return true;
        });
        Spells.register(Ability.SOUL_DRAIN, SoulSpells::drain);
        Spells.register(Ability.SOUL_WISPS, c -> {
            for (int i = 0; i < 3; i++) SoulWispEntity.summon(c.level(), c.player(), i, 500);
            Fx.sound(c.player(), MaSounds.WISP_CHIME.get(), 1.2f, 1f);
            return true;
        });
        Spells.register(Ability.SOUL_HEAL, SoulSpells::heal);
        Spells.register(Ability.SOUL_COMPANION, c -> summonBeasts(c, 1, 1200));
        Spells.register(Ability.SOUL_SEPARATION, c -> {
            LivingEntity t = Aim.entity(c.player(), 24, e -> !(e instanceof Player) && Aim.controllable(e));
            if (t == null) return false;
            EntityMarks.add(t, MarkKind.SEPARATED, 140, 0);
            if (t instanceof Mob m) m.setTarget(null);
            Fx.send(c.level(), FxKind.SOUL_RIP, t.position().add(0, t.getBbHeight() + 0.8, 0), Vec3.ZERO, ORANGE, 0, 0, t.getId());
            Fx.sound(c.level(), t.position(), MaSounds.ASTRAL_SEPARATE.get(), 1f, 0.8f);
            return true;
        });
        Spells.register(Ability.SOUL_RELEASE, SoulSpells::release);
        Spells.register(Ability.SOUL_ULTIMATE, SoulSpells::dominion);
    }

    private static boolean detect(Cast c) {
        ServerPlayer p = c.player();
        int souls = 0, players = 0;
        for (LivingEntity e : Aim.around(p, p.position(), 80, e -> true)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
            EntityMarks.add(e, MarkKind.REVEALED, 240, Aim.hostile(e) ? 1 : 2);
            if (souls < 24) Fx.send(c.level(), FxKind.SOUL_RIP, p.position().add(0, 1, 0), Vec3.ZERO, ORANGE, 0, 0, e.getId());
            souls++;
            if (e instanceof Player) players++;
        }
        Fx.send(c.level(), FxKind.DETECT, p.position().add(0, 1, 0), Vec3.ZERO, ORANGE, 80f, 40, p.getId());
        Fx.sound(p, MaSounds.DETECT.get(), 1f, 0.7f);
        p.displayClientMessage(Component.translatable("message.mysticarts.souls_detected", souls, players).withColor(ORANGE), true);
        return true;
    }

    private static boolean drain(Cast c) {
        ServerPlayer p = c.player();
        if (Aim.around(p, p.position(), 9, e -> Aim.foe(p, e)).isEmpty()) return false;
        for (int pulse = 0; pulse < 3; pulse++) {
            Scheduler.schedule(pulse * 20 + 1, server -> {
                if (!p.isAlive()) return;
                float total = 0;
                for (LivingEntity e : Aim.around(p, p.position(), 9, e -> Aim.foe(p, e))) {
                    float dmg = Spells.damage(p, Ability.SOUL_DRAIN, 3.5f);
                    if (e.hurt(p.damageSources().indirectMagic(p, p), dmg)) total += dmg;
                    Fx.send(p.serverLevel(), FxKind.SOUL_RIP, p.position().add(0, 1, 0), Vec3.ZERO, ORANGE, 0, 0, e.getId());
                }
                p.heal(total * 0.5f);
                Fx.sound(p, MaSounds.ABSORB.get(), 0.8f, 1.2f);
            });
        }
        c.data().pose(Poses.WHIP, 60);
        return true;
    }

    private static boolean heal(Cast c) {
        ServerPlayer p = c.player();
        p.heal(12f);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
        for (Player o : c.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), o -> o != p)) {
            o.heal(8f);
            o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
            Fx.send(c.level(), FxKind.ARC, p.position().add(0, 1, 0), o.position().subtract(p.position()), ORANGE, 0.3f, 0, p.getId());
        }
        Fx.send(c.level(), FxKind.WAVE, p.position().add(0, 1, 0), Vec3.ZERO, 0xFFD08A, 8f, 16, -1);
        Fx.sound(p, MaSounds.STONE_SOUL.get(), 1.2f, 1.3f);
        return true;
    }

    static boolean summonBeasts(Cast c, int count, int life) {
        ServerPlayer p = c.player();
        for (int i = 0; i < count; i++) {
            SpectralBeastEntity beast = MaEntities.SPECTRAL_BEAST.get().create(c.level());
            if (beast == null) return false;
            double a = Math.toRadians(p.getYRot()) + Math.PI / 2 + (i - (count - 1) / 2.0) * 0.9;
            Vec3 at = p.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            Vec3 spot = Aim.safeSpot(c.level(), beast, at, 3).orElse(p.position());
            beast.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0);
            beast.bind(p, life);
            c.level().addFreshEntity(beast);
            Fx.burst(c.level(), spot.add(0, 0.8, 0), ORANGE, 1.2f, 1.5f);
        }
        Fx.sound(p, MaSounds.SPECTRAL_HOWL.get(), 1.4f, 1f);
        return true;
    }

    private static boolean release(Cast c) {
        ServerPlayer p = c.player();
        int souls = c.data().souls;
        if (souls <= 0) {
            p.displayClientMessage(Component.translatable("message.mysticarts.no_souls"), true);
            return false;
        }
        List<LivingEntity> foes = Aim.around(p, p.position(), 24, e -> Aim.foe(p, e));
        ServerLevel level = c.level();
        for (int i = 0; i < souls; i++) {
            double a = i * Math.PI * 2 / souls;
            Vec3 from = p.position().add(Math.cos(a) * 1.2, 1.8, Math.sin(a) * 1.2);
            Vec3 dir = new Vec3(Math.cos(a), 0.6, Math.sin(a)).normalize();
            MysticBoltEntity bolt = MysticBoltEntity.shoot(level, p, from, dir, MysticBoltEntity.SOUL, ORANGE, 0.35f, 0.9f, c.damage(7f)).life(100);
            if (!foes.isEmpty()) bolt.homing(foes.get(i % foes.size()));
        }
        c.data().souls = 0;
        c.data().dirty = true;
        Fx.sound(p, MaSounds.SPECTRAL_HOWL.get(), 1.2f, 1.4f);
        c.data().pose(Poses.RAISE, 14);
        return true;
    }

    private static boolean dominion(Cast c) {
        ServerPlayer p = c.player();
        SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.SOUL_DOMINION, 24f, 400, ORANGE);
        summonBeasts(c, 4, 400);
        for (int i = 0; i < 3; i++) SoulWispEntity.summon(c.level(), p, i, 400);
        Fx.screen(c.level(), p.position(), 48, 1.2f, 0.6f, ORANGE);
        Fx.sound(p, MaSounds.ULTIMATE_RELEASE.get(), 1.6f, 1.1f);
        c.data().pose(Poses.RAISE, 30);
        return true;
    }
}
