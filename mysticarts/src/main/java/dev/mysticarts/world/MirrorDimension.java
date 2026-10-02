package dev.mysticarts.world;

import dev.mysticarts.MysticArts;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Mirror Dimension: a separate, empty dimension into which the caster's surroundings are copied on entry. Anything
 * done there (folding the terrain, fighting) never touches the real world. Also serves as the holding cell for
 * Dimensional Banishment.
 */
public final class MirrorDimension {
    public static final ResourceKey<Level> MIRROR = ResourceKey.create(Registries.DIMENSION, MysticArts.id("mirror"));
    public static final int RADIUS = 18;
    public static final int BELOW = 6;
    public static final int ABOVE = 14;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Entities dragged in alongside a caster, so they can be returned. */
    private static final Map<UUID, List<UUID>> COMPANIONS = new HashMap<>();
    /** Where each caster's copy was made, for the void safety net. */
    private static final Map<UUID, BlockPos> ORIGINS = new HashMap<>();

    private MirrorDimension() {}

    public static boolean isMirror(Level level) {
        return level.dimension() == MIRROR;
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(MIRROR);
    }

    /** Copies the caster's surroundings into the mirror and steps through, optionally dragging a target along. */
    public static boolean enter(ServerPlayer player, PowerData data, @Nullable LivingEntity target) {
        ServerLevel from = player.serverLevel();
        ServerLevel mirror = level(player.server);
        if (mirror == null || from == mirror) return false;
        BlockPos c = player.blockPosition();
        copy(from, mirror, c);
        data.mirrorReturn = new PowerData.Anchor(from.dimension(), player.position(), player.getYRot(), player.getXRot());
        ORIGINS.put(player.getUUID(), c);
        Vec3 pos = player.position();
        Fx.send(from, FxKind.RING, pos.add(0, 1, 0), player.getLookAngle(), 0x9FE8FF, 2.5f, 12, -1);
        List<UUID> dragged = new ArrayList<>();
        if (target != null && Aim.controllable(target) && !(target instanceof Player)) {
            Vec3 tp = target.position();
            Entity moved = Aim.teleport(target, mirror, tp, target.getYRot(), target.getXRot());
            if (moved != null) {
                dragged.add(moved.getUUID());
                moved.getPersistentData().put("mysticarts:mirror_origin", anchorTag(from, tp));
            }
        }
        COMPANIONS.put(player.getUUID(), dragged);
        player.teleportTo(mirror, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        Fx.to(player, FxKind.MIRROR, pos, 0x9FE8FF, 1f, 0);
        Fx.sound(mirror, pos, MaSounds.MIRROR_ENTER.get(), 1.2f, 1f);
        data.dirty = true;
        return true;
    }

    public static void exit(ServerPlayer player, PowerData data) {
        PowerData.Anchor back = data.mirrorReturn;
        ServerLevel mirror = player.serverLevel();
        ServerLevel to = back == null ? null : player.server.getLevel(back.dimension());
        if (to == null) to = player.server.overworld();
        Vec3 pos = back == null ? Vec3.atBottomCenterOf(to.getSharedSpawnPos()) : back.pos();
        List<UUID> dragged = COMPANIONS.remove(player.getUUID());
        if (dragged != null && isMirror(mirror)) {
            for (UUID id : dragged) {
                Entity e = mirror.getEntity(id);
                if (e != null && e.isAlive()) returnEntity(player.server, e);
            }
        }
        ORIGINS.remove(player.getUUID());
        player.teleportTo(to, pos.x, pos.y, pos.z, back == null ? player.getYRot() : back.yaw(), back == null ? player.getXRot() : back.pitch());
        data.mirrorReturn = null;
        data.dirty = true;
        Fx.to(player, FxKind.MIRROR, pos, 0x9FE8FF, 0f, 0);
        Fx.sound(to, pos, MaSounds.MIRROR_EXIT.get(), 1.2f, 1f);
    }

    /** Keeps casters from falling off the edge of their copied island into the void. */
    public static void tick(ServerPlayer player, PowerData data) {
        if (!isMirror(player.level())) return;
        BlockPos origin = ORIGINS.get(player.getUUID());
        if (origin == null) {
            if (data.mirrorReturn == null) {
                // stranded (e.g. after a restart) - send home
                exit(player, data);
            } else {
                ORIGINS.put(player.getUUID(), BlockPos.containing(data.mirrorReturn.pos()));
            }
            return;
        }
        if (player.getY() < origin.getY() - BELOW - 12) {
            Vec3 safe = Vec3.atBottomCenterOf(origin).add(0, 1, 0);
            player.teleportTo(safe.x, safe.y + 2, safe.z);
            player.resetFallDistance();
            Fx.sound(player, MaSounds.MIRROR_FOLD.get(), 1f, 1.4f);
        }
    }

    private static void copy(ServerLevel from, ServerLevel mirror, BlockPos c) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                boolean inside = dx * dx + dz * dz <= RADIUS * RADIUS;
                for (int dy = -BELOW - 1; dy <= ABOVE; dy++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (mirror.isOutsideBuildHeight(p)) continue;
                    BlockState s = inside && dy >= -BELOW ? from.getBlockState(p) : Blocks.AIR.defaultBlockState();
                    if (inside && dy == -BELOW - 1) s = MaBlocks.MIRROR_SHARD.get().defaultBlockState();
                    if (s.hasBlockEntity()) s = Blocks.AIR.defaultBlockState();
                    if (!s.getFluidState().isEmpty() && !s.getFluidState().isSource()) s = Blocks.AIR.defaultBlockState();
                    if (mirror.getBlockState(p) != s) mirror.setBlock(p, s, FLAGS);
                }
            }
        }
    }

    // ============================================================================================ banishment

    private static CompoundTag anchorTag(ServerLevel level, Vec3 pos) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", level.dimension().location().toString());
        t.putDouble("x", pos.x);
        t.putDouble("y", pos.y);
        t.putDouble("z", pos.z);
        return t;
    }

    /** Sends an entity to a mirror-glass cell for {@code ticks}, then brings it back to where it was. */
    public static boolean banish(ServerLevel from, LivingEntity target, int ticks) {
        ServerLevel mirror = level(from.getServer());
        if (mirror == null || from == mirror) return false;
        int slot = Math.floorMod(target.getUUID().hashCode(), 4096);
        BlockPos cell = new BlockPos(1_000_000 + slot * 16, 120, 1_000_000);
        for (BlockPos p : BlockPos.betweenClosed(cell.offset(-3, -1, -3), cell.offset(3, 4, 3))) {
            boolean shell = p.getY() == cell.getY() - 1 || p.getY() == cell.getY() + 4 || Math.abs(p.getX() - cell.getX()) == 3 || Math.abs(p.getZ() - cell.getZ()) == 3;
            mirror.setBlock(p, shell ? MaBlocks.MIRROR_SHARD.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
        }
        int cx = cell.getX() >> 4, cz = cell.getZ() >> 4;
        mirror.setChunkForced(cx, cz, true);
        Vec3 origin = target.position();
        target.getPersistentData().put("mysticarts:mirror_origin", anchorTag(from, origin));
        Entity moved = Aim.teleport(target, mirror, Vec3.atBottomCenterOf(cell), target.getYRot(), 0);
        if (moved == null) {
            mirror.setChunkForced(cx, cz, false);
            return false;
        }
        UUID id = moved.getUUID();
        Scheduler.scheduleSafe(ticks, server -> {
            ServerLevel m = level(server);
            if (m == null) return;
            Entity e = m.getEntity(id);
            if (e != null && e.isAlive()) returnEntity(server, e);
            m.setChunkForced(cx, cz, false);
        });
        return true;
    }

    private static void returnEntity(MinecraftServer server, Entity e) {
        CompoundTag t = e.getPersistentData().getCompound("mysticarts:mirror_origin");
        e.getPersistentData().remove("mysticarts:mirror_origin");
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(t.getString("dim"));
        ServerLevel to = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (to == null) to = server.overworld();
        Vec3 pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
        Entity back = Aim.teleport(e, to, pos, e.getYRot(), e.getXRot());
        if (back != null) Fx.burst(to, pos.add(0, 1, 0), 0x9FE8FF, 1.2f, 1.5f);
    }
}
