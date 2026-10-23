package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHANNEL;
import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.DARK;

import com.wandcraft.WandCraft;
import com.wandcraft.entity.ShadowClone;
import com.wandcraft.entity.SpellBeam;
import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.CastCtx;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.DarkEvents;
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
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Dark Arts: weakening, draining, shadow control and dangerous ground. Strong spells cost life force. */
final class DarkSpells {
    static final int VIOLET = DARK.color;
    static final int BRIGHT = 0xB06BFF;
    static final int DEEP = 0x140A22;

    private DarkSpells() {}

    static ParticleOptions smoke(float size) { return Fx.wisp(DEEP, size); }

    /** Black smoke with violet cores. */
    static void puff(ServerLevel l, Vec3 at, int n, double spread) {
        Fx.burst(l, smoke(1.3f), at, n, spread, 0.03);
        Fx.burst(l, Fx.glow(VIOLET, 0.8f), at, Math.max(2, n / 2), spread, 0.04);
        Fx.burst(l, Fx.spark(BRIGHT, 0.6f), at, Math.max(2, n / 3), spread, 0.08);
    }

    /** Wavering shadow tendril between two points. */
    static void tendril(ServerLevel l, Vec3 a, Vec3 b, double phase) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 n = d.scale(1 / len);
        Vec3 side = n.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(n).normalize();
        int steps = Math.max(6, (int) (len * 3));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, w = Math.sin(t * Math.PI);
            Vec3 p = a.add(d.scale(t)).add(side.scale(Math.sin(t * 9 + phase) * 0.22 * w)).add(up.scale(Math.cos(t * 7 + phase) * 0.22 * w));
            l.sendParticles(smoke(0.7f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 2 == 0) l.sendParticles(Fx.glow(BRIGHT, 0.5f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Particles streaming inward to a centre (implosions). */
    static void implode(ServerLevel l, Vec3 c, double radius, int n) {
        for (int i = 0; i < Fx.scaled(n); i++) {
            double u = l.random.nextDouble() * 2 - 1, a = l.random.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
            Vec3 dir = new Vec3(q * Math.cos(a), u, q * Math.sin(a));
            Vec3 p = c.add(dir.scale(radius));
            Vec3 v = dir.scale(-radius / 8.0);
            l.sendParticles(i % 2 == 0 ? Fx.glow(VIOLET, 0.8f) : smoke(0.9f), p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
        }
    }

    static ProjBehavior shadowBolt(boolean implosion) {
        return new ProjBehavior() {
            @Override public void trail(SpellProjectile p) {
                Fx.c(p.level(), smoke(0.9f), p.getX(), p.getY(), p.getZ(), 0, 0.01, 0);
                Fx.c(p.level(), Fx.glow(VIOLET, 1.1f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                if (p.age % 2 == 0) Fx.c(p.level(), Fx.spark(BRIGHT, 0.6f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
            }
            @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) {
                ServerLevel l = (ServerLevel) p.level();
                Combat.hurt(l, p.getOwner(), p, t, p.damage);
                p.credit(1);
                impact(p, l, p.position());
                return true;
            }
            @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) {
                ServerLevel l = (ServerLevel) p.level();
                impact(p, l, r.getLocation());
                l.sendParticles(Fx.runeDecal(DEEP, 0.7f), r.getLocation().x, r.getLocation().y + 0.02, r.getLocation().z, 1, 0, 0, 0, 0);
                return true;
            }
            private void impact(SpellProjectile p, ServerLevel l, Vec3 at) {
                puff(l, at, 14, 0.3);
                Fx.sound(l, at, SoundEvents.WARDEN_SONIC_CHARGE, 0.3f, 1.8f);
                if (!implosion) return;
                implode(l, at, 2.8, 24);
                int hits = 0;
                for (LivingEntity e : Combat.enemiesIn(l, at, 3.2, p.getOwner())) {
                    Vec3 pull = at.subtract(e.position().add(0, e.getBbHeight() / 2, 0)).normalize().scale(Combat.isHeavy(e) ? 0.1 : 0.55);
                    Combat.push(e, pull);
                    Combat.slow(e, 30, 2);
                    Combat.hurt(l, p.getOwner(), p, e, 3 * p.power);
                    hits++;
                }
                p.credit(hits);
                Fx.sound(l, at, SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 0.5f);
            }
        };
    }

    static void register() {
        Spells.add(Spell.of("shadow_bolt", "Shadow Bolt", DARK, INSTANT).icon(Glyph.BOLT).cost(9).cd(12).level(3).echoable().health(0.5f)
                .desc("A fast bolt of condensed shadow that leaves a smoky scorch on the ground. Costs a sliver of life.")
                .perks("Hits harder.", "Unlocks Void Bolt.")
                .projectile(shadowBolt(false))
                .onCast(c -> {
                    Fx.sound(c.level, c.eye(), SoundEvents.WARDEN_SONIC_CHARGE, 0.4f, 1.6f);
                    return Proj.launch(c, c.spell, 2.4, 0, c.dmg(5.5), 40) == null ? -1 : 0;
                }));

        Spells.add(Spell.of("void_bolt", "Void Bolt", DARK, INSTANT).icon(Glyph.BOLT).cost(17).cd(45).evolvesFrom("shadow_bolt").health(1f).echoable()
                .desc("Shadow Bolt evolved: on impact it implodes, dragging enemies inward and hindering their movement.")
                .projectile(shadowBolt(true))
                .onCast(c -> {
                    Fx.sound(c.level, c.eye(), SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 0.9f);
                    return Proj.launch(c, c.spell, 2.1, 0, c.dmg(7), 45) == null ? -1 : 0;
                }));

        Spells.add(Spell.of("shadow_grasp", "Shadow Grasp", DARK, INSTANT).icon(Glyph.SWIRL).cost(16).cd(140).level(5).health(1f)
                .desc("Dark tendrils link you to an enemy, slowing and restraining it until they snap. Bosses only slow.")
                .perks("Longer hold.", "Unlocks Abyssal Chains.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 22);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    Combat.immobilize(t, 40);
                    ServerPlayer p = c.player;
                    Fx.sound(c.level, t.position(), SoundEvents.WARDEN_TENDRIL_CLICKS, 1f, 0.8f);
                    Scheduler.repeat(c.level, 0, 4, 10, i -> {
                        if (!t.isAlive() || !p.isAlive()) return;
                        tendril(c.level, p.position().add(0, 1.0, 0), t.position().add(0, t.getBbHeight() * 0.5, 0), i);
                    });
                    Scheduler.after(c.level, 40, () -> {
                        if (!t.isAlive()) return;
                        puff(c.level, t.position().add(0, 1, 0), 18, 0.4);
                        Combat.hurt(c.level, p, p, t, c.dmg(2));
                        Fx.sound(c.level, t.position(), SoundEvents.CHAIN_BREAK, 1f, 0.6f);
                    });
                    Combat.hurt(c.level, p, p, t, c.dmg(2));
                    return 1;
                }));

        Spells.add(Spell.of("abyssal_chains", "Abyssal Chains", DARK, INSTANT).icon(Glyph.WALL).cost(28).cd(200).evolvesFrom("shadow_grasp").health(1.5f)
                .desc("Spectral chains burst from the ground around a target, binding every ordinary enemy nearby. Each chain fades on its own.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 24);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    Vec3 centre = t.position();
                    ServerPlayer p = c.player;
                    Fx.rune(c.level, centre.add(0, 0.1, 0), 3.4, VIOLET, 22, 0);
                    int hits = 0;
                    for (LivingEntity e : Combat.enemiesIn(c.level, centre, 3.6, p)) {
                        Combat.immobilize(e, 55);
                        Combat.hurt(c.level, p, p, e, c.dmg(3));
                        hits++;
                    }
                    for (int i = 0; i < 7; i++) {
                        final double ang = Math.PI * 2 * i / 7;
                        final int life = 6 + c.level.random.nextInt(6);
                        Scheduler.repeat(c.level, i * 2, 3, life, k -> {
                            Vec3 base = centre.add(Math.cos(ang) * 2.2, 0.0, Math.sin(ang) * 2.2);
                            Vec3 top = centre.add(Math.cos(ang) * 0.9, 2.3, Math.sin(ang) * 0.9);
                            Fx.line(c.level, Fx.glow(BRIGHT, 0.6f), base, top, 0.35);
                            Fx.line(c.level, smoke(0.6f), base, top, 0.5);
                            if (k == 0) Fx.sound(c.level, base, SoundEvents.CHAIN_PLACE, 1f, 0.7f);
                        });
                    }
                    Fx.sound(c.level, centre, SoundEvents.WARDEN_EMERGE, 0.5f, 1.5f);
                    return hits;
                }));

        Spells.add(Spell.of("soul_drain", "Soul Drain", DARK, CHANNEL).icon(Glyph.BEAM).cost(10).cd(60).level(4).health(1f)
                .desc("Channel a dark beam into one visible target: steady damage, and a trickle of mana back to you (capped per cast).")
                .onChannel((c, t) -> {
                    LivingEntity target = Aim.entity(c.player, 18);
                    int lost = c.get("lost", 0);
                    if (target == null || !c.player.hasLineOfSight(target)) {
                        c.state.put("lost", lost + 1);
                        return lost > 5 ? -1 : 0;
                    }
                    c.state.put("lost", 0);
                    SpellBeam beam = c.get("beam", null);
                    if (beam == null || beam.isRemoved()) {
                        beam = new SpellBeam(c.level, c.player, 0x8A3CFF);
                        c.level.addFreshEntity(beam);
                        c.state.put("beam", beam);
                        Fx.sound(c.level, c.eye(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 0.7f);
                    }
                    beam.update((float) c.eye().distanceTo(target.position().add(0, target.getBbHeight() * 0.5, 0)), 0.4f);
                    if (t % 5 == 0) {
                        Combat.hurt(c.level, c.player, c.player, target, c.dmg(1.6));
                        int gained = c.get("gained", 0);
                        if (gained < 20) {
                            Magic.restore(c.player, 1.5f);
                            c.state.put("gained", gained + 2);
                        }
                        Fx.burst(c.level, smoke(0.8f), target.position().add(0, target.getBbHeight() * 0.5, 0), 3, 0.25, 0.05);
                        c.hit();
                    }
                    return 0;
                })
                .onEnd(c -> {
                    SpellBeam beam = c.get("beam", null);
                    if (beam != null) beam.discard();
                }));

        Spells.add(Spell.of("umbral_cloak", "Umbral Cloak", DARK, INSTANT).icon(Glyph.EYE).cost(24).cd(400).level(6).stealthSafe()
                .desc("Wrap yourself in living shadow. Ordinary mobs lose track of you beyond a few blocks for 20 seconds, until you cast. Other players still see you.")
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
                            double a = rnd.nextDouble() * Math.PI * 2;
                            Fx.c(z.level(), smoke(1.1f), o.getX() + Math.cos(a) * 0.6, o.getY() + rnd.nextDouble() * 1.9, o.getZ() + Math.sin(a) * 0.6, 0, 0.02, 0);
                        }
                    }
                })
                .onCast(c -> {
                    PlayerMagic m = Magic.of(c.player);
                    m.stealthUntil = c.level.getGameTime() + 400;
                    SpellZone z = Proj.zone(c, c.spell, c.player.position(), 1f, 400);
                    if (z != null) z.followOwner = true;
                    puff(c.level, c.player.position().add(0, 1, 0), 30, 0.5);
                    Fx.sound(c.level, c.player.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("black_flame", "Black Flame", DARK, INSTANT).icon(Glyph.FLAME).cost(20).cd(120).level(7).health(1f)
                .desc("Lob a slow dark ember that sets a patch of supernatural flame on the ground. It burns enemies but cannot spread or burn blocks.")
                .projectile(new ProjBehavior() {
                    @Override public void trail(SpellProjectile p) {
                        Fx.c(p.level(), ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY(), p.getZ(), 0, 0.02, 0);
                        Fx.c(p.level(), smoke(0.8f), p.getX(), p.getY(), p.getZ(), 0, 0.02, 0);
                        Fx.c(p.level(), Fx.glow(VIOLET, 1f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) { land(p, t.position()); return true; }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) { land(p, r.getLocation()); return true; }
                    @Override public void expire(SpellProjectile p) { land(p, p.position()); }
                    private void land(SpellProjectile p, Vec3 at) {
                        ServerLevel l = (ServerLevel) p.level();
                        SpellZone z = new SpellZone(l, p.spell(), p.caster(), at, 2.3f, 100);
                        z.power = p.power;
                        l.addFreshEntity(z);
                        puff(l, at, 14, 0.4);
                        Fx.sound(l, at, SoundEvents.SOUL_ESCAPE.value(), 1f, 0.7f);
                    }
                })
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 10 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius(), z.owner())) {
                            Combat.hurt(l, z.owner(), z.owner(), e, 1.8f * z.power);
                            n++;
                        }
                        z.credit(Math.min(n, 2));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        for (int i = 0; i < 3; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), ParticleTypes.SOUL_FIRE_FLAME, z.getX() + Math.cos(a) * r, z.getY() + 0.05, z.getZ() + Math.sin(a) * r, 0, 0.04, 0);
                        }
                        if (z.age % 3 == 0) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * z.radius();
                            Fx.c(z.level(), smoke(1.0f), z.getX() + Math.cos(a) * r, z.getY() + 0.3, z.getZ() + Math.sin(a) * r, 0, 0.04, 0);
                        }
                        if (z.age % 12 == 0) Fx.c(z.level(), Fx.runeDecal(VIOLET, z.radius()), z.getX(), z.getY() + 0.02, z.getZ(), 0, 0, 0);
                    }
                })
                .onCast(c -> {
                    SpellProjectile p = Proj.launch(c, c.spell, 0.95, 0, 0, 40);
                    if (p == null) return -1;
                    p.gravity = 0.012;
                    Fx.sound(c.level, c.eye(), SoundEvents.SOUL_ESCAPE.value(), 0.8f, 1.2f);
                    return 0;
                }));

        Spells.add(Spell.of("nightfall", "Nightfall", DARK, INSTANT).icon(Glyph.EYE).cost(36).cd(400).level(10).health(2f)
                .desc("Draw down a magical eclipse over an area. Enemies inside see little and glow with markers; allies are unaffected.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 10 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position().add(0, 1, 0), z.radius(), z.owner())) {
                            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, false));
                            if (!(e instanceof net.minecraft.world.entity.player.Player) && !Combat.isBoss(e)) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
                            else if (e instanceof net.minecraft.world.entity.player.Player) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
                            n++;
                        }
                        if (z.age % 20 == 0) z.credit(Math.min(n, 3));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        for (int i = 0; i < 6; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), smoke(2.2f), z.getX() + Math.cos(a) * r, z.getY() + 3 + rnd.nextDouble() * 5, z.getZ() + Math.sin(a) * r, 0, 0, 0);
                        }
                        if (z.age % 3 == 0) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = z.radius();
                            Fx.c(z.level(), Fx.glow(0xFFE9B0, 1.6f), z.getX() + Math.cos(a) * r * 0.2, z.getY() + 9, z.getZ() + Math.sin(a) * r * 0.2, 0, 0, 0);
                        }
                        if (z.age % 15 == 0) Fx.c(z.level(), Fx.runeDecal(VIOLET, z.radius()), z.getX(), z.getY() + 0.02, z.getZ(), 0, 0, 0);
                    }
                })
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 28).pos();
                    if (Proj.zone(c, c.spell, at, 8f, 160) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f);
                    puff(c.level, at.add(0, 1, 0), 30, 2.5);
                    return 0;
                }));

        Spells.add(Spell.of("shadow_clone", "Shadow Clone", DARK, INSTANT).icon(Glyph.EYE).cost(30).cd(400).level(8).health(2f)
                .desc("Summon a shadow copy that fights your current target with weak strikes. Fragile, cannot cast, and fades after ten seconds.")
                .onCast(c -> {
                    for (ShadowClone old : c.level.getEntitiesOfClass(ShadowClone.class, c.player.getBoundingBox().inflate(64),
                            e -> c.player.getUUID().equals(e.ownerId()) && e.style() == ShadowClone.STYLE_SHADOW)) old.fade();
                    ShadowClone sc = new ShadowClone(WandCraft.SHADOW_CLONE.get(), c.level);
                    sc.setOwner(c.player);
                    sc.setStyle(ShadowClone.STYLE_SHADOW);
                    sc.damage = c.dmg(3);
                    sc.life = 200;
                    Vec3 look = c.look();
                    Vec3 at = c.player.position().add(-look.z * 1.2, 0, look.x * 1.2);
                    sc.moveTo(at.x, at.y, at.z, c.player.getYRot(), 0);
                    c.level.addFreshEntity(sc);
                    puff(c.level, at.add(0, 1, 0), 24, 0.4);
                    Fx.sound(c.level, at, SoundEvents.WARDEN_EMERGE, 0.4f, 1.8f);
                    return 0;
                }));

        Spells.add(Spell.of("curse_mark", "Curse Mark", DARK, INSTANT).icon(Glyph.EYE).cost(12).cd(60).level(4)
                .desc("Brand an enemy with a black rune. Your next three hits against it deal bonus damage. The mark expires and cannot stack.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 28);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    DarkEvents.curse(c.player, t, 3, c.dmg(2.5), 20 * 15);
                    Fx.rune(c.level, t.position().add(0, 0.1, 0), 1.1, VIOLET, 14, 0);
                    puff(c.level, t.position().add(0, t.getBbHeight() + 0.2, 0), 10, 0.25);
                    Fx.sound(c.level, t.position(), SoundEvents.WITCH_CELEBRATE, 0.6f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("hex", "Hex", DARK, INSTANT).icon(Glyph.SKULL).cost(14).cd(100).level(6).health(0.5f)
                .desc("Lay a curse on an ordinary enemy: weaker attacks, slower movement, and a trail of dark motes. Bosses get a weaker version.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 24);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    boolean boss = Combat.isBoss(t);
                    int ticks = boss ? 80 : 200;
                    t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, boss ? 0 : 1, false, true, true));
                    if (!boss) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true, true));
                    Scheduler.repeat(c.level, 0, 8, ticks / 8, i -> {
                        if (!t.isAlive()) return;
                        Fx.burst(c.level, smoke(0.7f), t.position().add(0, t.getBbHeight() * 0.8, 0), 3, 0.3, 0.02);
                        Fx.burst(c.level, Fx.glow(VIOLET, 0.6f), t.position().add(0, t.getBbHeight() * 0.8, 0), 2, 0.3, 0.02);
                    });
                    puff(c.level, t.position().add(0, 1, 0), 14, 0.4);
                    Fx.sound(c.level, t.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 0.8f, 0.6f);
                    return 1;
                }));

        Spells.add(Spell.of("withering_wave", "Withering Wave", DARK, INSTANT).icon(Glyph.WAVE).cost(22).cd(140).level(7).health(1f)
                .desc("Loose a wide wave of shadow that damages and weakens everything in front of you, hitting less hard at the edges.")
                .onCast(c -> {
                    double range = 9, half = 50;
                    Vec3 eye = c.eye(), look = c.look();
                    int hits = 0;
                    for (LivingEntity e : ForceSpells.cone(c, range, half)) {
                        Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye).normalize();
                        double angle = Math.toDegrees(Math.acos(Math.min(1, to.dot(look))));
                        double edge = angle / half;
                        float dmg = c.dmg(6 * (1.0 - 0.5 * edge));
                        Combat.hurt(c.level, c.player, c.player, e, dmg);
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, edge < 0.6 ? 1 : 0, false, true, true));
                        Combat.slow(e, 60, edge < 0.6 ? 1 : 0);
                        Fx.burst(c.level, smoke(1f), e.position().add(0, 1, 0), 8, 0.3, 0.04);
                        hits++;
                    }
                    ForceSpells.coneFx(c, range, half, BRIGHT);
                    for (int s = 1; s <= 5; s++) {
                        final int k = s;
                        Scheduler.after(c.level, s - 1, () -> {
                            Vec3 p = eye.add(look.scale(range * k / 5.0)).add(0, -0.5, 0);
                            Fx.burst(c.level, smoke(1.6f), p, 10, range * k / 5.0 * 0.45, 0.02);
                        });
                    }
                    Fx.sound(c.level, eye, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.4f);
                    return hits;
                }));

        Spells.add(Spell.of("shadowstep", "Shadowstep", DARK, INSTANT).icon(Glyph.WINGS).cost(18).cd(140).level(6).health(0.5f)
                .desc("Melt into shadow and reappear behind a target within range, or at a safe point in the aimed direction. Never through walls.")
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    Vec3 from = p.position();
                    LivingEntity t = Aim.entity(p, 14);
                    Vec3 dest = null;
                    if (t != null) {
                        Vec3 back = Vec3.directionFromRotation(0, t.getYRot()).scale(-1.7);
                        Vec3 side = new Vec3(-back.z, 0, back.x).normalize().scale(1.5);
                        for (Vec3 off : new Vec3[]{back, back.add(side), back.subtract(side), back.scale(-1)}) {
                            Vec3 cand = t.position().add(off);
                            if (c.level.noCollision(p, p.getBoundingBox().move(cand.subtract(from))) && !c.level.noCollision(p, p.getBoundingBox().move(cand.subtract(from)).move(0, -0.6, 0))) {
                                dest = cand;
                                break;
                            }
                        }
                    }
                    if (dest == null) dest = Kit.dash(c, 7);
                    if (dest == null) {
                        Casting.fail(p, "Nowhere to step");
                        return -1;
                    }
                    puff(c.level, from.add(0, 1, 0), 22, 0.4);
                    c.level.sendParticles(Fx.runeDecal(DEEP, 1.2f), from.x, from.y + 0.02, from.z, 1, 0, 0, 0, 0);
                    p.teleportTo(dest.x, dest.y, dest.z);
                    p.fallDistance = 0;
                    if (t != null) p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                    puff(c.level, dest.add(0, 1, 0), 22, 0.4);
                    Fx.sound(c.level, from, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.5f);
                    Fx.sound(c.level, dest, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.7f);
                    return t != null ? 1 : 0;
                }));

        registerNovas();

        Spells.add(Spell.of("void_rift", "Void Rift", DARK, INSTANT).prop("void_orb", 0.1f, 0).icon(Glyph.SWIRL).cost(30).cd(300).level(10).health(2f)
                .desc("Tear open a dark rift that drags enemies toward its heart and gnaws at them, then collapses. Bosses are not moved.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        Vec3 c = z.position();
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), z.owner())) {
                            if (Combat.isBoss(e)) continue;
                            double k = Combat.isHeavy(e) ? 0.3 : 1.0;
                            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                            if (to.lengthSqr() > 0.25) {
                                Vec3 v = e.getDeltaMovement().add(to.normalize().scale(0.09 * k));
                                e.setDeltaMovement(v.x, Math.min(v.y, 0.35), v.z);
                                e.hurtMarked = true;
                            }
                            if (z.age % 20 == 0) {
                                Combat.hurt(l, z.owner(), z.owner(), e, 2f * z.power);
                                hits++;
                            }
                        }
                        z.credit(hits);
                        if (z.age % 12 == 0) Fx.sound(l, c, SoundEvents.WARDEN_HEARTBEAT, 0.5f, 0.7f);
                    }
                    @Override public void end(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        implode(l, z.position(), z.radius(), 40);
                        Fx.burst(l, Fx.spark(0xFFFFFF, 1.2f), z.position(), 30, 0.3, 0.6);
                        Fx.sound(l, z.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 0.6f);
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius() * 0.6, z.owner())) {
                            Combat.hurt(l, z.owner(), z.owner(), e, 4 * z.power);
                            n++;
                        }
                        z.credit(n);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        Vec3 c = z.position();
                        Fx.cSphere(z.level(), smoke(2.4f), c, 0.6, 2);
                        Fx.cSphere(z.level(), Fx.glow(VIOLET, 1.1f), c, 0.9 + 0.1 * Math.sin(z.age * 0.4), 3);
                        for (int i = 0; i < 4; i++) {
                            double a = z.age * 0.35 + i * Math.PI / 2, r = 1.0 + rnd.nextDouble() * (z.radius() - 1.0);
                            double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
                            Fx.c(z.level(), Fx.spark(BRIGHT, 0.6f), c.x + ox, c.y + (rnd.nextDouble() - .5) * r * .5, c.z + oz, -ox * 0.07 - oz * 0.05, 0, -oz * 0.07 + ox * 0.05);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 26).pos().add(0, 1.2, 0);
                    if (Proj.zone(c, c.spell, at, 6.5f, 110) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.WARDEN_EMERGE, 1f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("corruption_orb", "Corruption Orb", DARK, INSTANT).icon(Glyph.ORB).cost(22).cd(120).level(8).health(1f).echoable()
                .desc("Lob a slow orb that bursts into a dark cloud on impact. Enemies inside are weakened and slowly wither. Terrain is untouched.")
                .projectile(new ProjBehavior() {
                    @Override public void trail(SpellProjectile p) {
                        Fx.cSphere(p.level(), smoke(1.0f), p.position(), 0.35, 3);
                        Fx.c(p.level(), Fx.glow(VIOLET, 1.4f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) { burst(p, t.position().add(0, 0.5, 0)); return true; }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) { burst(p, r.getLocation()); return true; }
                    @Override public void expire(SpellProjectile p) { burst(p, p.position()); }
                    private void burst(SpellProjectile p, Vec3 at) {
                        ServerLevel l = (ServerLevel) p.level();
                        SpellZone z = new SpellZone(l, p.spell(), p.caster(), at, 3.2f, 100);
                        z.power = p.power;
                        l.addFreshEntity(z);
                        puff(l, at, 24, 0.8);
                        Fx.sound(l, at, SoundEvents.WARDEN_SONIC_BOOM, 0.3f, 1.8f);
                    }
                })
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 10 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius(), z.owner())) {
                            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true, true));
                            if (z.age % 20 == 0) {
                                Combat.hurt(l, z.owner(), z.owner(), e, 1.5f * z.power);
                                n++;
                            }
                        }
                        z.credit(Math.min(n, 2));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        for (int i = 0; i < 5; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), smoke(1.7f), z.getX() + Math.cos(a) * r, z.getY() + 0.3 + rnd.nextDouble() * 1.8, z.getZ() + Math.sin(a) * r, 0, 0.01, 0);
                        }
                        if (rnd.nextInt(2) == 0) Fx.c(z.level(), Fx.glow(0x9A3CFF, 0.7f), z.getX() + (rnd.nextDouble() - .5) * z.radius() * 2, z.getY() + rnd.nextDouble() * 2, z.getZ() + (rnd.nextDouble() - .5) * z.radius() * 2, 0, 0.01, 0);
                    }
                })
                .onCast(c -> {
                    SpellProjectile p = Proj.launch(c, c.spell, 0.8, 0, 0, 45);
                    if (p == null) return -1;
                    p.gravity = 0.01;
                    Fx.sound(c.level, c.eye(), SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 0.8f);
                    return 0;
                }));

        Spells.add(Spell.of("devouring_shadow", "Devouring Shadow", DARK, INSTANT).icon(Glyph.SWIRL).cost(24).cd(180).level(9).health(1.5f)
                .desc("Open a pool of animated darkness beneath an enemy. It slows and gnaws at everything inside for a few seconds, then leaves the ground unchanged.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 10 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius(), z.owner())) {
                            Combat.slow(e, 20, 2);
                            Combat.hurt(l, z.owner(), z.owner(), e, 1.8f * z.power);
                            n++;
                        }
                        z.credit(Math.min(n, 2));
                        if (z.age % 20 == 0) Fx.sound(l, z.position(), SoundEvents.WARDEN_TENDRIL_CLICKS, 0.5f, 0.8f);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        if (z.age % 6 == 0) Fx.c(z.level(), Fx.runeDecal(DEEP, z.radius()), z.getX(), z.getY() + 0.02, z.getZ(), 0, 0, 0);
                        for (int i = 0; i < 4; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), smoke(1.2f), z.getX() + Math.cos(a) * r, z.getY() + 0.1, z.getZ() + Math.sin(a) * r, 0, 0.05, 0);
                        }
                        Fx.c(z.level(), Fx.glow(VIOLET, 0.8f), z.getX() + (rnd.nextDouble() - .5) * z.radius() * 2, z.getY() + 0.2, z.getZ() + (rnd.nextDouble() - .5) * z.radius() * 2, 0, 0.04, 0);
                    }
                })
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 24);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    if (Proj.zone(c, c.spell, t.position(), 2.4f, 80) == null) return -1;
                    puff(c.level, t.position().add(0, 0.3, 0), 20, 0.8);
                    Fx.sound(c.level, t.position(), SoundEvents.WARDEN_EMERGE, 0.6f, 1.3f);
                    return 0;
                }));

        Spells.add(Spell.of("dark_pact", "Dark Pact", DARK, INSTANT).icon(Glyph.SKULL).cost(1).cd(900).level(5).stealthSafe().health(1f)
                .desc("Sacrifice 30% of your current mana (at least 15) for 15 seconds of +25% spell power and doubled mana regeneration.")
                .onCast(c -> {
                    PlayerMagic m = Magic.of(c.player);
                    if (!Magic.infinite(c.player) && m.mana < 20) {
                        Casting.fail(c.player, "Not enough mana to sacrifice");
                        return -1;
                    }
                    if (m.pactUntil > c.level.getGameTime()) {
                        Casting.fail(c.player, "The pact still holds");
                        return -1;
                    }
                    float sac = Math.max(15f, m.mana * 0.3f);
                    if (!Magic.infinite(c.player)) m.mana = Math.max(1f, m.mana - sac);
                    m.pactUntil = c.level.getGameTime() + 300;
                    m.dirty = true;
                    puff(c.level, c.player.position().add(0, 1, 0), 40, 0.7);
                    Fx.rune(c.level, c.player.position().add(0, 0.1, 0), 1.8, VIOLET, 20, 0);
                    Fx.sound(c.level, c.player.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
                    Fx.sound(c.level, c.player.position(), SoundEvents.WITCH_CELEBRATE, 0.6f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("eclipsing_beam", "Eclipsing Beam", DARK, CHANNEL).icon(Glyph.BEAM).cost(24).cd(100).level(12).health(2f)
                .desc("Channel a devastating black beam that grows stronger on one target. It drains mana fast and overheats if held too long, stalling you.")
                .onChannel((c, t) -> {
                    if (t >= 110) {
                        c.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
                        puff(c.level, c.player.position().add(0, 1.5, 0), 30, 0.5);
                        Fx.sound(c.level, c.player.position(), SoundEvents.GENERIC_EXTINGUISH_FIRE, 1f, 0.5f);
                        Casting.fail(c.player, "Overheated!");
                        return -1;
                    }
                    Aim.Result r = Aim.trace(c.player, 28);
                    SpellBeam beam = c.get("beam", null);
                    if (beam == null || beam.isRemoved()) {
                        beam = new SpellBeam(c.level, c.player, 0x5B1FC8);
                        c.level.addFreshEntity(beam);
                        c.state.put("beam", beam);
                        Fx.sound(c.level, c.eye(), SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
                    }
                    UUID last = c.get("last", null);
                    int focus = c.get("focus", 0);
                    if (r.hitEntity()) focus = r.entity().getUUID().equals(last) ? Math.min(60, focus + 1) : 0;
                    else focus = Math.max(0, focus - 2);
                    c.state.put("focus", focus);
                    if (r.hitEntity()) c.state.put("last", r.entity().getUUID());
                    // Beam widens with focus and with heat so the overheat is clearly telegraphed.
                    beam.update((float) c.eye().distanceTo(r.pos()), Math.min(1f, focus / 60f * 0.7f + t / 110f * 0.3f));
                    if (t == 80) Fx.sound(c.level, c.eye(), SoundEvents.NOTE_BLOCK_BIT.value(), 1f, 0.5f);
                    if (t % 3 != 0) return 0;
                    if (r.hitEntity()) {
                        float mult = 1f + 2f * focus / 60f;
                        Combat.hurt(c.level, c.player, c.player, r.entity(), c.dmg(1.8 * mult));
                        puff(c.level, r.pos(), 3, 0.2);
                        c.hit();
                    } else if (r.hitBlock()) {
                        Fx.burst(c.level, smoke(0.9f), r.pos(), 2, 0.15, 0.03);
                    }
                    return 0;
                })
                .onEnd(c -> {
                    SpellBeam beam = c.get("beam", null);
                    if (beam != null) beam.discard();
                }));
    }

    private static void registerNovas() {
        Spells.add(Spell.of("dark_nova", "Dark Nova", DARK, CHARGE).icon(Glyph.BURST).cost(30).cd(260).charge(40).level(9).health(2f)
                .desc("Gather a sphere of black-violet energy and release an expanding ring that damages and hurls enemies outward. Nearby players are warned while it charges.")
                .perks("Cheaper, faster recharge.", "Unlocks Eclipse Nova.")
                .chargeFx((p, f) -> novaWarning(p, f, VIOLET))
                .onCast(c -> nova(c, 6 + 4 * c.charge, 7, 1.3, VIOLET)));

        Spells.add(Spell.of("eclipse_nova", "Eclipse Nova", DARK, CHARGE).icon(Glyph.BURST).cost(44).cd(420).charge(50).evolvesFrom("dark_nova").health(3f)
                .desc("Dark Nova evolved: a larger, far harder-hitting eclipse ring with a much longer recharge.")
                .chargeFx((p, f) -> novaWarning(p, f, 0xD9A8FF))
                .onCast(c -> nova(c, 9 + 6 * c.charge, 11, 1.7, 0xD9A8FF)));
    }

    /** Ground rune and heartbeat so bystanders can read the incoming blast radius. */
    static void novaWarning(ServerPlayer p, float f, int rgb) {
        ServerLevel l = p.serverLevel();
        Vec3 at = p.position().add(0, 0.1, 0);
        double r = 2 + f * 6;
        Fx.burst(l, smoke(1.2f), p.getEyePosition().add(p.getLookAngle().scale(0.8)), 2, 0.15, 0.02);
        l.sendParticles(Fx.runeDecal(rgb, (float) r), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        Fx.ring(l, Fx.spark(rgb, 0.5f), at, r, 12);
        Fx.sound(l, at, SoundEvents.WARDEN_HEARTBEAT, 0.6f + f * 0.6f, 0.5f + f * 0.4f);
    }

    static int nova(CastCtx c, double radius, double dmg, double push, int rgb) {
        Vec3 at = c.player.position();
        Fx.shockwave(c.level, smoke(1.6f), at.add(0, 0.5, 0), (float) radius, 8);
        Fx.shockwave(c.level, Fx.glow(rgb, 1.5f), at.add(0, 0.3, 0), (float) radius, 8);
        Fx.shockwave(c.level, Fx.spark(0xFFFFFF, 0.9f), at.add(0, 0.9, 0), (float) radius, 8);
        puff(c.level, at.add(0, 1, 0), 40, 0.8);
        Fx.sound(c.level, at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.6f);
        Fx.sound(c.level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.5f);
        int hits = 0;
        for (LivingEntity e : Combat.enemiesIn(c.level, at, radius, c.player)) {
            double falloff = 1.0 - 0.4 * (e.distanceTo(c.player) / radius);
            Combat.hurt(c.level, c.player, c.player, e, c.dmg(dmg * falloff));
            Combat.pushAway(e, at, push * falloff, 0.35);
            hits++;
        }
        return hits;
    }
}
