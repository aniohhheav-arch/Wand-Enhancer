package dev.riftverse.event;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.CreatureKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** The rules of each reality: weather, physics quirks and its living inhabitants. */
public final class UniverseEffects {
    private UniverseEffects() {}

    public static void playerSecond(ServerPlayer player, UniverseSpec spec) {
        if (spec == null || TransitManager.inTransit(player)) return;
        ServerLevel level = player.serverLevel();
        RandomSource r = player.getRandom();
        if (spec.dreamlike && player.getDeltaMovement().y < -0.45 && player.fallDistance > 4) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
        }
        if (spec.glitch > 0.3f && !player.isCreative() && r.nextFloat() < spec.glitch * 0.025f) {
            glitchBlink(level, player, r);
        }
        if (spec.stormIntensity > 0.55f && r.nextFloat() < 0.05f * spec.stormIntensity) {
            int dx = r.nextInt(61) - 30;
            int dz = r.nextInt(61) - 30;
            int x = player.getBlockX() + dx;
            int z = player.getBlockZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x + 0.5, y, z + 0.5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
    }

    private static void glitchBlink(ServerLevel level, ServerPlayer player, RandomSource r) {
        Vec3 from = player.position();
        for (int attempt = 0; attempt < 8; attempt++) {
            double dx = (r.nextDouble() - 0.5) * 9;
            double dz = (r.nextDouble() - 0.5) * 9;
            BlockPos target = BlockPos.containing(from.x + dx, from.y + 1, from.z + dz);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos p = target.above(dy);
                if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                        && level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                    level.sendParticles(RvParticles.GLITCH.get().with(0x39FF14, 0.5f, 14), from.x, from.y + 1, from.z, 30, 0.3, 0.8, 0.3, 0.05);
                    player.connection.teleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), player.getXRot());
                    level.playSound(null, p, RvSounds.GLITCH_NOISE.get(), SoundSource.PLAYERS, 0.7f, 0.8f + r.nextFloat() * 0.4f);
                    PacketDistributor.sendToPlayer(player, new Payloads.Shake(0.25f, 8, 0.15f, 0x39FF14));
                    return;
                }
            }
        }
    }

    public static void levelTick(ServerLevel level) {
        if (level.getGameTime() % 80 != 0) return;
        boolean spawning;
        try {
            spawning = RiftverseConfig.UNIVERSE_CREATURE_SPAWNING.get();
        } catch (IllegalStateException e) {
            spawning = true;
        }
        if (!spawning) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            UniverseSpec spec = UniverseRegistry.specAt(player.getBlockX(), player.getBlockZ());
            if (spec.creatures.isEmpty()) continue;
            spawnAround(level, player, spec);
        }
    }

    public static EntityType<? extends Mob> typeOf(CreatureKind kind) {
        return switch (kind) {
            case ASTRAL_JELLY -> RvEntities.ASTRAL_JELLY.get();
            case SKY_WHALE -> RvEntities.SKY_WHALE.get();
            case LUMEN_STRIDER -> RvEntities.LUMEN_STRIDER.get();
            case NEON_DRONE -> RvEntities.NEON_DRONE.get();
            case GLITCHLING -> RvEntities.GLITCHLING.get();
            case VOID_STALKER -> RvEntities.VOID_STALKER.get();
            case CRYSTAL_SENTINEL -> RvEntities.CRYSTAL_SENTINEL.get();
            case RIFT_WRAITH -> RvEntities.RIFT_WRAITH.get();
            case ABYSSAL_LEVIATHAN -> RvEntities.ABYSSAL_LEVIATHAN.get();
            case VOID_CULTIST -> RvEntities.VOID_CULTIST.get();
            case CRYSTAL_SPIDER -> RvEntities.CRYSTAL_SPIDER.get();
            case STAR_MOTH -> RvEntities.STAR_MOTH.get();
            case LUNAR_GOLEM -> RvEntities.LUNAR_GOLEM.get();
        };
    }

    private static boolean flies(CreatureKind kind) {
        return kind == CreatureKind.ASTRAL_JELLY || kind == CreatureKind.SKY_WHALE || kind == CreatureKind.NEON_DRONE
                || kind == CreatureKind.RIFT_WRAITH || kind == CreatureKind.ABYSSAL_LEVIATHAN || kind == CreatureKind.STAR_MOTH;
    }

    private static void spawnAround(ServerLevel level, ServerPlayer player, UniverseSpec spec) {
        RandomSource r = level.random;
        AABB area = player.getBoundingBox().inflate(72);
        int existing = level.getEntitiesOfClass(Mob.class, area, m -> m.getType().getDescriptionId().startsWith("entity.riftverse.")).size();
        int cap = 5 + Math.round(spec.hostility * 3);
        if (existing >= cap) return;
        CreatureKind kind = spec.creatures.get(r.nextInt(spec.creatures.size()));
        if (kind.hostile && r.nextFloat() > Math.min(0.9f, spec.hostility * 0.55f)) return;
        if (kind == CreatureKind.ABYSSAL_LEVIATHAN && (r.nextFloat() > 0.04f || !spec.hasSea)) return;
        boolean bigFlyer = kind == CreatureKind.SKY_WHALE || kind == CreatureKind.ABYSSAL_LEVIATHAN;
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = (bigFlyer ? 40 : 22) + r.nextDouble() * 26;
        int x = (int) (player.getX() + Math.cos(angle) * dist);
        int z = (int) (player.getZ() + Math.sin(angle) * dist);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        boolean noGround = ground <= level.getMinBuildHeight() + 1;
        if (!flies(kind) && noGround) return;
        int y;
        if (kind == CreatureKind.SKY_WHALE) y = Math.max(ground, spec.hasSea ? spec.seaLevel : 0) + 25 + r.nextInt(30);
        else if (kind == CreatureKind.ABYSSAL_LEVIATHAN) y = spec.seaLevel + 6 + r.nextInt(12);
        else if (flies(kind)) y = (noGround ? (int) player.getY() : ground) + 4 + r.nextInt(14);
        else y = ground;
        if (spec.terrain == TerrainMode.INVERTED && !flies(kind)) y = Math.min(y, (int) player.getY() + 6);
        y = Math.min(y, level.getMaxBuildHeight() - 8);
        Mob mob = typeOf(kind).create(level);
        if (mob == null) return;
        mob.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0);
        if (!flies(kind) && !level.noCollision(mob)) return;
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null && !kind.hostile) scale.setBaseValue(spec.creatureScale * (0.85 + r.nextDouble() * 0.3));
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.NATURAL, null);
        level.addFreshEntity(mob);
    }
}
