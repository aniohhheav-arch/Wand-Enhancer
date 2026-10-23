package com.wandcraft.magic.spells;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.ProjBehavior;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Reusable building blocks shared by the spell groups. */
final class Kit {
    private Kit() {}

    /** A straightforward damaging projectile with a coloured trail and a burst on impact. */
    static ProjBehavior bolt(int color, ParticleOptions accent, SoundEvent hit, float hitRadius) {
        return new ProjBehavior() {
            @Override public void trail(SpellProjectile p) {
                Vec3 v = p.getDeltaMovement();
                for (int i = 0; i < 2; i++) {
                    double t = i * 0.5;
                    Fx.c(p.level(), Fx.dust(color, 1.0f), p.getX() - v.x * t, p.getY() - v.y * t, p.getZ() - v.z * t, 0, 0, 0);
                }
                Fx.c(p.level(), accent, p.getX(), p.getY(), p.getZ(), 0, 0, 0);
            }
            @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) {
                ServerLevel l = (ServerLevel) p.level();
                Combat.hurt(l, p.getOwner(), p, t, p.damage);
                p.credit(1);
                impact(l, p.position(), color, accent, hit, hitRadius);
                return true;
            }
            @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) {
                impact((ServerLevel) p.level(), r.getLocation(), color, accent, hit, hitRadius * 0.7f);
                return true;
            }
        };
    }

    static void impact(ServerLevel l, Vec3 at, int color, ParticleOptions accent, SoundEvent hit, float radius) {
        Fx.burst(l, Fx.dust(color, 1.4f), at, 14, 0.25 * radius, 0.08);
        Fx.burst(l, accent, at, 8, 0.2 * radius, 0.12);
        if (hit != null) Fx.sound(l, at, hit, 0.7f, 1.2f);
    }

    static List<SpellZone> zonesOf(ServerPlayer owner, String spellId) {
        List<SpellZone> out = new ArrayList<>();
        for (SpellZone z : owner.serverLevel().getEntitiesOfClass(SpellZone.class, owner.getBoundingBox().inflate(128))) {
            Spell s = z.spell();
            if (s != null && s.id.equals(spellId) && owner.getUUID().equals(z.ownerId)) out.add(z);
        }
        out.sort(Comparator.comparingInt((SpellZone z) -> z.age).reversed());
        return out;
    }
}
