package dev.infinitygauntlet.mind;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.entity.TimedEffectEntity;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mental Shield: a golden geometric dome that travels with its caster. Arrows that reach it are turned back at
 * their shooter; anything else is stopped dead and burnt away. Hostile creatures are pressed back out of it. Every
 * strike sends a visible ripple across its surface.
 */
public class MentalShieldEntity extends TimedEffectEntity {
    public MentalShieldEntity(EntityType<? extends MentalShieldEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            discard();
            return;
        }
        setPos(owner.position());
        Vec3 c = position().add(0, 0.9, 0);
        double r = visualRadius;
        AABB box = new AABB(c, c).inflate(r + 2);
        for (Entity e : level.getEntities(this, box, e -> e instanceof Projectile)) {
            Projectile p = (Projectile) e;
            Entity shooter = p.getOwner();
            if (shooter == owner || shooter != null && TargetRules.isAlly(owner, shooter)) {
                continue;
            }
            Vec3 at = p.position();
            Vec3 out = at.subtract(c);
            double d = out.length();
            Vec3 v = p.getDeltaMovement();
            if (d > r + 0.6 || d < 0.2 || v.dot(out) >= 0) {
                continue; // only things crossing inward through the surface
            }
            Vec3 n = out.normalize();
            Vec3 hit = c.add(n.scale(r));
            if (p instanceof AbstractArrow) {
                Vec3 reflected = v.subtract(n.scale(2 * v.dot(n))).scale(0.8);
                p.setDeltaMovement(reflected);
                p.setOwner(owner);
                p.setPos(hit.add(n.scale(0.3)));
                p.hurtMarked = true;
            } else {
                p.discard();
                Vfx.send(level, VfxType.SPARK_BURST, hit, Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 0.6f, 8);
            }
            Vfx.send(level, VfxType.SHIELD_RIPPLE, hit, c, MindManager.GOLD, MindManager.PALE, (float) r, 14);
            Vfx.sound(level, hit, ModSounds.MIND_SHIELD_HIT, 1.4f, 0.9f + random.nextFloat() * 0.3f);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && TargetRules.canTarget(owner, e))) {
            Vec3 out = e.position().add(0, e.getBbHeight() / 2, 0).subtract(c);
            if (out.length() < r) {
                Vec3 n = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                e.setDeltaMovement(n.scale(0.45).add(0, 0.1, 0));
                e.hurtMarked = true;
                if (tickCount % 8 == 0) {
                    Vfx.send(level, VfxType.SHIELD_RIPPLE, c.add(n.scale(r)), c, MindManager.GOLD, MindManager.PALE, (float) r, 12);
                }
            }
        }
    }

    @Override
    protected void onExpire(ServerLevel level) {
        Vfx.send(level, VfxType.GEOMETRY_BURST, position().add(0, 1, 0), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, visualRadius, 14);
        Vfx.sound(level, position(), ModSounds.MIND_SHIELD, 1.2f, 0.6f);
    }

    /** 0..1 how much the dome is formed (grows in, fades out). */
    public float formed(float pt) {
        float t = tickCount + pt;
        return Math.min(1f, t / 8f) * Math.min(1f, (maxLife - t) / 12f);
    }
}
