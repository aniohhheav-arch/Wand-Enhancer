package dev.riftverse.wormhole;

import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.UniverseTravel;
import dev.riftverse.universe.Archetype;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Walkable wormholes. A wormhole mouth (a harmless, swirling singularity) leads into a physical spacetime tunnel built
 * far out in the Nexus: four themed sections you walk through while the tunnel's stability drains and random
 * phenomena strike. The destination is only revealed in the last section; reach the far end before stability hits
 * zero and you arrive there — otherwise the tunnel collapses and spits you out somewhere random.
 */
public final class WormholeManager {
    public static final int LENGTH = 168;
    public static final int RADIUS = 5;
    private static final String[] SECTIONS = {"EVENT HORIZON", "THE STARFIELD", "TIME ECHO", "EXIT APPROACH"};
    private static final int[] SECTION_COLORS = {0x8A3AFF, 0x40A0FF, 0xFFB040, 0xFFFFFF};


    private static final class Run {
        final int index;
        final BlockPos origin;
        final Destination destination;
        final String destinationName;
        final ServerBossEvent bar;
        final Set<UUID> travellers = new HashSet<>();
        final java.util.Map<UUID, Integer> section = new java.util.HashMap<>();
        float stability = 100f;
        int age;
        boolean done;

        Run(int index, BlockPos origin, Destination destination, String name) {
            this.index = index;
            this.origin = origin;
            this.destination = destination;
            this.destinationName = name;
            this.bar = new ServerBossEvent(Component.literal("WORMHOLE STABILITY"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static final List<Run> RUNS = new ArrayList<>();
    private static int counter;

    private WormholeManager() {}

    // ------------------------------------------------------------------ mouths

    public static final String TAG = "riftverse_wormhole";

    public static boolean isMouth(BlackHoleEntity e) {
        return e.getTags().contains(TAG);
    }

    @Nullable
    private static Archetype targetOf(BlackHoleEntity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(TAG + ":")) return Archetype.byName(t.substring(TAG.length() + 1));
        }
        return null;
    }

    public static BlackHoleEntity spawnMouth(ServerLevel level, Vec3 at, @Nullable Archetype target, int lifetime) {
        BlackHoleEntity hole = RvEntities.BLACK_HOLE.get().create(level);
        if (hole == null) return null;
        hole.addTag(TAG);
        if (target != null) hole.addTag(TAG + ":" + target.id);
        hole.setHarmless(true);
        hole.setCaptures(false);
        hole.setHorizonRadius(1.6f);
        hole.setStyle(BlackHoleEntity.STYLE_GRAVITY);
        hole.setLifetime(lifetime);
        hole.moveTo(at.x, at.y, at.z);
        level.addFreshEntity(hole);
        level.playSound(null, hole.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.AMBIENT, 2f, 0.6f);
        return hole;
    }

    /** Opens a wormhole mouth a few blocks in front of the player. */
    public static void openMouth(ServerPlayer p, @Nullable Archetype target) {
        Vec3 at = p.getEyePosition().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(6));
        spawnMouth(p.serverLevel(), at, target, 20 * 90);
        p.displayClientMessage(Component.literal("A wormhole tears open. Walk into it.").withColor(0xB070FF), true);
    }

    /** Called from the mouth's own tick: anyone stepping into it is taken into the tunnel. */
    public static void mouthTick(BlackHoleEntity hole) {
        ServerLevel level = (ServerLevel) hole.level();
        if (hole.tickCount % 3 == 0) {
            Vec3 c = hole.center();
            level.sendParticles(RvParticles.INFALL.get().with(0xB070FF, 0.8f, 20), c.x, c.y, c.z, 3, 2.5, 2.5, 2.5, 0.02);
        }
        List<ServerPlayer> in = level.getPlayers(p -> p.distanceToSqr(hole.center()) < 2.6 * 2.6 && !p.isSpectator() && !p.isPassenger());
        if (!in.isEmpty()) {
            enter(level.getServer(), in, targetOf(hole));
            hole.collapse();
        }
    }

    /** Natural wormholes: now and then one forms on the ground somewhere near a wandering player. */
    public static void naturalSecond(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (p.tickCount % 1200 != 0 || p.getRandom().nextFloat() > 0.35f) return;
        if (!level.getEntitiesOfClass(BlackHoleEntity.class, p.getBoundingBox().inflate(160), WormholeManager::isMouth).isEmpty()) return;
        RandomSource r = p.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        int dist = 30 + r.nextInt(50);
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(p.getX() + Math.cos(a) * dist, 0, p.getZ() + Math.sin(a) * dist));
        spawnMouth(level, Vec3.atBottomCenterOf(ground).add(0, 2.2, 0), null, 20 * 60 * 10);
    }

    // ------------------------------------------------------------------ tunnel

    private static BlockPos originFor(int index) {
        return new BlockPos(-300_000 - (index % 200) * 64, 110, 0);
    }

    /** Builds the tunnel over several ticks, then sends the travellers in. */
    public static void enter(MinecraftServer server, List<ServerPlayer> players, @Nullable Archetype target) {
        ServerLevel nexus = server.getLevel(RvWorldgen.NEXUS);
        if (nexus == null || players.isEmpty()) return;
        RandomSource r = nexus.random;
        Destination dest = target != null ? Destination.archetype(target) : Destination.random();
        String name = target != null ? target.displayName : "an unknown reality";
        Run run = new Run(counter++, originFor(counter), dest, name);
        for (ServerPlayer p : players) {
            run.travellers.add(p.getUUID());
            RealityOps.cinematic(p, CinematicType.TIME_TRAVEL, 50, p.getEyePosition(), 0x8A3AFF, 0x40A0FF, "", "");
            RealityOps.shield(p, 60);
        }
        int slices = LENGTH + 4;
        for (int z0 = -2; z0 < slices; z0 += 12) {
            int from = z0;
            Scheduler.later((z0 + 2) / 12, () -> {
                for (int z = from; z < Math.min(from + 12, slices); z++) buildSlice(nexus, run.origin, z, r);
            });
        }
        Scheduler.later(slices / 12 + 2, () -> {
            RUNS.add(run);
            BlockPos o = run.origin;
            for (ServerPlayer p : players) {
                if (p.isRemoved()) continue;
                p.teleportTo(nexus, o.getX() + 0.5, o.getY() + 2, o.getZ() + 1.5, 0f, 0f);
                run.bar.addPlayer(p);
                p.displayClientMessage(Component.literal("Walk forward. Don't stop. The tunnel won't hold forever.").withColor(0xB070FF), true);
            }
        });
    }

    private static int sectionOf(int z) {
        return Math.max(0, Math.min(3, z / (LENGTH / 4)));
    }

    private static void buildSlice(ServerLevel l, BlockPos o, int z, RandomSource r) {
        int sec = sectionOf(Math.max(0, z));
        boolean cap = z == -2 || z == LENGTH + 3;
        boolean ring = z % 7 == 0;
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int y = -RADIUS - 1; y <= RADIUS + 1; y++) {
                double d = Math.sqrt(x * x + y * y);
                BlockPos p = o.offset(x, y + RADIUS, z);
                if (d > RADIUS + 1.5) continue;
                boolean shell = d > RADIUS - 0.6 || cap;
                BlockState s;
                if (!shell) {
                    s = y == -RADIUS + 1 && Math.abs(x) <= 1 ? floor(sec, z, x, r) : Blocks.AIR.defaultBlockState();
                    if (y == -RADIUS + 1 && Math.abs(x) > 1 && d < RADIUS - 0.2) s = Blocks.AIR.defaultBlockState();
                } else {
                    s = wall(sec, ring, r);
                }
                l.setBlock(p, s, 2);
            }
        }
    }

    private static BlockState floor(int sec, int z, int x, RandomSource r) {
        return switch (sec) {
            case 0 -> r.nextInt(5) == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
            case 1 -> z % 4 == 0 && x == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
            case 2 -> r.nextInt(6) == 0 ? Blocks.SCULK.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            default -> z % 3 == 0 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
        };
    }

    /** Walls are fully opaque: inside a wormhole there is no outside. */
    private static BlockState wall(int sec, boolean ring, RandomSource r) {
        return switch (sec) {
            case 0 -> ring ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : r.nextInt(30) == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
            case 1 -> r.nextInt(14) == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : ring ? Blocks.LAPIS_BLOCK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
            case 2 -> ring ? Blocks.SHROOMLIGHT.defaultBlockState() : r.nextInt(3) == 0 ? Blocks.OXIDIZED_CUT_COPPER.defaultBlockState() : Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
            default -> ring ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
        };
    }

    private static void clear(ServerLevel l, BlockPos o) {
        for (int z0 = -2; z0 < LENGTH + 4; z0 += 12) {
            int from = z0;
            Scheduler.later((z0 + 2) / 12, () -> {
                for (BlockPos p : BlockPos.betweenClosed(o.offset(-RADIUS - 1, -1, from), o.offset(RADIUS + 1, 2 * RADIUS + 1, Math.min(from + 11, LENGTH + 3)))) {
                    if (!l.getBlockState(p).isAir()) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            });
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        ServerLevel nexus = server.getLevel(RvWorldgen.NEXUS);
        if (nexus == null) return;
        for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
            Run run = it.next();
            if (run.done) {
                it.remove();
                continue;
            }
            tickRun(server, nexus, run);
        }
    }

    private static void tickRun(MinecraftServer server, ServerLevel nexus, Run run) {
        run.age++;
        List<ServerPlayer> inside = new ArrayList<>();
        for (UUID id : run.travellers) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level() == nexus && Math.abs(p.getX() - run.origin.getX()) < 12 && p.getZ() > run.origin.getZ() - 4 && p.getZ() < run.origin.getZ() + LENGTH + 6) inside.add(p);
        }
        if (inside.isEmpty() && run.age > 40) {
            finish(nexus, run);
            return;
        }
        // stability drains steadily and faster in the deeper sections
        int deepest = 0;
        for (ServerPlayer p : inside) deepest = Math.max(deepest, sectionOf((int) (p.getZ() - run.origin.getZ())));
        run.stability -= 0.035f + deepest * 0.012f;
        run.bar.setProgress(Math.max(0f, run.stability / 100f));
        run.bar.setColor(run.stability > 50 ? BossEvent.BossBarColor.PURPLE : run.stability > 25 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        RandomSource r = nexus.random;
        for (ServerPlayer p : inside) {
            int z = (int) (p.getZ() - run.origin.getZ());
            int sec = sectionOf(z);
            Integer prev = run.section.put(p.getUUID(), sec);
            if (prev == null || prev != sec) {
                String sub = sec == 3 ? "DESTINATION: " + run.destinationName.toUpperCase() : "destination unknown";
                RealityOps.cinematic(p, CinematicType.ANNOUNCE, 60, p.getEyePosition(), SECTION_COLORS[sec], 0xFFFFFF, SECTIONS[sec], sub);
            }
            if (run.age % 4 == 0) {
                nexus.sendParticles(RvParticles.STREAK.get().with(SECTION_COLORS[sec], 0.9f, 20), p.getX(), p.getY() + 1, p.getZ() + 8, 6, 3, 3, 1, 0.05);
            }
            if (z >= LENGTH - 4) exit(server, run, p);
        }
        if (run.age % 160 == 80 && !inside.isEmpty()) phenomenon(nexus, run, inside.get(r.nextInt(inside.size())), r);
        if (run.stability <= 0) collapse(server, nexus, run, inside);
    }

    /** Random wormhole phenomena. */
    private static void phenomenon(ServerLevel l, Run run, ServerPlayer p, RandomSource r) {
        switch (r.nextInt(6)) {
            case 0 -> {
                p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 0));
                p.displayClientMessage(Component.literal("GRAVITY INVERSION").withColor(0x80C0FF), true);
            }
            case 1 -> {
                for (int i = 0; i < 6; i++) {
                    l.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, p.getX() + r.nextGaussian() * 2, p.getY() + 4, p.getZ() + r.nextGaussian() * 2, 2, 0.2, 0.2, 0.2, 0);
                }
                p.hurt(l.damageSources().magic(), 3f);
                p.displayClientMessage(Component.literal("SPACETIME DEBRIS").withColor(0xFF7040), true);
            }
            case 2 -> {
                RealityOps.cinematic(p, CinematicType.TIME_TRAVEL, 25, p.getEyePosition(), 0xFFB040, 0xFFFFFF, "", "");
                p.teleportTo(p.getX(), p.getY(), Math.max(run.origin.getZ() + 1.5, p.getZ() - 14));
                p.displayClientMessage(Component.literal("TIME SLIP — you lose ground").withColor(0xFFB040), true);
            }
            case 3 -> {
                Vex echo = EntityType.VEX.create(l);
                if (echo != null) {
                    echo.moveTo(p.getX(), p.getY() + 2, p.getZ() + 6);
                    echo.setCustomName(Component.literal("Echo of a Traveller").withColor(0xA0A0FF));
                    echo.setLimitedLife(20 * 12);
                    echo.setTarget(p);
                    l.addFreshEntity(echo);
                }
                p.displayClientMessage(Component.literal("SOMETHING FOLLOWED YOU IN").withColor(0xA0A0FF), true);
            }
            case 4 -> {
                run.stability = Math.min(100f, run.stability + 12f);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                p.displayClientMessage(Component.literal("STABILITY SURGE").withColor(0x60FF90), true);
            }
            default -> {
                run.stability -= 8f;
                l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 0.5f);
                p.displayClientMessage(Component.literal("THE TUNNEL SHUDDERS").withColor(0xFF4060), true);
            }
        }
    }

    /**
     * Leaving the tunnel: light rushes past in the destination's colour, everything blooms to white, and the new
     * reality fades in around you. The jump happens under cover of the bloom.
     */
    private static void exit(MinecraftServer server, Run run, ServerPlayer p) {
        run.travellers.remove(p.getUUID());
        run.bar.removePlayer(p);
        UniverseTravel.Target t = UniverseTravel.resolveFor(server, run.destination, p.getRandom());
        if (t == null) t = UniverseTravel.resolveFor(server, Destination.nexus(), p.getRandom());
        if (t == null) return;
        UniverseTravel.Target dest = t;
        int color = dest.spec() != null ? dest.color() : 0xB070FF;
        RealityOps.shield(p, 80);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 6, false, false));
        RealityOps.cinematic(p, CinematicType.WORMHOLE_EXIT, 0, p.getEyePosition(), color, 0xFFFFFF, "", "");
        p.serverLevel().playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 2f, 1.4f);
        Scheduler.later(24, () -> {
            if (p.isRemoved()) return;
            p.teleportTo(dest.level(), dest.pos().x, dest.pos().y, dest.pos().z, dest.yaw(), 0f);
            dest.level().sendParticles(RvParticles.RING.get().with(color, 5f, 20), p.getX(), p.getY() + 1, p.getZ(), 2, 0, 0, 0, 0);
            dest.level().sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 1.2f, 18), p.getX(), p.getY() + 1, p.getZ(), 50, 1.5, 1.5, 1.5, 0.4);
        });
        Scheduler.later(50, () -> {
            if (!p.isRemoved()) RealityOps.cinematic(p, CinematicType.ANNOUNCE, 80, p.getEyePosition(), color, 0xFFFFFF,
                    dest.spec() != null ? dest.spec().name : dest.title(), "wormhole transit complete");
        });
    }

    private static void collapse(MinecraftServer server, ServerLevel l, Run run, List<ServerPlayer> inside) {
        for (ServerPlayer p : inside) {
            RealityOps.cinematic(p, CinematicType.COLLAPSE, 70, p.getEyePosition(), 0xFF3A5A, 0x000000, "WORMHOLE COLLAPSE", "thrown out of the tunnel");
            UniverseTravel.Target t = UniverseTravel.resolveFor(server, Destination.random(), p.getRandom());
            if (t != null) {
                ServerPlayer pl = p;
                Scheduler.later(30, () -> pl.teleportTo(t.level(), t.pos().x, t.pos().y, t.pos().z, 0f, 0f));
            }
        }
        finish(l, run);
    }

    private static void finish(ServerLevel l, Run run) {
        run.done = true;
        run.bar.removeAllPlayers();
        Scheduler.later(60, () -> clear(l, run.origin));
    }

    public static int active() {
        return RUNS.size();
    }

    public static void clearAll() {
        for (Run r : RUNS) r.bar.removeAllPlayers();
        RUNS.clear();
    }
}
