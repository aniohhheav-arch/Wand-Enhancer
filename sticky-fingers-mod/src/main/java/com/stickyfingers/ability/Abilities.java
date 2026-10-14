package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.*;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModBlocks;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import com.stickyfingers.world.SeparationManager;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import javax.annotation.Nullable;
import java.util.*;

/** Server implementations of every Sticky Fingers ability. Each returns true when it actually fired. */
public final class Abilities {

    public static boolean activate(AbilityType type, ServerPlayer p, StandData data, boolean alt) {
        return switch (type) {
            case ZIPPER_OPEN -> zipperOpen(p, data, alt);
            case ZIPPER_DOOR -> passage(p, data, alt, ZipperKind.DOOR, 2, 3, AbilityType.ZIPPER_DOOR);
            case ZIPPER_WALL -> passage(p, data, alt, ZipperKind.WALL, 3, 4, AbilityType.ZIPPER_WALL);
            case ZIPPER_STORAGE -> storage(p);
            case ZIPPER_TRAVEL -> travel(p, data);
            case ZIPPER_MOB -> zipperMob(p);
            case ZIPPER_RESTRAINT -> restraint(p);
            case ZIPPER_PUNCH -> punch(p);
            case AERIAL_ZIPPER -> aerial(p, data);
            case ZIPPER_ESCAPE -> { Hiding.start(p, Math.max(20, SFConfig.duration(type)), false); yield true; }
            case ZIPPER_AMBUSH -> { Hiding.start(p, Math.max(20, SFConfig.duration(type)), true); yield true; }
            case ZIPPER_BODY -> body(p);
            case ZIPPER_DECOY -> decoy(p);
            case ZIPPER_TRAP -> trap(p);
            case ZIPPER_PROJECTILE -> projectile(p);
            case ZIPPER_TELEPORT -> teleport(p, data);
            case ZIPPER_SLICE -> slice(p);
            case ZIPPER_COMBO -> Sequences.combo(p);
            case ZIPPER_FLOOR -> floor(p);
            case ZIPPER_REPAIR -> repair(p);
            case ZIPPER_NETWORK -> { ZipperNetwork.sendSnapshot(p, true); yield true; }
            case ZIPPER_WORLD -> Sequences.zipperWorld(p);
        };
    }

    // ================================================================= targeting helpers

    @Nullable
    static BlockHitResult pickBlock(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    @Nullable
    static LivingEntity pickEntity(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = pickBlock(p, range);
        if (block != null) end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(p.getLookAngle().scale(range)).inflate(1.5),
                e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof DecoyEntity) && e != p && !e.isSpectator(), range * range);
        if (hit != null) return (LivingEntity) hit.getEntity();
        // generous aim assist: closest living entity within a narrow cone
        Vec3 look = p.getLookAngle();
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> e != p && e.isAlive()
                        && !(e instanceof DecoyEntity) && !e.isSpectator()
                        && e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye).normalize().dot(look) > 0.96
                        && p.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    static boolean canAffect(LivingEntity target) {
        return !(target instanceof ServerPlayer) || SFConfig.PVP_EFFECTS.get();
    }

    /** In-plane angle (degrees) that makes a zipper with normal {@code n} run along {@code dir}. */
    static float angleFor(Vec3 n, Vec3 dir) {
        Vec3 u = ZipperEntity.tangent(n);
        Vec3 v = n.cross(u).normalize();
        return (float) Math.toDegrees(Math.atan2(dir.dot(v), dir.dot(u)));
    }

    /** Zipper angle from the player's orientation mode. */
    static float orientAngle(ServerPlayer p, Vec3 n, float auto) {
        return switch (StandData.get(p).orientation()) {
            case 1 -> 0f;
            case 2 -> 90f;
            case 3 -> 45f;
            case 4 -> -45f;
            default -> auto;
        };
    }

    /** Nearest standable spot around {@code wanted}. */
    public static Vec3 safeSpot(ServerLevel level, Vec3 wanted, Vec3 fallback) {
        BlockPos base = BlockPos.containing(wanted);
        for (int dy = 0; dy <= 3; dy++) {
            for (int sign : new int[]{1, -1}) {
                BlockPos pos = base.above(dy * sign);
                if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                    return new Vec3(wanted.x, pos.getY(), wanted.z);
                }
            }
        }
        return fallback;
    }

    static ZipperEntity spawn(ServerPlayer p, ZipperKind kind, Vec3 pos, Vec3 normal, float angle, float length, float width) {
        ZipperEntity z = ZipperEntity.create(p.serverLevel(), p, kind, pos, normal, angle, length, width);
        p.level().addFreshEntity(z);
        Fx.sound(p.serverLevel(), pos, ModSounds.ZIPPER_PLACE.get(), 0.8f, 0.9f + p.getRandom().nextFloat() * 0.3f);
        return z;
    }

    static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.51, face.getStepY() * 0.51, face.getStepZ() * 0.51);
    }

    // ================================================================= 1. open

    static boolean zipperOpen(ServerPlayer p, StandData data, boolean alt) {
        int range = SFConfig.range(AbilityType.ZIPPER_OPEN);
        LivingEntity target = pickEntity(p, range);
        BlockHitResult hit = pickBlock(p, range);
        if (target != null && (hit == null || target.distanceToSqr(p) < hit.getLocation().distanceToSqr(p.getEyePosition()))) {
            return zipperMob(p);
        }
        if (hit == null) return aerial(p, data);
        ServerLevel level = p.serverLevel();
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        Vec3 n = ZipperEntity.normalOf(face);
        BlockState state = level.getBlockState(pos);
        StandManager.animate(p, StandAnim.UNZIP);

        // Containers: unzip them open from range.
        MenuProvider menu = state.getMenuProvider(level, pos);
        if (menu != null && level.mayInteract(p, pos)) {
            ZipperEntity z = spawn(p, ZipperKind.SURFACE, faceCenter(pos, face), n, 0f, 0.9f, 0.5f).life(40);
            z.open();
            p.openMenu(menu);
            return true;
        }
        if (state.getBlock() instanceof DoorBlock door && level.mayInteract(p, pos)) {
            ZipperEntity z = spawn(p, ZipperKind.SURFACE, faceCenter(pos, face), n, 90f, 1.8f, 0.6f).life(40);
            z.open();
            door.setOpen(p, level, state, pos, !state.getValue(DoorBlock.OPEN));
            return true;
        }

        float auto = Math.abs(n.y) > 0.7 ? angleFor(n, p.getLookAngle().multiply(1, 0, 1)) : 90f;
        float angle = orientAngle(p, n, auto);
        Vec3 dir = ZipperEntity.directionOf(n, angle);
        List<BlockPos> area = new ArrayList<>();
        for (int k = -1; k <= 1; k++) {
            BlockPos line = BlockPos.containing(Vec3.atCenterOf(pos).add(dir.scale(k)));
            for (int depth = 0; depth < 2; depth++) area.add(line.relative(face.getOpposite(), depth));
        }
        ZipperEntity z = spawn(p, ZipperKind.SURFACE, faceCenter(pos, face), n, angle, 3.0f, 1.0f)
                .opensArea(area, SFConfig.duration(AbilityType.ZIPPER_OPEN))
                .life(SFConfig.duration(AbilityType.ZIPPER_OPEN));
        z.open();
        linkPending(p, data, z);
        return true;
    }

    /** If a Travel zipper is waiting for its partner, any new portal-capable zipper connects to it. */
    static void linkPending(ServerPlayer p, StandData data, ZipperEntity z) {
        ZipperEntity pending = ZipperNetwork.byId(data.pendingTravelZipper());
        if (pending != null && pending != z && z.kind().portal) {
            ZipperNetwork.connect(pending, z);
            pending.open();
            data.setPendingTravelZipper(-1);
            p.displayClientMessage(Component.translatable("message.stickyfingers.linked", pending.zipperId(), z.zipperId())
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    // ================================================================= 2/18. door & wall

    static boolean passage(ServerPlayer p, StandData data, boolean permanent, ZipperKind kind, int width, int height, AbilityType type) {
        BlockHitResult hit = pickBlock(p, SFConfig.range(type));
        if (hit == null) return false;
        Direction face = hit.getDirection();
        if (face.getAxis().isVertical()) {
            return kind == ZipperKind.WALL ? false : floor(p);
        }
        ServerLevel level = p.serverLevel();
        BlockPos hitPos = hit.getBlockPos();
        Direction side = face.getClockWise();
        int floorY = (int) Math.floor(p.getY());
        int baseY = Math.min(hitPos.getY(), Math.max(floorY, hitPos.getY() - height + 1));
        int maxDepth = kind == ZipperKind.WALL ? 6 : 4;
        List<BlockPos> area = new ArrayList<>();
        int emptyRun = 0;
        for (int depth = 0; depth < maxDepth && emptyRun < 1; depth++) {
            boolean any = false;
            for (int w = 0; w < width; w++) {
                int off = w - (width - 1) / 2;
                for (int h = 0; h < height; h++) {
                    BlockPos bp = new BlockPos(hitPos.getX(), baseY + h, hitPos.getZ()).relative(side, off).relative(face.getOpposite(), depth);
                    if (!level.getBlockState(bp).isAir()) { area.add(bp); any = true; }
                }
            }
            emptyRun = any ? 0 : emptyRun + 1;
        }
        if (area.isEmpty()) return false;
        int duration = permanent ? 0 : SFConfig.duration(type);
        Vec3 n = ZipperEntity.normalOf(face);
        Vec3 center = new Vec3(hitPos.getX() + 0.5, baseY + height / 2.0, hitPos.getZ() + 0.5)
                .add(Vec3.atLowerCornerOf(side.getNormal()).scale(width % 2 == 0 ? 0.5 : 0))
                .add(n.scale(0.51));
        StandManager.animate(p, StandAnim.UNZIP);
        ZipperEntity z = spawn(p, kind, center, n, 90f, height - 0.2f, width - 0.1f)
                .opensArea(area, duration).life(permanent ? -1 : duration);
        z.open();
        Fx.shake(level, center, 10, 0.4f, 8);
        Fx.particles(level, ModParticles.ZIPPER_TOOTH.get(), center, 16, 0.6, 0.1);
        if (permanent) p.displayClientMessage(Component.translatable("message.stickyfingers.permanent").withStyle(ChatFormatting.GOLD), true);
        linkPending(p, data, z);
        return true;
    }

    // ================================================================= 3. storage

    static boolean storage(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 pos = p.getEyePosition().add(look.scale(0.9)).add(0, -0.25, 0);
        ZipperEntity z = spawn(p, ZipperKind.STORAGE, pos, look.reverse(), 0f, 0.9f, 0.6f).life(50);
        z.open();
        StandManager.animate(p, StandAnim.UNZIP);
        Fx.sound(p.serverLevel(), pos, ModSounds.STORAGE_OPEN.get(), 1f, 1f);
        var container = StandData.get(p).storage();
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, container),
                Component.translatable("container.stickyfingers.zipper_storage")));
        return true;
    }

    // ================================================================= 4. travel

    static boolean travel(ServerPlayer p, StandData data) {
        int range = SFConfig.range(AbilityType.ZIPPER_TRAVEL);
        BlockHitResult hit = pickBlock(p, Math.min(range, 8));
        Vec3 pos, n;
        float angle;
        if (hit != null && hit.getDirection().getAxis().isHorizontal()) {
            n = ZipperEntity.normalOf(hit.getDirection());
            pos = new Vec3(hit.getLocation().x, Math.floor(p.getY()) + 1.0, hit.getLocation().z).add(n.scale(0.03));
            angle = 90f;
        } else if (hit != null) {
            n = ZipperEntity.normalOf(hit.getDirection());
            pos = hit.getLocation().add(n.scale(0.03));
            angle = angleFor(n, p.getLookAngle().multiply(1, 0, 1));
        } else {
            n = p.getLookAngle().multiply(1, 0, 1).reverse();
            pos = p.getEyePosition().add(p.getLookAngle().scale(3)).add(0, -0.6, 0);
            angle = 90f;
        }
        ZipperEntity pending = ZipperNetwork.byId(data.pendingTravelZipper());
        if (pending != null && pending.level() == p.level() && pending.position().distanceTo(pos) > range) {
            AbilityExecutor.fail(p, "message.stickyfingers.out_of_range");
            return false;
        }
        ZipperEntity z = spawn(p, ZipperKind.TRAVEL, pos, n, angle, 2.0f, 1.0f).life(SFConfig.duration(AbilityType.ZIPPER_TRAVEL));
        StandManager.animate(p, StandAnim.CAST);
        if (pending != null) {
            ZipperNetwork.connect(pending, z);
            pending.open();
            z.open();
            data.setPendingTravelZipper(-1);
            p.displayClientMessage(Component.translatable("message.stickyfingers.linked", pending.zipperId(), z.zipperId())
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            data.setPendingTravelZipper(z.zipperId());
            p.displayClientMessage(Component.translatable("message.stickyfingers.travel_first", z.zipperId())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return true;
    }

    // ================================================================= 5. zipper on mob

    static boolean zipperMob(ServerPlayer p) {
        LivingEntity target = pickEntity(p, SFConfig.range(AbilityType.ZIPPER_MOB));
        if (target == null || !canAffect(target)) return false;
        int duration = SFConfig.duration(AbilityType.ZIPPER_MOB);
        StandEntity stand = StandManager.ensure(p);
        if (stand != null) {
            stand.play(StandAnim.UNZIP);
            stand.strikeAt(target.position().add(p.position().subtract(target.position()).normalize().scale(1.1)), 16);
        }
        ZipperEntity z = spawn(p, ZipperKind.MOB, target.position().add(0, target.getBbHeight() * 0.55, 0),
                p.position().subtract(target.position()).multiply(1, 0, 1), 0f, target.getBbWidth() + 0.7f, 0.6f)
                .attachTo(target).life(duration).effectDuration(duration);
        z.open();
        target.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, duration, 0, false, true));
        Fx.particles(p.serverLevel(), ModParticles.VOID_MOTE.get(), target.position().add(0, target.getBbHeight() / 2, 0), 20, 0.4, 0.04);
        return true;
    }

    // ================================================================= 6. restraint

    static boolean restraint(ServerPlayer p) {
        LivingEntity target = pickEntity(p, SFConfig.range(AbilityType.ZIPPER_RESTRAINT));
        if (target == null || !canAffect(target)) return false;
        ServerLevel level = p.serverLevel();
        int duration = SFConfig.duration(AbilityType.ZIPPER_RESTRAINT);
        List<BlockPos> anchors = findAnchors(level, target, 5, 4);
        if (anchors.isEmpty()) anchors = List.of(target.blockPosition().below());
        StandManager.animate(p, StandAnim.CAST);
        for (BlockPos a : anchors) {
            ZipperEntity z = spawn(p, ZipperKind.CHAIN, target.position().add(0, target.getBbHeight() * 0.5, 0), new Vec3(0, 1, 0), 0f, 1f, 0.3f)
                    .attachTo(target).anchorAt(a).life(duration).effectDuration(duration);
            z.open();
            Fx.seam(level, Vec3.atCenterOf(a), target.position().add(0, target.getBbHeight() * 0.5, 0), 10);
        }
        target.addEffect(new MobEffectInstance(ModEffects.ZIPPER_BOUND, duration, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration + 40, 2, false, false));
        Fx.sound(level, target.position(), ModSounds.ZIPPER_SNAP.get(), 1.2f, 0.8f);
        return true;
    }

    static List<BlockPos> findAnchors(ServerLevel level, Entity target, int radius, int max) {
        List<BlockPos> found = new ArrayList<>();
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int rays = 26;
        for (int i = 0; i < rays && found.size() < max * 3; i++) {
            double phi = Math.acos(1 - 2 * (i + 0.5) / rays);
            double theta = Math.PI * (1 + Math.sqrt(5)) * i;
            Vec3 d = new Vec3(Math.cos(theta) * Math.sin(phi), Math.cos(phi), Math.sin(theta) * Math.sin(phi));
            BlockHitResult hit = level.clip(new ClipContext(c, c.add(d.scale(radius)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.BLOCK && !found.contains(hit.getBlockPos())) found.add(hit.getBlockPos());
        }
        // prefer anchors spread around the target
        found.sort(Comparator.comparingDouble(b -> -Vec3.atCenterOf(b).distanceToSqr(c)));
        List<BlockPos> picked = new ArrayList<>();
        for (BlockPos b : found) {
            if (picked.stream().allMatch(o -> o.distSqr(b) > 4)) picked.add(b);
            if (picked.size() >= max) break;
        }
        return picked;
    }

    // ================================================================= 7. punch

    static boolean punch(ServerPlayer p) {
        int duration = Math.max(8, SFConfig.duration(AbilityType.ZIPPER_PUNCH));
        double range = SFConfig.range(AbilityType.ZIPPER_PUNCH);
        StandEntity stand = StandManager.ensure(p);
        if (stand != null) stand.play(StandAnim.BARRAGE);
        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                ServerLevel level = pl.serverLevel();
                Vec3 look = pl.getLookAngle();
                Vec3 front = pl.position().add(look.multiply(1, 0, 1).normalize().scale(1.3)).add(0, 0.2, 0);
                StandEntity s = StandManager.stand(pl);
                if (s != null) s.strikeAt(front, 3);
                if (t % 2 == 0) {
                    Vec3 hitPoint = pl.getEyePosition().add(look.scale(range * 0.7));
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), hitPoint.add(randomOffset(pl, 0.5)), 1, 0, 0);
                    Fx.sound(level, hitPoint, ModSounds.PUNCH.get(), 0.6f, 0.9f + pl.getRandom().nextFloat() * 0.4f);
                    for (LivingEntity e : cone(pl, range, 0.55)) {
                        e.invulnerableTime = 0;
                        e.hurt(level.damageSources().playerAttack(pl), (float) (1.6 * SFConfig.DAMAGE_MULTIPLIER.get()));
                        e.knockback(0.08, -look.x, -look.z);
                        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), e.position().add(0, e.getBbHeight() * 0.6, 0), 3, 0.25, 0.1);
                        if (t % 6 == 0) Fx.particles(level, ModParticles.SEAM_GLOW.get(), e.position().add(0, e.getBbHeight() * 0.6, 0), 4, 0.3, 0.0);
                    }
                    if (t % 4 == 0) Fx.screen(pl, ScreenFx.SHAKE, 0.25f, 4);
                }
                if (t == 2) Fx.sound(level, pl.position(), ModSounds.PUNCH_BARRAGE.get(), 1f, 1f);
                if (t == duration) {
                    if (s != null) s.play(StandAnim.HEAVY);
                    for (LivingEntity e : cone(pl, range + 0.5, 0.5)) {
                        e.invulnerableTime = 0;
                        e.hurt(level.damageSources().playerAttack(pl), (float) (6 * SFConfig.DAMAGE_MULTIPLIER.get()));
                        e.knockback(1.4, -look.x, -look.z);
                        if (canAffect(e)) e.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, 20, 0, false, true));
                    }
                    Vec3 hitPoint = pl.getEyePosition().add(look.scale(range * 0.8));
                    Fx.sound(level, hitPoint, ModSounds.PUNCH_HEAVY.get(), 1.2f, 1f);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), hitPoint, 3, 0.2, 0);
                    Fx.particles(level, ModParticles.ZIPPER_TOOTH.get(), hitPoint, 14, 0.3, 0.2);
                    Fx.shake(level, hitPoint, 12, 0.9f, 10);
                    Fx.screen(pl, ScreenFx.FOV_PUNCH, 1f, 8);
                    return false;
                }
                return true;
            }

            @Override
            public String key() { return "punch"; }
        });
        return true;
    }

    static Vec3 randomOffset(ServerPlayer p, double s) {
        var r = p.getRandom();
        return new Vec3((r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * s);
    }

    static List<LivingEntity> cone(ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range + 1), e -> {
            if (e == p || !e.isAlive() || e instanceof DecoyEntity) return false;
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            return to.length() <= range + e.getBbWidth() && to.normalize().dot(look) >= minDot;
        });
    }

    // ================================================================= 8. aerial

    static boolean aerial(ServerPlayer p, StandData data) {
        int range = SFConfig.range(AbilityType.AERIAL_ZIPPER);
        int duration = SFConfig.duration(AbilityType.AERIAL_ZIPPER);
        Vec3 look = p.getLookAngle();
        Vec3 point = p.getEyePosition().add(look.scale(range));
        BlockHitResult hit = pickBlock(p, range);
        if (hit != null) point = hit.getLocation().subtract(look.scale(1.2));
        BlockPos center = BlockPos.containing(point.x, point.y - 0.5, point.z);
        ServerLevel level = p.serverLevel();
        List<BlockPos> platform = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) platform.add(center.offset(dx, 0, dz));
        StandManager.animate(p, StandAnim.UNZIP);
        float angle = angleFor(new Vec3(0, 1, 0), look.multiply(1, 0, 1));
        ZipperEntity z = spawn(p, ZipperKind.AERIAL, new Vec3(center.getX() + 0.5, center.getY() + 1.03, center.getZ() + 0.5),
                new Vec3(0, 1, 0), angle, 3.0f, 1.6f).life(duration);
        z.open();
        // the platform is stitched from the zipper itself; restored when the zipper closes
        SeparationManager.get(level).stitch(level, platform, ModBlocks.ZIPPER_PLATFORM.get().defaultBlockState(), z.getUUID(), duration);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), Vec3.atCenterOf(center), 24, 1.0, 0.02);
        linkPending(p, data, z);
        return true;
    }

    // ================================================================= 10. body

    static boolean body(ServerPlayer p) {
        if (p.hasEffect(ModEffects.ZIPPER_BODY)) {
            p.removeEffect(ModEffects.ZIPPER_BODY);
            Fx.sound(p.serverLevel(), p.position(), ModSounds.ZIPPER_CLOSE.get(), 1f, 1.2f);
            return false;
        }
        int duration = SFConfig.duration(AbilityType.ZIPPER_BODY);
        p.addEffect(new MobEffectInstance(ModEffects.ZIPPER_BODY, duration, 0, false, false, true));
        ZipperEntity z = spawn(p, ZipperKind.BODY, p.position().add(0, 1, 0), new Vec3(0, 0, 1), 90f, 1.2f, 0.3f)
                .attachTo(p).life(duration + 20);
        z.open();
        Fx.sound(p.serverLevel(), p.position(), ModSounds.ZIPPER_OPEN.get(), 1f, 1.3f);
        Fx.particles(p.serverLevel(), ModParticles.SEAM_GLOW.get(), p.position().add(0, 1, 0), 20, 0.3, 0.0);
        return true;
    }

    // ================================================================= 11. decoy

    static boolean decoy(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int duration = SFConfig.duration(AbilityType.ZIPPER_DECOY);
        double range = SFConfig.range(AbilityType.ZIPPER_DECOY);
        DecoyEntity decoy = DecoyEntity.create(p, duration);
        level.addFreshEntity(decoy);
        Vec3 start = p.position();
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(p.getRandom().nextBoolean() ? 1 : -1);
        Vec3 wanted = start.add(side.scale(range * 0.6)).subtract(dir.scale(range * 0.5));
        BlockHitResult wall = level.clip(new ClipContext(start.add(0, 1, 0), wanted.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() == HitResult.Type.BLOCK) wanted = wall.getLocation().subtract(0, 1, 0).subtract(side.scale(0.6));
        Vec3 dest = safeSpot(level, wanted, start);

        ZipperEntity a = spawn(p, ZipperKind.ESCAPE, start.add(0, 1, 0), side.reverse(), 90f, 2.0f, 1.0f).life(20);
        a.open();
        p.teleportTo(level, dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        ZipperEntity b = spawn(p, ZipperKind.ESCAPE, dest.add(0, 1, 0), side, 90f, 2.0f, 1.0f).life(20);
        b.open();
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 50, 0, false, false));
        Fx.afterimage(level, start, dest);
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p).forEach(m -> m.setTarget(decoy));
        Fx.sound(level, dest, ModSounds.TELEPORT.get(), 0.8f, 1.4f);
        return true;
    }

    // ================================================================= 12. trap

    static boolean trap(ServerPlayer p) {
        BlockHitResult hit = pickBlock(p, SFConfig.range(AbilityType.ZIPPER_TRAP));
        Vec3 pos;
        if (hit != null && hit.getDirection() == Direction.UP) pos = hit.getLocation();
        else pos = new Vec3(p.getX(), Math.floor(p.getY()), p.getZ()).add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        ZipperEntity z = spawn(p, ZipperKind.TRAP, pos.add(0, 0.03, 0), new Vec3(0, 1, 0),
                angleFor(new Vec3(0, 1, 0), p.getLookAngle().multiply(1, 0, 1)), 1.6f, 1.0f)
                .life(3600).effectDuration(SFConfig.duration(AbilityType.ZIPPER_TRAP));
        StandManager.animate(p, StandAnim.CAST);
        p.displayClientMessage(Component.translatable("message.stickyfingers.trap_set", z.zipperId()).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    // ================================================================= 13. projectile

    static boolean projectile(ServerPlayer p) {
        ZipperProjectileEntity proj = ZipperProjectileEntity.shoot(p, 2.4f);
        p.level().addFreshEntity(proj);
        StandManager.animate(p, StandAnim.HEAVY);
        Fx.sound(p.serverLevel(), p.position(), ModSounds.PROJECTILE_FIRE.get(), 1f, 1f);
        return true;
    }

    // ================================================================= 14. teleport

    static boolean teleport(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        int range = SFConfig.range(AbilityType.ZIPPER_TELEPORT);
        ZipperEntity aimed = ZipperNetwork.raycast(p, range);
        Vec3 dest;
        float yaw = p.getYRot();
        ZipperEntity destZipper = null;
        if (aimed != null) {
            destZipper = aimed;
            dest = ZipperNetwork.exitPoint(aimed, p);
        } else {
            Entity marked = level.getEntity(data.markedTarget(level.getGameTime()));
            if (marked instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) <= range) {
                Vec3 from = le.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                dest = safeSpot(level, le.position().subtract(from.scale(1.4)), le.position());
                yaw = (float) Math.toDegrees(Math.atan2(-from.x, from.z));
            } else {
                ZipperEntity exit = ZipperNetwork.byId(data.exitZipper());
                if (exit != null && exit.level() == level && exit.distanceTo(p) <= range) {
                    destZipper = exit;
                    dest = ZipperNetwork.exitPoint(exit, p);
                } else {
                    BlockHitResult hit = pickBlock(p, range);
                    if (hit == null) return false;
                    Vec3 n = ZipperEntity.normalOf(hit.getDirection());
                    dest = safeSpot(level, hit.getLocation().add(n.scale(0.6)).subtract(0, n.y > 0.5 ? 0 : 0.9, 0), p.position());
                }
            }
        }
        final Vec3 target = dest;
        final float finalYaw = yaw;
        final ZipperEntity finalDest = destZipper;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        ZipperEntity origin = spawn(p, ZipperKind.ESCAPE, p.position().add(0, 1, 0).add(look.scale(0.5)), look.reverse(), 90f, 2.2f, 1.1f).life(30);
        origin.open();
        Fx.screen(p, ScreenFx.ZIP_TRANSITION, 1f, 16);
        StandManager.animate(p, StandAnim.UNZIP);
        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t < 6) {
                    // pulled into the zipper
                    pl.setDeltaMovement(origin.position().subtract(pl.position().add(0, 1, 0)).scale(0.25));
                    pl.hurtMarked = true;
                    return true;
                }
                Fx.afterimage(level, pl.position(), target);
                pl.teleportTo(level, target.x, target.y, target.z, finalYaw, pl.getXRot());
                pl.fallDistance = 0;
                if (finalDest != null) finalDest.open();
                else {
                    ZipperEntity out = spawn(pl, ZipperKind.ESCAPE, target.add(0, 1, 0), Vec3.directionFromRotation(0, finalYaw), 90f, 2.2f, 1.1f).life(24);
                    out.open();
                }
                Fx.sound(level, target, ModSounds.TELEPORT.get(), 1f, 1f);
                Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), target.add(0, 1, 0), 18, 0.4, 0.1);
                Fx.screen(pl, ScreenFx.FOV_PUNCH, 0.6f, 6);
                origin.closeAndDiscard();
                return false;
            }

            @Override
            public String key() { return "teleport"; }
        });
        return true;
    }

    // ================================================================= 15. slice

    static boolean slice(ServerPlayer p) {
        BlockHitResult hit = pickBlock(p, 6);
        if (hit == null) return false;
        ServerLevel level = p.serverLevel();
        int length = SFConfig.range(AbilityType.ZIPPER_SLICE);
        int duration = SFConfig.duration(AbilityType.ZIPPER_SLICE);
        Direction dir = p.getDirection();
        BlockPos start = hit.getBlockPos();
        List<BlockPos> area = new ArrayList<>();
        int top = start.getY();
        for (int k = 0; k < length; k++) {
            for (int h = -2; h <= 3; h++) {
                BlockPos bp = start.relative(dir, k).above(h);
                if (!level.getBlockState(bp).isAir()) { area.add(bp); top = Math.max(top, bp.getY()); }
            }
        }
        if (area.isEmpty()) return false;
        Vec3 a = Vec3.atCenterOf(start);
        Vec3 b = Vec3.atCenterOf(start.relative(dir, length - 1));
        Vec3 mid = a.lerp(b, 0.5);
        Vec3 n = new Vec3(0, 1, 0);
        Vec3 along = Vec3.atLowerCornerOf(dir.getNormal());
        StandManager.animate(p, StandAnim.HEAVY);
        ZipperEntity z = spawn(p, ZipperKind.SLICE, new Vec3(mid.x, top + 1.03, mid.z), n, angleFor(n, along), length, 1.0f)
                .opensArea(area, duration).life(duration);
        z.open();
        Fx.shake(level, mid, 14, 0.7f, 12);
        Fx.seam(level, a.add(0, top - start.getY() + 0.6, 0), b.add(0, top - start.getY() + 0.6, 0), length * 3);
        return true;
    }

    // ================================================================= 19. floor

    static boolean floor(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockHitResult hit = pickBlock(p, 6);
        BlockPos top = hit != null && hit.getDirection() == Direction.UP ? hit.getBlockPos() : p.blockPosition().below();
        int depth = SFConfig.range(AbilityType.ZIPPER_FLOOR);
        int duration = SFConfig.duration(AbilityType.ZIPPER_FLOOR);
        List<BlockPos> area = new ArrayList<>();
        for (int d = 0; d < depth; d++) {
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                BlockPos bp = top.offset(dx, -d, dz);
                if (bp.getY() <= level.getMinBuildHeight() + 1) continue;
                area.add(bp);
            }
        }
        // never open a shaft into lava / the void
        area.removeIf(bp -> !level.getFluidState(bp.below()).isEmpty() && level.getFluidState(bp.below()).is(net.minecraft.tags.FluidTags.LAVA));
        StandManager.animate(p, StandAnim.SLAM);
        Vec3 center = new Vec3(top.getX() + 1.0, top.getY() + 1.03, top.getZ() + 1.0);
        ZipperEntity z = spawn(p, ZipperKind.FLOOR, center, new Vec3(0, 1, 0),
                angleFor(new Vec3(0, 1, 0), p.getLookAngle().multiply(1, 0, 1)), 2.6f, 1.9f)
                .opensArea(area, duration).life(duration);
        z.open();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        Fx.shake(level, center, 10, 0.6f, 10);
        return true;
    }

    // ================================================================= 20. repair

    static boolean repair(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int range = SFConfig.range(AbilityType.ZIPPER_REPAIR);
        Set<UUID> owners = new HashSet<>();
        int closed = 0;
        for (ZipperEntity z : ZipperNetwork.ownedBy(p.getUUID())) {
            if (z.level() == level && z.distanceTo(p) <= range) {
                owners.add(z.getUUID());
                Fx.seam(level, z.position(), p.position().add(0, 1, 0), 8);
                z.closeAndDiscard();
                closed++;
            }
        }
        int restored = SeparationManager.get(level).restoreNear(level, p.blockPosition(), range, owners.isEmpty() ? Set.of(UUID.randomUUID()) : owners);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range))) {
            if (e.hasEffect(ModEffects.UNZIPPED) || e.hasEffect(ModEffects.ZIPPER_BOUND)) {
                e.removeEffect(ModEffects.UNZIPPED);
                e.removeEffect(ModEffects.ZIPPER_BOUND);
                closed++;
            }
        }
        StandManager.animate(p, StandAnim.CAST);
        Fx.sound(level, p.position(), ModSounds.WORLD_CLOSE.get(), 0.7f, 1.4f);
        Fx.particles(level, ModParticles.SEAM_GLOW.get(), p.position().add(0, 1, 0), 30, range * 0.3, 0.0);
        p.displayClientMessage(Component.translatable("message.stickyfingers.repaired", closed + restored).withStyle(ChatFormatting.AQUA), true);
        return closed + restored > 0;
    }

    private Abilities() {}
}
