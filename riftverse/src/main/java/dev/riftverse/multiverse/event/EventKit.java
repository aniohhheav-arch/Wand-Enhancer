package dev.riftverse.multiverse.event;

import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.block.RiftType;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.transit.Destination;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for event implementations. */
final class EventKit {
    private EventKit() {}

    static List<ServerPlayer> playersNear(ActiveEvent e, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : e.level.players()) {
            if (!p.isSpectator() && p.position().distanceToSqr(e.center) <= radius * radius) out.add(p);
        }
        return out;
    }

    /** Ground (top solid block + 1) at x/z, or the fallback height over the void. */
    static BlockPos ground(ServerLevel level, int x, int z, int fallbackY) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1) y = fallbackY;
        return new BlockPos(x, y, z);
    }

    static BlockPos ringPos(ActiveEvent e, double angle, double radius, int lift) {
        int x = (int) Math.floor(e.center.x + Math.cos(angle) * radius);
        int z = (int) Math.floor(e.center.z + Math.sin(angle) * radius);
        return ground(e.level, x, z, (int) e.center.y).above(lift);
    }

    /** Opens a temporary, non-harvestable rift owned by the event. Returns false if the spot is blocked. */
    static boolean openRift(ActiveEvent e, BlockPos pos, RiftType type, int lifetime, @Nullable Destination dest) {
        ServerLevel level = e.level;
        if (!level.getBlockState(pos).isAir() || !level.isInWorldBounds(pos)) return false;
        level.setBlockAndUpdate(pos, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, type));
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift) rift.configure(dest, level.getGameTime() + lifetime, false);
        e.rifts.add(pos.immutable());
        return true;
    }

    static void closeRifts(ActiveEvent e) {
        for (BlockPos p : e.rifts) {
            if (e.level.getBlockState(p).is(RvBlocks.RIFT.get())) RiftBlockEntity.collapse(e.level, p);
        }
        e.rifts.clear();
    }

    static void pruneRifts(ActiveEvent e) {
        e.rifts.removeIf(p -> !e.level.getBlockState(p).is(RvBlocks.RIFT.get()));
    }

    @Nullable
    static <T extends Mob> T spawn(ActiveEvent e, EntityType<T> type, Vec3 at) {
        T mob = type.create(e.level);
        if (mob == null) return null;
        mob.moveTo(at.x, at.y, at.z, e.level.random.nextFloat() * 360f, 0);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, e.level, e.level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        e.level.addFreshEntity(mob);
        e.entities.add(mob.getUUID());
        return mob;
    }

    @Nullable
    static Entity entity(ActiveEvent e, UUID id) {
        return e.level.getEntity(id);
    }

    /** Number of event entities still alive (forgetting the dead). */
    static int alive(ActiveEvent e) {
        Iterator<UUID> it = e.entities.iterator();
        int n = 0;
        while (it.hasNext()) {
            Entity ent = e.level.getEntity(it.next());
            if (ent == null || !ent.isAlive()) it.remove();
            else n++;
        }
        return n;
    }

    static void discardAll(ActiveEvent e) {
        for (UUID id : e.entities) {
            Entity ent = e.level.getEntity(id);
            if (ent != null) ent.discard();
        }
        e.entities.clear();
    }

    @Nullable
    static ServerPlayer nearestPlayer(ActiveEvent e, Vec3 from, double radius) {
        ServerPlayer best = null;
        double bd = radius * radius;
        for (ServerPlayer p : e.level.players()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double d = p.position().distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    static void drop(ActiveEvent e, Vec3 at, Item item, int count) {
        ItemEntity it = new ItemEntity(e.level, at.x, at.y, at.z, new ItemStack(item, count));
        it.setDefaultPickUpDelay();
        e.level.addFreshEntity(it);
    }

    /** A cinematic burst: staggered shockwave rings, a pillar of light and a spray of sparks. */
    static void burst(ActiveEvent e, Vec3 at, int color, float scale) {
        ServerLevel level = e.level;
        boolean heavy = dev.riftverse.RiftverseConfig.get(dev.riftverse.RiftverseConfig.HEAVY_EFFECTS, true);
        for (int i = 0; i < 4; i++) {
            final int k = i;
            dev.riftverse.multiverse.Scheduler.later(i * 5, () -> level.sendParticles(dev.riftverse.registry.RvParticles.RING.get()
                    .with(k % 2 == 0 ? color : 0xFFFFFF, (8f + k * 10f) * scale, 26), at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0));
        }
        int h = (int) (40 * scale);
        for (int y = 0; y < h; y += 2) {
            level.sendParticles(dev.riftverse.registry.RvParticles.STREAK.get().with(color, 1.6f * scale, 24), at.x, at.y + y, at.z, heavy ? 3 : 1, 0.3, 0.6, 0.3, 0.02);
        }
        level.sendParticles(dev.riftverse.registry.RvParticles.SPARK.get().with(color, 1f, 40), at.x, at.y + 2, at.z, heavy ? (int) (160 * scale) : 40,
                2 * scale, 2 * scale, 2 * scale, 0.9);
        level.sendParticles(dev.riftverse.registry.RvParticles.MOTE.get().with(0xFFFFFF, 1.4f, 60), at.x, at.y + 6, at.z, heavy ? 60 : 15, 10 * scale, 6, 10 * scale, 0.02);
        level.playSound(null, at.x, at.y, at.z, dev.riftverse.registry.RvSounds.BLACK_HOLE_COLLAPSE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 3f * scale, 1.3f);
        for (ServerPlayer p : playersNear(e, 96)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new dev.riftverse.network.Payloads.Shake(0.5f * scale, 25, 0.35f, color));
        }
    }
}
