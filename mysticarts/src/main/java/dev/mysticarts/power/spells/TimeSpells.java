package dev.mysticarts.power.spells;

import dev.mysticarts.MaConfig;
import dev.mysticarts.block.SpellBlock;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Source;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.service.TimeHistory;
import dev.mysticarts.power.service.TimeLoops;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.BlockHistory;
import dev.mysticarts.world.Scheduler;
import dev.mysticarts.world.TemporaryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Time magic, shared by the Eye of Agamotto (weaker) and the Time Stone (stronger). */
public final class TimeSpells {
    public static final int GREEN = 0x22E06A;

    private TimeSpells() {}

    public static void register() {
        Spells.register(Ability.TIME_STONE_STOP, c -> stop(c, 24, 200));
        Spells.register(Ability.TIME_STONE_SLOW, c -> slow(c, 16, 240, 4));
        Spells.register(Ability.TIME_STONE_ACCELERATE, c -> accelerate(c, 10, 240));
        Spells.register(Ability.TIME_STONE_REWIND, c -> rewind(c, 20, 60, 200));
        Spells.register(Ability.TIME_STONE_LOOP, c -> loop(c, 28, 60, 4));
        Spells.register(Ability.TIME_STONE_AGE, TimeSpells::age);
        Spells.register(Ability.TIME_STONE_FREEZE_PROJECTILES, c -> {
            SpellFieldEntity.spawn(c.level(), c.player(), c.player().position().add(0, 1, 0), FieldKind.PROJECTILE_FREEZE, 12f, 140, GREEN).follow();
            Fx.sound(c.player(), MaSounds.TIME_STOP.get(), 0.8f, 1.5f);
            return true;
        });
        Spells.register(Ability.TIME_STONE_DISPLACE, TimeSpells::displace);
        Spells.register(Ability.TIME_STONE_SAVESTATE, TimeSpells::restoreState);
        Spells.register(Ability.TIME_STONE_BARRIER, TimeSpells::barrier);
        Spells.register(Ability.TIME_ULTIMATE, TimeSpells::singularity);
    }

    // ============================================================================================ shared spells

    public static boolean stop(Cast c, float radius, int ticks) {
        SpellFieldEntity.spawn(c.level(), c.player(), c.player().position().add(0, 1, 0), FieldKind.TIME_STOP, radius, ticks, GREEN);
        Fx.send(c.level(), FxKind.TIME_STOP, c.player().position().add(0, 1, 0), Vec3.ZERO, GREEN, radius, ticks, c.player().getId());
        Fx.sound(c.player(), MaSounds.TIME_STOP.get(), 1.4f, 1f);
        c.data().pose(Poses.MUDRA, 24);
        return true;
    }

    public static boolean slow(Cast c, float radius, int ticks, int divisor) {
        SpellFieldEntity f = SpellFieldEntity.spawn(c.level(), c.player(), c.player().position().add(0, 1, 0), FieldKind.TIME_SLOW, radius, ticks, 0x5CFF9A);
        f.damage = divisor;
        Fx.sound(c.player(), MaSounds.TIME_SLOW.get(), 1.2f, 1f);
        c.data().pose(Poses.MUDRA, 20);
        return true;
    }

    public static boolean accelerate(Cast c, float radius, int ticks) {
        ServerPlayer p = c.player();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 2, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, false, true));
        SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.TIME_ACCEL, radius, ticks, 0xB4FFC8).follow();
        Fx.sound(p, MaSounds.TIME_ACCELERATE.get(), 1.2f, 1f);
        c.data().pose(Poses.MUDRA, 16);
        return true;
    }

    /**
     * Turns back time around the caster: every journaled block change in range is undone, spell-made blocks revert,
     * and the caster returns to where they stood {@code ticksAgo} ago (with at least the health they had then).
     */
    public static boolean rewind(Cast c, int radius, int seconds, int ticksAgo) {
        ServerLevel level = c.level();
        ServerPlayer p = c.player();
        int window = Math.min(seconds, MaConfig.REWIND_SECONDS.get());
        int blocks = BlockHistory.rewind(level, p.blockPosition(), radius, window);
        blocks += TemporaryBlocks.get(level).revertAround(level, p.blockPosition(), radius);
        TimeHistory.Snapshot then = TimeHistory.ago(p, ticksAgo);
        Vec3 from = p.position();
        if (then != null && then.dim() == level.dimension() && then.pos().distanceTo(from) > 0.5) {
            p.connection.teleport(then.pos().x, then.pos().y, then.pos().z, then.yaw(), then.pitch());
            p.resetFallDistance();
            if (then.health() > p.getHealth()) p.setHealth(then.health());
            p.clearFire();
        }
        Fx.send(level, FxKind.REWIND, from.add(0, 1, 0), Vec3.ZERO, GREEN, radius, 40, p.getId());
        Fx.send(level, FxKind.CLOCKS, from.add(0, 1.2, 0), c.look(), GREEN, 1.6f, 40, p.getId());
        Fx.sound(p, MaSounds.TIME_REWIND.get(), 1.3f, 1f);
        p.displayClientMessage(Component.translatable("message.mysticarts.rewound", blocks).withColor(GREEN), true);
        c.data().pose(Poses.MUDRA, 30);
        return true;
    }

    public static boolean loop(Cast c, double range, int length, int loops) {
        LivingEntity t = Aim.entity(c.player(), range, e -> !(e instanceof Player) && Aim.controllable(e));
        if (t == null) return false;
        TimeLoops.start(t, length, loops);
        Fx.send(c.level(), FxKind.CLOCKS, t.position().add(0, t.getBbHeight() * 0.5, 0), new Vec3(0, 1, 0), GREEN, 1.4f, 30, t.getId());
        Fx.sound(c.level(), t.position(), MaSounds.TIME_LOOP.get(), 1f, 1f);
        c.data().pose(Poses.MUDRA, 16);
        return true;
    }

    // ============================================================================================ stone-only

    private static boolean age(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 20);
        if (t instanceof AgeableMob ageable) {
            ageable.setAge(ageable.isBaby() ? 0 : -24000);
            Fx.send(c.level(), FxKind.CLOCKS, t.position().add(0, t.getBbHeight() * 0.5, 0), c.look(), GREEN, 1f, 20, t.getId());
            Fx.sound(c.level(), t.position(), MaSounds.TIME_ACCELERATE.get(), 0.8f, ageable.isBaby() ? 1.6f : 0.7f);
            return true;
        }
        if (t instanceof Zombie z) {
            z.setBaby(!z.isBaby());
            Fx.send(c.level(), FxKind.CLOCKS, t.position().add(0, t.getBbHeight() * 0.5, 0), c.look(), GREEN, 1f, 20, t.getId());
            return true;
        }
        // no ageable creature: age the plant being looked at to full maturity
        BlockHitResult hit = Aim.block(c.player(), 12);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState s = c.level().getBlockState(pos);
        if (!(s.getBlock() instanceof BonemealableBlock)) return false;
        boolean any = false;
        for (int i = 0; i < 12; i++) {
            s = c.level().getBlockState(pos);
            if (!(s.getBlock() instanceof BonemealableBlock g) || !g.isValidBonemealTarget(c.level(), pos, s)) break;
            g.performBonemeal(c.level(), c.level().random, pos, s);
            any = true;
        }
        if (any) {
            Fx.send(c.level(), FxKind.CLOCKS, Vec3.atCenterOf(pos), new Vec3(0, 1, 0), GREEN, 0.8f, 20, -1);
            Fx.burst(c.level(), Vec3.atCenterOf(pos), 0xB4FFC8, 0.6f, 1f);
        }
        return any;
    }

    /** Pushes the target a few seconds into the future: it vanishes now and reappears, unchanged, later. */
    private static boolean displace(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 24, e -> !(e instanceof Player) && Aim.controllable(e));
        if (t == null) return false;
        CompoundTag tag = new CompoundTag();
        if (!t.save(tag)) return false;
        ServerLevel level = c.level();
        Vec3 pos = t.position();
        ResourceKey<net.minecraft.world.level.Level> dim = level.dimension();
        Fx.send(level, FxKind.CLOCKS, pos.add(0, t.getBbHeight() * 0.5, 0), c.look(), GREEN, 1.4f, 20, -1);
        Fx.burst(level, pos.add(0, 1, 0), GREEN, 1.2f, 1.5f);
        Fx.sound(level, pos, MaSounds.TIME_REWIND.get(), 1f, 1.5f);
        t.discard();
        Scheduler.scheduleSafe(120, server -> {
            ServerLevel l = server.getLevel(dim);
            if (l == null) return;
            Entity back = EntityType.loadEntityRecursive(tag, l, e -> {
                e.moveTo(pos.x, pos.y, pos.z, e.getYRot(), e.getXRot());
                return e;
            });
            if (back != null) {
                l.addFreshEntity(back);
                Fx.send(l, FxKind.TIME_RESUME, pos.add(0, 1, 0), GREEN, 2f, 0);
                Fx.sound(l, pos, MaSounds.TIME_RESUME.get(), 1f, 1.3f);
            }
        });
        return true;
    }

    private static boolean restoreState(Cast c) {
        CompoundTag t = c.data().savedState;
        if (t == null) {
            c.player().displayClientMessage(Component.translatable("message.mysticarts.no_state"), true);
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
        ServerLevel level = id == null ? null : c.player().server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return false;
        ServerPlayer p = c.player();
        Vec3 from = p.position();
        Fx.send(c.level(), FxKind.REWIND, from.add(0, 1, 0), Vec3.ZERO, GREEN, 3f, 30, p.getId());
        p.teleportTo(level, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
        p.setHealth(Math.max(1f, t.getFloat("health")));
        p.getFoodData().setFoodLevel(t.getInt("food"));
        p.getFoodData().setSaturation(t.getFloat("saturation"));
        p.setAirSupply(t.getInt("air"));
        p.setRemainingFireTicks(t.getInt("fire"));
        p.resetFallDistance();
        Fx.send(level, FxKind.CLOCKS, p.position().add(0, 1, 0), p.getLookAngle(), GREEN, 1.6f, 30, p.getId());
        Fx.sound(level, p.position(), MaSounds.TIME_REWIND.get(), 1.2f, 0.8f);
        return true;
    }

    private static boolean barrier(Cast c) {
        ServerLevel level = c.level();
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        BlockPos center = c.player().blockPosition();
        BlockState wall = MaBlocks.SPELL_BARRIER.get().defaultBlockState().setValue(SpellBlock.TINT, SpellBlock.tintOf(Source.TIME));
        int placed = 0;
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            for (int y = 0; y < 3; y++) {
                BlockPos p = BlockPos.containing(center.getX() + 0.5 + Math.cos(a) * 4.5, center.getY() + y, center.getZ() + 0.5 + Math.sin(a) * 4.5);
                if (TemporaryBlocks.replaceable(level, p, true) && temp.place(level, p, wall, 200)) placed++;
            }
        }
        SpellFieldEntity.spawn(level, c.player(), Vec3.atBottomCenterOf(center).add(0, 1, 0), FieldKind.PROJECTILE_FREEZE, 6f, 200, GREEN);
        Fx.sound(c.player(), MaSounds.SHIELD_UP.get(), 1f, 1.3f);
        c.data().pose(Poses.SHIELD, 16);
        return true;
    }

    private static boolean singularity(Cast c) {
        ServerPlayer p = c.player();
        SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.TEMPORAL_SINGULARITY, 20f, 300, GREEN);
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 4, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, false, true));
        Fx.send(c.level(), FxKind.CLOCKS, p.position().add(0, 2, 0), new Vec3(0, 1, 0), GREEN, 6f, 60, p.getId());
        Fx.screen(c.level(), p.position(), 40, 1.2f, 0.5f, GREEN);
        Fx.sound(p, MaSounds.ULTIMATE_RELEASE.get(), 1.5f, 1.2f);
        Fx.sound(p, MaSounds.TIME_STOP.get(), 1.2f, 0.7f);
        c.data().pose(Poses.RAISE, 30);
        return true;
    }
}
