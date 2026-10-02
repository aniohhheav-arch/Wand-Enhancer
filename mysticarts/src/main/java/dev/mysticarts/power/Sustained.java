package dev.mysticarts.power;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.power.spells.MysticSpells;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Everything that keeps running after the cast: shields, grabs, beams, wind-ups, absorption windows, astral travel. */
public final class Sustained {
    public static final int HOLD_WHIP = 1;
    public static final int HOLD_TELEKINESIS = 2;
    public static final int HOLD_MIND = 3;
    public static final double BEAM_RANGE = 40;

    private record Thrown(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, int caster, float damage, int color, int[] age) {}

    private static final Map<Integer, Thrown> THROWN = new HashMap<>();

    private Sustained() {}

    // ============================================================================================ recasts

    /** Handles a key press that should end or redirect something already running. True when consumed. */
    public static boolean recast(ServerPlayer player, PowerData data, Ability a) {
        if (a.has(Ability.Flag.SNEAK_ALT) && player.isShiftKeyDown() && Anchors.set(player, data, a)) return true;
        if (a == Ability.SLING_PORTAL || a == Ability.SPACE_PORTAL) {
            if (MysticSpells.closeLookedAtPortal(player)) return true;
        }
        if (data.heldEntity != -1) {
            boolean matches = (a == Ability.ELDRITCH_WHIP && data.holdKind == HOLD_WHIP) || (a == Ability.TELEKINESIS && data.holdKind == HOLD_TELEKINESIS)
                    || (a == Ability.MIND_TELEKINESIS && data.holdKind == HOLD_MIND);
            if (matches) {
                throwHeld(player, data);
                return true;
            }
        }
        if (a == Ability.ELDRITCH_SHIELD && data.shieldMode == 1) {
            data.shieldMode = 0;
            data.casterDirty = true;
            data.dirty = true;
            Fx.sound(player, MaSounds.SHIELD_DOWN.get(), 0.8f, 1f);
            return true;
        }
        if (a == Ability.ASTRAL_PROJECTION && data.astralTicks > 0) {
            MysticSpells.endAstral(player, data);
            return true;
        }
        if (a == Ability.MIRROR_DIMENSION && dev.mysticarts.world.MirrorDimension.isMirror(player.level())) {
            dev.mysticarts.world.MirrorDimension.exit(player, data);
            return true;
        }
        if (data.beamTicks > 0 && data.beamAbility == a.ordinal()) {
            data.beamTicks = 0;
            data.casterDirty = true;
            return true;
        }
        return false;
    }

    // ============================================================================================ ticking

    public static void tick(ServerPlayer player, PowerData data) {
        ServerLevel level = player.serverLevel();
        boolean unlimited = PowerManager.unlimited(player);

        if (data.shieldMode == 1) {
            if (!unlimited) data.mystic -= 0.12f;
            if (data.mystic <= 0) {
                data.mystic = 0;
                data.shieldMode = 0;
                data.casterDirty = true;
                Fx.sound(player, MaSounds.SHIELD_DOWN.get(), 0.8f, 0.8f);
            }
            data.dirty |= player.tickCount % 5 == 0;
        }

        if (data.heldEntity != -1) holdTick(player, data, level);
        if (data.beamTicks > 0) beamTick(player, data, level);

        if (data.windupAbility >= 0) {
            Ability a = Ability.byId(data.windupAbility);
            if (a.cosmic() && dev.mysticarts.item.InfinityGauntletItem.held(player).isEmpty()) {
                data.windupAbility = -1;
                data.casterDirty = true;
            } else if (--data.windupTicks <= 0) {
                data.windupAbility = -1;
                data.casterDirty = true;
                Spells.finish(new Cast(player, level, data, a, 1f));
            }
            data.dirty = true;
        }

        if (data.absorbTicks > 0 && --data.absorbTicks == 0) {
            data.casterDirty = true;
            data.dirty = true;
        }
        if (data.kineticTicks > 0 && --data.kineticTicks == 0) {
            data.casterDirty = true;
            data.dirty = true;
            dev.mysticarts.power.spells.PowerSpells.releaseKinetic(player, data);
        }
        if (data.deflectTicks > 0) {
            deflect(player, level, 4.5);
            if (--data.deflectTicks == 0) {
                data.casterDirty = true;
                data.dirty = true;
            }
        }
        if (data.astralFormTicks > 0 && --data.astralFormTicks == 0) {
            data.casterDirty = true;
            data.dirty = true;
            player.setInvisible(false);
            Fx.sound(player, MaSounds.ASTRAL_RETURN.get(), 0.8f, 1.2f);
        }
        if (data.astralTicks > 0) MysticSpells.astralTick(player, data);
    }

    /** Called once per server tick (not per player) for thrown objects. */
    public static void worldTick(ServerLevel level) {
        Iterator<Map.Entry<Integer, Thrown>> it = THROWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Thrown> e = it.next();
            Thrown t = e.getValue();
            if (t.dim != level.dimension()) continue;
            Entity entity = level.getEntity(e.getKey());
            if (entity == null) {
                if (t.age[0]++ > 100) it.remove();
                continue;
            }
            int age = ++t.age[0];
            boolean hit = age > 3 && (entity.horizontalCollision || entity.verticalCollision || entity.onGround() || entity.getDeltaMovement().lengthSqr() < 0.01);
            if (!hit && age < 80) {
                if (age % 2 == 0) level.sendParticles(MaParticles.STREAK.get().with(t.color, 0.5f, 10), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                        entity.getZ(), 1, 0, 0, 0, 0);
                // hits whatever it flies into
                Entity caster = level.getEntity(t.caster);
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.4),
                        v -> v != entity && v != caster && v.isAlive())) {
                    victim.hurt(level.damageSources().indirectMagic(entity, caster), t.damage * 0.6f);
                    victim.knockback(0.8, -entity.getDeltaMovement().x, -entity.getDeltaMovement().z);
                    hit = true;
                }
            }
            if (hit || age >= 80) {
                Entity caster = level.getEntity(t.caster);
                if (hit && entity instanceof LivingEntity living) living.hurt(level.damageSources().indirectMagic(caster, caster), t.damage);
                if (hit) {
                    Fx.send(level, FxKind.IMPACT, entity.position().add(0, entity.getBbHeight() * 0.5, 0), Vec3.ZERO, t.color, 1.2f, 0, -1);
                    Fx.sound(level, entity.position(), MaSounds.BLAST_IMPACT.get(), 0.8f, 0.9f);
                }
                if (entity instanceof LivingEntity) entity.setNoGravity(false);
                it.remove();
            }
        }
    }

    // ============================================================================================ holding

    public static void startHold(ServerPlayer player, PowerData data, Entity target, int kind) {
        data.heldEntity = target.getId();
        data.holdKind = kind;
        data.holdTicks = 0;
        data.casterDirty = true;
        target.setNoGravity(true);
        if (target instanceof Mob mob) mob.getNavigation().stop();
    }

    private static void holdTick(ServerPlayer player, PowerData data, ServerLevel level) {
        Entity target = level.getEntity(data.heldEntity);
        int maxTicks = data.holdKind == HOLD_WHIP ? 70 : 140;
        if (target == null || !target.isAlive() || target.distanceToSqr(player) > 40 * 40 || ++data.holdTicks > maxTicks) {
            if (target != null) target.setNoGravity(false);
            data.releaseHold();
            return;
        }
        double dist = data.holdKind == HOLD_WHIP ? 3.0 : 4.5;
        Vec3 want = player.getEyePosition().add(player.getLookAngle().scale(dist)).subtract(0, target.getBbHeight() * 0.5, 0);
        Vec3 to = want.subtract(target.position());
        double pull = data.holdKind == HOLD_WHIP ? 0.35 : 0.25;
        target.setDeltaMovement(target.getDeltaMovement().scale(0.4).add(to.scale(pull)));
        target.hurtMarked = true;
        target.fallDistance = 0;
        if (target instanceof FallingBlockEntity fb) fb.time = 1;
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (data.holdKind == HOLD_WHIP && data.holdTicks % 10 == 0 && target instanceof LivingEntity living) {
            living.hurt(level.damageSources().indirectMagic(player, player), Spells.damage(player, Ability.ELDRITCH_WHIP, 1.5f));
        }
    }

    public static void throwHeld(ServerPlayer player, PowerData data) {
        Entity target = player.serverLevel().getEntity(data.heldEntity);
        int kind = data.holdKind;
        data.releaseHold();
        data.pose(Poses.PUSH, 8);
        if (target == null) return;
        target.setNoGravity(false);
        double speed = kind == HOLD_WHIP ? 1.8 : 2.4;
        target.setDeltaMovement(player.getLookAngle().scale(speed).add(0, 0.15, 0));
        target.hurtMarked = true;
        Ability source = kind == HOLD_WHIP ? Ability.ELDRITCH_WHIP : kind == HOLD_MIND ? Ability.MIND_TELEKINESIS : Ability.TELEKINESIS;
        float dmg = Spells.damage(player, source, kind == HOLD_MIND ? 9f : 7f);
        THROWN.put(target.getId(), new Thrown(player.level().dimension(), player.getId(), dmg, source.color(), new int[] {0}));
        Fx.sound(player, kind == HOLD_WHIP ? MaSounds.WHIP_CRACK.get() : MaSounds.TELEKINESIS_THROW.get(), 1f, 1f);
    }

    // ============================================================================================ beams

    public static void startBeam(ServerPlayer player, PowerData data, Ability a, int ticks) {
        data.beamAbility = a.ordinal();
        data.beamTicks = ticks;
        data.casterDirty = true;
        data.pose(Poses.PUSH, ticks);
    }

    /** End point of a beam from the caster's hands (terrain or first living thing). */
    public static HitResult beamHit(LivingEntity caster) {
        Vec3 from = Aim.hands(caster);
        Vec3 to = from.add(caster.getLookAngle().scale(BEAM_RANGE));
        BlockHitResult block = caster.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(caster.level(), caster, from, end, new AABB(from, end).inflate(1),
                e -> e instanceof LivingEntity l && l.isAlive() && e != caster && !e.isSpectator(), 0.5f);
        return ent != null ? ent : block;
    }

    private static void beamTick(ServerPlayer player, PowerData data, ServerLevel level) {
        Ability a = Ability.byId(data.beamAbility);
        if (a.cosmic() && dev.mysticarts.item.InfinityGauntletItem.held(player).isEmpty()) data.beamTicks = 1;
        if (--data.beamTicks <= 0) {
            data.beamAbility = -1;
            data.casterDirty = true;
            return;
        }
        HitResult hit = beamHit(player);
        Vec3 at = hit.getLocation();
        if (data.beamTicks % 3 == 0) {
            level.sendParticles(MaParticles.SPARK.get().with(a.color(), 0.6f, 14), at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.12);
        }
        if (data.beamTicks % 20 == 0) Fx.sound(level, at, a == Ability.POWER_BEAM ? MaSounds.BEAM_COSMIC.get() : MaSounds.BEAM_MYSTIC.get(), 0.6f, 1f);
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity victim) || data.beamTicks % 4 != 0) return;
        switch (a) {
            case POWER_BEAM -> {
                victim.hurt(level.damageSources().indirectMagic(player, player), Spells.damage(player, a, 5f));
                victim.igniteForSeconds(4);
                victim.knockback(0.3, player.getX() - victim.getX(), player.getZ() - victim.getZ());
            }
            case MIND_BEAM -> {
                victim.hurt(level.damageSources().indirectMagic(player, player), Spells.damage(player, a, 2.5f));
                if (victim instanceof Mob mob && Aim.controllable(mob)) {
                    mob.setTarget(null);
                    EntityMarks.add(mob, MarkKind.PACIFIED, 100, 0);
                    Vec3 wander = mob.position().add((level.random.nextDouble() - 0.5) * 12, 0, (level.random.nextDouble() - 0.5) * 12);
                    mob.getNavigation().moveTo(wander.x, wander.y, wander.z, 1.0);
                }
                if (victim instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            }
            case SOUL_BEAM -> {
                float dmg = Spells.damage(player, a, 3.5f);
                if (victim.hurt(level.damageSources().indirectMagic(player, player), dmg)) player.heal(dmg * 0.5f);
                Fx.send(level, FxKind.SOUL_RIP, player.position().add(0, 1, 0), Vec3.ZERO, a.color(), 0, 0, victim.getId());
            }
            default -> victim.hurt(level.damageSources().indirectMagic(player, player), Spells.damage(player, a, 3f));
        }
    }

    // ============================================================================================ deflection

    /** Turns every hostile projectile near the player around. */
    public static int deflect(ServerPlayer player, ServerLevel level, double radius) {
        int n = 0;
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius),
                p -> p.getOwner() != player && p.getDeltaMovement().lengthSqr() > 0.01)) {
            Vec3 toPlayer = player.position().add(0, 1, 0).subtract(p.position());
            if (p.getDeltaMovement().dot(toPlayer) <= 0) continue;
            Entity shooter = p.getOwner();
            Vec3 back = shooter != null ? shooter.getEyePosition().subtract(p.position()).normalize() : p.getDeltaMovement().normalize().reverse();
            p.setDeltaMovement(back.scale(Math.max(1.2, p.getDeltaMovement().length() * 1.3)));
            p.setOwner(player);
            p.hurtMarked = true;
            Fx.send(level, FxKind.SHIELD_HIT, p.position(), Vec3.ZERO, Ability.DEFLECTION.color(), 0.8f, 0, player.getId());
            n++;
        }
        if (n > 0) Fx.sound(player, MaSounds.DEFLECT.get(), 0.9f, 1.1f);
        return n;
    }

    public static void clear() {
        THROWN.clear();
    }
}
