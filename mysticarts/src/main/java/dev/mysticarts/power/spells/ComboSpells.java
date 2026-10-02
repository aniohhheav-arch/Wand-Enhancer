package dev.mysticarts.power.spells;

import dev.mysticarts.MaConfig;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.SlingPortalEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.entity.SummonEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.service.TimeHistory;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.BlockHistory;
import dev.mysticarts.world.Scheduler;
import dev.mysticarts.world.TemporaryBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Powers that need two or more stones working together, and the Snap. */
public final class ComboSpells {
    public static final int SNAP_WINDUP = 100;

    private ComboSpells() {}

    public static void register() {
        Spells.register(Ability.COSMIC_CATACLYSM, ComboSpells::cataclysm);
        Spells.register(Ability.TEMPORAL_REWRITE, ComboSpells::temporalRewrite);
        Spells.register(Ability.DIMENSIONAL_TELEKINESIS, ComboSpells::dimensionalTelekinesis);
        Spells.register(Ability.SPECTRAL_DIMENSION, ComboSpells::spectralDimension);
        Spells.windup(Ability.THE_SNAP, SNAP_WINDUP, ComboSpells::snapStart, ComboSpells::snapFinish);
    }

    /** Space opens a rift; Power pours through it; the rift erupts. */
    private static boolean cataclysm(Cast c) {
        Vec3 at = Aim.point(c.player(), 40).add(0, 2.5, 0);
        int color = c.color();
        SlingPortalEntity.open(c.level(), c.player(), SlingPortalEntity.KIND_RIFT, at, c.player().getYRot() + 180, 0, 3.4f, 0x2F7BFF, 60);
        SpellFieldEntity f = SpellFieldEntity.spawn(c.level(), c.player(), at, FieldKind.CATACLYSM, 7f, 50, color);
        f.damage = c.damage(26f);
        Fx.send(c.level(), FxKind.CHARGE_UP, at, Vec3.ZERO, 0x9B2CFF, 2f, 50, -1);
        Fx.sound(c.level(), at, MaSounds.RIFT_OPEN.get(), 2f, 0.8f);
        Fx.sound(c.level(), at, MaSounds.ULTIMATE_CHARGE.get(), 1.6f, 1f);
        c.data().pose(Poses.RAISE, 40);
        return true;
    }

    /** Time turns back while Reality unmakes every spell-change: a sweeping restoration. */
    private static boolean temporalRewrite(Cast c) {
        ServerPlayer p = c.player();
        ServerLevel level = c.level();
        int window = Math.min(300, MaConfig.REWIND_SECONDS.get());
        int restored = BlockHistory.rewind(level, p.blockPosition(), 28, window);
        restored += TemporaryBlocks.get(level).revertAround(level, p.blockPosition(), 32);
        for (Player o : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(16))) {
            TimeHistory.Snapshot then = TimeHistory.ago(o, 200);
            if (then != null && then.health() > o.getHealth()) o.setHealth(then.health());
            o.removeAllEffects();
            o.clearFire();
        }
        SpellFieldEntity.spawn(level, p, p.position().add(0, 1, 0), FieldKind.TEMPORAL_REWRITE, 28f, 80, c.color());
        Fx.send(level, FxKind.REWIND, p.position().add(0, 1, 0), Vec3.ZERO, 0x22E06A, 28f, 60, p.getId());
        Fx.send(level, FxKind.CLOCKS, p.position().add(0, 2, 0), new Vec3(0, 1, 0), 0xE3122E, 4f, 60, p.getId());
        Fx.screen(level, p.position(), 48, 1f, 0.7f, c.color());
        Fx.sound(p, MaSounds.TIME_REWIND.get(), 1.6f, 0.7f);
        Fx.sound(p, MaSounds.STONE_REALITY.get(), 1.2f, 0.8f);
        p.displayClientMessage(Component.translatable("message.mysticarts.rewound", restored).withColor(c.color()), true);
        c.data().pose(Poses.RAISE, 30);
        return true;
    }

    /** Mind lifts everything around the caster; Space carries it to where the caster is looking. */
    private static boolean dimensionalTelekinesis(Cast c) {
        ServerPlayer p = c.player();
        Vec3 target = Aim.safeLookSpot(c.level(), p, 80).orElse(null);
        if (target == null) return false;
        if (Aim.around(p, p.position(), 10, e -> !MysticSpells.pvpBlocked(c, e)).isEmpty()) return false;
        SpellFieldEntity f = SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.DIMENSIONAL_TELEKINESIS, 10f, 50, c.color());
        f.target = target;
        SlingPortalEntity.open(c.level(), p, SlingPortalEntity.KIND_SPACE, target.add(0, 3, 0), p.getYRot(), 0, 2.4f, 0x2F7BFF, 60);
        Fx.sound(p, MaSounds.STONE_MIND.get(), 1.2f, 0.8f);
        Fx.sound(c.level(), target, MaSounds.PORTAL_OPEN.get(), 1.4f, 0.8f);
        c.data().pose(Poses.RAISE, 50);
        return true;
    }

    /** Reality is repainted in soul-light; the dead walk beside the caster. */
    private static boolean spectralDimension(Cast c) {
        ServerPlayer p = c.player();
        SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.SPECTRAL_DIMENSION, 16f, 300, c.color());
        RealitySpells.environment(c, p.blockPosition(), 12, new Block[] {Blocks.SOUL_SOIL, Blocks.SOUL_SAND, Blocks.ORANGE_STAINED_GLASS, Blocks.SHROOMLIGHT}, 300);
        SoulSpells.summonBeasts(c, 2, 300);
        Fx.screen(c.level(), p.position(), 40, 0.8f, 0.6f, c.color());
        Fx.sound(p, MaSounds.STONE_SOUL.get(), 1.4f, 0.7f);
        c.data().pose(Poses.RAISE, 30);
        return true;
    }

    // ============================================================================================ the snap

    private static boolean snapStart(Cast c) {
        ServerPlayer p = c.player();
        SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.SNAP, 6f, SNAP_WINDUP + 60, c.color()).follow();
        Fx.send(c.level(), FxKind.SNAP, p.position().add(0, 1, 0), p.getLookAngle(), c.color(), MaConfig.SNAP_RADIUS.get(), SNAP_WINDUP, p.getId());
        Fx.sound(p, MaSounds.SNAP_BUILD.get(), 2f, 1f);
        c.data().pose(Poses.SNAP, SNAP_WINDUP + 20);
        return true;
    }

    private static boolean snapFinish(Cast c) {
        ServerPlayer p = c.player();
        ServerLevel level = c.level();
        Fx.sound(p, MaSounds.SNAP_CLICK.get(), 2f, 1f);
        Fx.sound(p, MaSounds.SNAP_WAVE.get(), 2.5f, 1f);
        int radius = MaConfig.SNAP_RADIUS.get();
        Fx.send(level, FxKind.WAVE, p.position().add(0, 1, 0), Vec3.ZERO, 0xFFE27A, radius, 60, p.getId());
        Fx.screen(level, p.position(), radius, 1.5f, 1f, 0xFFF4D0);
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
        MaConfig.SnapMode mode = MaConfig.SNAP_MODE.get();
        if (mode == MaConfig.SnapMode.VISUAL_ONLY) {
            p.displayClientMessage(Component.translatable("message.mysticarts.snap_visual").withColor(0xFFE27A), false);
            return true;
        }
        List<LivingEntity> pool = new ArrayList<>(Aim.around(p, p.position(), radius, e -> e instanceof Mob && !(e instanceof SummonEntity)
                && Aim.controllable(e) && (mode == MaConfig.SnapMode.HALF_ALL_MOBS || e instanceof Enemy) && !e.hasCustomName()));
        Collections.shuffle(pool, new java.util.Random(level.random.nextLong()));
        int half = pool.size() / 2 + (pool.size() % 2 == 1 && level.random.nextBoolean() ? 1 : 0);
        for (int i = 0; i < half; i++) {
            LivingEntity e = pool.get(i);
            int delay = 20 + level.random.nextInt(80);
            Scheduler.schedule(delay, server -> {
                if (!e.isAlive()) return;
                Fx.send(level, FxKind.DUST_AWAY, e.position(), Vec3.ZERO, 0x8A6A4A, e.getBbWidth(), e.getBbHeight(), e.getId());
                Fx.sound(level, e.position(), MaSounds.SNAP_DUST.get(), 0.8f, 0.8f + level.random.nextFloat() * 0.4f);
                if (e instanceof Mob m) {
                    m.setNoAi(true);
                    m.setSilent(true);
                }
                Scheduler.schedule(40, s2 -> {
                    if (e.isAlive()) e.discard();
                });
            });
        }
        p.displayClientMessage(Component.translatable("message.mysticarts.snap_done", half, pool.size()).withColor(0xFFE27A), false);
        return true;
    }
}
