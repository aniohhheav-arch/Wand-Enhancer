package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.NATURE;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.ProjBehavior;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import com.wandcraft.magic.util.Scheduler;
import com.wandcraft.magic.util.TempBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Nature Magic: vines, roots, flowers and healing. */
final class NatureSpells {
    static final int LEAF = 0x57C84D;

    private NatureSpells() {}

    static ParticleOptions leaves() { return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()); }

    /** A curved vine of glowing leaf particles from {@code a} to {@code b}. */
    static void vine(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 n = d.scale(1 / len);
        Vec3 side = n.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        int steps = Math.max(6, (int) (len * 2.5));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double wave = Math.sin(t * Math.PI * 3) * 0.25 * Math.sin(t * Math.PI);
            Vec3 p = a.add(d.scale(t)).add(side.scale(wave)).add(0, Math.sin(t * Math.PI) * 0.2, 0);
            l.sendParticles(Fx.glow(LEAF, 0.9f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 2 == 0) l.sendParticles(leaves(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    static ProjBehavior thorn() {
        return new ProjBehavior() {
            @Override public void trail(SpellProjectile p) {
                Fx.c(p.level(), Fx.glow(0x8AE05A, 0.6f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
            }
            @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) {
                ServerLevel l = (ServerLevel) p.level();
                Combat.hurt(l, p.getOwner(), p, t, p.damage);
                p.credit(1);
                Fx.burst(l, leaves(), p.position(), 5, 0.15, 0.05);
                Fx.sound(l, p.position(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 0.5f, 1.6f);
                return true;
            }
            @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) {
                Fx.burst((ServerLevel) p.level(), leaves(), r.getLocation(), 4, 0.1, 0.03);
                return true;
            }
        };
    }

    static void spreadThorns(com.wandcraft.magic.CastCtx c, Spell thornSpell, Vec3 from, Vec3 dir, int n, double spread, float dmg, double speed, int life) {
        for (int i = 0; i < n; i++) Proj.launch(c, thornSpell, from, dir, speed, spread, dmg, life);
    }

    static void register() {
        Spells.add(Spell.of("thorn_volley", "Thorn Volley", NATURE, INSTANT).icon(Glyph.SPIKE).cost(14).cd(50).level(1).echoable()
                .desc("Fire a fan of thorns. Modest damage each, best at close to medium range against groups.")
                .projectile(thorn())
                .onCast(c -> {
                    Fx.sound(c.level, c.eye(), SoundEvents.SWEET_BERRY_BUSH_BREAK, 0.8f, 1.4f);
                    Vec3 from = c.eye().add(c.look().scale(0.6)).add(0, -0.15, 0);
                    spreadThorns(c, c.spell, from, c.look(), 7, 9, c.dmg(2), 1.7, 14);
                    return 0;
                }));

        Spells.add(Spell.of("vine_lash", "Vine Lash", NATURE, INSTANT).icon(Glyph.LEAF).cost(8).cd(22).level(0)
                .desc("Whip a long vine at a foe: damage and a brief slow. The vine snaps back at once.")
                .perks("Faster recovery.", "Unlocks Thorn Lash.")
                .onCast(c -> {
                    Aim.Result r = Aim.trace(c.player, 11);
                    Vec3 from = c.eye().add(c.look().scale(0.6)).add(0, -0.25, 0);
                    vine(c.level, from, r.pos());
                    Fx.sound(c.level, from, SoundEvents.SWEET_BERRY_BUSH_PLACE, 1f, 1.4f);
                    if (!r.hitEntity()) return 0;
                    Combat.hurt(c.level, c.player, c.player, r.entity(), c.dmg(4));
                    Combat.slow(r.entity(), 50, 1);
                    Fx.burst(c.level, leaves(), r.pos(), 8, 0.2, 0.05);
                    return 1;
                }));

        Spells.add(Spell.of("thorn_lash", "Thorn Lash", NATURE, INSTANT).icon(Glyph.LEAF).cost(12).cd(30).evolvesFrom("vine_lash")
                .desc("A vine strike that bursts into a spray of thorns on impact.")
                .projectile(thorn())
                .onCast(c -> {
                    Aim.Result r = Aim.trace(c.player, 12);
                    Vec3 from = c.eye().add(c.look().scale(0.6)).add(0, -0.25, 0);
                    vine(c.level, from, r.pos());
                    Fx.sound(c.level, from, SoundEvents.SWEET_BERRY_BUSH_PLACE, 1f, 1.0f);
                    if (!r.hitEntity()) return 0;
                    LivingEntity t = r.entity();
                    Combat.hurt(c.level, c.player, c.player, t, c.dmg(5));
                    Combat.slow(t, 50, 1);
                    Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
                    spreadThorns(c, c.spell, at, c.look(), 5, 25, c.dmg(2), 1.4, 10);
                    Fx.burst(c.level, leaves(), at, 14, 0.3, 0.1);
                    return 1;
                }));

        Spells.add(Spell.of("root_snare", "Root Snare", NATURE, INSTANT).icon(Glyph.WAVE).cost(18).cd(140).level(3)
                .desc("Roots surface where you aim, showing a warning, then clamp nearby enemies in place for a few seconds.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        Vec3 c = z.position();
                        if (z.age < 14) {
                            if (z.age % 4 == 0) {
                                Fx.rune(l, c.add(0, 0.1, 0), z.radius(), LEAF, 16, 0);
                                Fx.burst(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), c, 6, z.radius() * 0.5, 0.05);
                            }
                            return;
                        }
                        if (z.age == 14) {
                            Fx.sound(l, c, SoundEvents.ROOTED_DIRT_BREAK, 1f, 0.7f);
                            for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), owner)) {
                                Combat.immobilize(e, 60);
                                z.data[0]++;
                                Fx.burst(l, Fx.wisp(0x6B4A2A, 1f), e.position().add(0, 0.5, 0), 10, 0.3, 0.03);
                            }
                            z.credit(z.data[0]);
                        }
                        // Roots hold feet in place for the first few seconds.
                        if (z.age < 14 + 50) {
                            for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), owner)) {
                                if (Combat.isBoss(e)) continue;
                                if (e.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                                    Vec3 v = e.getDeltaMovement();
                                    e.setDeltaMovement(0, Math.min(0, v.y), 0);
                                    e.hurtMarked = true;
                                }
                            }
                            if (z.age % 6 == 0) Fx.burst(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.JUNGLE_WOOD.defaultBlockState()), c.add(0, 0.5, 0), 6, z.radius() * 0.5, 0.05);
                        }
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        if (z.age >= 14 && z.age < 64) {
                            for (int i = 0; i < 3; i++) {
                                double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * z.radius();
                                double h = rnd.nextDouble() * 1.4;
                                Fx.c(z.level(), Fx.glow(0x8A5A2A, 0.9f), z.getX() + Math.cos(a) * r, z.getY() + h, z.getZ() + Math.sin(a) * r, 0, 0.01, 0);
                            }
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 20);
                    if (g == null) return -1;
                    if (Proj.zone(c, c.spell, g, 3f, 80) == null) return -1;
                    Fx.sound(c.level, g, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("healing_bloom", "Healing Bloom", NATURE, INSTANT).icon(Glyph.LEAF).cost(24).cd(400).level(2)
                .desc("A glowing flower blossoms at your feet, healing you and allies near it over eight seconds. Step too far away and it fades.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        if (owner == null || owner.position().distanceToSqr(z.position()) > 36) {
                            z.discard();
                            return;
                        }
                        if (z.age % 20 != 10) return;
                        int healed = 0;
                        for (LivingEntity e : Kit.friendsIn(l, z.position(), z.radius(), owner)) {
                            if (e.getHealth() < e.getMaxHealth()) {
                                e.heal(1.0f * z.power);
                                healed++;
                                Fx.burst(l, Fx.glow(0xFF8AD0, 0.9f), e.position().add(0, 1, 0), 6, 0.3, 0.1);
                            }
                        }
                        if (healed > 0) z.credit(1);
                        Fx.sound(l, z.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.8f);
                    }
                    @Override public void end(SpellZone z) {
                        Fx.burst((ServerLevel) z.level(), leaves(), z.position().add(0, 0.5, 0), 12, 0.4, 0.05);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        for (int i = 0; i < 2; i++) {
                            double a = z.age * 0.15 + i * Math.PI;
                            Fx.c(z.level(), Fx.glow(i == 0 ? 0xFF8AD0 : 0xFFE08A, 0.8f), z.getX() + Math.cos(a) * 0.7, z.getY() + 0.3 + (z.age % 20) * 0.04, z.getZ() + Math.sin(a) * 0.7, 0, 0.01, 0);
                        }
                        if (rnd.nextInt(3) == 0) Fx.c(z.level(), Fx.wisp(0xFFB8E8, 0.7f), z.getX(), z.getY() + 0.5, z.getZ(), 0, 0.03, 0);
                    }
                })
                .onCast(c -> {
                    for (SpellZone old : Kit.zonesOf(c.player, c.spell.id)) old.discard();
                    Vec3 at = c.player.position();
                    SpellZone z = Proj.zone(c, c.spell, at, 2.8f, 160);
                    if (z == null) return -1;
                    BlockPos bp = c.player.blockPosition();
                    TempBlocks.place(c.level, bp, Blocks.PINK_TULIP.defaultBlockState(), 160, 1, c.player);
                    Fx.rune(c.level, at.add(0, 0.1, 0), 2.8, 0xFF8AD0, 24, 0);
                    Fx.burst(c.level, leaves(), at.add(0, 0.4, 0), 14, 0.5, 0.05);
                    Fx.sound(c.level, at, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 1.2f);
                    return 0;
                }));

        Spells.add(Spell.of("guardian_vine", "Guardian Vine", NATURE, INSTANT).icon(Glyph.LEAF).cost(28).cd(300).level(5)
                .desc("Grow a vine sentinel that slowly lashes nearby enemies. Fragile and short-lived; only one at a time.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        Vec3 head = z.position().add(0, 2.2, 0);
                        // Enemies chewing on the vine wear it down.
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), 1.8, owner)) {
                            if (z.age % 20 == 0) z.data[1] += 4;
                        }
                        if (z.data[1] >= 40) {
                            Fx.burst(l, leaves(), head, 20, 0.5, 0.1);
                            Fx.sound(l, head, SoundEvents.AZALEA_LEAVES_BREAK, 1f, 0.8f);
                            z.discard();
                            return;
                        }
                        if (z.age % 35 != 20) return;
                        LivingEntity target = null;
                        double bd = 49;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), 7, owner)) {
                            double d = e.distanceToSqr(z.position());
                            if (d < bd) { bd = d; target = e; }
                        }
                        if (target == null) return;
                        vine(l, head, target.position().add(0, target.getBbHeight() / 2, 0));
                        Combat.hurt(l, owner, owner, target, 5 * z.power);
                        Combat.slow(target, 30, 0);
                        Fx.sound(l, head, SoundEvents.SWEET_BERRY_BUSH_PLACE, 1f, 0.9f);
                        z.credit(1);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        double sway = Math.sin(z.age * 0.1) * 0.15;
                        for (int i = 0; i < 4; i++) {
                            double h = rnd.nextDouble() * 2.2;
                            double a = h * 3 + z.age * 0.05;
                            Fx.c(z.level(), Fx.glow(LEAF, 0.9f), z.getX() + Math.cos(a) * 0.25 + sway * h * 0.3, z.getY() + h, z.getZ() + Math.sin(a) * 0.25, 0, 0, 0);
                        }
                        if (z.age % 6 == 0) Fx.c(z.level(), leaves(), z.getX(), z.getY() + 2.3, z.getZ(), 0, -0.02, 0);
                        Fx.c(z.level(), Fx.glow(0xFFE08A, 1.2f), z.getX() + sway * 0.6, z.getY() + 2.3, z.getZ(), 0, 0, 0);
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 8);
                    if (g == null) return -1;
                    for (SpellZone old : Kit.zonesOf(c.player, c.spell.id)) old.discard();
                    if (Proj.zone(c, c.spell, g, 1f, 400) == null) return -1;
                    Fx.burst(c.level, leaves(), g.add(0, 0.5, 0), 20, 0.3, 0.2);
                    Fx.sound(c.level, g, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("pollen_veil", "Pollen Veil", NATURE, INSTANT).icon(Glyph.WAVE).cost(18).cd(200).level(4)
                .desc("A cloud of pollen drifts over an area. Ordinary hostile mobs inside lose sight and fumble their aim. Bosses resist.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 10 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position().add(0, 1, 0), z.radius(), z.owner())) {
                            if (Combat.isBoss(e)) continue;
                            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
                            if (e instanceof Mob m && l.random.nextInt(3) == 0) m.setTarget(null);
                            n++;
                        }
                        if (z.age % 20 == 0) z.credit(Math.min(n, 2));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        for (int i = 0; i < 5; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), Fx.wisp(0xE8D95A, 1.6f), z.getX() + Math.cos(a) * r, z.getY() + 0.3 + rnd.nextDouble() * 2.4, z.getZ() + Math.sin(a) * r, 0, 0.01, 0);
                        }
                        Fx.c(z.level(), Fx.glow(0xFFF3A0, 0.6f), z.getX() + (rnd.nextDouble() - .5) * z.radius() * 2, z.getY() + rnd.nextDouble() * 2, z.getZ() + (rnd.nextDouble() - .5) * z.radius() * 2, 0, 0, 0);
                    }
                })
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 20).pos();
                    if (Proj.zone(c, c.spell, at, 4f, 120) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 0.4f);
                    return 0;
                }));

        Spells.add(Spell.of("natures_step", "Nature's Step", NATURE, INSTANT).icon(Glyph.WINGS).cost(14).cd(100).level(2)
                .desc("Dissolve into leaves and vines and reappear up to eight blocks ahead. Cannot pass walls.")
                .onCast(c -> {
                    Vec3 dest = Kit.dash(c, 8);
                    if (dest == null) {
                        Casting.fail(c.player, "Path blocked");
                        return -1;
                    }
                    Vec3 from = c.player.position();
                    Fx.burst(c.level, leaves(), from.add(0, 1, 0), 30, 0.5, 0.2);
                    Fx.burst(c.level, Fx.glow(LEAF, 1.2f), from.add(0, 1, 0), 15, 0.4, 0.05);
                    Fx.line(c.level, Fx.glow(LEAF, 0.8f), from.add(0, 1, 0), dest.add(0, 1, 0), 0.35);
                    c.player.teleportTo(dest.x, dest.y, dest.z);
                    c.player.fallDistance = 0;
                    Fx.burst(c.level, leaves(), dest.add(0, 1, 0), 30, 0.5, 0.2);
                    Fx.shockwave(c.level, Fx.glow(LEAF, 0.9f), dest.add(0, 0.2, 0), 2f, 3);
                    Fx.sound(c.level, from, SoundEvents.AZALEA_LEAVES_BREAK, 1f, 1.2f);
                    Fx.sound(c.level, dest, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 1.2f);
                    return 0;
                }));

        Spells.add(Spell.of("blooming_ground", "Blooming Ground", NATURE, INSTANT).icon(Glyph.LEAF).cost(22).cd(300).level(6)
                .desc("Flowers sprout on a patch of ground, glowing softly and giving allies standing there regeneration.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 20 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Kit.friendsIn(l, z.position(), z.radius(), z.owner())) {
                            e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, false, false, true));
                            if (e.getHealth() < e.getMaxHealth()) n++;
                        }
                        z.credit(Math.min(n, 2));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                        Fx.c(z.level(), Fx.glow(rnd.nextBoolean() ? 0xFFE08A : 0xFF9ADB, 0.8f), z.getX() + Math.cos(a) * r, z.getY() + 0.3, z.getZ() + Math.sin(a) * r, 0, 0.03, 0);
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 14);
                    if (g == null) return -1;
                    SpellZone z = Proj.zone(c, c.spell, g, 4f, 200);
                    if (z == null) return -1;
                    Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.PINK_TULIP, Blocks.ALLIUM};
                    int placed = 0;
                    for (int i = 0; i < 22; i++) {
                        double a = c.level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(c.level.random.nextDouble()) * 4;
                        int idx = i;
                        Vec3 p = g.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                        var gr = Aim.stepGround(c.level, BlockPos.containing(p).below());
                        if (gr.isEmpty()) continue;
                        BlockPos bp = gr.get();
                        if (!c.level.getBlockState(bp.below()).is(net.minecraft.tags.BlockTags.DIRT)) continue;
                        Scheduler.after(c.level, i / 2, () -> {
                            if (TempBlocks.place(c.level, bp, flowers[idx % flowers.length].defaultBlockState(), 200, 1, c.player)) {
                                Fx.burst(c.level, Fx.glow(0xFFE08A, 0.8f), Vec3.atCenterOf(bp), 4, 0.2, 0.03);
                            }
                        });
                        placed++;
                    }
                    BlockPos lightPos = BlockPos.containing(g).above();
                    TempBlocks.place(c.level, lightPos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9), 200, 1, c.player);
                    Fx.rune(c.level, g.add(0, 0.1, 0), 4, 0xFF9ADB, 26, 0);
                    Fx.sound(c.level, g, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 1.4f);
                    return 0;
                }));
    }
}
