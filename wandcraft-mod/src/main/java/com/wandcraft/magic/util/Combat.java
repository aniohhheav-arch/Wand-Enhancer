package com.wandcraft.magic.util;

import com.wandcraft.Config;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Target selection and damage rules shared by every spell. */
public final class Combat {
    private Combat() {}

    public static boolean isBoss(LivingEntity e) { return e.getType().is(Tags.EntityTypes.BOSSES); }

    /** Heavy entities resist displacement and crowd control. */
    public static boolean isHeavy(LivingEntity e) { return isBoss(e) || e.getMaxHealth() >= 100; }

    /** True if {@code owner} may directly target {@code t} (no allies, own pets, armor stands, or players when PvP is off). */
    public static boolean canTarget(Entity owner, LivingEntity t) {
        if (t == owner || !t.isAlive() || t.isSpectator() || t instanceof ArmorStand) return false;
        if (owner != null && (t.isAlliedTo(owner) || owner.isAlliedTo(t))) return false;
        if (t instanceof TamableAnimal ta && owner instanceof LivingEntity le && ta.isOwnedBy(le)) return false;
        if (t instanceof Player p) {
            if (p.isCreative() || !Config.SPELLS_HURT_PLAYERS.get()) return false;
            if (owner != null && owner.level().getServer() != null && !owner.level().getServer().isPvpAllowed()) return false;
        }
        return true;
    }

    /** Hostile-only filter for area spells so they do not slaughter farm animals. */
    public static boolean isHostile(Entity owner, LivingEntity t) {
        if (!canTarget(owner, t)) return false;
        if (t instanceof Player) return true;
        return t instanceof Enemy || (t instanceof Mob m && m.getTarget() != null);
    }

    public static List<LivingEntity> enemiesIn(ServerLevel l, Vec3 c, double r, Entity owner) {
        return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> e.position().distanceToSqr(c) <= r * r + 1 && isHostile(owner, e));
    }

    public static List<LivingEntity> targetsIn(ServerLevel l, Vec3 c, double r, Entity owner) {
        return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> e.position().distanceToSqr(c) <= r * r + 1 && canTarget(owner, e));
    }

    public static DamageSource source(ServerLevel l, Entity direct, Entity owner) {
        return l.damageSources().indirectMagic(direct == null ? owner : direct, owner);
    }

    public static boolean hurt(ServerLevel l, Entity owner, Entity direct, LivingEntity t, float amount) {
        if (amount <= 0) return false;
        t.invulnerableTime = Math.min(t.invulnerableTime, 0); // magic ignores hit-immunity so multi-hit spells work
        return t.hurt(source(l, direct, owner), amount);
    }

    public static void push(LivingEntity t, Vec3 impulse) {
        double resist = isHeavy(t) ? 0.0 : 1.0 - t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        Vec3 v = t.getDeltaMovement().add(impulse.scale(resist));
        t.setDeltaMovement(v);
        t.hurtMarked = true;
    }

    public static void pushAway(LivingEntity t, Vec3 from, double strength, double lift) {
        Vec3 d = t.position().subtract(from);
        Vec3 h = new Vec3(d.x, 0, d.z);
        if (h.lengthSqr() < 1e-4) h = new Vec3(Math.random() - 0.5, 0, Math.random() - 0.5);
        h = h.normalize().scale(strength);
        push(t, new Vec3(h.x, lift, h.z));
    }

    public static void slow(LivingEntity t, int ticks, int amp) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp, false, true, true));
    }

    /** Short stun: slowed to a halt and unable to jump. Heavy entities only get a brief slow. */
    public static void immobilize(LivingEntity t, int ticks) {
        if (isBoss(t)) {
            slow(t, Math.min(ticks, 20), 1);
            return;
        }
        int d = isHeavy(t) ? ticks / 3 : ticks;
        slow(t, d, 7);
        t.addEffect(new MobEffectInstance(MobEffects.JUMP, d, 128, false, false, false));
    }

    public static void stagger(LivingEntity t, int ticks) {
        if (isBoss(t)) return;
        slow(t, ticks, 3);
    }

    public static void ignite(LivingEntity t, int seconds) { t.igniteForSeconds(seconds); }
}
