package dev.mysticarts.power.spells;

import dev.mysticarts.block.SpellBlock;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.SlingPortalEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Source;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.MirrorDimension;
import dev.mysticarts.world.TemporaryBlocks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The Space Stone: the fabric of distance itself. */
public final class SpaceSpells {
    public static final int BLUE = 0x2F7BFF;

    private SpaceSpells() {}

    public static void register() {
        Spells.register(Ability.SPACE_TELEPORT, SpaceSpells::teleport);
        Spells.register(Ability.SPACE_TRAVEL, SpaceSpells::travel);
        Spells.register(Ability.SPACE_PORTAL, SpaceSpells::portal);
        Spells.register(Ability.SPACE_DISPLACE, SpaceSpells::displace);
        Spells.register(Ability.SPACE_SWAP, SpaceSpells::swap);
        Spells.register(Ability.SPACE_PRISON, SpaceSpells::prison);
        Spells.register(Ability.SPACE_GRAVITY, c -> {
            Vec3 at = Aim.point(c.player(), 24);
            SpellFieldEntity.spawn(c.level(), c.player(), at.add(0, 1, 0), FieldKind.GRAVITY, 8f, 110, BLUE);
            Fx.sound(c.level(), at, MaSounds.STONE_SPACE.get(), 1.2f, 0.6f);
            return true;
        });
        Spells.register(Ability.SPACE_DISTORTION, SpaceSpells::distortion);
        Spells.register(Ability.SPACE_RIFT, c -> {
            Vec3 at = Aim.point(c.player(), 24);
            SpellFieldEntity.spawn(c.level(), c.player(), at.add(0, 1.2, 0), FieldKind.COSMIC_RIFT, 3f, 120, BLUE);
            Fx.sound(c.level(), at, MaSounds.RIFT_OPEN.get(), 1.4f, 1f);
            return true;
        });
        Spells.register(Ability.SPACE_BARRIER, c -> RealitySpells.wall(c, MaBlocks.SPELL_BARRIER.get().defaultBlockState()
                .setValue(SpellBlock.TINT, SpellBlock.tintOf(Source.SPACE)), 3, 7, 4, 240));
        Spells.windup(Ability.SPACE_ULTIMATE, 60, SpaceSpells::ultimateStart, SpaceSpells::ultimateFinish);
    }

    private static boolean teleport(Cast c) {
        ServerPlayer p = c.player();
        Optional<Vec3> spot = Aim.safeLookSpot(c.level(), p, 64);
        if (spot.isEmpty()) return false;
        Vec3 from = p.position();
        p.connection.teleport(spot.get().x, spot.get().y, spot.get().z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        Fx.teleport(c.level(), from.add(0, 1, 0), spot.get().add(0, 1, 0), BLUE);
        Fx.sound(c.level(), from, MaSounds.DASH.get(), 1f, 0.8f);
        Fx.sound(c.level(), spot.get(), MaSounds.PORTAL_TRAVEL.get(), 1f, 1.4f);
        return true;
    }

    /**
     * Steps between dimensions: Overworld and Nether (with the usual 8:1 scaling), sneak-cast for the End, and from the
     * End or Mirror back to the Overworld. Always lands on a safe spot, conjuring a temporary platform if needed.
     */
    private static boolean travel(Cast c) {
        ServerPlayer p = c.player();
        ServerLevel here = c.level();
        if (MirrorDimension.isMirror(here)) {
            MirrorDimension.exit(p, c.data());
            return true;
        }
        ServerLevel to;
        Vec3 target;
        if (c.sneaking() && here.dimension() != Level.END) {
            to = p.server.getLevel(Level.END);
            target = new Vec3(100.5, 50, 0.5);
        } else if (here.dimension() == Level.NETHER) {
            to = p.server.overworld();
            target = new Vec3(p.getX() * 8, 0, p.getZ() * 8);
        } else if (here.dimension() == Level.OVERWORLD) {
            to = p.server.getLevel(Level.NETHER);
            target = new Vec3(p.getX() / 8, 0, p.getZ() / 8);
        } else {
            to = p.server.overworld();
            target = Vec3.atBottomCenterOf(to.getSharedSpawnPos());
        }
        if (to == null) return false;
        Vec3 land = findColumn(to, p, target);
        Vec3 from = p.position();
        SlingPortalEntity.open(here, p, SlingPortalEntity.KIND_SPACE, from.add(0, 1, 0), p.getYRot() + 180, 0, 1.6f, BLUE, 40);
        p.teleportTo(to, land.x, land.y, land.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        SlingPortalEntity.open(to, p, SlingPortalEntity.KIND_SPACE, land.add(0, 1, 0), p.getYRot(), 0, 1.6f, BLUE, 40);
        Fx.screen(to, land, 8, 0.8f, 0.6f, BLUE);
        Fx.sound(to, land, MaSounds.PORTAL_TRAVEL.get(), 1.4f, 0.7f);
        return true;
    }

    /** A safe landing in the given column: scans down from the top (skipping nether roofs), else builds a platform. */
    static Vec3 findColumn(ServerLevel level, Entity e, Vec3 at) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        level.getChunk(x >> 4, z >> 4);
        int top = level.dimension() == Level.NETHER ? 120 : level.getMaxBuildHeight() - 2;
        int bottom = level.getMinBuildHeight() + 2;
        for (int dx = 0; dx <= 8; dx += 4) {
            for (int y = top; y >= bottom; y--) {
                Vec3 feet = new Vec3(x + dx + 0.5, y, z + 0.5);
                if (Aim.safe(level, e, feet)) return feet;
            }
        }
        int y = level.dimension() == Level.END ? 50 : Math.max(bottom + 10, 70);
        BlockPos base = new BlockPos(x, y - 1, z);
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        BlockState floor = MaBlocks.SPELL_BARRIER.get().defaultBlockState().setValue(SpellBlock.TINT, SpellBlock.tintOf(Source.SPACE));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = base.offset(dx, 0, dz);
                if (TemporaryBlocks.replaceable(level, p, true)) temp.place(level, p, floor, 1200);
                for (int up = 1; up <= 2; up++) {
                    BlockPos a = p.above(up);
                    if (!level.getBlockState(a).isAir() && TemporaryBlocks.replaceable(level, a, false)) temp.place(level, a, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 1200);
                }
            }
        }
        return Vec3.atBottomCenterOf(base.above());
    }

    private static boolean portal(Cast c) {
        ServerPlayer p = c.player();
        PowerData.Anchor anchor = c.data().spaceAnchor;
        ServerLevel destLevel;
        Vec3 dest;
        float yaw = p.getYRot();
        if (anchor != null) {
            destLevel = p.server.getLevel(anchor.dimension());
            if (destLevel == null) return false;
            dest = anchor.pos();
            yaw = anchor.yaw();
        } else {
            destLevel = c.level();
            Optional<Vec3> spot = Aim.safeLookSpot(c.level(), p, 256);
            if (spot.isEmpty()) return false;
            dest = spot.get();
        }
        Vec3 entry = p.getEyePosition().add(Aim.horizontal(c.look()).scale(2.6)).subtract(0, 0.4, 0);
        Vec3 exit = dest.add(0, 1.2, 0).add(Vec3.directionFromRotation(0, yaw).scale(-0.6));
        SlingPortalEntity.pair(c.level(), p, SlingPortalEntity.KIND_SPACE, entry, p.getYRot() + 180, destLevel, exit, yaw, 1.6f, BLUE, 900);
        c.data().pose(Poses.CIRCLE, 20);
        return true;
    }

    private static boolean displace(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 32, e -> !MysticSpells.pvpBlocked(c, e));
        if (t == null) return false;
        ServerLevel level = c.level();
        for (int tries = 0; tries < 8; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = 12 + level.random.nextDouble() * 12;
            Vec3 to = t.position().add(Math.cos(a) * d, 2, Math.sin(a) * d);
            Optional<Vec3> spot = Aim.safeSpot(level, t, to, 4);
            if (spot.isPresent()) {
                Vec3 from = t.position();
                Aim.moveTo(t, spot.get());
                if (t instanceof Mob m) m.getNavigation().stop();
                Fx.teleport(level, from.add(0, 1, 0), spot.get().add(0, 1, 0), BLUE);
                Fx.sound(level, from, MaSounds.DASH.get(), 1f, 0.7f);
                return true;
            }
        }
        return false;
    }

    private static boolean swap(Cast c) {
        ServerPlayer p = c.player();
        LivingEntity t = Aim.entity(p, 48, e -> !MysticSpells.pvpBlocked(c, e) && Aim.controllable(e));
        if (t == null) return false;
        Vec3 a = p.position(), b = t.position();
        float yaw = MysticSpells.yawTo(b, a);
        p.connection.teleport(b.x, b.y, b.z, yaw, p.getXRot());
        p.resetFallDistance();
        Aim.moveTo(t, a);
        Fx.teleport(c.level(), a.add(0, 1, 0), b.add(0, 1, 0), BLUE);
        Fx.teleport(c.level(), b.add(0, 1, 0), a.add(0, 1, 0), 0x9FD4FF);
        Fx.sound(c.level(), a, MaSounds.PORTAL_TRAVEL.get(), 1f, 1.2f);
        return true;
    }

    private static boolean prison(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 32, e -> !MysticSpells.pvpBlocked(c, e) && Aim.controllable(e));
        if (t == null) return false;
        EntityMarks.Mark m = EntityMarks.add(t, MarkKind.PRISON, 160, 0);
        m.anchor = t.position().add(0, 0.5, 0);
        Fx.send(c.level(), FxKind.RING, t.position().add(0, t.getBbHeight() * 0.5, 0), new Vec3(0, 1, 0), BLUE, Math.max(1.5f, t.getBbWidth() * 1.4f), 12, -1);
        Fx.sound(c.level(), t.position(), MaSounds.STONE_SPACE.get(), 1.2f, 1.3f);
        return true;
    }

    private static boolean distortion(Cast c) {
        List<LivingEntity> targets = Aim.cone(c.player(), 16, 0.7);
        targets.removeIf(e -> MysticSpells.pvpBlocked(c, e) || !Aim.controllable(e));
        if (targets.isEmpty()) return false;
        Vec3 focus = c.eye().add(c.look().scale(7));
        Optional<Vec3> ground = Aim.safeSpot(c.level(), targets.get(0), focus, 4);
        Vec3 point = ground.orElse(focus);
        for (LivingEntity e : targets) {
            Vec3 from = e.position();
            Aim.moveTo(e, point.add(c.level().random.nextGaussian() * 0.4, 0, c.level().random.nextGaussian() * 0.4));
            MysticSpells.hurt(c, e, 7f);
            Fx.teleport(c.level(), from.add(0, 1, 0), point.add(0, 1, 0), BLUE);
        }
        Fx.send(c.level(), FxKind.WAVE, point.add(0, 1, 0), Vec3.ZERO, BLUE, 4f, 12, -1);
        Fx.sound(c.level(), point, MaSounds.SLAM.get(), 1.2f, 1.4f);
        c.data().pose(Poses.WHIP, 14);
        return true;
    }

    // ============================================================================================ ultimate: cosmic rift

    private static boolean ultimateStart(Cast c) {
        ServerPlayer p = c.player();
        Vec3 at = p.position().add(Aim.horizontal(c.look()).scale(3)).add(0, 2, 0);
        SpellFieldEntity.spawn(c.level(), p, at, FieldKind.SPACE_RIFT_ULT, 4.5f, 70, BLUE);
        Fx.send(c.level(), FxKind.CHARGE_UP, p.position().add(0, 1, 0), Vec3.ZERO, BLUE, 1f, 60, p.getId());
        Fx.sound(p, MaSounds.RIFT_OPEN.get(), 2f, 0.8f);
        Fx.sound(p, MaSounds.ULTIMATE_CHARGE.get(), 1.4f, 1f);
        p.displayClientMessage(Component.translatable(c.data().spaceAnchor != null ? "message.mysticarts.rift_anchor" : "message.mysticarts.rift_home").withColor(BLUE), true);
        return true;
    }

    private static boolean ultimateFinish(Cast c) {
        ServerPlayer p = c.player();
        PowerData.Anchor anchor = c.data().spaceAnchor;
        ServerLevel to;
        Vec3 target;
        if (anchor != null && p.server.getLevel(anchor.dimension()) != null) {
            to = p.server.getLevel(anchor.dimension());
            target = anchor.pos();
        } else {
            @Nullable BlockPos bed = p.getRespawnPosition();
            ServerLevel respawn = p.server.getLevel(p.getRespawnDimension());
            if (bed != null && respawn != null) {
                to = respawn;
                target = Vec3.atBottomCenterOf(bed).add(0, 0.6, 0);
            } else {
                to = p.server.overworld();
                target = Vec3.atBottomCenterOf(to.getSharedSpawnPos());
            }
        }
        Vec3 land = Aim.safeSpot(to, p, target, 6).orElseGet(() -> findColumn(to, p, target));
        List<Player> party = c.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(6), o -> o != p && o.isAlive() && !o.isSpectator());
        Vec3 from = p.position();
        Fx.send(c.level(), FxKind.WAVE, from.add(0, 1, 0), Vec3.ZERO, BLUE, 10f, 20, -1);
        p.teleportTo(to, land.x, land.y, land.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        for (Player o : party) {
            Vec3 spot = Aim.safeSpot(to, o, land.add(c.level().random.nextGaussian() * 1.5, 0, c.level().random.nextGaussian() * 1.5), 4).orElse(land);
            o.teleportTo(to, spot.x, spot.y, spot.z, java.util.Set.of(), o.getYRot(), o.getXRot());
            o.resetFallDistance();
        }
        SlingPortalEntity.open(to, p, SlingPortalEntity.KIND_RIFT, land.add(0, 2, 0), p.getYRot(), 0, 3.2f, BLUE, 50);
        Fx.screen(to, land, 32, 1.5f, 1f, BLUE);
        Fx.sound(to, land, MaSounds.ULTIMATE_RELEASE.get(), 1.6f, 1.2f);
        Fx.sound(to, land, MaSounds.PORTAL_TRAVEL.get(), 1.6f, 0.6f);
        return true;
    }
}
