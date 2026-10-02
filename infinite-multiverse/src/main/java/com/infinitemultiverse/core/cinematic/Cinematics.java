package com.infinitemultiverse.core.cinematic;

import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.network.ScenePayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Server-side entry point for rendered scenes and cutscenes. Presentation only: never changes game state. */
public final class Cinematics {
    /** Flag: the scene follows its entity (auras, wards, thrusters). */
    public static final int FOLLOW = 1;
    /** Flag: an upgraded (mastery) variant — scenes render bigger and denser. */
    public static final int EMPOWERED = 2;
    /** Flag: area waves only sweep a frontal cone. */
    public static final int CONE = 4;

    /** Domain type index rides in the high bits of flags. */
    public static int domainFlags(int index) {
        return index << 4;
    }

    private Cinematics() {
    }

    public static void scene(ServerLevel level, ResourceLocation scene, Vec3 origin, Vec3 dir, int color, int duration,
                             @Nullable Entity entity, float param, int flags) {
        double range = Math.max(MultiverseConfig.SERVER.vfxBroadcastRange.get(), param * 3.0);
        PacketDistributor.sendToPlayersNear(level, null, origin.x, origin.y, origin.z, range,
                new ScenePayload(scene, origin, dir, color & 0xFFFFFF, duration, entity == null ? -1 : entity.getId(), param, flags));
    }

    public static void scene(ServerLevel level, ResourceLocation scene, Vec3 origin, Vec3 dir, int color, int duration, @Nullable Entity entity, float param) {
        scene(level, scene, origin, dir, color, duration, entity, param, 0);
    }

    /** Ends a long-running scene (domain interior, portal, mirror world...) early. */
    public static void stop(ServerLevel level, ResourceLocation scene, Vec3 near, @Nullable Entity entity) {
        PacketDistributor.sendToPlayersNear(level, null, near.x, near.y, near.z, 256,
                new ScenePayload(scene, near, Vec3.ZERO, 0, 0, entity == null ? -1 : entity.getId(), 0, 0));
    }

    /** Attached to (and following) an entity, e.g. an aura. */
    public static void attached(ServerLevel level, ResourceLocation scene, Entity entity, int color, int duration, float param) {
        scene(level, scene, entity.position(), entity.getLookAngle(), color, duration, entity, param, FOLLOW);
    }
}
