package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.UniverseTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cross-dimensional entity travel. Creatures (vanilla or modded, named, tamed, equipped) can pass through rifts and be
 * carried off by black holes. The real entity is moved with vanilla's dimension transition, so its identity, name,
 * equipment, health and data survive; a persistent cooldown tag prevents ping-pong loops and duplicates; a failed
 * resolution simply leaves the creature where it was.
 */
public final class MigrationManager {
    public static final String COOLDOWN_TAG = "riftverse_migrated_until";

    private MigrationManager() {}

    public static boolean enabled(ServerLevel level) {
        return RealityState.get(level.getServer()).migrationEnabled && RiftverseConfig.get(RiftverseConfig.ENTITY_MIGRATION, true);
    }

    public static boolean canMigrate(Entity e) {
        if (e instanceof Player || !e.isAlive() || e.isPassenger() || e.isVehicle()) return false;
        if (e instanceof dev.riftverse.entity.BlackHoleEntity || e instanceof dev.riftverse.entity.PortalEntity) return false;
        if (e instanceof dev.riftverse.entity.boss.CosmicDeityEntity deity && deity.isDevourer()) return false;
        if (e instanceof Mob m && m.isLeashed()) return false;
        return e.getPersistentData().getLong(COOLDOWN_TAG) <= e.level().getGameTime();
    }

    /** Moves an entity through a dimensional passage. Returns the arrived entity (a new object if it changed dimension). */
    @Nullable
    public static Entity migrate(Entity e, Destination dest, int color) {
        if (!(e.level() instanceof ServerLevel from) || !canMigrate(e)) return null;
        UniverseTravel.Target t = UniverseTravel.resolveFor(from.getServer(), dest, e.getRandom());
        if (t == null) return null;
        Vec3 origin = e.position();
        departFx(from, origin, e.getBbHeight(), color);
        Vec3 at = t.pos().add((e.getRandom().nextDouble() - 0.5) * 2, 0, (e.getRandom().nextDouble() - 0.5) * 2);
        BlockPos bp = BlockPos.containing(at);
        t.level().getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(bp), 2, bp);
        Entity arrived;
        if (t.level() == from) {
            e.teleportTo(at.x, at.y, at.z);
            arrived = e;
        } else {
            arrived = e.changeDimension(new DimensionTransition(t.level(), at, Vec3.ZERO, e.getYRot(), e.getXRot(), DimensionTransition.DO_NOTHING));
        }
        if (arrived == null) return null;
        arrived.getPersistentData().putLong(COOLDOWN_TAG, t.level().getGameTime() + 2400);
        arrived.setDeltaMovement(Vec3.ZERO);
        arrived.fallDistance = 0;
        arriveFx(t.level(), arrived.position(), arrived.getBbHeight(), color);
        return arrived;
    }

    public static void departFx(ServerLevel level, Vec3 p, float height, int color) {
        level.sendParticles(RvParticles.RING.get().with(color, 2.5f, 14), p.x, p.y + height / 2, p.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.STREAK.get().with(color, 0.7f, 18), p.x, p.y + height / 2, p.z, 24, 0.3, height / 2, 0.3, 0.25);
        level.sendParticles(RvParticles.INFALL.get().with(0xFFFFFF, 0.6f, 16), p.x, p.y + height / 2, p.z, 16, 0.6, height / 2, 0.6, 0.05);
        level.playSound(null, p.x, p.y, p.z, RvSounds.RIFT_ENTER.get(), SoundSource.NEUTRAL, 0.8f, 1.4f);
    }

    public static void arriveFx(ServerLevel level, Vec3 p, float height, int color) {
        level.sendParticles(RvParticles.RING.get().with(color, 3f, 16), p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.SPARK.get().with(color, 0.6f, 24), p.x, p.y + height / 2, p.z, 30, 0.4, height / 2, 0.4, 0.3);
        level.playSound(null, p.x, p.y, p.z, RvSounds.UNIVERSE_ARRIVE.get(), SoundSource.NEUTRAL, 0.6f, 1.5f);
    }

    /** A living creature caught by a black hole: it spins in, flares, and is flung into another universe. */
    public static boolean swallow(LivingEntity living, int color) {
        if (!enabled((ServerLevel) living.level()) || !canMigrate(living)) return false;
        return migrate(living, Destination.random(), color) != null;
    }
}
