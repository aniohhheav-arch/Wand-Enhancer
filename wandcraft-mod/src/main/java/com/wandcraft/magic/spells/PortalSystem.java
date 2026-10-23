package com.wandcraft.magic.spells;

import com.wandcraft.Config;
import com.wandcraft.entity.SpellProp;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Fx;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Shared machinery for linked portals: placement validation, safe teleportation with momentum, loop protection and cleanup. */
final class PortalSystem {
    /** A portal standing on the ground or against a wall. {@code normal} is horizontal and points out of the surface. */
    record Gate(Vec3 pos, Vec3 normal, String kind, float scale, int cells, boolean wall) {
        float yaw() { return (float) (Mth.atan2(-normal.x, normal.z) * Mth.RAD_TO_DEG); }
        Vec3 side() { return new Vec3(-normal.z, 0, normal.x); }
        double halfWidth() { return 0.45 + 0.5 * scale; }
        Vec3 centre() { return pos.add(0, cells * 0.5, 0); }
    }

    /** Stored in {@link SpellZone#extra}. */
    static final class Link {
        final Gate[] gates = new Gate[2];
        final SpellProp[] props = new SpellProp[2];
        int count;
    }

    private PortalSystem() {}

    // --- placement --------------------------------------------------------------------------------------------------

    private static boolean free(ServerLevel l, BlockPos p) {
        return l.getBlockState(p).getCollisionShape(l, p).isEmpty() && l.getBlockState(p).getFluidState().isEmpty();
    }

    private static boolean cellsFree(ServerLevel l, BlockPos base, int cells) {
        for (int h = 0; h < cells; h++) if (!free(l, base.above(h))) return false;
        return true;
    }

    /** Builds a gate from a targeted surface, or null (with feedback) if there is no room or it is protected. */
    static Gate fromSurface(ServerLevel l, ServerPlayer p, BlockHitResult hit, String kind, float scale, int cells) {
        if (hit.getType() != HitResult.Type.BLOCK) {
            Casting.fail(p, "Aim at a surface");
            return null;
        }
        Direction face = hit.getDirection();
        Vec3 loc = hit.getLocation();
        Vec3 n;
        BlockPos base = null;
        boolean wall = face.getAxis().isHorizontal();
        if (wall) {
            n = new Vec3(face.getStepX(), 0, face.getStepZ());
            Vec3 front = loc.add(n.scale(0.1));
            int y0 = Mth.floor(loc.y);
            for (int dy = 0; dy >= -cells + 1 && base == null; dy--) {
                BlockPos b = new BlockPos(Mth.floor(front.x), y0 + dy, Mth.floor(front.z));
                if (cellsFree(l, b, cells) && !free(l, b.relative(face.getOpposite()).above())) base = b;
            }
        } else {
            Vec3 toP = p.position().subtract(loc);
            n = new Vec3(toP.x, 0, toP.z);
            n = n.lengthSqr() < 1e-3 ? p.getLookAngle().multiply(-1, 0, -1).normalize() : n.normalize();
            BlockPos b = face == Direction.UP ? BlockPos.containing(loc) : BlockPos.containing(loc).below(cells);
            if (cellsFree(l, b, cells)) base = b;
        }
        if (base == null) {
            Casting.fail(p, "No room for a portal there");
            return null;
        }
        if (!l.mayInteract(p, base)) {
            Casting.fail(p, "That area is protected");
            return null;
        }
        Vec3 pos = wall ? new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5).subtract(n.scale(0.43))
                : new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        return new Gate(pos, n, kind, scale, cells, wall);
    }

    static SpellProp spawnProp(ServerLevel l, Gate g, int life) {
        SpellProp pr = SpellProp.spawn(l, g.kind(), g.pos(), g.scale(), life);
        pr.facing(g.yaw(), 0f);
        return pr;
    }

    static boolean stillValid(ServerLevel l, Gate g) {
        BlockPos base = BlockPos.containing(g.pos().add(g.normal().scale(0.5)));
        if (!cellsFree(l, base, g.cells())) return false;
        if (g.wall()) {
            BlockPos behind = BlockPos.containing(g.pos().subtract(g.normal().scale(0.2))).above();
            return !free(l, behind);
        }
        return true;
    }

    // --- teleportation ---------------------------------------------------------------------------------------------

    private static boolean eligible(Entity e) {
        if (e instanceof Player p) return !p.isSpectator() && p.isAlive();
        if (e instanceof LivingEntity le) return le.isAlive() && !le.getType().is(Tags.EntityTypes.BOSSES);
        return e instanceof ItemEntity;
    }

    /** First collision-free, non-hazardous spot in front of {@code g} for {@code e}. */
    static Vec3 safeExit(ServerLevel l, Entity e, Gate g) {
        for (double d : new double[]{1.1, 1.6, 2.2, 0.8}) {
            Vec3 cand = g.pos().add(g.normal().scale(d));
            AABB bb = e.getBoundingBox().move(cand.subtract(e.position()));
            if (!l.noCollision(e, bb)) continue;
            if (!SpaceSpells.floorOk(l, BlockPos.containing(cand))) continue;
            return cand;
        }
        return null;
    }

    private static void teleport(ServerLevel l, Entity e, Gate in, Gate out, Vec3 dest, SpellZone z, int outIndex) {
        Vec3 v = e.getDeltaMovement();
        Vec3 s1 = in.side();
        double along = v.dot(s1), depth = v.dot(in.normal()), up = v.y;
        // Entering from the front means moving against the normal; leave along the exit normal, mirrored sideways.
        double fwd = Math.max(0.12, Math.abs(depth));
        Vec3 nv = out.normal().scale(fwd).add(out.side().scale(-along)).add(0, up, 0);
        float yaw = e.getYRot() + (out.yaw() - in.yaw()) + 180f;
        e.teleportTo(l, dest.x, dest.y, dest.z, Set.of(), yaw, e.getXRot());
        e.setDeltaMovement(nv);
        e.hurtMarked = true;
        e.fallDistance = 0;
        e.getPersistentData().putLong("wc_portal_t", l.getGameTime() + 40);
        // arrival ripple in the plane of the exit portal
        Vec3 c = out.centre();
        Vec3 s2 = out.side();
        for (int i = 0; i < Fx.scaled(18); i++) {
            double a = Math.PI * 2 * i / Fx.scaled(18);
            Vec3 p = c.add(s2.scale(Math.cos(a) * 0.9)).add(0, Math.sin(a) * 1.2, 0).add(out.normal().scale(0.1));
            Vec3 vel = out.normal().scale(0.12).add(s2.scale(Math.cos(a) * 0.05)).add(0, Math.sin(a) * 0.05, 0);
            l.sendParticles(Fx.spark(0xC8A0FF, 0.6f), p.x, p.y, p.z, 0, vel.x, vel.y, vel.z, 1.0);
        }
        Fx.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.2f);
        Fx.sound(l, dest, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f);
        if (z.prop != null) z.prop.act();
        if (e != z.owner() && !(e instanceof ItemEntity)) z.credit(1);
    }

    /** Zone behaviour for any linked portal pair; spells fill {@link Link} and the zone's lifetime. */
    static final ZoneBehavior BEHAVIOR = new ZoneBehavior() {
        @Override
        public void tick(SpellZone z) {
            if (!(z.extra instanceof Link link) || !(z.level() instanceof ServerLevel l)) return;
            long now = l.getGameTime();
            // Either portal becoming invalid closes the whole link.
            if (z.age % 10 == 0) {
                for (int i = 0; i < link.count; i++) {
                    if (!stillValid(l, link.gates[i])) {
                        Fx.sound(l, link.gates[i].pos(), SoundEvents.GLASS_BREAK, 0.8f, 0.6f);
                        z.discard();
                        return;
                    }
                }
            }
            if (z.age % 40 == 0) {
                for (int i = 0; i < link.count; i++) Fx.sound(l, link.gates[i].centre(), SoundEvents.PORTAL_AMBIENT, 0.12f, 0.6f);
            }
            if (z.age % 4 == 0) {
                for (int i = 0; i < link.count; i++) inflow(l, link.gates[i]);
            }
            if (link.count < 2) return;
            for (int i = 0; i < 2; i++) {
                Gate g = link.gates[i], other = link.gates[1 - i];
                Vec3 c = g.centre();
                double reach = Math.max(g.cells(), 2) * 0.5 + 0.4;
                for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(g.halfWidth() + 0.5, reach, g.halfWidth() + 0.5))) {
                    if (!eligible(e) || e.getPersistentData().getLong("wc_portal_t") > now) continue;
                    Vec3 rel = e.position().subtract(g.pos());
                    double depth = rel.dot(g.normal()), along = rel.dot(g.side());
                    if (Math.abs(depth) > 0.6 || Math.abs(along) > g.halfWidth() || rel.y < -0.4 || rel.y > g.cells() + 0.2) continue;
                    Vec3 dest = safeExit(l, e, other);
                    if (dest == null) {
                        // Blocked on the far side: bounce back softly instead of risking suffocation.
                        e.getPersistentData().putLong("wc_portal_t", now + 20);
                        continue;
                    }
                    teleport(l, e, g, other, dest, z, 1 - i);
                    if (link.props[1 - i] != null) link.props[1 - i].act();
                }
            }
        }

        @Override
        public void end(SpellZone z) {
            ServerLevel l = (ServerLevel) z.level();
            if (z.extra instanceof Link link) {
                for (int i = 0; i < link.count; i++) {
                    Gate g = link.gates[i];
                    if (link.props[i] != null) link.props[i].dismiss();
                    Fx.burst(l, Fx.glow(0xA060FF, 1f), g.centre(), 18, 0.5, 0.04);
                    Fx.burst(l, Fx.wisp(0x3A1070, 1f), g.centre(), 8, 0.4, 0.03);
                    Fx.sound(l, g.centre(), SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.8f);
                }
            }
            ServerPlayer o = z.owner();
            if (o != null) {
                PlayerMagic m = Magic.of(o);
                if (m.portalZone != null && m.portalZone.equals(z.getUUID())) {
                    m.portalZone = null;
                    m.portalState = 0;
                    m.dirty = true;
                }
            }
        }
    };

    /** Particles drifting from a ring around the portal into its centre. */
    static void inflow(ServerLevel l, Gate g) {
        Vec3 c = g.centre();
        Vec3 s = g.side();
        int n = Math.max(1, Fx.scaled(3));
        for (int i = 0; i < n; i++) {
            double a = l.random.nextDouble() * Math.PI * 2;
            double rx = 1.1 * g.scale() * 1.4, ry = g.cells() * 0.6;
            Vec3 off = s.scale(Math.cos(a) * rx).add(0, Math.sin(a) * ry, 0).add(g.normal().scale(0.5 + l.random.nextDouble() * 0.6));
            Vec3 vel = off.scale(-0.07);
            l.sendParticles(i % 2 == 0 ? Fx.glow(0xB070FF, 0.6f) : Fx.spark(0xE0C8FF, 0.5f), c.x + off.x, c.y + off.y, c.z + off.z, 0, vel.x, vel.y, vel.z, 1.0);
        }
    }

    static int ticks(int seconds) { return seconds * 20; }

    static int duration() { return ticks(Config.PORTAL_DURATION_SECONDS.get()); }
    static int gateDuration() { return ticks(Config.RIFT_GATE_DURATION_SECONDS.get()); }
}
