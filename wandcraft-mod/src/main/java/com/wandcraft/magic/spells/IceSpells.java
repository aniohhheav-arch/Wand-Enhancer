package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.ELEMENTAL;

import com.wandcraft.WandCraft;
import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ice spells. */
final class IceSpells {
    static final int ICE = 0x9FE4FF;

    private IceSpells() {}

    static void register() {
        Spells.add(Spell.of("frost_shard", "Frost Shard", ELEMENTAL, CHARGE).icon(Glyph.SPIKE).cost(9).cd(14).charge(25).level(0).echoable()
                .desc("A sharp shard of ice that slows its target and shatters into fragments. Charging adds damage and range.")
                .projectile(new ProjBehavior() {
                    @Override public void trail(SpellProjectile p) {
                        Fx.c(p.level(), ParticleTypes.SNOWFLAKE, p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                        Fx.c(p.level(), Fx.dust(ICE, 1.0f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) {
                        ServerLevel l = (ServerLevel) p.level();
                        Combat.hurt(l, p.getOwner(), p, t, p.damage);
                        Combat.slow(t, 60, 1);
                        int hits = 1;
                        for (LivingEntity e : Combat.enemiesIn(l, p.position(), 2.5, p.getOwner())) {
                            if (e == t) continue;
                            Combat.hurt(l, p.getOwner(), p, e, p.damage * 0.35f);
                            Combat.slow(e, 40, 0);
                            hits++;
                        }
                        p.credit(hits);
                        shatter(l, p.position());
                        return true;
                    }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) { shatter((ServerLevel) p.level(), r.getLocation()); return true; }
                    private void shatter(ServerLevel l, Vec3 at) {
                        Fx.burst(l, ParticleTypes.SNOWFLAKE, at, 20, 0.3, 0.15);
                        Fx.burst(l, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.ICE.defaultBlockState()), at, 12, 0.2, 0.15);
                        Fx.sound(l, at, SoundEvents.GLASS_BREAK, 0.6f, 1.5f);
                    }
                })
                .onCast(c -> {
                    SpellProjectile p = Proj.launch(c, c.spell, 2.0, 0, c.dmg(4 + 2 * c.charge), (int) (30 + 30 * c.charge));
                    if (p == null) return -1;
                    Fx.sound(c.level, c.eye(), SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 1.6f);
                    return 0;
                }));

        Spells.add(Spell.of("glacial_prison", "Glacial Prison", ELEMENTAL, INSTANT).icon(Glyph.WALL).cost(28).cd(220).level(7)
                .desc("Encase one enemy in a shell of ice. It stays visible and can still be hurt. Bosses barely notice.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 28);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    int ticks = Combat.isBoss(t) ? 0 : 80;
                    Combat.immobilize(t, Math.max(20, ticks));
                    Fx.burst(c.level, ParticleTypes.SNOWFLAKE, t.position().add(0, t.getBbHeight() / 2, 0), 30, 0.5, 0.1);
                    Fx.sound(c.level, t.position(), SoundEvents.GLASS_PLACE, 1f, 0.6f);
                    if (ticks > 0 && !Combat.isHeavy(t)) {
                        AABB bb = t.getBoundingBox();
                        int placedCount = 0;
                        for (int x = (int) Math.floor(bb.minX) - 1; x <= (int) Math.floor(bb.maxX) + 1; x++)
                            for (int y = (int) Math.floor(bb.minY) - 1; y <= (int) Math.floor(bb.maxY) + 1; y++)
                                for (int z = (int) Math.floor(bb.minZ) - 1; z <= (int) Math.floor(bb.maxZ) + 1; z++) {
                                    AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
                                    if (cell.intersects(bb) || !cell.inflate(0.99).intersects(bb)) continue;
                                    if (TempBlocks.place(c.level, new BlockPos(x, y, z), WandCraft.ICE_CONSTRUCT.get().defaultBlockState(), ticks, 2, c.player)) placedCount++;
                                }
                    }
                    return 1;
                }));

        Spells.add(Spell.of("blizzard", "Blizzard", ELEMENTAL, INSTANT).icon(Glyph.WAVE).cost(35).cd(400).level(9)
                .desc("A snowstorm surrounds you for 10 seconds, slowing and half-blinding enemies and pelting them with ice.")
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        var owner = z.owner();
                        if (owner == null) return;
                        owner.setTicksFrozen(0);
                        if (z.age % 10 != 0) return;
                        List<LivingEntity> foes = Combat.enemiesIn(l, z.position(), z.radius(), owner);
                        int hits = 0;
                        for (LivingEntity e : foes) {
                            Combat.slow(e, 30, 1);
                            if (e instanceof Mob) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
                        }
                        if (z.age % 20 == 0 && !foes.isEmpty()) {
                            for (int i = 0; i < Math.min(3, foes.size()); i++) {
                                LivingEntity e = foes.get(l.random.nextInt(foes.size()));
                                Vec3 top = e.position().add(0, e.getBbHeight() + 3, 0);
                                Fx.line(l, ParticleTypes.SNOWFLAKE, top, e.position().add(0, e.getBbHeight() / 2, 0), 0.5);
                                Combat.hurt(l, owner, owner, e, 2.5f * z.power);
                                Fx.sound(l, e.position(), SoundEvents.GLASS_BREAK, 0.3f, 1.8f);
                                hits++;
                            }
                        }
                        z.credit(hits);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        for (int i = 0; i < 6; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), ParticleTypes.SNOWFLAKE, z.getX() + Math.cos(a) * r, z.getY() + rnd.nextDouble() * 5, z.getZ() + Math.sin(a) * r, -0.15, -0.05, 0.1);
                        }
                        if (z.age % 4 == 0) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = z.radius() * 0.9;
                            Fx.c(z.level(), ParticleTypes.CLOUD, z.getX() + Math.cos(a) * r, z.getY() + 1 + rnd.nextDouble() * 2, z.getZ() + Math.sin(a) * r, -0.1, 0, 0.05);
                        }
                    }
                })
                .onCast(c -> {
                    SpellZone z = Proj.zone(c, c.spell, c.player.position(), 8f, 200);
                    if (z == null) return -1;
                    z.followOwner = true;
                    Fx.sound(c.level, c.player.position(), SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("ice_bridge", "Ice Bridge", ELEMENTAL, INSTANT).icon(Glyph.WALL).cost(22).cd(160).level(3)
                .desc("Lay a translucent bridge of ice ahead, sloping with your gaze. It cracks before it melts away.")
                .onCast(c -> {
                    Vec3 look = c.look();
                    Vec3 dir = new Vec3(look.x, 0, look.z);
                    if (dir.lengthSqr() < 1e-3) dir = Vec3.directionFromRotation(0, c.player.getYRot());
                    final Vec3 d = dir.normalize();
                    final Vec3 side = new Vec3(-d.z, 0, d.x);
                    double slope = c.player.getXRot() < -20 ? 0.5 : c.player.getXRot() > 35 ? -0.5 : 0;
                    BlockPos base = c.player.blockPosition().below();
                    int len = 14;
                    int[] ok = {0};
                    for (int i = 1; i <= len; i++) {
                        final int step = i;
                        int dy = (int) Math.round(i * slope);
                        Vec3 centre = Vec3.atCenterOf(base).add(d.scale(i)).add(0, dy, 0);
                        Scheduler.after(c.level, i / 2, () -> {
                            for (int w = -1; w <= 1; w++) {
                                BlockPos bp = BlockPos.containing(centre.add(side.scale(w)));
                                if (TempBlocks.place(c.level, bp, WandCraft.ICE_CONSTRUCT.get().defaultBlockState(), 200, 2, c.player)) {
                                    Fx.burst(c.level, ParticleTypes.SNOWFLAKE, Vec3.atCenterOf(bp), 3, 0.3, 0.02);
                                    ok[0]++;
                                }
                            }
                            if (step % 3 == 0) Fx.sound(c.level, centre, SoundEvents.GLASS_PLACE, 0.4f, 1.4f);
                        });
                    }
                    Fx.sound(c.level, c.player.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 1.2f);
                    return 0;
                }));
    }
}
