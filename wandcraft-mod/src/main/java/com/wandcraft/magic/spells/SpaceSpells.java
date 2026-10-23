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

    record Gate(Vec3 pos, Vec3 normal) {
        float yaw() { return (float) (Mth.atan2(-normal.x, normal.z) * Mth.RAD_TO_DEG); }
    }

    static boolean gateFree(ServerLevel l, ServerPlayer p, Vec3 base) {
        BlockPos bp = BlockPos.containing(base);
        if (!l.mayInteract(p, bp)) return false;
        for (int h = 0; h < 2; h++) {
            BlockPos q = bp.above(h);
            if (!l.getBlockState(q).getCollisionShape(l, q).isEmpty()) return false;
        }
        return true;
    }

    static void register() {
        Spells.add(Spell.of("blink", "Blink", SPACE, INSTANT).icon(Glyph.WINGS).cost(10).cd(40).level(0)
                .desc("Teleport up to 12 blocks toward your crosshair, stopping at the nearest safe spot before any obstruction.")
                .perks("Cheaper, faster recharge.", "Unlocks Rift Step.")
                .onCast(c -> teleport(c, 12, false)));

        Spells.add(Spell.of("rift_step", "Rift Step", SPACE, INSTANT).icon(Glyph.SWIRL).cost(24).cd(160).evolvesFrom("blink")
                .desc("Blink evolved: step through a small violet rift up to 24 blocks away and leave a rippling portal afterimage.")
                .onCast(c -> teleport(c, 24, true)));

        Spells.add(Spell.of("portal_pair", "Portal Pair", SPACE, INSTANT).icon(Glyph.SWIRL).cost(36).cd(400).level(9)
                .desc("Open two linked portals: one where you aim, one in front of you. You and other creatures can step through. They close after 15 seconds.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 2 != 0 || !(z.extra instanceof Gate[] gates)) return;
                        ServerLevel l = (ServerLevel) z.level();
                        long now = l.getGameTime();
                        for (int i = 0; i < 2; i++) {
                            Gate g = gates[i], other = gates[1 - i];
                            Vec3 centre = g.pos().add(0, 1.2, 0);
                            for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(centre, centre).inflate(0.9, 1.3, 0.9),
                                    e -> (e instanceof LivingEntity le && le.isAlive() && !le.isSpectator()) || e instanceof ItemEntity)) {
                                if (e.getPersistentData().getLong("wc_portal_t") > now) continue;
                                Vec3 dest = other.pos().add(other.normal().scale(1.1));
                                e.getPersistentData().putLong("wc_portal_t", now + 30);
                                Vec3 v = e.getDeltaMovement();
                                float yaw = other.yaw() + 180f;
                                e.teleportTo(l, dest.x, dest.y, dest.z, Set.of(), yaw, e.getXRot());
                                e.setDeltaMovement(other.normal().scale(Math.max(0.3, v.length())));
                                e.hurtMarked = true;
                                Fx.burst(l, rift(1f), dest.add(0, 1, 0), 14, 0.4, 0.1);
                                Fx.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.2f);
                                if (z.prop != null) z.prop.act();
                                if (e != z.owner()) z.credit(1);
                            }
                        }
                    }
                })
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    BlockHitResult hit = Aim.block(p, 28);
                    if (hit.getType() != HitResult.Type.BLOCK) {
                        Casting.fail(p, "Aim at a surface");
                        return -1;
                    }
                    Vec3 n;
                    Vec3 base;
                    if (hit.getDirection().getAxis().isHorizontal()) {
                        n = new Vec3(hit.getDirection().getStepX(), 0, hit.getDirection().getStepZ());
                        base = hit.getLocation().add(n.scale(0.05));
                        base = new Vec3(base.x, Math.floor(base.y), base.z);
                    } else {
                        Vec3 toP = p.position().subtract(hit.getLocation());
                        n = new Vec3(toP.x, 0, toP.z).normalize();
                        base = hit.getLocation().add(0, hit.getDirection() == net.minecraft.core.Direction.UP ? 0.02 : -2.0, 0);
                    }
                    Vec3 look = c.look();
                    Vec3 h = new Vec3(look.x, 0, look.z).normalize();
                    Vec3 near = p.position().add(h.scale(2.6));
                    var ground = Aim.stepGround(c.level, BlockPos.containing(near).below());
                    if (ground.isEmpty()) {
                        Casting.fail(p, "No room for the second portal");
                        return -1;
                    }
                    Vec3 b2 = Vec3.atBottomCenterOf(ground.get());
                    Gate a = new Gate(base, n), b = new Gate(b2, h.scale(-1));
                    if (!gateFree(c.level, p, a.pos()) || !gateFree(c.level, p, b.pos()) || a.pos().distanceTo(b.pos()) < 3) {
                        Casting.fail(p, "Portals need open space");
                        return -1;
                    }
                    SpellZone z = Proj.zone(c, c.spell, a.pos(), 1f, 300);
                    if (z == null) return -1;
                    z.extra = new Gate[]{a, b};
                    SpellProp pa = SpellProp.spawn(c.level, "portal_blue", a.pos(), 0.9f, 320).facing(a.yaw(), 0f);
                    SpellProp pb = SpellProp.spawn(c.level, "portal_orange", b.pos(), 0.9f, 320).facing(b.yaw(), 0f);
                    pa.follow(z, Vec3.ZERO);
                    z.prop = pa;
                    pb.follow(z, b.pos().subtract(a.pos()));
                    ripple(c.level, a.pos(), 3f);
                    ripple(c.level, b.pos(), 3f);
                    Fx.sound(c.level, a.pos(), SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.6f);
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
                .prop("void_orb", 0.18f, 0.9)
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
                    }
                    @Override public void end(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
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
                    if (Proj.zone(c, c.spell, t.position(), 2.6f, 55) == null) return -1;
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
