package dev.mysticarts.power.spells;

import dev.mysticarts.block.SpellBlock;
import dev.mysticarts.entity.FieldKind;
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
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.Scheduler;
import dev.mysticarts.world.TemporaryBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The Reality Stone: everything it changes is temporary, and always comes back. */
public final class RealitySpells {
    public static final int RED = 0xE3122E;
    private static final String TRANSFORM_TAG = "mysticarts:transformed";

    private static final Block[] TRANSMUTE = {Blocks.GOLD_BLOCK, Blocks.RED_STAINED_GLASS, Blocks.SLIME_BLOCK, Blocks.AMETHYST_BLOCK,
            Blocks.CRYING_OBSIDIAN, Blocks.RED_GLAZED_TERRACOTTA, Blocks.MAGENTA_STAINED_GLASS, Blocks.RAW_GOLD_BLOCK, Blocks.CRIMSON_NYLIUM, Blocks.SHROOMLIGHT};
    private static final Block[][] ENVIRONMENTS = {
            {Blocks.AMETHYST_BLOCK, Blocks.CALCITE, Blocks.SMOOTH_BASALT},
            {Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, Blocks.MAGMA_BLOCK},
            {Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICKS},
            {Blocks.MYCELIUM, Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK},
            {Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE},
            {Blocks.MOSS_BLOCK, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES},
    };
    private static final EntityType<?>[] HARMLESS = {EntityType.CHICKEN, EntityType.RABBIT, EntityType.SHEEP, EntityType.PIG, EntityType.FROG};

    private RealitySpells() {}

    public static void register() {
        Spells.register(Ability.REALITY_TRANSMUTE, RealitySpells::transmute);
        Spells.register(Ability.REALITY_TERRAIN, RealitySpells::terrain);
        Spells.register(Ability.REALITY_DISTORT, c -> {
            Vec3 at = Aim.point(c.player(), 24);
            SpellFieldEntity.spawn(c.level(), c.player(), at.add(0, 1, 0), FieldKind.REALITY_DISTORT, 7f, 220, RED);
            Fx.sound(c.level(), at, MaSounds.STONE_REALITY.get(), 1.2f, 0.7f);
            return true;
        });
        Spells.register(Ability.REALITY_ILLUSION, RealitySpells::illusion);
        Spells.register(Ability.REALITY_WEATHER, RealitySpells::weather);
        Spells.register(Ability.REALITY_TRANSFORM, c -> {
            LivingEntity t = Aim.entity(c.player(), 28, e -> e instanceof Mob && !(e instanceof Player) && Aim.controllable(e));
            return t instanceof Mob m && transform(c.level(), m, 600);
        });
        Spells.register(Ability.REALITY_PHYSICS, c -> {
            Vec3 at = Aim.point(c.player(), 24);
            SpellFieldEntity.spawn(c.level(), c.player(), at.add(0, 1.5, 0), FieldKind.ZERO_G, 8f, 220, 0xFF8A7A);
            Fx.sound(c.level(), at, MaSounds.STONE_REALITY.get(), 1.2f, 1.4f);
            return true;
        });
        Spells.register(Ability.REALITY_BARRIER, c -> wall(c, MaBlocks.SPELL_HAZARD.get().defaultBlockState().setValue(SpellBlock.TINT, SpellBlock.tintOf(Source.REALITY)), 3, 7, 4, 240));
        Spells.register(Ability.REALITY_RESTORE, RealitySpells::restore);
        Spells.register(Ability.REALITY_ENVIRONMENT, c -> environment(c, c.player().blockPosition(), 9, ENVIRONMENTS[c.level().random.nextInt(ENVIRONMENTS.length)], 600));
        Spells.register(Ability.REALITY_ULTIMATE, RealitySpells::rewrite);
    }

    // ============================================================================================ helpers

    /** A conjured wall {@code distance} ahead of the caster, perpendicular to their view. */
    public static boolean wall(Cast c, BlockState state, int distance, int width, int height, int ticks) {
        ServerLevel level = c.level();
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        Vec3 fwd = Aim.horizontal(c.look());
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 base = c.player().position().add(fwd.scale(distance));
        int placed = 0;
        for (int w = -width / 2; w <= width / 2; w++) {
            for (int h = 0; h < height; h++) {
                BlockPos p = BlockPos.containing(base.add(side.scale(w)).add(0, h, 0));
                if (TemporaryBlocks.replaceable(level, p, true) && temp.place(level, p, state, ticks)) placed++;
            }
        }
        if (placed == 0) return false;
        Fx.send(level, FxKind.RING, base.add(0, height * 0.5, 0), fwd, c.color(), width * 0.5f, 12, -1);
        Fx.sound(level, base, MaSounds.SHIELD_UP.get(), 1.2f, 0.8f);
        c.data().pose(Poses.SHIELD, 14);
        return true;
    }

    /** The first solid block with air above, searching up and down from {@code from}. */
    @org.jetbrains.annotations.Nullable
    static BlockPos surface(ServerLevel level, BlockPos from) {
        for (int dy = 4; dy >= -6; dy--) {
            BlockPos p = from.offset(0, dy, 0);
            if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
        }
        return null;
    }

    static boolean environment(Cast c, BlockPos center, int radius, Block[] palette, int ticks) {
        ServerLevel level = c.level();
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos top = surface(level, center.offset(dx, 0, dz));
                if (top == null) continue;
                for (int depth = 0; depth < 2; depth++) {
                    BlockPos p = top.below(depth);
                    if (!TemporaryBlocks.replaceable(level, p, false)) continue;
                    Block b = palette[level.random.nextInt(palette.length)];
                    if (temp.place(level, p, b.defaultBlockState(), ticks + level.random.nextInt(40))) n++;
                }
            }
        }
        Fx.send(level, FxKind.WAVE, Vec3.atCenterOf(center), Vec3.ZERO, RED, radius, 20, -1);
        Fx.sound(level, Vec3.atCenterOf(center), MaSounds.TRANSFORM.get(), 1.4f, 0.8f);
        return n > 0;
    }

    // ============================================================================================ spells

    private static boolean transmute(Cast c) {
        BlockHitResult hit = Aim.block(c.player(), 28);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        ServerLevel level = c.level();
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        Block pick = TRANSMUTE[level.random.nextInt(TRANSMUTE.length)];
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(hit.getBlockPos().offset(-2, -2, -2), hit.getBlockPos().offset(2, 2, 2))) {
            if (p.distSqr(hit.getBlockPos()) > 6.5 || !TemporaryBlocks.replaceable(level, p, false)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.isCollisionShapeFullBlock(level, p)) continue;
            BlockPos at = p.immutable();
            if (temp.place(level, at, pick.defaultBlockState(), 600)) {
                n++;
                if (level.random.nextInt(3) == 0) Fx.send(level, FxKind.TRANSMUTE, Vec3.atCenterOf(at), RED, 1f, 0);
            }
        }
        if (n == 0) return false;
        Fx.send(level, FxKind.ARC, c.hands(), hit.getLocation().subtract(c.hands()), RED, 0.4f, 0, c.player().getId());
        Fx.sound(level, hit.getLocation(), MaSounds.TRANSFORM.get(), 1.2f, 1.2f);
        return true;
    }

    private static boolean terrain(Cast c) {
        ServerLevel level = c.level();
        LivingEntity t = Aim.entity(c.player(), 24);
        BlockPos center;
        if (t != null) center = t.blockPosition().below();
        else {
            BlockHitResult hit = Aim.block(c.player(), 24);
            if (hit.getType() != HitResult.Type.BLOCK) return false;
            center = hit.getBlockPos();
        }
        BlockPos top = surface(level, center);
        if (top == null) return false;
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        int rise = 5;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos colTop = surface(level, top.offset(dx, 0, dz));
                if (colTop == null) continue;
                BlockState column = level.getBlockState(colTop);
                if (column.hasBlockEntity()) continue;
                for (int y = 1; y <= rise; y++) {
                    BlockPos p = colTop.above(y);
                    if (!TemporaryBlocks.replaceable(level, p, true)) break;
                    temp.place(level, p, y == rise ? column : (level.random.nextBoolean() ? Blocks.STONE.defaultBlockState() : column), 300);
                }
            }
        }
        for (Entity e : level.getEntities(c.player(), new net.minecraft.world.phys.AABB(top).inflate(1.5, 2, 1.5))) {
            e.setDeltaMovement(e.getDeltaMovement().add(0, 1.4, 0));
            e.hurtMarked = true;
            if (e instanceof LivingEntity l && l != c.player()) MysticSpells.hurt(c, l, 4f);
        }
        Fx.screen(level, Vec3.atCenterOf(top), 16, 0.8f, 0, RED);
        Fx.sound(level, Vec3.atCenterOf(top), MaSounds.SLAM.get(), 1.2f, 0.7f);
        c.data().pose(Poses.SLAM, 10);
        return true;
    }

    private static boolean illusion(Cast c) {
        ServerLevel level = c.level();
        Vec3 at = Aim.point(c.player(), 20);
        BlockPos base = surface(level, BlockPos.containing(at));
        if (base == null) base = BlockPos.containing(at);
        TemporaryBlocks temp = TemporaryBlocks.get(level);
        BlockState wall = MaBlocks.SPELL_ILLUSION.get().defaultBlockState().setValue(SpellBlock.TINT, SpellBlock.tintOf(Source.REALITY));
        int n = 0;
        // an illusory watchtower: hollow walls, a crenellated top
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int y = 1; y <= 7; y++) {
                    boolean place = edge && (y < 7 || (dx + dz) % 2 == 0) || y == 5;
                    if (!place) continue;
                    BlockPos p = base.offset(dx, y, dz);
                    if (TemporaryBlocks.replaceable(level, p, true) && temp.place(level, p, wall, 600)) n++;
                }
            }
        }
        if (n == 0) return false;
        Fx.send(level, FxKind.WAVE, Vec3.atCenterOf(base.above(3)), Vec3.ZERO, RED, 4f, 14, -1);
        Fx.sound(level, Vec3.atCenterOf(base), MaSounds.TRANSFORM.get(), 1f, 1.4f);
        return true;
    }

    private static boolean weather(Cast c) {
        ServerLevel level = c.level();
        String key;
        if (level.isThundering()) {
            level.setWeatherParameters(6000, 0, false, false);
            key = "message.mysticarts.weather_clear";
        } else if (level.isRaining()) {
            level.setWeatherParameters(0, 6000, true, true);
            key = "message.mysticarts.weather_thunder";
        } else {
            level.setWeatherParameters(0, 6000, true, false);
            key = "message.mysticarts.weather_rain";
        }
        Fx.send(level, FxKind.WAVE, c.player().position().add(0, 2, 0), Vec3.ZERO, RED, 24f, 30, -1);
        Fx.screen(level, c.player().position(), 32, 0.4f, 0.3f, RED);
        Fx.sound(c.player(), MaSounds.STONE_REALITY.get(), 1.5f, 0.6f);
        c.player().displayClientMessage(Component.translatable(key).withColor(RED), true);
        c.data().pose(Poses.RAISE, 20);
        return true;
    }

    /**
     * Rewrites a creature into something harmless. The original is stored on the stand-in and restored when the spell
     * ends, even across a restart (checked when the stand-in loads).
     */
    public static boolean transform(ServerLevel level, Mob target, int ticks) {
        CompoundTag original = new CompoundTag();
        if (!target.save(original)) return false;
        EntityType<?> type = HARMLESS[level.random.nextInt(HARMLESS.length)];
        Entity standIn = type.create(level);
        if (!(standIn instanceof Mob mob)) return false;
        mob.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0);
        CompoundTag tag = new CompoundTag();
        tag.put("original", original);
        tag.putLong("until", level.getGameTime() + ticks);
        mob.getPersistentData().put(TRANSFORM_TAG, tag);
        mob.setPersistenceRequired();
        mob.setGlowingTag(true);
        target.discard();
        level.addFreshEntity(mob);
        Fx.send(level, FxKind.WAVE, mob.position().add(0, 0.5, 0), Vec3.ZERO, RED, 2f, 10, -1);
        Fx.burst(level, mob.position().add(0, 0.5, 0), RED, 1f, 1.5f);
        Fx.sound(level, mob.position(), MaSounds.TRANSFORM.get(), 1f, 1f);
        UUID id = mob.getUUID();
        Scheduler.scheduleSafe(ticks, server -> {
            Entity e = level.getEntity(id);
            if (e != null) revert(level, e);
        });
        return true;
    }

    /** Called on entity load too: restores a transformed creature whose spell has ended. */
    public static void checkTransformed(ServerLevel level, Entity e) {
        if (!e.getPersistentData().contains(TRANSFORM_TAG)) return;
        long until = e.getPersistentData().getCompound(TRANSFORM_TAG).getLong("until");
        if (level.getGameTime() >= until) revert(level, e);
        else {
            UUID id = e.getUUID();
            Scheduler.scheduleSafe((int) (until - level.getGameTime()), server -> {
                Entity x = level.getEntity(id);
                if (x != null) revert(level, x);
            });
        }
    }

    private static void revert(ServerLevel level, Entity standIn) {
        CompoundTag tag = standIn.getPersistentData().getCompound(TRANSFORM_TAG);
        if (tag.isEmpty()) return;
        standIn.getPersistentData().remove(TRANSFORM_TAG);
        Vec3 pos = standIn.position();
        Entity back = EntityType.loadEntityRecursive(tag.getCompound("original"), level, e -> {
            e.moveTo(pos.x, pos.y, pos.z, e.getYRot(), e.getXRot());
            return e;
        });
        standIn.discard();
        if (back != null) {
            level.addFreshEntity(back);
            Fx.burst(level, pos.add(0, 1, 0), RED, 1f, 1.2f);
            Fx.sound(level, pos, MaSounds.TRANSFORM.get(), 0.8f, 0.7f);
        }
    }

    private static boolean restore(Cast c) {
        PowerData.Anchor a = c.data().realityAnchor;
        if (a == null) {
            c.player().displayClientMessage(Component.translatable("message.mysticarts.no_reality_anchor"), true);
            return false;
        }
        ServerLevel to = c.player().server.getLevel(a.dimension());
        if (to == null) return false;
        ServerPlayer p = c.player();
        Vec3 from = p.position();
        Fx.send(c.level(), FxKind.WAVE, from.add(0, 1, 0), Vec3.ZERO, RED, 3f, 12, -1);
        p.teleportTo(to, a.pos().x, a.pos().y, a.pos().z, a.yaw(), a.pitch());
        p.resetFallDistance();
        Fx.send(to, FxKind.WAVE, a.pos().add(0, 1, 0), Vec3.ZERO, RED, 3f, 12, -1);
        Fx.sound(to, a.pos(), MaSounds.TRANSFORM.get(), 1.2f, 1.2f);
        return true;
    }

    private static boolean rewrite(Cast c) {
        ServerPlayer p = c.player();
        ServerLevel level = c.level();
        environment(c, p.blockPosition(), 18, new Block[] {MaBlocks.REALITY_CRYSTAL.get(), Blocks.CRIMSON_NYLIUM, Blocks.RED_STAINED_GLASS, Blocks.SHROOMLIGHT}, 700);
        List<LivingEntity> foes = Aim.around(p, p.position(), 18, e -> e instanceof Mob && Aim.hostile(e) && Aim.controllable(e));
        int n = 0;
        for (LivingEntity e : foes) {
            if (n++ >= 12) break;
            transform(level, (Mob) e, 700);
        }
        SpellFieldEntity.spawn(level, p, p.position().add(0, 1, 0), FieldKind.REALITY_REWRITE, 18f, 700, RED);
        Fx.screen(level, p.position(), 48, 1.6f, 0.8f, RED);
        Fx.sound(p, MaSounds.ULTIMATE_RELEASE.get(), 1.6f, 0.9f);
        c.data().pose(Poses.RAISE, 30);
        return true;
    }
}
