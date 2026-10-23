package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHANNEL;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.DEFENSE;

import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.CastCtx;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.DefenseEvents;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Defense Magic: timing and awareness over invincibility. */
final class DefenseSpells {
    static final int SHIELD = 0x6FC8FF;

    private DefenseSpells() {}

    private static Spell shield(String id, String name, int cost, float capacity, float reduction, double arcDeg, int rgb) {
        return Spell.of(id, name, DEFENSE, CHANNEL).propFront(arcDeg > 70 ? "shield_panel_gold" : "shield_panel", arcDeg > 70 ? 1.2f : 0.9f, 1.6, -0.9).icon(Glyph.SHIELD).cost(cost).cd(80)
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        // Kept alive by the channel; vanishes a few ticks after it stops.
                        if (z.remaining() < 1) z.discard();
                    }
                    @Override public void fx(SpellZone z) { /* the 3D prop provides the visuals */ }
                })
                .onChannel((c, t) -> {
                    PlayerMagic m = Magic.of(c.player);
                    if (t == 0) {
                        m.shieldCapacity = capacity * c.power;
                        m.shieldReduction = reduction;
                        m.shieldArc = Math.cos(Math.toRadians(arcDeg));
                        Fx.sound(c.level, c.eye(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
                    }
                    SpellZone z = c.get("zone", null);
                    if (z == null || z.isRemoved()) {
                        z = Proj.zone(c, c.spell, c.player.position(), 1f, 6);
                        if (z != null) {
                            z.followOwner = true;
                            c.state.put("zone", z);
                        }
                    } else {
                        z.setMaxAge(z.age + 6);
                    }
                    return 0;
                })
                .onEnd(c -> {
                    Magic.of(c.player).shieldCapacity = 0;
                    SpellZone z = c.get("zone", null);
                    if (z != null) z.discard();
                });
    }

    static void register() {
        Spells.add(shield("magic_shield", "Magic Shield", 6, 24f, 0.6f, 65, SHIELD).level(0)
                .desc("Hold to raise a barrier in front of you. It stops frontal projectiles and softens hits until it shatters. Drains mana.")
                .perks("Sturdier barrier.", "Unlocks Aegis Shield."));
        Spells.add(shield("aegis_shield", "Aegis Shield", 9, 50f, 0.8f, 80, 0xFFE08A).evolvesFrom("magic_shield")
                .desc("A wider, far more durable barrier with stronger damage reduction."));

        Spells.add(Spell.of("aegis_dome", "Aegis Dome", DEFENSE, INSTANT).prop("dome_gold", 1.0f, 0).icon(Glyph.SHIELD).cost(32).cd(400).level(9)
                .desc("Raise a dome that eats incoming projectiles and shelters allies inside. Enemies can walk in.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        Vec3 c = z.position();
                        double r = z.radius();
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r + 1), p -> p.getOwner() != owner)) {
                            double d = p.position().distanceTo(c);
                            boolean inbound = p.getDeltaMovement().dot(c.subtract(p.position())) > 0;
                            if (d <= r + 0.6 && d >= r - 1.2 && inbound) {
                                Fx.burst(l, Fx.spark(0xFFE08A, 0.9f), p.position(), 8, 0.2, 0.15);
                                Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 1.1f);
                                p.discard();
                                z.data[0] += 6;
                                z.credit(1);
                            }
                        }
                        if (z.age % 10 == 0) {
                            for (LivingEntity e : Kit.friendsIn(l, c, r, owner)) {
                                e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false, true));
                            }
                        }
                        if (z.data[0] >= 30) {
                            Fx.shockwave(l, Fx.spark(0xFFE08A, 1f), c, (float) r, 4);
                            Fx.sound(l, c, SoundEvents.GLASS_BREAK, 1f, 0.6f);
                            z.discard();
                        }
                    }
                    @Override public void fx(SpellZone z) { /* the 3D prop provides the visuals */ }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 8);
                    Vec3 at = g == null ? c.player.position() : g;
                    SpellZone z = Proj.zone(c, c.spell, at, 4.2f, 160);
                    if (z == null) return -1;
                    Fx.rune(c.level, at.add(0, 0.1, 0), 4.2, 0xFFE08A, 30, 0);
                    Fx.sound(c.level, at, SoundEvents.BEACON_ACTIVATE, 1f, 1.2f);
                    return 0;
                }));

        Spells.add(Spell.of("counterspell", "Counterspell", DEFENSE, INSTANT).icon(Glyph.RING).cost(14).cd(120).level(6)
                .desc("Open a brief parry window. A magical or projectile hit inside it is cancelled and answered with a counterblast. Melee is not parried.")
                .onCast(c -> {
                    Magic.of(c.player).parryUntil = c.level.getGameTime() + 14;
                    Fx.rune(c.level, c.player.position().add(0, 0.1, 0), 1.4, 0xFFE9A0, 18, 0);
                    Fx.burst(c.level, Fx.spark(0xFFE9A0, 0.9f), c.player.position().add(0, 1.2, 0), 14, 0.5, 0.05);
                    Fx.sound(c.level, c.player.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 2f);
                    return 0;
                }));

        Spells.add(Spell.of("mirror_ward", "Mirror Ward", DEFENSE, INSTANT).propFront("mirror", 0.9f, 1.6, -1.0).icon(Glyph.EYE).cost(20).cd(200).level(8)
                .desc("Conjure a mirror ahead. The first projectile to touch it is thrown back at a nearby enemy, and the mirror shatters.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        if (owner == null || Magic.of(owner).mirrorUntil <= l.getGameTime()) {
                            z.discard();
                            return;
                        }
                        Vec3 centre = owner.getEyePosition().add(owner.getLookAngle().scale(1.6)).add(0, -0.2, 0);
                        z.setPos(centre.x, centre.y, centre.z);
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(2.4), p -> p.getOwner() != owner)) {
                            Vec3 v = p.getDeltaMovement();
                            if (v.dot(owner.getLookAngle()) > 0) continue; // only things flying toward the caster
                            Magic.of(owner).mirrorUntil = 0;
                            DefenseEvents.reflect(owner, p);
                            Fx.burst(l, ParticleTypes.CLOUD, centre, 10, 0.5, 0.1);
                            Fx.sound(l, centre, SoundEvents.GLASS_BREAK, 1f, 1.5f);
                            z.discard();
                            return;
                        }
                    }
                    @Override public void fx(SpellZone z) { /* the 3D prop provides the visuals */ }
                })
                .onCast(c -> {
                    Magic.of(c.player).mirrorUntil = c.level.getGameTime() + 160;
                    SpellZone z = Proj.zone(c, c.spell, c.eye().add(c.look().scale(1.6)), 1f, 160);
                    if (z == null) return -1;
                    Fx.sound(c.level, c.eye(), SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 1.8f);
                    return 0;
                }));

        Spells.add(Spell.of("phase_guard", "Phase Guard", DEFENSE, INSTANT).icon(Glyph.EYE).cost(22).cd(240).level(7)
                .desc("Slip into an arcane silhouette. The next hit within three seconds mostly passes through you. Later hits land normally.")
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        ServerPlayer o = z.owner();
                        if (o == null || Magic.of(o).phaseUntil <= z.level().getGameTime()) z.discard();
                    }
                    @Override public void fx(SpellZone z) {
                        var o = z.clientOwner();
                        if (o == null) return;
                        var rnd = z.level().random;
                        for (int i = 0; i < 3; i++) {
                            Fx.c(z.level(), Fx.wisp(0xB48CFF, 0.9f), o.getX() + (rnd.nextDouble() - .5) * 0.7, o.getY() + rnd.nextDouble() * 1.8, o.getZ() + (rnd.nextDouble() - .5) * 0.7, 0, 0.02, 0);
                        }
                    }
                })
                .onCast(c -> {
                    Magic.of(c.player).phaseUntil = c.level.getGameTime() + 60;
                    SpellZone z = Proj.zone(c, c.spell, c.player.position(), 1f, 60);
                    if (z != null) z.followOwner = true;
                    c.player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, false));
                    Fx.burst(c.level, Fx.wisp(0xB48CFF, 1.2f), c.player.position().add(0, 1, 0), 25, 0.4, 0.05);
                    Fx.sound(c.level, c.player.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.7f);
                    return 0;
                }));

        Spells.add(Spell.of("cleansing_light", "Cleansing Light", DEFENSE, INSTANT).icon(Glyph.STAR).cost(24).cd(300).level(5)
                .desc("Wash away poison, weakness, slowness and other common curses from you and nearby allies.")
                .onCast(c -> {
                    List<net.minecraft.core.Holder<MobEffect>> bad = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS,
                            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.CONFUSION,
                            MobEffects.DARKNESS, MobEffects.LEVITATION, MobEffects.HUNGER);
                    int cleansed = 0;
                    Vec3 at = c.player.position();
                    Fx.shockwave(c.level, Fx.glow(0xFFF0A0, 1.3f), at.add(0, 0.2, 0), 6, 5);
                    Fx.burst(c.level, Fx.spark(0xFFFFFF, 1f), at.add(0, 1, 0), 30, 0.6, 0.3);
                    Fx.sound(c.level, at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
                    for (LivingEntity e : Kit.friendsIn(c.level, at, 6, c.player)) {
                        boolean any = false;
                        for (var eff : bad) if (e.removeEffect(eff)) any = true;
                        if (e.isOnFire()) { e.clearFire(); any = true; }
                        if (any) cleansed++;
                        Fx.burst(c.level, Fx.glow(0xFFF0A0, 1f), e.position().add(0, 1, 0), 10, 0.3, 0.1);
                    }
                    if (cleansed == 0) {
                        Casting.fail(c.player, "Nothing to cleanse");
                        return -1;
                    }
                    return cleansed;
                }));

        Spells.add(Spell.of("rescue_blink", "Rescue Blink", DEFENSE, INSTANT).icon(Glyph.WINGS).cost(26).cd(300).level(4)
                .desc("Teleport to a visible, safe spot away from nearby threats. Needs a clear destination and never passes through walls.")
                .onCast(c -> {
                    ServerLevel l = c.level;
                    ServerPlayer p = c.player;
                    List<LivingEntity> threats = Combat.enemiesIn(l, p.position(), 12, p);
                    Vec3 best = null;
                    double bestScore = -1e9;
                    for (int i = 0; i < 40; i++) {
                        double ang = l.random.nextDouble() * Math.PI * 2, dist = 5 + l.random.nextDouble() * 8;
                        BlockPos probe = BlockPos.containing(p.getX() + Math.cos(ang) * dist, p.getY() + 3, p.getZ() + Math.sin(ang) * dist);
                        var ground = Aim.stepGround(l, probe.below(3));
                        if (ground.isEmpty()) {
                            // look a little further down for a ledge
                            ground = Aim.floorBelow(l, probe, 8);
                            if (ground.isEmpty()) continue;
                        }
                        BlockPos g = ground.get();
                        if (!safeSpot(l, g)) continue;
                        Vec3 spot = Vec3.atBottomCenterOf(g);
                        Vec3 head = spot.add(0, 1.62, 0);
                        var clip = l.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), head,
                                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
                        if (clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
                        if (!l.noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) continue;
                        double score = 0;
                        for (LivingEntity t : threats) score += Math.min(14, t.position().distanceTo(spot));
                        if (threats.isEmpty()) score += spot.distanceTo(p.position());
                        score -= Math.abs(spot.y - p.getY()) * 0.5;
                        if (score > bestScore) {
                            bestScore = score;
                            best = spot;
                        }
                    }
                    if (best == null) {
                        Casting.fail(p, "No safe destination in sight");
                        return -1;
                    }
                    Vec3 from = p.position();
                    Fx.burst(l, Fx.wisp(0x8FD0FF, 1.2f), from.add(0, 1, 0), 25, 0.4, 0.05);
                    Fx.line(l, Fx.spark(0xCFEFFF, 0.6f), from.add(0, 1, 0), best.add(0, 1, 0), 0.5);
                    p.teleportTo(best.x, best.y, best.z);
                    p.fallDistance = 0;
                    Fx.burst(l, Fx.wisp(0x8FD0FF, 1.2f), best.add(0, 1, 0), 25, 0.4, 0.05);
                    Fx.sound(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
                    Fx.sound(l, best, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
                    return threats.isEmpty() ? 0 : 1;
                }));
    }

    /** Solid, non-hazardous floor with two blocks of headroom. */
    static boolean safeSpot(ServerLevel l, BlockPos g) {
        BlockState floor = l.getBlockState(g.below());
        if (floor.isAir() || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                || floor.is(Blocks.SWEET_BERRY_BUSH) || floor.is(Blocks.POWDER_SNOW) || !floor.getFluidState().isEmpty()) return false;
        for (BlockPos q : new BlockPos[]{g, g.above()}) {
            BlockState s = l.getBlockState(q);
            if (!s.getCollisionShape(l, q).isEmpty() || !s.getFluidState().isEmpty() || s.is(BlockTags.FIRE) || s.is(Blocks.COBWEB)) return false;
        }
        return true;
    }
}
