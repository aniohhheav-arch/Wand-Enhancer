package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.ELEMENTAL;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fire spells. */
final class FireSpells {
    static final int FIRE = 0xFF7A1A;

    private FireSpells() {}

    static void register() {
        Spells.add(Spell.of("firebolt", "Firebolt", ELEMENTAL, CHARGE).icon(Glyph.FLAME).cost(10).cd(14).charge(20).level(0).echoable()
                .desc("Hurl a fireball. Charging makes it faster with a larger blast. Briefly scorches the ground.")
                .perks("Stronger blast, cheaper.", "Unlocks Flame Lance.")
                .projectile(new ProjBehavior() {
                    @Override public void trail(SpellProjectile p) {
                        Fx.c(p.level(), ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 0, 0.01, 0);
                        Fx.c(p.level(), Fx.dust(FIRE, 1.2f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                        if (p.age % 2 == 0) Fx.c(p.level(), ParticleTypes.SMOKE, p.getX(), p.getY(), p.getZ(), 0, 0.02, 0);
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) { explode(p, t); return true; }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) { explode(p, null); return true; }
                    private void explode(SpellProjectile p, LivingEntity direct) {
                        ServerLevel l = (ServerLevel) p.level();
                        float r = 1.8f + p.charge * 1.3f;
                        Fx.burst(l, ParticleTypes.FLAME, p.position(), 30, r * 0.3, 0.12);
                        Fx.burst(l, ParticleTypes.LAVA, p.position(), 6, r * 0.2, 0);
                        Fx.burst(l, ParticleTypes.SMOKE, p.position(), 12, r * 0.3, 0.05);
                        Fx.sound(l, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
                        Set<LivingEntity> hit = new HashSet<>(Combat.enemiesIn(l, p.position(), r, p.getOwner()));
                        if (direct != null) hit.add(direct);
                        for (LivingEntity e : hit) {
                            Combat.hurt(l, p.getOwner(), p, e, e == direct ? p.damage : p.damage * 0.7f);
                            Combat.ignite(e, 4);
                        }
                        p.credit(hit.size());
                        Elem.scorch(l, p.position(), r * 0.8, 3, p.caster(), 60);
                    }
                })
                .onCast(c -> {
                    SpellProjectile p = Proj.launch(c, c.spell, 1.5 + 0.8 * c.charge, 0, c.dmg(5), 50);
                    if (p == null) return -1;
                    p.setSize(0.3f + c.charge * 0.2f);
                    Fx.sound(c.level, c.eye(), SoundEvents.BLAZE_SHOOT, 0.7f, 1.1f + c.charge * 0.2f);
                    return 0;
                }));

        Spells.add(Spell.of("flame_lance", "Flame Lance", ELEMENTAL, CHARGE).icon(Glyph.BEAM).cost(18).cd(40).charge(30).evolvesFrom("firebolt")
                .desc("A piercing lance of fire that burns every enemy in a line. Charging narrows it and extends its reach.")
                .onCast(c -> {
                    double reach = 14 + 14 * c.charge;
                    Vec3 from = c.eye().add(c.look().scale(0.5)).add(0, -0.2, 0);
                    Vec3 to = from.add(c.look().scale(reach));
                    BlockHitResult b = c.level.clip(new net.minecraft.world.level.ClipContext(from, to,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, c.player));
                    if (b.getType() == HitResult.Type.BLOCK) to = b.getLocation();
                    final Vec3 end = to;
                    double width = 0.55 - 0.3 * c.charge;
                    int hits = 0;
                    for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5),
                            e -> Combat.canTarget(c.player, e))) {
                        if (e.getBoundingBox().inflate(width).clip(from, end).isPresent()) {
                            Combat.hurt(c.level, c.player, c.player, e, c.dmg(7 + 3 * c.charge));
                            Combat.ignite(e, 5);
                            Fx.burst(c.level, ParticleTypes.FLAME, e.position().add(0, e.getBbHeight() / 2, 0), 12, 0.3, 0.1);
                            hits++;
                        }
                    }
                    Fx.line(c.level, Fx.dust(0xFFC040, 1.1f), from, end, 0.5);
                    Fx.line(c.level, ParticleTypes.FLAME, from, end, 0.9);
                    Fx.line(c.level, ParticleTypes.SMALL_FLAME, from, end, 1.2);
                    Fx.sound(c.level, from, SoundEvents.BLAZE_SHOOT, 0.9f, 0.7f);
                    Fx.sound(c.level, end, SoundEvents.FIRECHARGE_USE, 0.8f, 1.0f);
                    if (b.getType() == HitResult.Type.BLOCK) Elem.scorch(c.level, end, 1.2, 2, c.player, 50);
                    return hits;
                }));

        Spells.add(Spell.of("firestorm", "Firestorm", ELEMENTAL, CHARGE).prop("rune_sigil_red", 1.1f, 0.05).icon(Glyph.BURST).cost(40).cd(240).charge(60).level(8)
                .desc("Mark the ground with a rune. After a warning, fire pillars erupt in sequence. Charging widens the area.")
                .zone(new ZoneBehavior() {
                    @SuppressWarnings("unchecked")
                    @Override public void start(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        List<Vec3> pillars = new ArrayList<>();
                        float r = z.radius();
                        for (double x = -r; x <= r; x += 2.2) {
                            for (double zz = -r; zz <= r; zz += 2.2) {
                                if (x * x + zz * zz > r * r) continue;
                                BlockPos probe = BlockPos.containing(z.getX() + x, z.getY() + 1, z.getZ() + zz);
                                Aim.stepGround(l, probe.below()).ifPresent(g -> pillars.add(Vec3.atBottomCenterOf(g)));
                            }
                        }
                        Collections.shuffle(pillars, new java.util.Random(l.random.nextLong()));
                        z.extra = pillars;
                        z.setMaxAge(WARN + pillars.size() * 2 + 10);
                    }
                    @SuppressWarnings("unchecked")
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        if (z.age < WARN) {
                            if (z.age % 8 == 0) {
                                Fx.rune(l, z.position().add(0, 0.1, 0), z.radius(), FIRE, (int) (z.radius() * 10), z.age * 0.05);
                                Fx.sound(l, z.position(), SoundEvents.FIRECHARGE_USE, 0.4f, 0.5f + z.age * 0.02f);
                            }
                            return;
                        }
                        List<Vec3> pillars = (List<Vec3>) z.extra;
                        if ((z.age - WARN) % 2 != 0) return;
                        int i = (z.age - WARN) / 2;
                        if (pillars == null || i >= pillars.size()) return;
                        Vec3 at = pillars.get(i);
                        for (int h = 0; h < 5; h++) {
                            l.sendParticles(ParticleTypes.FLAME, at.x, at.y + h * 0.8, at.z, Fx.scaled(8), 0.35, 0.1, 0.35, 0.05);
                        }
                        Fx.burst(l, ParticleTypes.LAVA, at.add(0, 1, 0), 3, 0.3, 0);
                        com.wandcraft.entity.SpellProp.spawn(l, "fire_pillar", at, 0.42f, 18);
                        Fx.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 1.6f);
                        int hits = 0;
                        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at.x - 1.3, at.y, at.z - 1.3, at.x + 1.3, at.y + 4, at.z + 1.3),
                                e -> Combat.isHostile(z.owner(), e))) {
                            Combat.hurt(l, z.owner(), z.owner(), e, 6 * z.power);
                            Combat.ignite(e, 4);
                            Combat.push(e, new Vec3(0, 0.55, 0));
                            hits++;
                        }
                        z.credit(hits);
                    }
                    final int WARN = 30;
                    @Override public void fx(SpellZone z) {
                        if (z.age % 3 == 0 && z.age < 40) {
                            double a = z.level().random.nextDouble() * Math.PI * 2, r = z.radius() * Math.sqrt(z.level().random.nextDouble());
                            Fx.c(z.level(), ParticleTypes.SMALL_FLAME, z.getX() + Math.cos(a) * r, z.getY() + 0.1, z.getZ() + Math.sin(a) * r, 0, 0.05, 0);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 30);
                    if (g == null) return -1;
                    SpellZone z = Proj.zone(c, c.spell, g.add(0, 0.05, 0), 3f + 4f * c.charge, 200);
                    if (z == null) return -1;
                    Fx.sound(c.level, g, SoundEvents.BLAZE_AMBIENT, 1f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("phoenix_wings", "Phoenix Wings", ELEMENTAL, INSTANT).icon(Glyph.WINGS).cost(30).cd(400).level(6)
                .desc("Fiery wings let you glide and slow your fall for 12 seconds. Cast again while active to dash.")
                .recast(p -> Magic.of(p).wingsUntil > p.level().getGameTime(), 8)
                .onCast(c -> {
                    PlayerMagic m = Magic.of(c.player);
                    long now = c.level.getGameTime();
                    if (m.wingsUntil > now) {
                        if (now - m.lastWingDash < 25) return -1;
                        m.lastWingDash = now;
                        Vec3 d = c.look().scale(1.5).add(0, 0.15, 0);
                        c.player.setDeltaMovement(d);
                        c.player.hurtMarked = true;
                        c.player.fallDistance = 0;
                        Fx.burst(c.level, ParticleTypes.FLAME, c.player.position().add(0, 1, 0), 25, 0.5, 0.15);
                        Fx.sound(c.level, c.player.position(), SoundEvents.PHANTOM_FLAP, 1f, 0.6f);
                        return 0;
                    }
                    m.wingsUntil = now + 240;
                    m.dirty = true;
                    Fx.burst(c.level, ParticleTypes.FLAME, c.player.position().add(0, 1.2, 0), 50, 1.0, 0.1);
                    Fx.burst(c.level, ParticleTypes.LAVA, c.player.position().add(0, 1.2, 0), 8, 0.8, 0);
                    Fx.sound(c.level, c.player.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
                    Fx.sound(c.level, c.player.position(), SoundEvents.PHANTOM_FLAP, 1f, 0.7f);
                    return 0;
                }));

        Spells.add(Spell.of("magma_grasp", "Magma Grasp", ELEMENTAL, INSTANT).icon(Glyph.WALL).cost(26).cd(200).level(4)
                .desc("Raise a short line of molten stone that burns enemies and blocks the way, then cools and vanishes.")
                .onCast(c -> {
                    Vec3 dir = new Vec3(c.look().x, 0, c.look().z);
                    if (dir.lengthSqr() < 1e-3) dir = Vec3.directionFromRotation(0, c.player.getYRot());
                    final Vec3 d = dir.normalize();
                    Vec3 start = c.player.position().add(d.scale(1.5));
                    int placed = 0;
                    int[] hits = {0};
                    for (int i = 0; i < 9; i++) {
                        Vec3 p = start.add(d.scale(i));
                        var ground = Aim.stepGround(c.level, BlockPos.containing(p).below());
                        if (ground.isEmpty()) break;
                        BlockPos g = ground.get();
                        final int idx = i;
                        placed++;
                        Scheduler.after(c.level, i, () -> {
                            Vec3 centre = Vec3.atBottomCenterOf(g);
                            Fx.burst(c.level, ParticleTypes.LAVA, centre.add(0, 0.5, 0), 3, 0.3, 0);
                            Fx.burst(c.level, ParticleTypes.FLAME, centre.add(0, 0.7, 0), 8, 0.25, 0.05);
                            Fx.sound(c.level, centre, SoundEvents.LAVA_POP, 0.6f, 0.8f);
                            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(g).inflate(0.4, 0.3, 0.4),
                                    e -> Combat.canTarget(c.player, e))) {
                                Combat.hurt(c.level, c.player, c.player, e, c.dmg(4));
                                Combat.ignite(e, 3);
                                Combat.push(e, new Vec3(0, 0.6, 0));
                                Magic.credit(c.player, c.spell, 1);
                            }
                            if (Proj.pointFree(c.player, g)) TempBlocks.place(c.level, g, Blocks.MAGMA_BLOCK.defaultBlockState(), 160, 1, c.player);
                        });
                    }
                    if (placed == 0) return -1;
                    Fx.sound(c.level, start, SoundEvents.LAVA_AMBIENT, 1f, 0.6f);
                    return 0;
                }));
    }
}
