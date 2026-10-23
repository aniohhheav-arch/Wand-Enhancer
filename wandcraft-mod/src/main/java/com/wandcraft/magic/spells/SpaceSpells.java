package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.SPACE;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellProp;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.CastCtx;
import com.wandcraft.magic.Casting;
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
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Space Magic: teleportation, linked portals and spatial effects. */
final class SpaceSpells {
    static final int VIOLET = SPACE.color;

    private SpaceSpells() {}

    static ParticleOptions rift(float s) { return Fx.glow(VIOLET, s); }

    static void ripple(ServerLevel l, Vec3 at, float radius) {
        Fx.shockwave(l, Fx.spark(0xC8A0FF, 0.8f), at.add(0, 0.15, 0), radius, 5);
        Fx.burst(l, Fx.wisp(0x2A0A55, 1.2f), at.add(0, 1, 0), 10, 0.4, 0.04);
        Fx.burst(l, rift(1f), at.add(0, 1, 0), 14, 0.5, 0.05);
    }

    /** Nearest safe standing point along the look direction, stopping short of obstructions. */
    static Vec3 blinkDest(CastCtx c, double max) {
        ServerPlayer p = c.player;
        Vec3 eye = c.eye(), look = c.look();
        BlockHitResult hit = c.level.clip(new ClipContext(eye, eye.add(look.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double dist = hit.getType() == HitResult.Type.MISS ? max : eye.distanceTo(hit.getLocation()) - 0.6;
        for (double d = dist; d >= 1.0; d -= 0.5) {
            Vec3 feet = eye.add(look.scale(d)).subtract(0, p.getEyeHeight(), 0);
            if (!c.level.noCollision(p, p.getBoundingBox().move(feet.subtract(p.position())))) continue;
            if (!floorOk(c.level, BlockPos.containing(feet))) continue;
            return feet;
        }
        return null;
    }

    static boolean floorOk(ServerLevel l, BlockPos feet) {
        for (int i = 1; i <= 10; i++) {
            var s = l.getBlockState(feet.below(i));
            if (s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.FIRE) || s.is(Blocks.CACTUS)) return false;
            if (!s.getFluidState().isEmpty() && i <= 2) return false;
            if (!s.getCollisionShape(l, feet.below(i)).isEmpty()) return true;
        }
        return false; // nothing solid within ten blocks: likely over the void
    }

    static int teleport(CastCtx c, double range, boolean portalFx) {
        Vec3 dest = blinkDest(c, range);
        if (dest == null) {
            Casting.fail(c.player, "No safe place to land");
            return -1;
        }
        Vec3 from = c.player.position();
        ripple(c.level, from, 2f);
        if (portalFx) {
            SpellProp.spawn(c.level, "portal_violet", from, 0.55f, 26).facing(c.player.getYRot() + 180f, 0f);
        }
        c.player.teleportTo(dest.x, dest.y, dest.z);
        c.player.fallDistance = 0;
        ripple(c.level, dest, portalFx ? 4f : 2.5f);
        if (portalFx) SpellProp.spawn(c.level, "portal_violet", dest, 0.55f, 26).facing(c.player.getYRot(), 0f);
        Fx.line(c.level, Fx.spark(0xC8A0FF, 0.5f), from.add(0, 1, 0), dest.add(0, 1, 0), 0.5);
        Fx.sound(c.level, from, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.4f);
        Fx.sound(c.level, dest, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.0f);
        return 0;
    }

    static void register() {
        Spells.add(Spell.of("blink", "Blink", SPACE, INSTANT).icon(Glyph.WINGS).cost(10).cd(40).level(0)
                .desc("Teleport up to 12 blocks toward your crosshair, stopping at the nearest safe spot before any obstruction.")
                .perks("Cheaper, faster recharge.", "Unlocks Rift Step.")
                .onCast(c -> teleport(c, 12, false)));

        Spells.add(Spell.of("rift_step", "Rift Step", SPACE, INSTANT).icon(Glyph.SWIRL).cost(24).cd(160).evolvesFrom("blink")
                .desc("Blink evolved: step through a small violet rift up to 24 blocks away and leave a rippling portal afterimage.")
                .onCast(c -> teleport(c, 24, true)));

        Spells.add(Spell.of("portal_pair", "Portal Pair", SPACE, INSTANT).icon(Glyph.PORTAL).cost(36).cd(400).level(9)
                .desc("Cast once to open the first portal where you aim; cast again to open the linked second portal within range. Step through either one. Sneak-cast to close them. They close on their own, or if either is blocked.")
                .recast(p -> {
                    int st = Magic.of(p).portalState;
                    return st == 1 || (st == 2 && p.isShiftKeyDown());
                }, 14)
                .zone(PortalSystem.BEHAVIOR)
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    ServerLevel l = c.level;
                    PlayerMagic m = Magic.of(p);
                    SpellZone zone = m.portalZone == null ? null : l.getEntity(m.portalZone) instanceof SpellZone zz && !zz.isRemoved() ? zz : null;
                    if (zone == null && m.portalState != 0) {
                        m.portalState = 0;
                        m.portalZone = null;
                    }
                    // Sneak-cast with a linked pair: dismiss it for free.
                    if (zone != null && m.portalState == 2 && p.isShiftKeyDown()) {
                        zone.discard();
                        c.free = true;
                        c.noCooldown = true;
                        return 0;
                    }
                    BlockHitResult hit = Aim.block(p, 30);
                    if (zone != null && m.portalState == 1 && zone.extra instanceof PortalSystem.Link link) {
                        PortalSystem.Gate b = PortalSystem.fromSurface(l, p, hit, "portal_magenta", 0.78f, 2);
                        if (b == null) return -1;
                        if (b.pos().distanceTo(link.gates[0].pos()) > com.wandcraft.Config.PORTAL_MAX_DISTANCE.get()) {
                            Casting.fail(p, "Too far from the first portal");
                            return -1;
                        }
                        if (b.pos().distanceTo(link.gates[0].pos()) < 2.0) {
                            Casting.fail(p, "Too close to the first portal");
                            return -1;
                        }
                        link.gates[1] = b;
                        link.count = 2;
                        link.props[1] = PortalSystem.spawnProp(l, b, 100000);
                        link.props[1].follow(zone, b.pos().subtract(zone.position()));
                        zone.setMaxAge(zone.age + PortalSystem.duration());
                        m.portalState = 2;
                        m.dirty = true;
                        ripple(l, b.pos(), 3f);
                        Fx.sound(l, b.pos(), SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.8f);
                        Fx.sound(l, link.gates[0].pos(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.9f);
                        Magic.setCooldown(p, c.spell, c.spell.cooldownTicks());
                        return 0;
                    }
                    if (zone != null) zone.discard();
                    PortalSystem.Gate a = PortalSystem.fromSurface(l, p, hit, "portal_violet", 0.78f, 2);
                    if (a == null) return -1;
                    SpellZone z = Proj.zone(c, c.spell, a.pos(), 1f, 1200);
                    if (z == null) return -1;
                    PortalSystem.Link link = new PortalSystem.Link();
                    link.gates[0] = a;
                    link.count = 1;
                    link.props[0] = PortalSystem.spawnProp(l, a, 100000);
                    link.props[0].follow(z, Vec3.ZERO);
                    z.prop = link.props[0];
                    z.extra = link;
                    m.portalZone = z.getUUID();
                    m.portalState = 1;
                    m.dirty = true;
                    c.noCooldown = true;
                    ripple(l, a.pos(), 3f);
                    Fx.sound(l, a.pos(), SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.4f);
                    p.displayClientMessage(net.minecraft.network.chat.Component.literal("First portal open. Cast again to place its partner."), true);
                    return 0;
                }));

        Spells.add(Spell.of("rift_gate", "Rift Gate", SPACE, INSTANT).icon(Glyph.PORTAL).cost(60).cd(1100).level(14).masters(2)
                .desc("Tear open a huge, layered rift on the surface you aim at. Walk through it to arrive where you cast; a smaller rift there leads back. Lasts 90 seconds.")
                .zone(PortalSystem.BEHAVIOR)
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    ServerLevel l = c.level;
                    BlockHitResult hit = Aim.block(p, 30);
                    PortalSystem.Gate big = PortalSystem.fromSurface(l, p, hit, "rift_gate", 0.68f, 3);
                    if (big == null) return -1;
                    Vec3 look = c.look();
                    Vec3 h = new Vec3(look.x, 0, look.z);
                    h = h.lengthSqr() < 1e-3 ? Vec3.directionFromRotation(0, p.getYRot()) : h.normalize();
                    PortalSystem.Gate home = null;
                    for (double back : new double[]{1.0, 1.6, 2.2}) {
                        Vec3 pos = p.position().subtract(h.scale(back));
                        var gr = Aim.stepGround(l, BlockPos.containing(pos).below());
                        if (gr.isEmpty()) continue;
                        PortalSystem.Gate cand = new PortalSystem.Gate(Vec3.atBottomCenterOf(gr.get()), h, "portal_magenta", 0.7f, 2, false);
                        if (PortalSystem.stillValid(l, cand) && l.mayInteract(p, gr.get()) && cand.pos().distanceTo(big.pos()) > 3) {
                            home = cand;
                            break;
                        }
                    }
                    if (home == null) {
                        Casting.fail(p, "No room for the arrival rift behind you");
                        return -1;
                    }
                    SpellZone z = Proj.zone(c, c.spell, big.pos(), 1f, PortalSystem.gateDuration());
                    if (z == null) return -1;
                    PortalSystem.Link link = new PortalSystem.Link();
                    link.gates[0] = big;
                    link.gates[1] = home;
                    link.count = 2;
                    link.props[0] = PortalSystem.spawnProp(l, big, 100000);
                    link.props[1] = PortalSystem.spawnProp(l, home, 100000);
                    link.props[0].follow(z, Vec3.ZERO);
                    link.props[1].follow(z, home.pos().subtract(big.pos()));
                    z.prop = link.props[0];
                    z.extra = link;
                    ripple(l, big.pos().add(0, 1, 0), 5f);
                    ripple(l, home.pos(), 3f);
                    Fx.sound(l, big.pos(), SoundEvents.END_PORTAL_SPAWN, 1f, 0.9f);
                    Fx.sound(l, big.pos(), SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 1.6f);
                    return 0;
                }));

        Spells.add(Spell.of("blink_rift", "Blink Rift", SPACE, INSTANT).icon(Glyph.PORTAL).cost(18).cd(100).level(6)
                .desc("Teleport up to 16 blocks toward your crosshair through two circular rifts: one where you leave, one where you land. Both collapse into fading violet motes.")
                .onCast(c -> {
                    Vec3 dest = blinkDest(c, 16);
                    if (dest == null) {
                        Casting.fail(c.player, "No safe place to land");
                        return -1;
                    }
                    ServerLevel l = c.level;
                    Vec3 from = c.player.position();
                    Vec3 travel = dest.subtract(from);
                    float yaw = (float) (Mth.atan2(-travel.x, travel.z) * Mth.RAD_TO_DEG);
                    SpellProp a = SpellProp.spawn(l, "rift_circle", from, 0.6f, 34).facing(yaw, 0f);
                    ripple(l, from, 2.5f);
                    Fx.sound(l, from, SoundEvents.PORTAL_TRIGGER, 0.2f, 1.9f);
                    c.player.teleportTo(dest.x, dest.y, dest.z);
                    c.player.fallDistance = 0;
                    SpellProp b = SpellProp.spawn(l, "rift_circle", dest, 0.6f, 34).facing(yaw + 180f, 0f);
                    ripple(l, dest, 3f);
                    Fx.line(l, Fx.spark(0xC8A0FF, 0.5f), from.add(0, 1, 0), dest.add(0, 1, 0), 0.45);
                    Fx.sound(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.5f);
                    Fx.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.1f);
                    // The rifts collapse shortly after, leaving a few fading violet motes.
                    Scheduler.after(l, 26, () -> {
                        for (Vec3 at : new Vec3[]{from, dest}) {
                            Fx.burst(l, Fx.glow(0x9A5BFF, 0.9f), at.add(0, 1, 0), 22, 0.6, 0.03);
                            Fx.burst(l, Fx.wisp(0x2A0A55, 0.9f), at.add(0, 1, 0), 8, 0.5, 0.02);
                        }
                    });
                    return 0;
                }));

        Spells.add(Spell.of("spatial_anchor", "Spatial Anchor", SPACE, INSTANT).icon(Glyph.STAR).cost(18).cd(40).level(3).stealthSafe()
                .desc("Plant a temporary anchor where you stand. Cast again within 30 seconds to snap back, if the spot is still clear.")
                .recast(p -> {
                    PlayerMagic m = Magic.of(p);
                    return m.anchorPos != null && m.anchorUntil > p.level().getGameTime() && m.anchorDim == p.level().dimension();
                }, 12)
                .onCast(c -> {
                    PlayerMagic m = Magic.of(c.player);
                    long now = c.level.getGameTime();
                    if (m.anchorPos != null && m.anchorUntil > now && m.anchorDim == c.level.dimension()) {
                        Vec3 a = m.anchorPos;
                        var bb = c.player.getBoundingBox().move(a.subtract(c.player.position()));
                        if (!c.level.noCollision(c.player, bb) || !floorOk(c.level, BlockPos.containing(a)) || !c.level.getWorldBorder().isWithinBounds(bb)) {
                            Casting.fail(c.player, "The anchor is obstructed");
                            m.anchorPos = null;
                            return -1;
                        }
                        Vec3 from = c.player.position();
                        ripple(c.level, from, 2.5f);
                        c.player.teleportTo(a.x, a.y, a.z);
                        c.player.fallDistance = 0;
                        ripple(c.level, a, 2.5f);
                        Fx.sound(c.level, a, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.8f);
                        m.anchorPos = null;
                        return 0;
                    }
                    m.anchorPos = c.player.position();
                    m.anchorDim = c.level.dimension();
                    m.anchorUntil = now + 600;
                    SpellProp.spawn(c.level, "rune_sigil", c.player.position(), 0.9f, 600);
                    Fx.rune(c.level, c.player.position().add(0, 0.1, 0), 1.2, VIOLET, 14, 0);
                    Fx.sound(c.level, c.player.position(), SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("gravity_swap", "Gravity Swap", SPACE, INSTANT).icon(Glyph.WAVE).cost(22).cd(200).level(7)
                .desc("Flip the vertical momentum of ordinary enemies in a small area so they float upward for a moment before gravity returns.")
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 24).pos();
                    java.util.List<LivingEntity> foes = Combat.enemiesIn(c.level, at, 5, c.player).stream().filter(e -> !Combat.isHeavy(e)).toList();
                    if (foes.isEmpty()) {
                        Casting.fail(c.player, "Nothing to lift");
                        return -1;
                    }
                    Fx.shockwave(c.level, rift(1.2f), at.add(0, 0.2, 0), 5, 5);
                    Fx.sound(c.level, at, SoundEvents.BREEZE_JUMP, 1f, 0.5f);
                    Scheduler.repeat(c.level, 0, 1, 14, i -> {
                        for (LivingEntity e : foes) {
                            if (!e.isAlive()) continue;
                            Vec3 v = e.getDeltaMovement();
                            e.setDeltaMovement(v.x * 0.9, i < 11 ? 0.32 : -0.45, v.z * 0.9);
                            e.hurtMarked = true;
                            e.fallDistance = 0;
                            if (i % 3 == 0) Fx.burst(c.level, Fx.spark(0xC8A0FF, 0.5f), e.position().add(0, 0.2, 0), 2, 0.3, 0.02);
                        }
                    });
                    return foes.size();
                }));

        Spells.add(Spell.of("rift_snare", "Rift Snare", SPACE, INSTANT).icon(Glyph.SWIRL).cost(20).cd(180).level(6)
                .desc("Tear a small rift beneath a target. It slows and drags the target toward its centre, then collapses with a small implosion.")
                
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        Vec3 c = z.position();
                        for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), z.owner())) {
                            Combat.slow(e, 12, 3);
                            if (Combat.isBoss(e)) continue;
                            Vec3 to = c.subtract(e.position());
                            if (to.lengthSqr() > 0.2) {
                                Vec3 v = e.getDeltaMovement().add(to.normalize().scale(0.1));
                                e.setDeltaMovement(v.x, v.y, v.z);
                                e.hurtMarked = true;
                            }
                        }
                        if (z.age % 6 == 0) Fx.sound(l, c, SoundEvents.PORTAL_AMBIENT, 0.3f, 1.6f);
                        if (z.age % 3 == 0) {
                            // shadowy tendrils reach in from the rim toward the centre
                            double a = l.random.nextDouble() * Math.PI * 2;
                            DarkSpells.tendril(l, c.add(Math.cos(a) * z.radius() * 0.9, 0.1, Math.sin(a) * z.radius() * 0.9), c.add(0, 0.6, 0), z.age);
                        }
                    }
                    @Override public void end(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        if (z.extra instanceof SpellProp rift) rift.dismiss();
                        DarkSpells.implode(l, z.position().add(0, 0.8, 0), z.radius(), 26);
                        Fx.sound(l, z.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.4f);
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius() * 0.8, z.owner())) {
                            Combat.hurt(l, z.owner(), z.owner(), e, 5 * z.power);
                            n++;
                        }
                        z.credit(n);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        Fx.c(z.level(), rift(0.9f), z.getX() + (rnd.nextDouble() - .5) * z.radius(), z.getY() + 0.1, z.getZ() + (rnd.nextDouble() - .5) * z.radius(), 0, 0.04, 0);
                    }
                })
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 22);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    SpellZone sz = Proj.zone(c, c.spell, t.position(), 2.6f, 55);
                    if (sz == null) return -1;
                    SpellProp rift = SpellProp.spawn(c.level, "rift_floor", t.position().add(0, 0.05, 0), 2.1f, 58);
                    sz.extra = rift;
                    Scheduler.after(c.level, 12, () -> { if (!rift.isRemoved()) rift.act(); });
                    Fx.sound(c.level, t.position(), SoundEvents.END_PORTAL_FRAME_FILL, 1f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("dimensional_slash", "Dimensional Slash", SPACE, INSTANT).icon(Glyph.BEAM).cost(18).cd(80).level(5).echoable()
                .desc("Send a thin wave of cut space forward. It passes through up to four targets and is stopped by terrain.")
                .projectile(new ProjBehavior() {
                    private void sync(SpellProjectile p) {
                        if (p.extra instanceof SpellProp pr && !pr.isRemoved()) {
                            pr.setPos(p.getX(), p.getY(), p.getZ());
                        }
                    }
                    @Override public void tick(SpellProjectile p) {
                        sync(p);
                        ServerLevel l = (ServerLevel) p.level();
                        if (p.age % 2 == 0) Fx.burst(l, Fx.spark(0xDCC8FF, 0.6f), p.position(), 3, 0.5, 0.02);
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) {
                        ServerLevel l = (ServerLevel) p.level();
                        Combat.hurt(l, p.getOwner(), p, t, p.damage);
                        p.credit(1);
                        Fx.burst(l, Fx.spark(0xFFFFFF, 0.9f), t.position().add(0, t.getBbHeight() / 2, 0), 8, 0.3, 0.2);
                        Fx.sound(l, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
                        return true;
                    }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) {
                        if (p.extra instanceof SpellProp pr) pr.dismiss();
                        Fx.burst((ServerLevel) p.level(), Fx.spark(0xDCC8FF, 0.8f), r.getLocation(), 8, 0.3, 0.1);
                        return true;
                    }
                    @Override public void expire(SpellProjectile p) { if (p.extra instanceof SpellProp pr) pr.dismiss(); }
                })
                .onCast(c -> {
                    SpellProjectile p = Proj.launch(c, c.spell, c.eye().add(c.look().scale(1.0)), c.look(), 2.2, 0, c.dmg(9), 9);
                    if (p == null) return -1;
                    p.pierce = 3;
                    SpellProp pr = SpellProp.spawn(c.level, "slash_wave", p.position(), 0.55f, 14);
                    pr.facing(c.player.getYRot() + 180f, 0f);
                    p.extra = pr;
                    Fx.sound(c.level, c.eye(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("pocket_rift", "Pocket Rift", SPACE, INSTANT).icon(Glyph.SWIRL).cost(20).cd(100).level(8).stealthSafe()
                .desc("Open a small rift holding nine item slots. It closes after 20 seconds, and the items always stay safely inside for next time.")
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    PlayerMagic m = Magic.of(p);
                    p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inv, m.pocket, 1), Component.literal("Pocket Rift")));
                    SpellProp.spawn(c.level, "portal_violet", p.position(), 0.5f, 400).frontOf(p, 1.3, -1.1).facing(p.getYRot(), 0);
                    Fx.sound(c.level, p.position(), SoundEvents.ENDER_CHEST_OPEN, 1f, 0.8f);
                    Scheduler.after(c.level, 400, () -> {
                        if (p.containerMenu instanceof ChestMenu cm && cm.getContainer() == m.pocket) {
                            p.closeContainer();
                            Fx.sound(c.level, p.position(), SoundEvents.ENDER_CHEST_CLOSE, 1f, 0.8f);
                        }
                    });
                    return 0;
                }));
    }
}
