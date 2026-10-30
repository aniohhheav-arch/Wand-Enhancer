package dev.arcana.spells;

import dev.arcana.ArcanaConfig;
import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.SpellContext;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Glacier: ice spikes, freezing novas, frozen prisons and blizzards. */
public final class GlacierSpells {
    private static final BlockParticleOption ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
    private static final BlockParticleOption BLUE_ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());

    static void chill(LivingEntity e, int ticks) {
        e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + ticks));
        Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
    }

    /** Ice spikes burst from the ground in a line (a fan when awakened), freezing whatever they pierce. */
    public static boolean iceSpikes(SpellContext c) {
        Vec3 origin = c.player.position();
        Vec3 fwd = Magic.flatLook(c.player);
        int lines = c.scale(1, 3, 5);
        double length = 8 + 8 * c.charge;
        Set<UUID> hit = new HashSet<>();
        Magic.snd(c.level, origin, SoundEvents.GLASS_BREAK, 0.8f, 0.6f);
        EffectManager.run(c.level, (int) (length / 1.5), age -> {
            double d = 1.5 + age * 1.5;
            for (int l = 0; l < lines; l++) {
                double ang = (l - (lines - 1) / 2.0) * Math.toRadians(15);
                Vec3 p = origin.add(Magic.rotateY(fwd, ang).scale(d));
                Vec3 base = new Vec3(p.x, Magic.groundY(c.level, p.x, origin.y, p.z), p.z);
                for (int h = 0; h < 5; h++) {
                    double w = 0.35 * (1 - h / 5.0);
                    Vec3 q = base.add(0, h * 0.5, 0);
                    c.level.sendParticles(h % 2 == 0 ? (net.minecraft.core.particles.ParticleOptions) ICE : ParticleTypes.SNOWFLAKE, q.x, q.y, q.z, Magic.count(3), w, 0.05, w, 0.01);
                }
                Magic.fx(c.level, BLUE_ICE, base.add(0, 0.4, 0), 4, 0.4, 0.2, 0.4, 0.1);
                for (LivingEntity e : Magic.enemies(c, base.add(0, 1, 0), 1.6)) {
                    if (!hit.add(e.getUUID())) continue;
                    Magic.hurt(c, e, 4f);
                    chill(e, 140);
                }
            }
            return true;
        }, null);
        return true;
    }

    /** An expanding ring of frost around the caster; freezes shallow water into frosted ice. */
    public static boolean frostNova(SpellContext c) {
        Vec3 center = c.player.position();
        double max = 7 * c.radius;
        Set<UUID> hit = new HashSet<>();
        Magic.snd(c.level, center, SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.7f);
        EffectManager.run(c.level, 10, age -> {
            double r = 1 + (max - 1) * (age + 1) / 10.0;
            Magic.ring(c.level, ParticleTypes.SNOWFLAKE, center, r, 56, 0.15);
            Magic.ring(c.level, ICE, center, r, 28, 0.1);
            for (LivingEntity e : Magic.enemies(c, center.add(0, 0.5, 0), r + 0.8)) {
                if (!hit.add(e.getUUID())) continue;
                Magic.hurt(c, e, 3f);
                chill(e, 100);
            }
            if (ArcanaConfig.BLOCK_CHANGES.get()) {
                for (int i = 0; i < 14; i++) {
                    double a = c.level.random.nextDouble() * Math.PI * 2;
                    BlockPos bp = BlockPos.containing(center.x + Math.cos(a) * r, center.y - 1, center.z + Math.sin(a) * r);
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos q = bp.offset(0, dy, 0);
                        if (c.level.getFluidState(q).getType() == Fluids.WATER && c.level.getBlockState(q).is(Blocks.WATER) && c.level.getBlockState(q.above()).isAir()) {
                            c.level.setBlockAndUpdate(q, Blocks.FROSTED_ICE.defaultBlockState());
                            break;
                        }
                    }
                }
            }
            return true;
        }, null);
        return true;
    }

    /** Encases the targeted enemy in ice; it is immobile until the prison shatters for heavy damage. */
    public static boolean frozenPrison(SpellContext c) {
        LivingEntity t = Magic.aimEntity(c, 22);
        if (t == null) return false;
        int duration = 80 + c.stage * 20;
        Magic.snd(c.level, t.position(), SoundEvents.GLASS_PLACE, 1f, 0.5f);
        EffectManager.run(c.level, duration, age -> {
            if (!t.isAlive()) return false;
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            t.hurtMarked = true;
            t.setTicksFrozen(t.getTicksRequiredToFreeze() + 20);
            Magic.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 5, 9);
            Magic.effect(t, MobEffects.WEAKNESS, 5, 1);
            for (int h = 0; h < 3; h++) Magic.ring(c.level, ICE, t.position(), t.getBbWidth() * 0.9 + 0.3, 8, 0.1 + h * (t.getBbHeight() / 2.2));
            if (age % 6 == 0) Magic.fx(c.level, ParticleTypes.SNOWFLAKE, t.position().add(0, t.getBbHeight() / 2, 0), 6, 0.4, 0.6, 0.4, 0.02);
            return true;
        }, () -> {
            Vec3 at = t.position().add(0, t.getBbHeight() / 2, 0);
            Magic.fx(c.level, BLUE_ICE, at, 60, 0.5, t.getBbHeight() / 2, 0.5, 0.25);
            Magic.snd(c.level, at, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
            if (t.isAlive()) Magic.hurt(c, t, 8f);
        });
        return true;
    }

    /** Ultimate: a raging blizzard that grinds enemies down and flings them around its eye. */
    public static boolean blizzard(SpellContext c) {
        Vec3 center = Magic.aimFar(c, 26, 17);
        double r = 10 * c.radius;
        Magic.snd(c.level, center, SoundEvents.WIND_CHARGE_BURST.value(), 1.2f, 0.5f);
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.ICE_SPIRE, center, 5.5f * c.radius, 200, dev.arcana.entity.SpellFxEntity.EASE);
        EffectManager.run(c.level, 200, age -> {
            if (!c.valid()) return false;
            for (int i = 0; i < Magic.count(10); i++) {
                double a = c.level.random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(c.level.random.nextDouble()) * r;
                c.level.sendParticles(ParticleTypes.SNOWFLAKE, center.x + Math.cos(a) * d, center.y + 1 + c.level.random.nextDouble() * 5, center.z + Math.sin(a) * d, 0,
                    -Math.sin(a) * 0.9, -0.05, Math.cos(a) * 0.9, 1);
            }
            Magic.ring(c.level, ParticleTypes.CLOUD, center, r, 32, 0.4 + (age % 10) * 0.4);
            for (LivingEntity e : Magic.enemies(c, center.add(0, 2, 0), r)) {
                Vec3 off = e.position().subtract(center);
                Vec3 tangent = new Vec3(-off.z, 0, off.x).normalize().scale(0.14);
                e.setDeltaMovement(e.getDeltaMovement().add(tangent));
                e.hurtMarked = true;
                if (age % 8 == 0) {
                    Magic.hurt(c, e, 1.6f);
                    chill(e, 60);
                }
            }
            if (age % 25 == 0) Magic.snd(c.level, center, SoundEvents.POWDER_SNOW_STEP, 1f, 0.5f);
            return true;
        }, null);
        return true;
    }

    private GlacierSpells() {}
}
