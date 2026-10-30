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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Verdant: roots, healing barriers, blooming growth and nature guardians. */
public final class VerdantSpells {
    private static final BlockParticleOption LEAVES = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState());
    private static final BlockParticleOption LOG = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());

    /** A wave of roots erupts along the ground, damaging and rooting everything it passes. Awakened wands send a fan. */
    public static boolean rootSnare(SpellContext c) {
        Vec3 origin = c.player.position();
        Vec3 fwd = Magic.flatLook(c.player);
        int lines = c.scale(1, 3, 5);
        double length = 9 + 7 * c.charge;
        int rootTicks = 50 + (int) (c.charge * 80);
        Set<UUID> hit = new HashSet<>();
        Magic.snd(c.level, origin, SoundEvents.ROOTS_BREAK, 1f, 0.7f);
        int steps = (int) (length / 1.3);
        EffectManager.run(c.level, steps, age -> {
            double d = 1.6 + age * 1.3;
            for (int l = 0; l < lines; l++) {
                double ang = (l - (lines - 1) / 2.0) * Math.toRadians(16);
                Vec3 p = origin.add(Magic.rotateY(fwd, ang).scale(d));
                double y = Magic.groundY(c.level, p.x, origin.y, p.z);
                Vec3 base = new Vec3(p.x, y, p.z);
                for (int h = 0; h < 4; h++) {
                    Vec3 q = base.add(Math.sin(age + h) * 0.15, h * 0.55, Math.cos(age + h) * 0.15);
                    c.level.sendParticles(LOG, q.x, q.y, q.z, Magic.count(3), 0.15, 0.1, 0.15, 0.02);
                }
                Magic.fx(c.level, LEAVES, base.add(0, 1.2, 0), 6, 0.3, 0.3, 0.3, 0.05);
                Magic.fx(c.level, ParticleTypes.COMPOSTER, base.add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0);
                for (LivingEntity e : Magic.enemies(c, base.add(0, 1, 0), 1.7)) {
                    if (!hit.add(e.getUUID())) continue;
                    Magic.hurt(c, e, 3.5f);
                    Magic.root(e, rootTicks);
                }
            }
            return true;
        }, null);
        return true;
    }

    /** A thorned, healing ring that follows the caster: heals allies, repels and pricks enemies. */
    public static boolean thornBarrier(SpellContext c) {
        int duration = 160 + c.stage * 40;
        double r = 4.2 * c.radius;
        Magic.snd(c.level, c.player.position(), SoundEvents.AZALEA_LEAVES_PLACE, 1f, 0.6f);
        EffectManager.run(c.level, duration, age -> {
            if (!c.valid()) return false;
            Vec3 center = c.player.position();
            int pts = Magic.count(24);
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2 * i / pts + age * 0.08;
                double h = 0.3 + (Math.sin(age * 0.2 + i) + 1) * 0.5;
                c.level.sendParticles(i % 3 == 0 ? ParticleTypes.COMPOSTER : LEAVES, center.x + Math.cos(a) * r, center.y + h, center.z + Math.sin(a) * r, 1, 0.05, 0.2, 0.05, 0);
            }
            if (age % 10 == 0) {
                for (LivingEntity a : Magic.allies(c, center, r)) {
                    a.heal(1f + c.stage);
                    Magic.fx(c.level, ParticleTypes.HAPPY_VILLAGER, a.position().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
                }
                for (LivingEntity e : Magic.enemies(c, center, r)) {
                    Vec3 out = e.position().subtract(center).multiply(1, 0, 1).normalize().scale(0.55);
                    Magic.push(e, new Vec3(out.x, 0.2, out.z));
                    Magic.hurt(c, e, 1.5f);
                }
            }
            if (c.stage >= 1 && age % 40 == 0) c.player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, c.stage - 1, false, false));
            return true;
        }, null);
        return true;
    }

    /** A spore cloud at the aimed spot: heals allies, poisons enemies and accelerates crop growth. */
    public static boolean sporeBloom(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 20);
        double r = 5 * c.radius;
        Magic.snd(c.level, center, SoundEvents.SPORE_BLOSSOM_PLACE, 1f, 0.7f);
        EffectManager.run(c.level, 140, age -> {
            Magic.fx(c.level, ParticleTypes.SPORE_BLOSSOM_AIR, center.add(0, 1.5, 0), 10, r * 0.5, 1.2, r * 0.5, 0);
            Magic.fx(c.level, ParticleTypes.FALLING_SPORE_BLOSSOM, center.add(0, 3, 0), 4, r * 0.5, 0.2, r * 0.5, 0);
            Magic.ring(c.level, ParticleTypes.HAPPY_VILLAGER, center, r, 24, 0.2);
            if (age % 10 == 0) {
                for (LivingEntity a : Magic.allies(c, center, r)) a.heal(2f + c.stage);
                for (LivingEntity e : Magic.enemies(c, center, r)) {
                    Magic.effect(e, MobEffects.POISON, 50, 0);
                    Magic.hurt(c, e, 1f);
                }
            }
            if (age % 30 == 0 && ArcanaConfig.BLOCK_CHANGES.get()) {
                for (int i = 0; i < 6; i++) {
                    BlockPos bp = BlockPos.containing(center.x + (c.level.random.nextDouble() - 0.5) * r * 2, center.y, center.z + (c.level.random.nextDouble() - 0.5) * r * 2);
                    for (int dy = 2; dy >= -2; dy--) {
                        if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), c.level, bp.above(dy))) break;
                    }
                }
            }
            return true;
        }, null);
        return true;
    }

    /** Ultimate: summon iron-golem guardians around the caster and root every nearby enemy. */
    public static boolean groveGuardians(SpellContext c) {
        int count = 2 + c.stage;
        Vec3 pos = c.player.position();
        java.util.List<IronGolem> golems = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Vec3 p = pos.add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
            IronGolem g = EntityType.IRON_GOLEM.create(c.level);
            if (g == null) continue;
            g.moveTo(p.x, p.y, p.z, c.level.random.nextFloat() * 360f, 0);
            g.setPlayerCreated(true);
            g.setPersistenceRequired();
            g.getPersistentData().putBoolean("arcana_summon", true);
            g.setCustomName(Component.translatable("entity.arcana.grove_guardian"));
            g.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1000, 1, false, false));
            g.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1000, 0, false, false));
            g.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000, 0, false, false));
            EffectManager.SUMMONS.add(g.getUUID());
            if (c.level.addFreshEntity(g)) golems.add(g);
        }
        for (LivingEntity e : Magic.enemies(c, pos, 10 * c.radius)) {
            Magic.root(e, 80);
            Magic.hurt(c, e, 4f);
        }
        Magic.snd(c.level, pos, SoundEvents.RAVAGER_ROAR, 1f, 0.6f);
        Vec3 treeAt = pos.add(Magic.flatLook(c.player).scale(7));
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.WORLD_TREE, new Vec3(treeAt.x, Magic.groundY(c.level, treeAt.x, pos.y, treeAt.z), treeAt.z), 4.5f * c.radius, 140, dev.arcana.entity.SpellFxEntity.GROW);
        EffectManager.run(c.level, 900, age -> {
            golems.removeIf(g -> {
                if (g.isAlive()) return false;
                EffectManager.SUMMONS.remove(g.getUUID());
                return true;
            });
            for (IronGolem g : golems) if (age % 4 == 0) Magic.fx(c.level, LEAVES, g.position().add(0, 2, 0), 2, 0.5, 0.6, 0.5, 0.02);
            if (age == 0) {
                for (int i = 0; i < 4; i++) Magic.ring(c.level, ParticleTypes.HAPPY_VILLAGER, pos, 2 + i * 2.2, 36, 0.2);
            }
            return !golems.isEmpty();
        }, () -> {
            for (IronGolem g : golems) {
                if (g.isAlive()) {
                    Magic.fx(c.level, ParticleTypes.POOF, g.position().add(0, 1.5, 0), 20, 0.5, 1, 0.5, 0.05);
                    EffectManager.SUMMONS.remove(g.getUUID());
                    g.discard();
                }
            }
        });
        return !golems.isEmpty();
    }

    private VerdantSpells() {}
}
