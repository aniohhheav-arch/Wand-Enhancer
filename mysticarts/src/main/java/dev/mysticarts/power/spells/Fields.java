package dev.mysticarts.power.spells;

import dev.mysticarts.MaConfig;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.MysticBoltEntity;
import dev.mysticarts.entity.ShockwaveEntity;
import dev.mysticarts.entity.SlingPortalEntity;
import dev.mysticarts.entity.SoulWispEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.entity.SummonEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Blast;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.registry.MaSounds;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server behaviour of every spell field kind. */
public final class Fields {
    private Fields() {}

    /** Things a field never touches: other spell constructs, the owner and the owner's summons. */
    private static boolean exempt(Entity e, @Nullable Entity owner) {
        if (e instanceof SpellFieldEntity || e instanceof SlingPortalEntity || e instanceof ShockwaveEntity || e instanceof SoulWispEntity) return true;
        if (owner == null) return false;
        if (e == owner) return true;
        if (e instanceof SummonEntity s && owner.getUUID().equals(s.ownerId())) return true;
        if (e instanceof Projectile p && p.getOwner() == owner) return true;
        return e.isAlliedTo(owner);
    }

    private static List<Entity> inside(SpellFieldEntity f, ServerLevel level, @Nullable Entity owner, double radius) {
        Vec3 c = f.position();
        return level.getEntities(f, new AABB(c, c).inflate(radius), e -> !exempt(e, owner) && e.getBoundingBox().getCenter().distanceToSqr(c) <= radius * radius);
    }

    private static boolean canTimeLock(Entity e) {
        return !(e instanceof Player) || MaConfig.FREEZE_PLAYERS.get();
    }

    public static void tick(SpellFieldEntity f, ServerLevel level, @Nullable Entity owner, int age) {
        float r = f.radius();
        switch (f.kind()) {
            case FieldKind.SHIELD_DOME -> dome(f, level, owner, r);
            case FieldKind.TIME_STOP -> {
                for (Entity e : inside(f, level, owner, r)) if (canTimeLock(e)) EntityMarks.add(e, MarkKind.FROZEN, 3, 0);
            }
            case FieldKind.TIME_SLOW -> {
                int div = Math.max(2, (int) f.damage);
                for (Entity e : inside(f, level, owner, r)) {
                    if (e instanceof Player p) {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
                    } else {
                        EntityMarks.add(e, MarkKind.SLOWED, 3, div);
                    }
                }
            }
            case FieldKind.TIME_ACCEL -> randomTicks(level, f.blockPosition(), (int) r, 8);
            case FieldKind.TEMPORAL_SINGULARITY -> {
                for (Entity e : inside(f, level, owner, r)) {
                    if (e instanceof Projectile) EntityMarks.add(e, MarkKind.FROZEN, 3, 0);
                    else if (e instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 3, false, false));
                    else EntityMarks.add(e, MarkKind.SLOWED, 3, 5);
                }
                randomTicks(level, f.blockPosition(), (int) r, 16);
                // the caster's own clock runs faster inside the singularity: cooldowns recover at double speed
                if (owner instanceof ServerPlayer sp && sp.distanceToSqr(f) < r * r) {
                    PowerData d = PowerManager.data(sp);
                    for (int i = 0; i < d.cooldowns.length; i++) if (d.cooldowns[i] > 0) d.cooldowns[i]--;
                }
            }
            case FieldKind.GRAVITY -> {
                if (age < f.duration() - 20) {
                    for (Entity e : inside(f, level, owner, r)) {
                        if (!(e instanceof LivingEntity || e instanceof ItemEntity)) continue;
                        if (f.affected.add(e.getId())) {
                            EntityMarks.Mark m = EntityMarks.add(e, MarkKind.LIFTED, f.duration() - age, 0);
                            m.anchor = e.position().add(0, 5 + level.random.nextDouble() * 2, 0);
                        }
                    }
                }
            }
            case FieldKind.COSMIC_RIFT -> rift(f, level, owner, r);
            case FieldKind.REALITY_DISTORT, FieldKind.REALITY_REWRITE -> {
                if (age % 20 == 0) {
                    for (Entity e : inside(f, level, owner, r)) {
                        if (e instanceof LivingEntity l && (f.kind() == FieldKind.REALITY_DISTORT || e instanceof Enemy)) distort(level, l);
                    }
                }
            }
            case FieldKind.ZERO_G -> {
                for (Entity e : inside(f, level, owner, r)) {
                    if (e.isNoGravity() && !f.affected.contains(e.getId())) continue;
                    if (f.affected.add(e.getId())) e.setNoGravity(true);
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.96).add(0, 0.006, 0));
                    e.hurtMarked = true;
                    e.fallDistance = 0;
                }
                f.affected.removeIf(id -> {
                    Entity e = level.getEntity(id);
                    if (e == null) return true;
                    if (e.distanceToSqr(f) > r * r) {
                        e.setNoGravity(false);
                        return true;
                    }
                    return false;
                });
            }
            case FieldKind.MIND_LIFT, FieldKind.DIMENSIONAL_TELEKINESIS -> {
                if (age < f.duration() - 8) {
                    for (Entity e : inside(f, level, owner, r)) {
                        if (!(e instanceof LivingEntity || e instanceof ItemEntity || e instanceof FallingBlockEntity)) continue;
                        if (e instanceof Player && !level.getServer().isPvpAllowed()) continue;
                        if (f.affected.add(e.getId())) {
                            EntityMarks.Mark m = EntityMarks.add(e, MarkKind.LIFTED, f.duration() - age + 2, 0);
                            m.anchor = e.position().add(0, 3.5 + level.random.nextDouble() * 3, 0);
                        }
                    }
                }
            }
            case FieldKind.SOUL_DOMINION -> {
                if (age % 20 == 0) {
                    for (Entity e : inside(f, level, owner, r)) {
                        if (e instanceof LivingEntity l) {
                            l.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                            if (l instanceof Enemy) l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
                        }
                    }
                }
            }
            case FieldKind.SPECTRAL_DIMENSION -> {
                if (age % 20 == 0) {
                    for (Entity e : inside(f, level, owner, r)) {
                        if (e instanceof LivingEntity l && Aim.hostile(l)) {
                            l.hurt(level.damageSources().indirectMagic(owner, owner), 3f * MaConfig.DAMAGE_MULTIPLIER.get().floatValue());
                            l.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
                            Fx.send(level, FxKind.SOUL_RIP, f.position(), Vec3.ZERO, 0xFF7A12, 0, 0, l.getId());
                        } else if (e instanceof Player p) {
                            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
                        }
                    }
                    if (owner instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
                }
            }
            case FieldKind.PROJECTILE_FREEZE -> {
                for (Entity e : inside(f, level, owner, r)) if (e instanceof Projectile) EntityMarks.add(e, MarkKind.FROZEN, 3, 0);
            }
            case FieldKind.CATACLYSM, FieldKind.SPACE_RIFT_ULT -> {
                // the rift drinks in everything loose around it while it charges
                for (Entity e : inside(f, level, owner, r * 2)) {
                    if (e instanceof ItemEntity || e instanceof LivingEntity) {
                        Vec3 to = f.position().subtract(e.position());
                        e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(0.04)));
                        e.hurtMarked = true;
                    }
                }
            }
            case FieldKind.DELAYED_EXPLOSION -> {
                if (age % 10 == 0) Fx.sound(level, f.position(), MaSounds.ULTIMATE_CHARGE.get(), 0.6f, 0.8f + age / (float) f.duration());
            }
            default -> {}
        }
    }

    public static void end(SpellFieldEntity f, ServerLevel level, @Nullable Entity owner) {
        Vec3 c = f.position();
        switch (f.kind()) {
            case FieldKind.TIME_STOP -> {
                for (Entity e : EntityMarks.marked(level, MarkKind.FROZEN)) {
                    if (e.distanceToSqr(c) <= (f.radius() + 2) * (f.radius() + 2)) EntityMarks.remove(e, MarkKind.FROZEN);
                }
                Fx.send(level, FxKind.TIME_RESUME, c, 0x22E06A, f.radius(), 0);
                Fx.sound(level, c, MaSounds.TIME_RESUME.get(), 1.4f, 1f);
            }
            case FieldKind.TEMPORAL_SINGULARITY, FieldKind.PROJECTILE_FREEZE -> {
                for (Entity e : EntityMarks.marked(level, MarkKind.FROZEN)) {
                    if (e instanceof Projectile && e.distanceToSqr(c) <= (f.radius() + 2) * (f.radius() + 2)) EntityMarks.remove(e, MarkKind.FROZEN);
                }
                Fx.send(level, FxKind.TIME_RESUME, c, 0x22E06A, f.radius() * 0.5f, 0);
            }
            case FieldKind.GRAVITY -> {
                for (int id : f.affected) {
                    Entity e = level.getEntity(id);
                    if (e == null) continue;
                    EntityMarks.remove(e, MarkKind.LIFTED);
                    e.setNoGravity(false);
                    e.setDeltaMovement(0, -2.4, 0);
                    e.hurtMarked = true;
                    if (e instanceof LivingEntity l) l.hurt(level.damageSources().indirectMagic(owner, owner), 5f * MaConfig.DAMAGE_MULTIPLIER.get().floatValue());
                }
                Fx.sound(level, c, MaSounds.SLAM.get(), 1.3f, 0.7f);
                Fx.ring(level, c.subtract(0, 1, 0), new Vec3(0, 1, 0), f.color(), f.radius(), 16);
            }
            case FieldKind.ZERO_G -> {
                for (int id : f.affected) {
                    Entity e = level.getEntity(id);
                    if (e != null) e.setNoGravity(false);
                }
            }
            case FieldKind.MIND_LIFT -> {
                for (int id : f.affected) {
                    Entity e = level.getEntity(id);
                    if (e == null) continue;
                    EntityMarks.remove(e, MarkKind.LIFTED);
                    e.setNoGravity(false);
                }
                ShockwaveEntity.spawn(level, owner, c, ShockwaveEntity.SPHERE, f.color(), f.radius() + 4, 14, f.damage, 2.6f, 0.6f);
                Fx.screen(level, c, 48, 1.4f, 0.4f, f.color());
                Fx.sound(level, c, MaSounds.ULTIMATE_RELEASE.get(), 1.6f, 1.3f);
            }
            case FieldKind.DIMENSIONAL_TELEKINESIS -> {
                Vec3 to = f.target != null ? f.target : c;
                for (int id : f.affected) {
                    Entity e = level.getEntity(id);
                    if (e == null) continue;
                    EntityMarks.remove(e, MarkKind.LIFTED);
                    e.setNoGravity(false);
                    Vec3 jitter = to.add(level.random.nextGaussian() * 2, 0, level.random.nextGaussian() * 2);
                    Optional<Vec3> spot = Aim.safeSpot(level, e, jitter, 4);
                    Vec3 from = e.position();
                    Aim.moveTo(e, spot.orElse(to.add(0, 1, 0)));
                    Fx.teleport(level, from.add(0, 1, 0), e.position().add(0, 1, 0), f.color());
                }
                Fx.sound(level, to, MaSounds.PORTAL_TRAVEL.get(), 1.4f, 0.8f);
            }
            case FieldKind.DELAYED_EXPLOSION -> Blast.detonate(level, owner, c, f.radius(), f.damage, 1.8f, f.color(), true);
            case FieldKind.CATACLYSM -> {
                Blast.detonate(level, owner, c, f.radius(), f.damage, 2.2f, f.color(), true);
                ShockwaveEntity.spawn(level, owner, c, ShockwaveEntity.SPHERE, f.color(), f.radius() * 2.5f, 20, f.damage * 0.5f, 2.4f, 0.8f);
                Fx.send(level, FxKind.WAVE, c, Vec3.ZERO, f.color(), f.radius() * 2.5f, 24, -1);
                Fx.screen(level, c, 64, 2f, 0.8f, f.color());
            }
            default -> {}
        }
    }

    // ============================================================================================ helpers

    private static void dome(SpellFieldEntity f, ServerLevel level, @Nullable Entity owner, float r) {
        Vec3 c = f.position();
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r + 2))) {
            if (exempt(p, owner)) continue;
            Vec3 rel = p.position().subtract(c);
            double d = rel.length();
            if (d > r + 0.5 || d < r - 1.5 || p.getDeltaMovement().dot(rel) >= 0) continue;
            p.setDeltaMovement(rel.normalize().scale(Math.max(0.6, p.getDeltaMovement().length() * 0.8)));
            p.hurtMarked = true;
            if (owner != null) p.setOwner(owner);
            Fx.send(level, FxKind.SHIELD_HIT, p.position(), rel.normalize(), f.color(), 1f, 0, f.getId());
            Fx.sound(level, p.position(), MaSounds.SHIELD_IMPACT.get(), 0.8f, 1f);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r))) {
            if (exempt(e, owner)) {
                if (e instanceof Player p && f.age() % 20 == 0) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1, false, false));
                continue;
            }
            if (!(e instanceof Enemy)) continue;
            Vec3 rel = e.position().subtract(c);
            if (rel.length() < r) {
                e.setDeltaMovement(rel.normalize().scale(0.6).add(0, 0.2, 0));
                e.hurtMarked = true;
                if (f.age() % 10 == 0) Fx.send(level, FxKind.SHIELD_HIT, e.position().add(0, 1, 0), rel.normalize(), f.color(), 1.2f, 0, f.getId());
            }
        }
    }

    private static void rift(SpellFieldEntity f, ServerLevel level, @Nullable Entity owner, float r) {
        Vec3 c = f.position();
        for (Entity e : inside(f, level, owner, r * 1.6)) {
            if (!(e instanceof LivingEntity || e instanceof ItemEntity || e instanceof Projectile)) continue;
            if (e instanceof Player && !level.getServer().isPvpAllowed()) continue;
            Vec3 to = c.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d < 1.4) {
                Vec3 from = e.position();
                double a = level.random.nextDouble() * Math.PI * 2;
                Vec3 away = c.add(Math.cos(a) * (20 + level.random.nextInt(16)), 6, Math.sin(a) * (20 + level.random.nextInt(16)));
                Aim.moveTo(e, Aim.safeSpot(level, e, away, 6).orElse(away));
                if (e instanceof LivingEntity l) l.hurt(level.damageSources().indirectMagic(owner, owner), 6f * MaConfig.DAMAGE_MULTIPLIER.get().floatValue());
                Fx.teleport(level, from.add(0, 1, 0), e.position().add(0, 1, 0), f.color());
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(to.normalize().scale(0.12 + 0.6 / (d + 1))));
            e.hurtMarked = true;
            if (e instanceof Mob m) m.getNavigation().stop();
        }
    }

    private static void distort(ServerLevel level, LivingEntity l) {
        RandomSource rnd = level.random;
        switch (rnd.nextInt(5)) {
            case 0 -> l.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            case 1 -> l.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1));
            case 2 -> l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            case 3 -> l.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            default -> {
                Vec3 to = l.position().add(rnd.nextGaussian() * 2, 0, rnd.nextGaussian() * 2);
                Aim.safeSpot(level, l, to, 2).ifPresent(p -> Aim.moveTo(l, p));
            }
        }
        Fx.burst(level, l.position().add(0, l.getBbHeight() * 0.5, 0), 0xE3122E, 0.6f, 0.6f);
    }

    /** Random-ticks blocks around a point, so crops grow, saplings sprout and copper ages. */
    static void randomTicks(ServerLevel level, BlockPos center, int radius, int count) {
        RandomSource rnd = level.random;
        for (int i = 0; i < count; i++) {
            BlockPos p = center.offset(rnd.nextInt(radius * 2 + 1) - radius, rnd.nextInt(7) - 3, rnd.nextInt(radius * 2 + 1) - radius);
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isRandomlyTicking()) s.randomTick(level, p, rnd);
        }
    }

    /** Spawns a bolt from a field (used by Soul Dominion constructs). */
    static void bolt(ServerLevel level, LivingEntity owner, Vec3 from, LivingEntity target, int color) {
        Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        MysticBoltEntity.shoot(level, owner, from, dir, MysticBoltEntity.SOUL, color, 0.3f, 1.2f, 4f).homing(target);
    }
}
