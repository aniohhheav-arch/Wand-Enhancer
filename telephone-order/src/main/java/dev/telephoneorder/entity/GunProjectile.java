package dev.telephoneorder.entity;

import dev.telephoneorder.Fx;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.weapon.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;

import java.util.UUID;

/** All gun-fired physical projectiles (plasma, rockets, homing micro-missiles, rift seeds, EMP shells ...). Server authoritative. */
public class GunProjectile extends ThrowableItemProjectile {
    public enum Kind { PLASMA, SHARD, CHARGE, HEAVY, RIFT, EMP, MISSILE, ROCKET }

    public Kind kind = Kind.PLASMA;
    public float damage = 5, splash = 0, splashDmg = 0, scale = 1;
    public int life = 100, bounces = 0, color = 0xFFFFFF, param = 0;
    public double grav = 0;
    public UUID target;
    public double speed = 1.5;

    public GunProjectile(EntityType<? extends GunProjectile> type, Level level) {
        super(type, level);
    }

    public GunProjectile setup(Kind k, float damage, float splash, float splashDmg, int life, int color) {
        this.kind = k; this.damage = damage; this.splash = splash; this.splashDmg = splashDmg; this.life = life; this.color = color;
        this.setItem(new net.minecraft.world.item.ItemStack(ModItems.proj(k)));
        return this;
    }

    @Override
    protected Item getDefaultItem() { return ModItems.proj(Kind.PLASMA); }

    @Override
    protected double getDefaultGravity() { return grav; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        if (--life <= 0) { detonate(sl, position()); return; }
        // homing
        if (kind == Kind.MISSILE && target != null && tickCount > 4) {
            Entity t = sl.getEntity(target);
            if (t != null && t.isAlive()) {
                Vec3 want = t.getBoundingBox().getCenter().subtract(position());
                if (want.lengthSqr() > 0.01) {
                    Vec3 cur = getDeltaMovement();
                    Vec3 desired = want.normalize().scale(Math.max(0.4, speed));
                    Vec3 next = cur.scale(0.78).add(desired.scale(0.22));
                    setDeltaMovement(next.normalize().scale(Math.max(0.4, speed)));
                }
            }
        }
        trail(sl);
    }

    private void trail(ServerLevel l) {
        Vec3 p = position();
        switch (kind) {
            case PLASMA -> { Fx.p(l, Fx.dust(color, 1.1f), p, 2, 0.05, 0.05, 0.05, 0); if (tickCount % 3 == 0) Fx.p(l, ParticleTypes.END_ROD, p, 1, 0, 0, 0, 0.01); }
            case SHARD -> Fx.p(l, Fx.dust(color, 0.8f), p, 1, 0.02, 0.02, 0.02, 0);
            case CHARGE -> { Fx.p(l, Fx.dust(color, 2.2f), p, 3, 0.15, 0.15, 0.15, 0); Fx.p(l, ParticleTypes.ELECTRIC_SPARK, p, 2, 0.3, 0.3, 0.3, 0.2); }
            case HEAVY -> { Fx.p(l, Fx.dust(color, 2.0f), p, 3, 0.12, 0.12, 0.12, 0); Fx.p(l, ParticleTypes.FLAME, p, 2, 0.1, 0.1, 0.1, 0.02); Fx.p(l, ParticleTypes.SMOKE, p, 1, 0.1, 0.1, 0.1, 0.01); }
            case RIFT -> { Fx.p(l, ParticleTypes.REVERSE_PORTAL, p, 4, 0.2, 0.2, 0.2, 0.05); Fx.p(l, Fx.dust(color, 1.4f), p, 2, 0.1, 0.1, 0.1, 0); }
            case EMP -> { Fx.p(l, ParticleTypes.ELECTRIC_SPARK, p, 2, 0.15, 0.15, 0.15, 0.1); Fx.p(l, Fx.dust(color, 1.6f), p, 2, 0.1, 0.1, 0.1, 0); }
            case MISSILE -> { Fx.p(l, ParticleTypes.SMOKE, p, 1, 0.02, 0.02, 0.02, 0); Fx.p(l, Fx.dust(color, 0.9f), p, 1, 0.02, 0.02, 0.02, 0); }
            case ROCKET -> { Fx.p(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, p, 1, 0.03, 0.03, 0.03, 0.002); Fx.p(l, ParticleTypes.FLAME, p, 1, 0.03, 0.03, 0.03, 0.01); }
        }
    }

    private ServerPlayer owner() { return getOwner() instanceof ServerPlayer sp ? sp : null; }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity e = hit.getEntity();
        if (e == getOwner()) return;
        Vec3 dir = getDeltaMovement().normalize();
        switch (kind) {
            case ROCKET, HEAVY -> Ballistics.damage(sl, owner(), e, damage, 0f, DamageKind.EXPLOSION, dir, false, false);
            case RIFT, EMP -> { }
            default -> Ballistics.damage(sl, owner(), e, damage, 0f, DamageKind.ENERGY, dir, false, false);
        }
        detonate(sl, hit.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (kind == Kind.SHARD && bounces > 0) {
            bounces--;
            Vec3 v = getDeltaMovement();
            Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
            Vec3 r = v.subtract(n.scale(2 * v.dot(n))).scale(0.8);
            setDeltaMovement(r);
            setPos(hit.getLocation().add(n.scale(0.1)));
            Fx.p(sl, ParticleTypes.ELECTRIC_SPARK, hit.getLocation(), 4, 0.1, 0.1, 0.1, 0.1);
            Fx.sound(sl, hit.getLocation(), "impact_energy", SoundSource.PLAYERS, 0.4f, 1.4f);
            return;
        }
        detonate(sl, hit.getLocation());
    }

    private void detonate(ServerLevel sl, Vec3 at) {
        if (isRemoved()) return;
        ServerPlayer o = owner();
        Fx.sound(sl, at, "impact_energy", SoundSource.PLAYERS, 0.7f, 1.0f);
        switch (kind) {
            case PLASMA, SHARD -> {
                Fx.p(sl, Fx.dust(color, 1.6f), at, 8, 0.2, 0.2, 0.2, 0.05);
                Fx.p(sl, ParticleTypes.ELECTRIC_SPARK, at, 6, 0.2, 0.2, 0.2, 0.2);
                if (splash > 0) Boom.explode(sl, at, splash, splashDmg, o, DamageKind.ENERGY, this, 0.25f, false);
            }
            case CHARGE -> {
                Fx.p(sl, Fx.dust(color, 3f), at, 24, 0.5 * scale, 0.5 * scale, 0.5 * scale, 0.1);
                Fx.ring(sl, ParticleTypes.END_ROD, at, splash * 0.6, 24, 0.2);
                Boom.explode(sl, at, splash, splashDmg, o, DamageKind.ENERGY, this, 0.8f * scale, true);
            }
            case HEAVY -> {
                Fx.ring(sl, ParticleTypes.FLAME, at, splash * 0.7, 36, 0.1);
                Fx.flash(sl, at, 18, 0xFFC890, 8);
                Boom.explode(sl, at, splash, splashDmg, o, DamageKind.EXPLOSION, this, 1.4f, true);
            }
            case MISSILE -> Boom.explode(sl, at, splash, splashDmg, o, DamageKind.EXPLOSION, this, 0.5f, false);
            case ROCKET -> Boom.explode(sl, at, splash, splashDmg, o, DamageKind.EXPLOSION, this, 1.6f, true);
            case EMP -> {
                Fx.sound(sl, at, "emp_burst", SoundSource.PLAYERS, 1.4f, 1f);
                Fx.ring(sl, ParticleTypes.ELECTRIC_SPARK, at, splash * 0.8, 40, 0.3);
                Fx.ring(sl, Fx.dust(color, 2f), at, splash * 0.5, 30, 0.4);
                Fx.flash(sl, at, 14, 0x9AD8FF, 6);
                Boom.explode(sl, at, splash, splashDmg, o, DamageKind.EMP, this, 0.5f, false);
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(splash))) {
                    if (le == o || le.position().distanceTo(at) > splash) continue;
                    le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, param, 2));
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, param, 1));
                    le.addEffect(new MobEffectInstance(MobEffects.GLOWING, param, 0));
                }
            }
            case RIFT -> DistortionField.create(sl, at, o, (int) damage, splash, (float) grav);
        }
        discard();
    }
}
