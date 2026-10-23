package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.ILLUSION;

import com.wandcraft.WandCraft;
import com.wandcraft.entity.Props;
import com.wandcraft.entity.ShadowClone;
import com.wandcraft.entity.SpellProp;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.CastCtx;
import com.wandcraft.magic.Casting;
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
import com.wandcraft.magic.util.Scheduler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Illusion Magic: decoys, concealment and deception without breaking enemy AI for good. */
final class IllusionSpells {
    static final int ILLUSION_C = ILLUSION.color;

    private IllusionSpells() {}

    static ShadowClone summon(CastCtx c, int style, Vec3 at, int life, float hp, float dmg) {
        ShadowClone sc = new ShadowClone(WandCraft.SHADOW_CLONE.get(), c.level);
        sc.setOwner(c.player);
        sc.setStyle(style);
        sc.life = life;
        sc.damage = dmg;
        sc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        sc.setHealth(hp);
        sc.moveTo(at.x, at.y, at.z, c.player.getYRot(), 0);
        c.level.addFreshEntity(sc);
        return sc;
    }

    static void clearClones(CastCtx c, int... styles) {
        for (ShadowClone old : c.level.getEntitiesOfClass(ShadowClone.class, c.player.getBoundingBox().inflate(80), e -> c.player.getUUID().equals(e.ownerId()))) {
            for (int st : styles) if (old.style() == st) old.fade();
        }
    }

    static ParticleOptions haze(float size) { return Fx.wisp(0xE2B8FF, size); }

    static void shimmer(ServerLevel l, Vec3 at, int n) {
        Fx.burst(l, haze(1.1f), at, n, 0.4, 0.04);
        Fx.burst(l, Fx.spark(ILLUSION_C, 0.7f), at, Math.max(3, n / 2), 0.4, 0.08);
    }

    static void register() {
        Spells.add(Spell.of("mirror_image", "Mirror Image", ILLUSION, INSTANT).icon(Glyph.EYE).cost(22).cd(300).level(2)
                .desc("Conjure a decoy of yourself that draws the attention of nearby hostile mobs. It can be hit and fades when hurt enough or after 12 seconds.")
                .perks("Sturdier decoy.", "Unlocks Phantom Double.")
                .onCast(c -> {
                    clearClones(c, ShadowClone.STYLE_MIRROR);
                    Vec3 look = c.look();
                    Vec3 at = c.player.position().add(-look.z * 1.8, 0, look.x * 1.8);
                    ShadowClone sc = summon(c, ShadowClone.STYLE_MIRROR, at, 240, 12 + 4 * c.rank.ordinal(), 0);
                    sc.decoy = true;
                    sc.drawsAggro = true;
                    shimmer(c.level, at.add(0, 1, 0), 24);
                    Fx.sound(c.level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.7f);
                    return 0;
                }));

        Spells.add(Spell.of("phantom_double", "Phantom Double", ILLUSION, INSTANT).icon(Glyph.EYE).cost(30).cd(360).evolvesFrom("mirror_image")
                .desc("A far more convincing double: it mirrors your movement, facing and wand swings while drawing enemy attention. It cannot cast or deal damage.")
                .onCast(c -> {
                    clearClones(c, ShadowClone.STYLE_MIRROR);
                    Vec3 at = c.player.position();
                    ShadowClone sc = summon(c, ShadowClone.STYLE_MIRROR, at, 300, 20, 0);
                    sc.decoy = true;
                    sc.mimic = true;
                    sc.drawsAggro = true;
                    shimmer(c.level, at.add(0, 1, 0), 30);
                    Fx.sound(c.level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
                    return 0;
                }));

        Spells.add(Spell.of("veil_of_shadows", "Veil of Shadows", ILLUSION, INSTANT).icon(Glyph.EYE).cost(16).cd(300).level(3).stealthSafe()
                .desc("Blur your outline so ordinary mobs lose you beyond a few blocks for 12 seconds. Casting or attacking ends the veil early.")
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        ServerPlayer o = z.owner();
                        if (o == null || Magic.of(o).stealthUntil <= z.level().getGameTime()) z.discard();
                    }
                    @Override public void fx(SpellZone z) {
                        var o = z.clientOwner();
                        if (o == null) return;
                        var rnd = z.level().random;
                        for (int i = 0; i < 2; i++) {
                            Fx.c(z.level(), haze(0.9f), o.getX() + (rnd.nextDouble() - .5) * .8, o.getY() + rnd.nextDouble() * 1.9, o.getZ() + (rnd.nextDouble() - .5) * .8, 0, 0.015, 0);
                        }
                        if (z.age % 3 == 0) Fx.c(z.level(), Fx.spark(ILLUSION_C, 0.5f), o.getX() + (rnd.nextDouble() - .5), o.getY() + rnd.nextDouble() * 2, o.getZ() + (rnd.nextDouble() - .5), 0, 0.02, 0);
                    }
                })
                .onCast(c -> {
                    Magic.of(c.player).stealthUntil = c.level.getGameTime() + 240;
                    SpellZone z = Proj.zone(c, c.spell, c.player.position(), 1f, 240);
                    if (z != null) z.followOwner = true;
                    shimmer(c.level, c.player.position().add(0, 1, 0), 26);
                    Fx.sound(c.level, c.player.position(), SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.8f);
                    return 0;
                }));

        Spells.add(Spell.of("false_footsteps", "False Footsteps", ILLUSION, INSTANT).icon(Glyph.WAVE).cost(14).cd(150).level(4)
                .desc("Fill a spot with fleeting footprints and noise that lure idle hostile mobs toward it. Only you hear the footsteps.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        Vec3 c = z.position();
                        if (z.age % 6 == 0) {
                            double a = l.random.nextDouble() * Math.PI * 2, r = 0.5 + l.random.nextDouble() * z.radius();
                            Vec3 p = c.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
                            l.sendParticles(Fx.spark(0xC9B8E8, 0.45f), p.x, p.y, p.z, 2, 0.1, 0, 0.1, 0.01);
                            if (owner != null) {
                                owner.connection.send(new ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvents.STONE_STEP), SoundSource.PLAYERS,
                                        p.x, p.y, p.z, 0.6f, 0.9f + l.random.nextFloat() * 0.3f, l.random.nextLong()));
                            }
                        }
                        if (z.age % 10 != 0) return;
                        int n = 0;
                        for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(18),
                                m -> m instanceof net.minecraft.world.entity.monster.Enemy && !Combat.isBoss(m))) {
                            LivingEntity t = m.getTarget();
                            if (t != null && m.distanceToSqr(t) < 64) continue;
                            m.getNavigation().moveTo(c.x, c.y, c.z, 1.0);
                            n++;
                        }
                        if (z.age % 20 == 0) z.credit(Math.min(n, 2));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        if (z.age % 4 == 0) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * z.radius();
                            Fx.c(z.level(), haze(0.7f), z.getX() + Math.cos(a) * r, z.getY() + 0.1, z.getZ() + Math.sin(a) * r, 0, 0.01, 0);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 24);
                    if (g == null) return -1;
                    if (Proj.zone(c, c.spell, g, 3.2f, 80) == null) return -1;
                    Fx.rune(c.level, g.add(0, 0.1, 0), 2.4, ILLUSION_C, 12, 0);
                    return 0;
                }));

        Spells.add(Spell.of("hall_of_mirrors", "Hall of Mirrors", ILLUSION, INSTANT).icon(Glyph.EYE).cost(26).cd(260).level(8)
                .desc("Surround an enemy with fragile copies of itself... of you. Each pops when struck, and the target keeps losing track of which is real. Bosses are barely fooled.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 20);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    clearClones(c, ShadowClone.STYLE_SPECTRAL);
                    boolean boss = Combat.isBoss(t);
                    int ticks = boss ? 60 : 160;
                    List<ShadowClone> copies = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        double a = Math.PI * 2 * i / 4 + 0.4;
                        Vec3 at = t.position().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
                        ShadowClone sc = summon(c, ShadowClone.STYLE_MIRROR, at, ticks, 1, 0);
                        sc.oneHit = true;
                        sc.decoy = true;
                        copies.add(sc);
                        shimmer(c.level, at.add(0, 1, 0), 10);
                    }
                    if (t instanceof Mob m && !boss) {
                        Scheduler.repeat(c.level, 0, 15, ticks / 15, i -> {
                            List<ShadowClone> alive = copies.stream().filter(ShadowClone::isAlive).toList();
                            if (!t.isAlive() || alive.isEmpty()) return;
                            m.setTarget(alive.get(c.level.random.nextInt(alive.size())));
                        });
                        m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, false, false));
                    }
                    Fx.sound(c.level, t.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
                    return 1;
                }));

        Spells.add(Spell.of("phantom_wall", "Phantom Wall", ILLUSION, INSTANT).icon(Glyph.WALL).cost(20).cd(200).level(5)
                .desc("Raise an illusory stone wall that looks solid but never blocks you. Ordinary mobs that reach it hesitate and may lose interest.")
                .prop("phantom_wall", 0.34f, 0)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 5 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        Vec3 n = z.vec; // wall normal (horizontal)
                        Vec3 c = z.position();
                        int hits = 0;
                        for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(4, 2, 4),
                                m -> m instanceof net.minecraft.world.entity.monster.Enemy && !Combat.isBoss(m))) {
                            Vec3 rel = m.position().subtract(c);
                            double along = rel.x * -n.z + rel.z * n.x, depth = rel.x * n.x + rel.z * n.z;
                            if (Math.abs(along) > 3.4 || Math.abs(depth) > 2.4) continue;
                            m.getNavigation().stop();
                            Combat.slow(m, 25, 3);
                            if (l.random.nextInt(3) == 0) m.setTarget(null);
                            hits++;
                        }
                        if (hits > 0 && z.age % 20 == 0) {
                            z.credit(1);
                            if (z.prop != null) z.prop.act();
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 18);
                    if (g == null) return -1;
                    Vec3 toCaster = c.player.position().subtract(g);
                    Vec3 n = new Vec3(toCaster.x, 0, toCaster.z);
                    n = n.lengthSqr() < 1e-3 ? c.look().multiply(-1, 0, -1) : n.normalize();
                    SpellZone z = Proj.zone(c, c.spell, g, 3f, 200);
                    if (z == null) return -1;
                    z.vec = n;
                    // The prop is attached by the zone; turn it to face the caster once it exists.
                    final Vec3 normal = n;
                    Scheduler.after(c.level, 1, () -> {
                        if (z.prop != null) z.prop.facing((float) (Mth.atan2(-normal.x, normal.z) * Mth.RAD_TO_DEG), 0f);
                    });
                    Fx.burst(c.level, Fx.spark(0xB8B8C8, 0.7f), g.add(0, 1.5, 0), 20, 1.2, 0.05);
                    Fx.sound(c.level, g, SoundEvents.STONE_PLACE, 1f, 0.7f);
                    return 0;
                }));

        Spells.add(Spell.of("dread_vision", "Dread Vision", ILLUSION, INSTANT).icon(Glyph.SKULL).cost(16).cd(160).level(6)
                .desc("Slip a flicker of terror into an ordinary enemy: it hesitates, loses its target and fights clumsily for a while. Bosses are unaffected.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 22);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    if (Combat.isBoss(t)) {
                        Casting.fail(c.player, "It is not afraid");
                        return -1;
                    }
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, true, true));
                    t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, true, true));
                    t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
                    if (t instanceof Mob m) m.setTarget(null);
                    Scheduler.repeat(c.level, 0, 6, 16, i -> {
                        if (!t.isAlive()) return;
                        Vec3 eye = t.position().add(0, t.getBbHeight() + 0.4, 0);
                        Fx.burst(c.level, Fx.glow(0xFF4060, 0.6f), eye.add(-0.2, 0, 0), 1, 0.02, 0);
                        Fx.burst(c.level, Fx.glow(0xFF4060, 0.6f), eye.add(0.2, 0, 0), 1, 0.02, 0);
                        Fx.burst(c.level, haze(0.8f), t.position().add(0, t.getBbHeight() * 0.5, 0), 2, 0.3, 0.02);
                        if (t instanceof Mob m && i % 3 == 0) m.setTarget(null);
                    });
                    Fx.sound(c.level, t.position(), SoundEvents.WARDEN_NEARBY_CLOSE, 0.7f, 1.4f);
                    return 1;
                }));
    }
}
