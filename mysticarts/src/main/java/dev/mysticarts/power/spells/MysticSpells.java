package dev.mysticarts.power.spells;

import dev.mysticarts.MaConfig;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.MysticBoltEntity;
import dev.mysticarts.entity.MysticCloneEntity;
import dev.mysticarts.entity.ShockwaveEntity;
import dev.mysticarts.entity.SlingPortalEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.Sustained;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.BlockHistory;
import dev.mysticarts.world.MirrorDimension;
import dev.mysticarts.world.Scheduler;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The Mystic Arts: Doctor Strange's own repertoire. */
public final class MysticSpells {
    private MysticSpells() {}

    public static void register() {
        Spells.register(Ability.ELDRITCH_WHIP, MysticSpells::whip);
        Spells.register(Ability.ELDRITCH_SHIELD, MysticSpells::shield);
        Spells.register(Ability.SHIELD_DOME, MysticSpells::dome);
        Spells.register(Ability.MYSTIC_BLAST, MysticSpells::blast);
        Spells.register(Ability.MYSTIC_BINDING, MysticSpells::binding);
        Spells.register(Ability.SLING_PORTAL, MysticSpells::slingPortal);
        Spells.register(Ability.PORTAL_TRAP, MysticSpells::portalTrap);
        Spells.register(Ability.PORTAL_REDIRECT, MysticSpells::portalRedirect);
        Spells.register(Ability.PORTAL_DODGE, MysticSpells::portalDodge);
        Spells.register(Ability.TIME_REVERSAL, c -> TimeSpells.rewind(c, 12, 30, 100));
        Spells.register(Ability.TIME_LOOP, c -> TimeSpells.loop(c, 20, 40, 3));
        Spells.register(Ability.TIME_SLOW, c -> TimeSpells.slow(c, 10, 200, 3));
        Spells.register(Ability.TIME_ACCELERATION, c -> TimeSpells.accelerate(c, 6, 200));
        Spells.register(Ability.TIME_STOP, c -> TimeSpells.stop(c, 16, 160));
        Spells.register(Ability.MIRROR_DIMENSION, MysticSpells::mirror);
        Spells.register(Ability.MIRROR_FOLD, MysticSpells::mirrorFold);
        Spells.register(Ability.ASTRAL_PROJECTION, MysticSpells::astral);
        Spells.register(Ability.ASTRAL_FORM, MysticSpells::astralForm);
        Spells.register(Ability.TELEKINESIS, c -> telekinesis(c, Sustained.HOLD_TELEKINESIS, 20));
        Spells.register(Ability.LEVITATION, MysticSpells::levitation);
        Spells.register(Ability.MYSTIC_PUSH, MysticSpells::push);
        Spells.register(Ability.MYSTIC_PULL, MysticSpells::pull);
        Spells.register(Ability.BANISHMENT, MysticSpells::banish);
        Spells.register(Ability.CLONING, MysticSpells::cloning);
        Spells.register(Ability.GROUND_SLAM, MysticSpells::slam);
        Spells.register(Ability.ENERGY_ABSORPTION, MysticSpells::absorption);
        Spells.register(Ability.MYSTIC_DETECTION, c -> detect(c, 40, false));
        Spells.register(Ability.DIMENSIONAL_DASH, MysticSpells::dash);
        Spells.register(Ability.DEFLECTION, MysticSpells::deflection);
    }

    static float hurt(Cast c, LivingEntity e, float base) {
        float dmg = c.damage(base);
        e.hurt(c.level().damageSources().indirectMagic(c.player(), c.player()), dmg);
        return dmg;
    }

    static boolean pvpBlocked(Cast c, LivingEntity e) {
        return e instanceof Player && !c.level().getServer().isPvpAllowed();
    }

    // ============================================================================================ combat

    private static boolean whip(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 18, e -> !pvpBlocked(c, e));
        if (t == null) return false;
        Sustained.startHold(c.player(), c.data(), t, Sustained.HOLD_WHIP);
        hurt(c, t, 2f);
        Fx.send(c.level(), FxKind.ARC, c.hands(), t.getBoundingBox().getCenter().subtract(c.hands()), c.color(), 0.5f, 0, c.player().getId());
        Fx.sound(c.player(), MaSounds.WHIP_GRAB.get(), 1f, 1f);
        c.data().pose(Poses.WHIP, 14);
        return true;
    }

    private static boolean shield(Cast c) {
        c.data().shieldMode = 1;
        c.data().casterDirty = true;
        Fx.sound(c.player(), MaSounds.SHIELD_UP.get(), 1f, 1f);
        c.data().pose(Poses.SHIELD, 10);
        return true;
    }

    private static boolean dome(Cast c) {
        SpellFieldEntity.spawn(c.level(), c.player(), c.player().position().add(0, 0.1, 0), FieldKind.SHIELD_DOME, 4.5f, 200, c.color());
        Fx.sound(c.player(), MaSounds.SHIELD_UP.get(), 1.2f, 0.7f);
        return true;
    }

    private static boolean blast(Cast c) {
        float ch = c.charge();
        Vec3 dir = c.look();
        if (ch < 0.5f) {
            MysticBoltEntity.shoot(c.level(), c.player(), c.hands(), dir, MysticBoltEntity.MYSTIC, c.color(), 0.35f, 2.2f, c.damage(6f));
        } else {
            MysticBoltEntity.shoot(c.level(), c.player(), c.hands(), dir, MysticBoltEntity.MYSTIC_CHARGED, c.color(), 0.5f + ch * 0.5f, 1.6f, c.damage(7f + 9f * ch))
                    .blast(2f + 2.5f * ch, false);
            Fx.screen(c.level(), c.eye(), 8, 0.3f * ch, 0, c.color());
        }
        Fx.sound(c.player(), MaSounds.BLAST_FIRE.get(), 1f, 1.2f - ch * 0.4f);
        c.data().pose(Poses.PUSH, 8);
        return true;
    }

    private static boolean binding(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 24, e -> !pvpBlocked(c, e));
        if (t == null) return false;
        EntityMarks.add(t, MarkKind.BOUND, 120, 0);
        hurt(c, t, 2f);
        Fx.send(c.level(), FxKind.CHAINS, t.position(), Vec3.ZERO, c.color(), t.getBbWidth(), t.getBbHeight(), t.getId());
        Fx.sound(c.level(), t.position(), MaSounds.BINDING_CHAINS.get(), 1.2f, 1f);
        return true;
    }

    // ============================================================================================ portals

    /** Closes the caster's portal they are looking at. */
    public static boolean closeLookedAtPortal(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (SlingPortalEntity p : player.serverLevel().getEntitiesOfClass(SlingPortalEntity.class, player.getBoundingBox().inflate(24))) {
            if (!player.getUUID().equals(p.owner())) continue;
            Vec3 to = p.position().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > 24) continue;
            if (to.subtract(look.scale(along)).length() <= p.radius() * 1.1) {
                p.close();
                return true;
            }
        }
        return false;
    }

    private static boolean slingPortal(Cast c) {
        ServerPlayer p = c.player();
        float yaw = p.getYRot();
        Vec3 entry = p.getEyePosition().add(Aim.horizontal(c.look()).scale(2.4)).subtract(0, 0.5, 0);
        ServerLevel destLevel;
        Vec3 dest;
        PowerData.Anchor anchor = c.data().slingAnchor;
        if (anchor != null) {
            destLevel = p.server.getLevel(anchor.dimension());
            if (destLevel == null) return false;
            dest = anchor.pos();
            yaw = anchor.yaw();
        } else {
            destLevel = c.level();
            Optional<Vec3> spot = Aim.safeLookSpot(c.level(), p, MaConfig.PORTAL_RANGE.get());
            if (spot.isEmpty()) return false;
            dest = spot.get();
            if (dest.distanceTo(p.position()) < 6) return false;
        }
        Vec3 exitCenter = dest.add(0, 1.1, 0).add(Vec3.directionFromRotation(0, yaw).scale(-0.6));
        SlingPortalEntity.pair(c.level(), p, SlingPortalEntity.KIND_SLING, entry, p.getYRot() + 180f, destLevel, exitCenter, yaw, 1.35f, c.color(), 600);
        Fx.send(c.level(), FxKind.CAST_CIRCLE, c.hands(), c.look(), c.color(), 0.6f, 20, p.getId());
        c.data().pose(Poses.CIRCLE, 24);
        return true;
    }

    private static boolean portalTrap(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 28, e -> !pvpBlocked(c, e) && Aim.controllable(e));
        if (t == null) return false;
        ServerLevel level = c.level();
        Vec3 under = t.position().add(0, 0.05, 0);
        SlingPortalEntity portal = SlingPortalEntity.open(level, c.player(), SlingPortalEntity.KIND_TRAP, under, t.getYRot(), -90f, Math.max(1.1f, t.getBbWidth() + 0.5f), c.color(), 70);
        PowerData.Anchor anchor = c.data().slingAnchor;
        if (anchor != null && c.player().isShiftKeyDown() && c.player().server.getLevel(anchor.dimension()) instanceof ServerLevel dl) {
            portal.exit(dl, anchor.pos().add(0, 1, 0), 0, 90f);
        } else {
            double top = Math.min(level.getMaxBuildHeight() - 4, t.getY() + 28);
            Vec3 drop = new Vec3(t.getX(), top, t.getZ());
            portal.exit(level, drop, t.getYRot(), 90f);
            SlingPortalEntity.open(level, c.player(), SlingPortalEntity.KIND_TRAP, drop.add(0, 0.3, 0), t.getYRot(), 90f, 1.4f, c.color(), 70);
        }
        if (t instanceof Mob m) m.getNavigation().stop();
        return true;
    }

    private static boolean portalRedirect(Cast c) {
        Vec3 at = c.eye().add(c.look().scale(2.2)).subtract(0, 0.3, 0);
        SlingPortalEntity.open(c.level(), c.player(), SlingPortalEntity.KIND_REDIRECT, at, c.player().getYRot(), Mth.clamp(c.player().getXRot(), -45, 45), 1.7f, c.color(), 100);
        c.data().pose(Poses.SHIELD, 20);
        return true;
    }

    private static boolean portalDodge(Cast c) {
        ServerPlayer p = c.player();
        LivingEntity t = Aim.entity(p, 18);
        Vec3 target;
        if (t != null) target = t.position().subtract(Aim.horizontal(t.getLookAngle()).scale(2.2));
        else target = p.position().subtract(Aim.horizontal(c.look()).scale(9));
        Optional<Vec3> spot = Aim.safeSpot(c.level(), p, target, 3);
        if (spot.isEmpty()) return false;
        Vec3 from = p.position();
        SlingPortalEntity.open(c.level(), p, SlingPortalEntity.KIND_SLING, from.add(0, 1, 0), p.getYRot() + 180, 0, 1.1f, c.color(), 28);
        SlingPortalEntity.open(c.level(), p, SlingPortalEntity.KIND_SLING, spot.get().add(0, 1, 0), p.getYRot(), 0, 1.1f, c.color(), 28);
        float yaw = t != null ? yawTo(spot.get(), t.position()) : p.getYRot();
        p.connection.teleport(spot.get().x, spot.get().y, spot.get().z, yaw, p.getXRot());
        p.resetFallDistance();
        Fx.teleport(c.level(), from.add(0, 1, 0), spot.get().add(0, 1, 0), c.color());
        Fx.sound(c.level(), spot.get(), MaSounds.PORTAL_TRAVEL.get(), 1f, 1.3f);
        return true;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
    }

    // ============================================================================================ mirror dimension

    private static boolean mirror(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 16, e -> !(e instanceof Player));
        return MirrorDimension.enter(c.player(), c.data(), t);
    }

    /**
     * Folds the mirror world: a strip of terrain ahead rises into a wall that ripples outwards (or, sneaking, sinks into
     * a trench). Anything standing on the strip is flung. Only ever happens inside the Mirror Dimension.
     */
    private static boolean mirrorFold(Cast c) {
        ServerLevel level = c.level();
        if (!MirrorDimension.isMirror(level)) return false;
        Vec3 fwd = Aim.horizontal(c.look());
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        boolean sink = c.sneaking();
        BlockPos base = c.player().blockPosition();
        for (int d = 3; d <= 14; d++) {
            final int dist = d;
            Scheduler.schedule((d - 3) * 2, server -> {
                for (int w = -4; w <= 4; w++) {
                    Vec3 at = Vec3.atBottomCenterOf(base).add(fwd.scale(dist)).add(side.scale(w));
                    BlockPos col = BlockPos.containing(at.x, base.getY(), at.z);
                    shiftColumn(level, col, sink ? -3 : 3 + (dist % 3));
                }
                Vec3 center = Vec3.atBottomCenterOf(base).add(fwd.scale(dist));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(5, 3, 5),
                        e -> e != c.player())) {
                    e.setDeltaMovement(e.getDeltaMovement().add(fwd.scale(0.6)).add(0, sink ? -0.4 : 0.9, 0));
                    e.hurtMarked = true;
                }
                level.playSound(null, center.x, center.y, center.z, MaSounds.MIRROR_FOLD.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.8f + dist * 0.04f);
            });
        }
        Fx.send(level, FxKind.RING, c.player().position(), fwd, 0x9FE8FF, 3f, 16, -1);
        c.data().pose(Poses.PUSH, 20);
        return true;
    }

    private static void shiftColumn(ServerLevel level, BlockPos col, int shift) {
        int top = col.getY() + 8, bottom = col.getY() - 6;
        BlockState[] states = new BlockState[top - bottom + 1];
        for (int y = bottom; y <= top; y++) states[y - bottom] = level.getBlockState(new BlockPos(col.getX(), y, col.getZ()));
        for (int y = bottom; y <= top; y++) {
            int src = y - shift;
            BlockState s = src >= bottom && src <= top ? states[src - bottom] : (shift > 0 ? MaBlocks.MIRROR_SHARD.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
            if (s.hasBlockEntity()) s = Blocks.AIR.defaultBlockState();
            level.setBlock(new BlockPos(col.getX(), y, col.getZ()), s, 2 | 16 | 32);
        }
    }

    // ============================================================================================ astral

    private static boolean astral(Cast c) {
        ServerPlayer p = c.player();
        PowerData d = c.data();
        MysticCloneEntity body = MaEntities.MYSTIC_CLONE.get().create(c.level());
        if (body == null) return false;
        body.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        body.setYHeadRot(p.getYHeadRot());
        body.setYBodyRot(p.getYRot());
        body.bind(p, 1300);
        body.mode(MysticCloneEntity.BODY);
        c.level().addFreshEntity(body);
        d.astralBody = body.getId();
        d.astralTicks = 1200;
        d.astralReturn = new PowerData.Anchor(c.level().dimension(), p.position(), p.getYRot(), p.getXRot());
        d.astralGameMode = p.gameMode.getGameModeForPlayer().getId();
        p.setGameMode(GameType.SPECTATOR);
        d.casterDirty = true;
        Fx.send(c.level(), FxKind.SOUL_RIP, p.position().add(0, 1, 0), Vec3.ZERO, 0x9FD4FF, 0, 0, body.getId());
        Fx.sound(p, MaSounds.ASTRAL_SEPARATE.get(), 1f, 1f);
        p.displayClientMessage(Component.translatable("message.mysticarts.astral_start"), true);
        return true;
    }

    public static void astralTick(ServerPlayer p, PowerData d) {
        d.astralTicks--;
        Entity body = p.serverLevel().getEntity(d.astralBody);
        boolean sameDim = d.astralReturn != null && d.astralReturn.dimension() == p.level().dimension();
        if (d.astralTicks <= 0 || body == null || !sameDim || p.distanceToSqr(body) > 64 * 64) endAstral(p, d);
        else if (p.distanceToSqr(body) > 56 * 56 && d.astralTicks % 20 == 0) p.displayClientMessage(Component.translatable("message.mysticarts.astral_far"), true);
    }

    /** Returns an astral traveller to their body (also used after a restart, from the persisted anchor). */
    public static void endAstral(ServerPlayer p, PowerData d) {
        Entity body = d.astralBody >= 0 ? p.serverLevel().getEntity(d.astralBody) : null;
        PowerData.Anchor back = d.astralReturn;
        Vec3 pos = body != null ? body.position() : back != null ? back.pos() : p.position();
        ServerLevel level = back != null && p.server.getLevel(back.dimension()) != null ? p.server.getLevel(back.dimension()) : p.serverLevel();
        GameType mode = d.astralGameMode >= 0 ? GameType.byId(d.astralGameMode) : GameType.SURVIVAL;
        if (p.isSpectator()) p.setGameMode(mode);
        p.teleportTo(level, pos.x, pos.y, pos.z, body != null ? body.getYRot() : p.getYRot(), p.getXRot());
        if (body != null) body.discard();
        d.astralBody = -1;
        d.astralTicks = 0;
        d.astralReturn = null;
        d.astralGameMode = -1;
        d.casterDirty = true;
        d.dirty = true;
        d.cooldowns[Ability.ASTRAL_PROJECTION.ordinal()] = Math.max(d.cooldowns[Ability.ASTRAL_PROJECTION.ordinal()], 100);
        Fx.sound(p, MaSounds.ASTRAL_RETURN.get(), 1f, 1f);
        Fx.burst(level, pos.add(0, 1, 0), 0x9FD4FF, 1f, 1f);
    }

    private static boolean astralForm(Cast c) {
        c.data().astralFormTicks = 140;
        c.data().casterDirty = true;
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, c.player().getBoundingBox().inflate(24), m -> m.getTarget() == c.player())) {
            m.setTarget(null);
        }
        Fx.sound(c.player(), MaSounds.ASTRAL_SEPARATE.get(), 0.8f, 1.4f);
        return true;
    }

    // ============================================================================================ telekinesis & force

    static boolean telekinesis(Cast c, int kind, double range) {
        ServerPlayer p = c.player();
        Entity t = Aim.anyEntity(p, range);
        if (t instanceof Player && !c.level().getServer().isPvpAllowed()) t = null;
        if (t != null && !(t instanceof Player) && !Aim.controllable(t)) t = null;
        if (t == null) {
            BlockHitResult hit = Aim.block(p, Math.min(range, 14));
            if (hit.getType() != HitResult.Type.BLOCK) return false;
            BlockPos pos = hit.getBlockPos();
            BlockState s = c.level().getBlockState(pos);
            float hardness = s.getDestroySpeed(c.level(), pos);
            if (s.isAir() || s.hasBlockEntity() || hardness < 0 || hardness > 50 || !c.level().mayInteract(p, pos)) return false;
            BlockHistory.record(c.level(), pos, s);
            FallingBlockEntity fb = FallingBlockEntity.fall(c.level(), pos, s);
            fb.dropItem = true;
            t = fb;
        }
        Sustained.startHold(p, c.data(), t, kind);
        Fx.send(c.level(), FxKind.ARC, c.hands(), t.getBoundingBox().getCenter().subtract(c.hands()), c.color(), 0.3f, 0, p.getId());
        Fx.sound(p, MaSounds.TELEKINESIS_GRAB.get(), 1f, 1f);
        c.data().pose(Poses.POINT, 20);
        return true;
    }

    private static boolean levitation(Cast c) {
        List<LivingEntity> targets = Aim.cone(c.player(), 12, 0.7);
        targets.removeIf(e -> pvpBlocked(c, e));
        if (targets.isEmpty()) return false;
        for (LivingEntity e : targets) {
            EntityMarks.Mark m = EntityMarks.add(e, MarkKind.LIFTED, 100, 0);
            m.anchor = e.position().add(0, 4.5, 0);
            Fx.send(c.level(), FxKind.RING, e.position().add(0, 0.1, 0), new Vec3(0, 1, 0), c.color(), 1.2f, 16, -1);
        }
        Fx.sound(c.player(), MaSounds.TELEKINESIS_GRAB.get(), 1f, 1.3f);
        c.data().pose(Poses.RAISE, 14);
        return true;
    }

    private static boolean push(Cast c) {
        Vec3 dir = c.look();
        for (LivingEntity e : Aim.cone(c.player(), 10, 0.55)) {
            if (pvpBlocked(c, e)) continue;
            hurt(c, e, 3f);
            e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(2.3)).add(0, 0.5, 0));
            e.hurtMarked = true;
        }
        for (Projectile pr : c.level().getEntitiesOfClass(Projectile.class, c.player().getBoundingBox().inflate(10))) {
            if (pr.position().subtract(c.eye()).normalize().dot(dir) > 0.55) {
                pr.setDeltaMovement(dir.scale(1.8));
                pr.hurtMarked = true;
            }
        }
        Fx.send(c.level(), FxKind.RING, c.hands(), dir, c.color(), 2.4f, 10, -1);
        Fx.sound(c.player(), MaSounds.PUSH.get(), 1f, 1f);
        c.data().pose(Poses.PUSH, 10);
        return true;
    }

    private static boolean pull(Cast c) {
        List<LivingEntity> targets = Aim.cone(c.player(), 16, 0.7);
        targets.removeIf(e -> pvpBlocked(c, e));
        if (targets.isEmpty()) return false;
        for (LivingEntity e : targets) {
            Vec3 to = c.player().position().subtract(e.position());
            e.setDeltaMovement(to.normalize().scale(Math.min(2.2, to.length() * 0.22)).add(0, 0.35, 0));
            e.hurtMarked = true;
            Fx.send(c.level(), FxKind.ARC, c.hands(), e.getBoundingBox().getCenter().subtract(c.hands()), c.color(), 0.3f, 0, c.player().getId());
        }
        Fx.sound(c.player(), MaSounds.WHIP_CRACK.get(), 1f, 0.8f);
        c.data().pose(Poses.WHIP, 10);
        return true;
    }

    private static boolean banish(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 20, e -> !(e instanceof Player) && Aim.controllable(e));
        if (t == null) return false;
        Vec3 at = t.position();
        if (!MirrorDimension.banish(c.level(), t, 400)) return false;
        Fx.send(c.level(), FxKind.RING, at.add(0, 1, 0), c.look(), 0x9FE8FF, 2f, 14, -1);
        Fx.burst(c.level(), at.add(0, 1, 0), c.color(), 1.4f, 2f);
        Fx.sound(c.level(), at, MaSounds.BANISH.get(), 1.2f, 1f);
        return true;
    }

    private static boolean cloning(Cast c) {
        ServerPlayer p = c.player();
        MysticCloneEntity[] clones = new MysticCloneEntity[3];
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(p.getYRot()) + (i - 1) * 1.2 + Math.PI / 2;
            Vec3 at = p.position().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8);
            MysticCloneEntity clone = MaEntities.MYSTIC_CLONE.get().create(c.level());
            if (clone == null) continue;
            Vec3 spot = Aim.safeSpot(c.level(), clone, at, 2).orElse(p.position());
            clone.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0);
            clone.bind(p, 500);
            clone.mode(MysticCloneEntity.CLONE);
            c.level().addFreshEntity(clone);
            clones[i] = clone;
            Fx.burst(c.level(), spot.add(0, 1, 0), c.color(), 1f, 1f);
        }
        int i = 0;
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
            MysticCloneEntity decoy = clones[i++ % 3];
            if (decoy != null) m.setTarget(decoy);
        }
        Fx.sound(p, MaSounds.CLONE.get(), 1f, 1f);
        return true;
    }

    private static boolean slam(Cast c) {
        ServerPlayer p = c.player();
        boolean airborne = !p.onGround();
        float power = airborne ? Math.min(2f, 1f + p.fallDistance * 0.1f) : 1f;
        ShockwaveEntity.spawn(c.level(), p, p.position().add(0, 0.1, 0), ShockwaveEntity.GROUND, c.color(), 7f * power, 14, c.damage(6f * power), 1.1f, 0.65f);
        if (airborne) {
            p.setDeltaMovement(0, -2.5, 0);
            p.hurtMarked = true;
            p.resetFallDistance();
        }
        Fx.screen(c.level(), p.position(), 16, 0.9f * power, 0, c.color());
        Fx.sound(p, MaSounds.SLAM.get(), 1.3f, 1f);
        c.data().pose(Poses.SLAM, 10);
        return true;
    }

    private static boolean absorption(Cast c) {
        c.data().absorbTicks = 100;
        c.data().casterDirty = true;
        Fx.send(c.level(), FxKind.ABSORB, c.player().position().add(0, 1, 0), Vec3.ZERO, c.color(), 3f, 100, c.player().getId());
        Fx.sound(c.player(), MaSounds.ABSORB.get(), 1f, 1f);
        c.data().pose(Poses.CHARGE, 20);
        return true;
    }

    /** Detection: everything living nearby is revealed through walls; the caster gets a count. */
    static boolean detect(Cast c, double radius, boolean hostileOnly) {
        int hostile = 0, total = 0;
        for (LivingEntity e : Aim.around(c.player(), c.player().position(), radius, e -> true)) {
            if (hostileOnly && !Aim.hostile(e)) continue;
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            EntityMarks.add(e, MarkKind.REVEALED, 200, Aim.hostile(e) ? 1 : 0);
            total++;
            if (Aim.hostile(e)) hostile++;
        }
        Fx.send(c.level(), FxKind.DETECT, c.player().position().add(0, 1, 0), Vec3.ZERO, c.color(), (float) radius, 30, c.player().getId());
        Fx.sound(c.player(), MaSounds.DETECT.get(), 1f, 1f);
        c.player().displayClientMessage(Component.translatable("message.mysticarts.detected", total, hostile).withColor(c.color()), true);
        return true;
    }

    private static boolean dash(Cast c) {
        ServerPlayer p = c.player();
        Vec3 dir = c.look().multiply(1, 0.35, 1).normalize();
        Vec3 from = p.position();
        Vec3 target = from;
        for (double d = 9; d >= 2; d -= 0.5) {
            Vec3 cand = from.add(dir.scale(d));
            if (Aim.safe(c.level(), p, cand) || c.level().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(cand))) {
                target = cand;
                break;
            }
        }
        if (target == from) return false;
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, target).inflate(1), e -> e != p && !pvpBlocked(c, e))) {
            hurt(c, e, 3f);
        }
        p.connection.teleport(target.x, target.y, target.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        Fx.teleport(c.level(), from.add(0, 1, 0), target.add(0, 1, 0), c.color());
        Fx.sound(c.level(), target, MaSounds.DASH.get(), 1f, 1f);
        return true;
    }

    private static boolean deflection(Cast c) {
        c.data().deflectTicks = 60;
        c.data().casterDirty = true;
        Sustained.deflect(c.player(), c.level(), 5);
        Fx.send(c.level(), FxKind.CAST_CIRCLE, c.hands(), c.look(), c.color(), 1.2f, 60, c.player().getId());
        c.data().pose(Poses.SHIELD, 30);
        return true;
    }
}
