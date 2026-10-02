package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The End Protocols. A protocol run is a timed sequence: build-up (each protocol's own set pieces, particles, sounds
 * and environmental reactions around every occupant), the point of no return (the universe is unmade by the shared
 * erasure wave and everyone inside is evacuated to the Nexus) and an aftermath tail. Before anything happens the
 * universe definition is backed up and a block snapshot is taken around each occupant, so every protocol can be
 * followed by an optional automatic reconstruction or a later manual restore. Previews play the full sequence for one
 * viewer without touching the world.
 */
public final class EndProtocols {
    private static final List<Run> RUNS = new ArrayList<>();
    private static int nextId = 1;

    private EndProtocols() {}

    public static final class Run {
        public final int id;
        public final EndProtocol protocol;
        @Nullable
        public final UniverseId universe;
        public final ServerLevel level;
        public final boolean preview;
        public final boolean reconstruct;
        public boolean permanent;
        /** True when this run ends a whole vanilla/modded dimension rather than a universe slot. */
        public boolean wholeDimension;
        @Nullable
        public final UUID viewer;
        public final int duration;
        public final int climax;
        public int age;
        boolean pastClimax;
        final List<UUID> entities = new ArrayList<>();
        final List<BlockPos> placed = new ArrayList<>();
        Vec3 origin;

        Run(int id, EndProtocol protocol, @Nullable UniverseId universe, ServerLevel level, boolean preview, boolean reconstruct, @Nullable UUID viewer,
            int duration, Vec3 origin) {
            this.id = id;
            this.protocol = protocol;
            this.universe = universe;
            this.level = level;
            this.preview = preview;
            this.reconstruct = reconstruct;
            this.viewer = viewer;
            this.duration = duration;
            this.climax = (int) (duration * 0.82f);
            this.origin = origin;
        }

        public String describe() {
            String what = wholeDimension ? RealityOps.worldName(level) : universe == null ? "?" : universe.designation();
            return "#" + id + " " + protocol.id + (preview ? " (preview)" : " on " + what) + (permanent ? " • PERMANENT" : "")
                    + " — " + Math.max(0, (climax - age) / 20) + "s to " + (preview ? "climax" : "point of no return")
                    + (reconstruct ? " • reconstruct after" : "");
        }
    }

    public static List<Run> runs() {
        return List.copyOf(RUNS);
    }

    public static int durationTicks(EndProtocol p) {
        return RiftverseConfig.get(RiftverseConfig.PROTOCOL_SECONDS.get(p), p.defaultSeconds) * 20;
    }

    private static boolean heavy() {
        return RiftverseConfig.get(RiftverseConfig.HEAVY_EFFECTS, true);
    }

    // ------------------------------------------------------------------ control

    public static RealityOps.Outcome execute(MinecraftServer server, UniverseId id, EndProtocol protocol, @Nullable ServerPlayer actor, boolean reconstruct) {
        return execute(server, id, protocol, actor, reconstruct, false);
    }

    public static RealityOps.Outcome execute(MinecraftServer server, UniverseId id, EndProtocol protocol, @Nullable ServerPlayer actor, boolean reconstruct,
                                             boolean permanent) {
        if (permanent) reconstruct = false;
        RealityOps.Outcome fail = RealityOps.prepareErase(server, id, actor, protocol.id + " protocol", permanent);
        if (fail != null) return fail;
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        UniverseSpec spec = RealityOps.spec(server, id);
        int duration = durationTicks(protocol);
        Vec3 origin = Vec3.atBottomCenterOf(surface(level, id.centerX(), id.centerZ(), 80));
        Run run = new Run(nextId++, protocol, id, level, false, reconstruct, actor == null ? null : actor.getUUID(), duration, origin);
        run.permanent = permanent;
        RUNS.add(run);
        for (ServerPlayer p : RealityOps.playersIn(server, id)) {
            RealityOps.cinematic(p, protocol.cinematic, duration + 40, cinematicFocus(run, p.position()), protocol.colorA, protocol.colorB, protocol.title, protocol.subtitle);
            RealityOps.shield(p, duration + 160);
        }
        if (actor != null && !id.equals(RealityOps.universeOf(actor))) {
            RealityOps.cinematic(actor, CinematicType.ANNOUNCE, 0, actor.getEyePosition(), protocol.colorA, 0xFFFFFF, protocol.title,
                    "Executing on " + spec.name + " [" + id.designation() + "]");
        }
        setup(run);
        return RealityOps.Outcome.ok(protocol.title + " engaged on " + spec.name + " [" + id.designation() + "]. Point of no return in " + run.climax / 20
                + "s (/multiverse reality protocol stop aborts). " + (permanent ? "This ending is PERMANENT: no backup was kept." : "Backup + snapshots saved"
                + (reconstruct ? "; it will be reconstructed afterwards." : ".")));
    }

    /** Ends the whole dimension a player stands in (Earth, the Nether, the End, modded worlds) — permanently. */
    public static RealityOps.Outcome executeDimension(MinecraftServer server, ServerLevel level, EndProtocol protocol, @Nullable ServerPlayer actor) {
        RealityOps.Outcome fail = RealityOps.prepareEndDimension(server, level, actor, protocol.id + " protocol");
        if (fail != null) return fail;
        int duration = durationTicks(protocol);
        Vec3 origin = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        Run run = new Run(nextId++, protocol, null, level, false, false, actor == null ? null : actor.getUUID(), duration, origin);
        run.permanent = true;
        run.wholeDimension = true;
        RUNS.add(run);
        String name = RealityOps.worldName(level);
        for (ServerPlayer p : level.players()) {
            RealityOps.cinematic(p, protocol.cinematic, duration + 40, cinematicFocus(run, p.position()), protocol.colorA, protocol.colorB, protocol.title,
                    "The end of " + name);
            RealityOps.shield(p, duration + 160);
        }
        if (actor != null && actor.level() != level) {
            RealityOps.cinematic(actor, CinematicType.ANNOUNCE, 0, actor.getEyePosition(), protocol.colorA, 0xFFFFFF, protocol.title, "Executing on " + name);
        }
        setup(run);
        return RealityOps.Outcome.ok(protocol.title + " engaged on " + name + ". It will be gone FOREVER in " + run.climax / 20
                + "s; everyone there is carried to the Nexus. (/multiverse reality protocol stop aborts before then.)");
    }

    public static RealityOps.Outcome preview(ServerPlayer viewer, EndProtocol protocol) {
        for (Run r : RUNS) if (r.preview && viewer.getUUID().equals(r.viewer)) return RealityOps.Outcome.fail("You are already previewing a protocol.");
        int duration = durationTicks(protocol);
        Run run = new Run(nextId++, protocol, null, viewer.serverLevel(), true, false, viewer.getUUID(), duration, viewer.position());
        RUNS.add(run);
        RealityOps.cinematic(viewer, protocol.cinematic, duration + 40, cinematicFocus(run, viewer.position()), protocol.colorA, protocol.colorB,
                protocol.title + " (PREVIEW)", protocol.subtitle);
        RealityOps.shield(viewer, duration + 60);
        setup(run);
        return RealityOps.Outcome.ok("Previewing " + protocol.title + " (" + duration / 20 + "s). Nothing will be erased.");
    }

    /** Stops runs (all, or those targeting one universe / previewed by one player). Runs past the point of no return only lose their visuals. */
    public static RealityOps.Outcome stop(MinecraftServer server, @Nullable UniverseId universe, @Nullable UUID viewer) {
        int aborted = 0;
        int late = 0;
        for (Run r : runs()) {
            if (universe != null && !universe.equals(r.universe)) continue;
            if (viewer != null && universe == null && !(r.preview && viewer.equals(r.viewer)) && !viewer.equals(r.viewer)) continue;
            RUNS.remove(r);
            if (!r.preview && !r.pastClimax && r.wholeDimension) {
                RealityOps.abortEndDimension(server, r.level);
                aborted++;
            } else if (!r.preview && !r.pastClimax && r.universe != null) {
                RealityOps.abortErase(server, r.universe);
                aborted++;
            } else if (!r.preview) {
                late++;
            } else {
                aborted++;
            }
            cleanup(r);
            for (ServerPlayer p : viewers(r)) RealityOps.stopCinematic(p);
        }
        if (aborted == 0 && late == 0) return RealityOps.Outcome.fail("No matching protocol is running.");
        return RealityOps.Outcome.ok("Stopped " + aborted + " protocol(s)" + (late > 0 ? "; " + late + " had passed the point of no return (erasure continues, restore afterwards)." : "."));
    }

    // ------------------------------------------------------------------ simulation

    private static List<ServerPlayer> viewers(Run r) {
        if (r.preview) {
            ServerPlayer p = r.viewer == null ? null : r.level.getServer().getPlayerList().getPlayer(r.viewer);
            return p == null ? List.of() : List.of(p);
        }
        if (r.wholeDimension) return new ArrayList<>(r.level.players());
        return r.universe == null ? List.of() : RealityOps.playersIn(r.level.getServer(), r.universe);
    }

    /** Points the sequence is staged around: every viewer, or the universe origin when nobody is inside. */
    private static List<Vec3> anchors(Run r) {
        List<Vec3> out = new ArrayList<>();
        for (ServerPlayer p : viewers(r)) out.add(p.position());
        if (out.isEmpty() && !r.preview) out.add(r.origin);
        return out;
    }

    private static Vec3 cinematicFocus(Run r, Vec3 around) {
        return switch (r.protocol) {
            case ORBITAL_ANNIHILATION -> around.add(0, 48, 0);
            case SINGULARITY_COLLAPSE -> around.add(0, 24, 0);
            case CELESTIAL_DEVOURER -> around.add(0, 40, 0);
            case BLACK_HOLE_INFUSION -> around.add(18, 12, 0);
            default -> around.add(0, 1.5, 0);
        };
    }

    public static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        Iterator<Run> it = RUNS.iterator();
        List<Run> done = new ArrayList<>();
        while (it.hasNext()) {
            Run r = it.next();
            r.age++;
            try {
                for (Vec3 a : anchors(r)) stage(r, a);
                if (r.age == r.climax) climax(r);
            } catch (RuntimeException ex) {
                dev.riftverse.Riftverse.LOGGER.error("End protocol {} failed", r.describe(), ex);
                if (!r.pastClimax && !r.preview) climax(r);
                r.age = r.duration;
            }
            if (r.age >= r.duration) {
                it.remove();
                done.add(r);
            }
        }
        for (Run r : done) cleanup(r);
    }

    private static void setup(Run r) {
        ServerLevel level = r.level;
        for (Vec3 a : anchors(r)) {
            switch (r.protocol) {
                case ORBITAL_ANNIHILATION -> {
                    if (r.preview) break;
                    BlockPos c = BlockPos.containing(a.add(0, 48, 0));
                    BlockState bricks = RvBlocks.NEXUS_BRICKS.get().defaultBlockState();
                    BlockState glow = RvBlocks.NEXUS_GLOW.get().defaultBlockState();
                    BlockState dark = RvBlocks.VOID_STONE.get().defaultBlockState();
                    for (int dx = -9; dx <= 9; dx++) {
                        for (int dz = -9; dz <= 9; dz++) {
                            double d = Math.sqrt(dx * dx + dz * dz);
                            if (d > 9.3) continue;
                            BlockPos p = c.offset(dx, 0, dz);
                            BlockState s = d > 8.2 ? glow : (d < 2.2 ? glow : ((dx + dz) % 3 == 0 ? dark : bricks));
                            place(r, p, s);
                            if (d < 3.2 && d > 2.2) place(r, p.below(), glow);
                            if (d > 6.5 && d < 7.5 && (dx == 0 || dz == 0)) for (int up = 1; up <= 4; up++) place(r, p.above(up), up == 4 ? glow : bricks);
                        }
                    }
                }
                case SINGULARITY_COLLAPSE -> spawnHole(r, a.add(0, 24, 0), 0.4f);
                case BLACK_HOLE_INFUSION -> spawnHole(r, a.add(18, 12, 0), 1.0f);
                case CELESTIAL_DEVOURER -> {
                    Mob whale = RvEntities.SKY_WHALE.get().create(level);
                    if (whale == null) break;
                    whale.moveTo(a.x, a.y + 80, a.z, 0, 90);
                    whale.setNoAi(true);
                    whale.setNoGravity(true);
                    whale.setInvulnerable(true);
                    whale.setSilent(true);
                    whale.setPersistenceRequired();
                    AttributeInstance scale = whale.getAttribute(Attributes.SCALE);
                    if (scale != null) scale.setBaseValue(14.0);
                    level.addFreshEntity(whale);
                    r.entities.add(whale.getUUID());
                }
                default -> {}
            }
        }
        level.playSound(null, r.origin.x, r.origin.y, r.origin.z, RvSounds.WARDEN_CHARGE.get(), SoundSource.AMBIENT, 4f, 0.5f);
    }

    private static void place(Run r, BlockPos p, BlockState s) {
        if (!r.level.isInWorldBounds(p) || !r.level.getBlockState(p).isAir()) return;
        r.level.setBlock(p, s, 3);
        r.placed.add(p.immutable());
    }

    private static void spawnHole(Run r, Vec3 at, float radius) {
        BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), r.level);
        hole.moveTo(at.x, at.y, at.z, 0, 0);
        hole.setHorizonRadius(radius);
        hole.setNatural(!r.preview);
        hole.setCaptures(false);
        hole.setHarmless(r.preview);
        r.level.addFreshEntity(hole);
        r.entities.add(hole.getUUID());
    }

    @Nullable
    private static Entity nearestOwned(Run r, Vec3 a) {
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (UUID id : r.entities) {
            Entity e = r.level.getEntity(id);
            if (e == null) continue;
            double d = e.position().distanceToSqr(a);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static BlockPos surface(ServerLevel level, int x, int z, int fallback) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y <= level.getMinBuildHeight() + 1 ? fallback : y, z);
    }

    private static BlockPos randomSurface(Run r, Vec3 a, double radius) {
        RandomSource rnd = r.level.random;
        double ang = rnd.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(rnd.nextDouble()) * radius;
        return surface(r.level, (int) Math.floor(a.x + Math.cos(ang) * d), (int) Math.floor(a.z + Math.sin(ang) * d), (int) a.y);
    }

    /** Lifts a surface block into a flying fragment (never in previews or outside the Expanse). */
    private static void lift(Run r, BlockPos top, Vec3 velocity, boolean floating) {
        if (r.preview || r.level.dimension() != RvWorldgen.EXPANSE) return;
        BlockPos p = top.below();
        BlockState s = r.level.getBlockState(p);
        if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(r.level, p) < 0 || s.is(Blocks.BEDROCK)) return;
        FallingBlockEntity fb = FallingBlockEntity.fall(r.level, p, s);
        fb.dropItem = false;
        fb.setNoGravity(floating);
        fb.setDeltaMovement(velocity);
        fb.time = -2000;
    }

    private static void beam(ServerLevel level, Vec3 from, Vec3 to, int color, float size, int steps) {
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(RvParticles.STREAK.get().with(color, size, 6), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void shake(Run r, float amount, int ticks, float flash, int color) {
        for (ServerPlayer p : viewers(r)) PacketDistributor.sendToPlayer(p, new Payloads.Shake(amount, ticks, flash, color));
    }

    private static void sound(Run r, Vec3 at, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        if (r.preview) {
            for (ServerPlayer p : viewers(r)) p.playNotifySound(s, SoundSource.AMBIENT, vol, pitch);
        } else {
            r.level.playSound(null, at.x, at.y, at.z, s, SoundSource.AMBIENT, vol, pitch);
        }
    }

    /** One anchor's share of the sequence this tick. */
    private static void stage(Run r, Vec3 a) {
        ServerLevel level = r.level;
        RandomSource rnd = level.random;
        float k = Math.min(1f, r.age / (float) r.climax);
        boolean heavy = heavy();
        EndProtocol pr = r.protocol;
        // shared atmosphere: the sky fills with drifting motes and falling streaks that thicken as the end approaches
        if (r.age % 2 == 0) {
            int n = (int) ((heavy ? 10 : 3) * (0.3f + k));
            level.sendParticles(RvParticles.MOTE.get().with(pr.colorA, 1.2f, 50), a.x, a.y + 12, a.z, n, 28, 14, 28, 0.03);
            if (k > 0.4f) level.sendParticles(RvParticles.STREAK.get().with(pr.colorB, 1.1f, 20), a.x, a.y + 30, a.z, n / 2 + 1, 30, 10, 30, 0.2);
        }
        if (r.age % 30 == 0) {
            level.sendParticles(RvParticles.RING.get().with(pr.colorA, 12f + 40f * k, 26), a.x, a.y + 0.5, a.z, 1, 0, 0, 0, 0);
            if (heavy && k > 0.5f) level.sendParticles(RvParticles.GLITCH.get().with(pr.colorB, 0.8f, 18), a.x, a.y + 2, a.z, 40, 20, 6, 20, 0.05);
        }
        switch (pr) {
            case ORBITAL_ANNIHILATION -> {
                Vec3 platform = a.add(0, 47, 0);
                if (r.age % 3 == 0) level.sendParticles(RvParticles.RING.get().with(pr.colorA, 10f, 8), platform.x, platform.y - 1, platform.z, 1, 0, 0, 0, 0);
                if (k < 0.35f) {
                    // targeting scan: a ring sweeps the ground while laser sights flick across it
                    if (r.age % 10 == 0) {
                        level.sendParticles(RvParticles.RING.get().with(pr.colorA, 6f + 60f * (k / 0.35f), 14), a.x, a.y + 0.3, a.z, 1, 0, 0, 0, 0);
                        sound(r, a, RvSounds.UI_SELECT.get(), 2f, 0.6f + k * 2);
                    }
                    if (r.age % 6 == 0) {
                        BlockPos t = randomSurface(r, a, 40);
                        beam(level, platform, Vec3.atCenterOf(t), pr.colorA, 0.35f, heavy ? 30 : 12);
                    }
                } else {
                    // barrage: energy lances slam down, gouging the land and lighting the sky
                    int every = k < 0.7f ? 6 : 3;
                    if (r.age % every == 0) {
                        BlockPos t = randomSurface(r, a, 44);
                        Vec3 hit = Vec3.atBottomCenterOf(t);
                        beam(level, platform.add(rnd.nextDouble() * 6 - 3, 0, rnd.nextDouble() * 6 - 3), hit, pr.colorB, 1.4f, heavy ? 40 : 16);
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hit.x, hit.y + 0.5, hit.z, 1, 0, 0, 0, 0);
                        level.sendParticles(RvParticles.RING.get().with(pr.colorA, 7f, 12), hit.x, hit.y + 0.3, hit.z, 1, 0, 0, 0, 0);
                        sound(r, hit, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.6f + rnd.nextFloat() * 0.3f);
                        for (int i = 0; i < 3; i++) lift(r, surface(level, t.getX() + rnd.nextInt(5) - 2, t.getZ() + rnd.nextInt(5) - 2, t.getY()),
                                new Vec3(rnd.nextDouble() - 0.5, 0.6 + rnd.nextDouble() * 0.6, rnd.nextDouble() - 0.5), false);
                        if (rnd.nextInt(4) == 0) lightning(r, t);
                        shake(r, 0.35f + 0.4f * k, 10, 0.08f, pr.colorB);
                    }
                }
            }
            case SINGULARITY_COLLAPSE -> {
                Entity e = nearestOwned(r, a);
                Vec3 c = e != null ? e.position() : a.add(0, 24, 0);
                if (e instanceof BlackHoleEntity hole && r.age % 5 == 0) {
                    float size = k < 0.88f ? 0.4f + 7.5f * (k / 0.88f) : 7.9f * (1f - (k - 0.88f) / 0.12f) + 0.15f;
                    hole.setHorizonRadius(Math.max(0.15f, size));
                }
                if (r.age % 2 == 0) {
                    int n = heavy ? 10 : 3;
                    for (int i = 0; i < n; i++) {
                        double th = rnd.nextDouble() * Math.PI * 2;
                        double ph = rnd.nextDouble() * Math.PI;
                        double rad = 34 * (1 - 0.6 * k);
                        level.sendParticles(RvParticles.INFALL.get().with(pr.colorA, 0.8f, 40), c.x + Math.sin(ph) * Math.cos(th) * rad, c.y + Math.cos(ph) * rad,
                                c.z + Math.sin(ph) * Math.sin(th) * rad, 1, 0, 0, 0, 0.02);
                    }
                }
                if (k > 0.3f && r.age % (k > 0.7f ? 2 : 5) == 0) {
                    BlockPos t = randomSurface(r, a, 36);
                    Vec3 dir = c.subtract(Vec3.atCenterOf(t)).normalize().scale(0.6 + k);
                    lift(r, t, dir, true);
                }
                if (r.age % 40 == 0) sound(r, c, RvSounds.BLACK_HOLE_PULL.get(), 3f, 0.5f + k * 0.4f);
                if (r.age % 20 == 0) shake(r, 0.2f + 0.6f * k, 20, 0f, pr.colorA);
            }
            case CELESTIAL_DEVOURER -> {
                Entity whale = nearestOwned(r, a);
                float descend = Math.min(1f, k / 0.4f);
                Vec3 body = a.add(0, 80 - 42 * descend, 0);
                if (whale != null) {
                    whale.moveTo(body.x, body.y, body.z, whale.getYRot() + 0.6f, 90);
                    whale.setDeltaMovement(Vec3.ZERO);
                }
                Vec3 mouth = body.add(0, -10, 0);
                double spin = r.age * 0.12;
                if (r.age % 2 == 0) {
                    for (int arm = 0; arm < (heavy ? 5 : 2); arm++) {
                        for (int i = 0; i < 6; i++) {
                            double rr = 2 + i * 2.2;
                            double ang = spin + arm * Math.PI * 2 / 5 + i * 0.45;
                            level.sendParticles(RvParticles.MOTE.get().with(i % 2 == 0 ? pr.colorA : pr.colorB, 1.2f, 16), mouth.x + Math.cos(ang) * rr, mouth.y,
                                    mouth.z + Math.sin(ang) * rr, 1, 0, 0, 0, 0);
                        }
                    }
                }
                if (r.age % 8 == 0) level.sendParticles(RvParticles.RING.get().with(pr.colorA, 14f + 4f * (float) Math.sin(spin), 10), mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
                if (k > 0.4f) {
                    // the inhale: light, air and terrain stream up into the maw
                    if (r.age % 2 == 0) {
                        BlockPos t = randomSurface(r, a, 40);
                        beam(level, Vec3.atCenterOf(t), mouth, pr.colorB, 0.5f, heavy ? 14 : 6);
                    }
                    if (r.age % 3 == 0) {
                        BlockPos t = randomSurface(r, a, 30);
                        lift(r, t, mouth.subtract(Vec3.atCenterOf(t)).normalize().scale(0.8 + k), true);
                    }
                }
                if (r.age % 60 == 0) sound(r, mouth, RvSounds.WHALE_CALL.get(), 6f, 0.35f);
                if (r.age % 30 == 0) shake(r, 0.25f + 0.4f * k, 24, 0f, pr.colorA);
            }
            case REALITY_DISASSEMBLY -> {
                if (r.age % 3 == 0) {
                    int n = 1 + (int) (k * (heavy ? 5 : 2));
                    for (int i = 0; i < n; i++) {
                        BlockPos t = randomSurface(r, a, 12 + 28 * k);
                        cubeOutline(level, t.below(), rnd.nextBoolean() ? pr.colorA : pr.colorB);
                        lift(r, t, new Vec3((rnd.nextDouble() - 0.5) * 0.05, 0.04 + rnd.nextDouble() * 0.12, (rnd.nextDouble() - 0.5) * 0.05), true);
                    }
                }
                if (r.age % 7 == 0) {
                    level.sendParticles(RvParticles.GLITCH.get().with(pr.colorB, 0.7f, 20), a.x, a.y + 2, a.z, heavy ? 30 : 8, 12, 6, 12, 0.02);
                    sound(r, a, RvSounds.GLITCH_NOISE.get(), 1.5f, 0.5f + rnd.nextFloat());
                }
                if (r.age % 25 == 0) shake(r, 0.15f + 0.3f * k, 12, 0.05f, pr.colorA);
            }
            case BLACK_HOLE_INFUSION -> {
                Entity e = nearestOwned(r, a);
                Vec3 c = e != null ? e.position() : a.add(18, 12, 0);
                if (e instanceof BlackHoleEntity hole && r.age % 4 == 0) {
                    float size = k < 0.9f ? 1f + 13f * (k / 0.9f) * (k / 0.9f) : 14f * (1f - (k - 0.9f) / 0.1f) + 0.2f;
                    hole.setHorizonRadius(Math.max(0.2f, size));
                }
                double spin = r.age * 0.09;
                if (r.age % 2 == 0) {
                    for (int arm = 0; arm < (heavy ? 3 : 1); arm++) {
                        for (int i = 0; i < 10; i++) {
                            double rr = 6 + i * 3 + 10 * k;
                            double ang = spin * (1 + 2 * k) + arm * Math.PI * 2 / 3 - i * 0.32;
                            level.sendParticles(RvParticles.STREAK.get().with(i < 4 ? 0xFFF0C8 : pr.colorA, 1f, 12), c.x + Math.cos(ang) * rr,
                                    c.y + Math.sin(ang * 0.5) * 1.5, c.z + Math.sin(ang) * rr, 1, 0, 0, 0, 0);
                        }
                    }
                }
                if (k > 0.25f && r.age % 3 == 0) {
                    BlockPos t = randomSurface(r, a, 40);
                    Vec3 to = c.subtract(Vec3.atCenterOf(t));
                    Vec3 swirl = to.cross(new Vec3(0, 1, 0)).normalize().scale(0.4);
                    lift(r, t, to.normalize().scale(0.5 + k).add(swirl), true);
                }
                if (r.age % 50 == 0) sound(r, c, RvSounds.BLACK_HOLE_PULL.get(), 4f, 0.4f + 0.3f * k);
                if (r.age % 15 == 0) shake(r, 0.25f + 0.7f * k, 18, 0f, pr.colorA);
            }
            case TIMELINE_ERASURE -> {
                // eras rewind: ash, ice, verdant, dawn... then the world turns to glowing wireframe and fades
                int[] eras = {0xC27B4A, 0xA8E8FF, 0x5CFF9D, 0xFFC24D, 0x8F6BFF, 0xFF5A1F};
                if (k < 0.5f) {
                    int era = eras[(r.age / 20) % eras.length];
                    if (r.age % 2 == 0) level.sendParticles(RvParticles.DUST.get().with(era, 1.4f, 30), a.x, a.y + 3, a.z, heavy ? 24 : 6, 16, 6, 16, 0.08);
                    if (r.age % 20 == 0) {
                        level.sendParticles(RvParticles.RING.get().with(pr.colorB, 18f, 20), a.x, a.y + 1, a.z, 1, 0, 0, 0, 0);
                        sound(r, a, RvSounds.UI_SELECT.get(), 2f, 2.0f - k * 2.5f);
                        shake(r, 0.15f, 10, 0.25f, era);
                    }
                } else {
                    if (r.age % 3 == 0) {
                        for (int i = 0; i < (heavy ? 6 : 2); i++) cubeOutline(level, randomSurface(r, a, 24).below(), pr.colorA);
                    }
                    if (r.age % 5 == 0) level.sendParticles(RvParticles.SPARK.get().with(pr.colorA, 0.6f, 40), a.x, a.y + 1, a.z, heavy ? 30 : 8, 14, 1, 14, 0.12);
                    if (r.age % 30 == 0) sound(r, a, RvSounds.WORMHOLE_TRAVEL.get(), 1.5f, 0.5f);
                }
            }
        }
    }

    private static void lightning(Run r, BlockPos at) {
        if (r.preview) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(r.level);
        if (bolt == null) return;
        bolt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        bolt.setVisualOnly(true);
        r.level.addFreshEntity(bolt);
    }

    /** Glowing wireframe edges of one block. */
    private static void cubeOutline(ServerLevel level, BlockPos p, int color) {
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0;
            double[][] pts = {{x + t, y, z}, {x + t, y + 1, z}, {x + t, y, z + 1}, {x + t, y + 1, z + 1}, {x, y + t, z}, {x + 1, y + t, z}, {x, y + t, z + 1},
                    {x + 1, y + t, z + 1}, {x, y, z + t}, {x + 1, y, z + t}, {x, y + 1, z + t}, {x + 1, y + 1, z + t}};
            for (double[] q : pts) level.sendParticles(RvParticles.MOTE.get().with(color, 0.35f, 24), q[0], q[1], q[2], 1, 0, 0, 0, 0);
        }
    }

    private static void climax(Run r) {
        r.pastClimax = true;
        MinecraftServer server = r.level.getServer();
        EndProtocol pr = r.protocol;
        shake(r, 1.2f, 30, 1f, 0xFFFFFF);
        for (Vec3 a : anchors(r)) {
            r.level.sendParticles(RvParticles.RING.get().with(pr.colorB, 40f, 30), a.x, a.y + 2, a.z, 1, 0, 0, 0, 0);
            r.level.sendParticles(RvParticles.SPARK.get().with(pr.colorA, 1.4f, 50), a.x, a.y + 2, a.z, heavy() ? 300 : 80, 6, 6, 6, 1.4);
            sound(r, a, RvSounds.BLACK_HOLE_COLLAPSE.get(), 6f, pr == EndProtocol.TIMELINE_ERASURE ? 1.6f : 0.7f);
        }
        for (UUID id : r.entities) {
            Entity e = r.level.getEntity(id);
            if (e != null) e.discard();
        }
        r.entities.clear();
        for (Vec3 a : anchors(r)) {
            // shockwave rings racing outward, a pillar of light, and every creature in sight torn out of existence
            for (int i = 0; i < 4; i++) {
                final int k = i;
                Scheduler.later(i * 4, () -> r.level.sendParticles(RvParticles.RING.get().with(k % 2 == 0 ? pr.colorA : 0xFFFFFF, 30f + k * 30f, 30),
                        a.x, a.y + 1, a.z, 1, 0, 0, 0, 0));
            }
            for (int y = 0; y < 80; y += 2) r.level.sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 2f, 20), a.x, a.y + y, a.z, 2, 0.4, 0.5, 0.4, 0.02);
            if (!r.preview) {
                double reach = (r.level.getServer().getPlayerList().getViewDistance() + 1) * 16;
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(a, a).inflate(reach, 512, reach);
                for (Entity e : r.level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    if (heavy() && r.level.random.nextInt(3) == 0) {
                        r.level.sendParticles(RvParticles.GLITCH.get().with(pr.colorA, 0.6f, 14), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                    }
                    e.discard();
                }
            }
        }
        if (r.preview) return;
        if (r.wholeDimension) {
            RealityOps.endDimensionWave(server, r.level, 50);
            return;
        }
        if (r.universe == null) return;
        UniverseId id = r.universe;
        boolean reconstruct = r.reconstruct;
        RealityOps.eraseWave(server, id, 50, reconstruct ? () -> Scheduler.later(60, () -> RealityOps.restore(server, id, null)) : null);
    }

    private static void cleanup(Run r) {
        for (UUID id : r.entities) {
            Entity e = r.level.getEntity(id);
            if (e != null) e.discard();
        }
        r.entities.clear();
        for (BlockPos p : r.placed) {
            BlockState s = r.level.getBlockState(p);
            if (s.is(RvBlocks.NEXUS_BRICKS.get()) || s.is(RvBlocks.NEXUS_GLOW.get()) || s.is(RvBlocks.VOID_STONE.get())) r.level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        r.placed.clear();
        if (r.preview) {
            for (ServerPlayer p : viewers(r)) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Preview of " + r.protocol.title + " complete."), true);
        }
    }

    public static void clear() {
        RUNS.clear();
    }
}
