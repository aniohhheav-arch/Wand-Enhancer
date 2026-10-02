package dev.riftverse.multiverse.event;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.block.RiftType;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.Destination;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** The ten multiverse events. Each is a small state machine driven by {@link EventManager}. */
public final class EventHandlers {
    private EventHandlers() {}

    static Map<EventType, MultiverseEvent> create() {
        Map<EventType, MultiverseEvent> m = new EnumMap<>(EventType.class);
        m.put(EventType.REALITY_COLLAPSE, new RealityCollapse());
        m.put(EventType.DIMENSIONAL_INVASION, new Invasion());
        m.put(EventType.COSMIC_LEVIATHAN, new Leviathan());
        m.put(EventType.ANCIENT_GUARDIAN, new Guardian());
        m.put(EventType.DIMENSIONAL_ANOMALY, new Anomaly());
        m.put(EventType.VOID_WANDERER, new VoidWanderer());
        m.put(EventType.UNIVERSE_BIRTH, new UniverseBirth());
        m.put(EventType.BLACK_HOLE, new Singularity());
        m.put(EventType.RIFT_STORM, new RiftStorm());
        m.put(EventType.COSMIC_CONVERGENCE, new Convergence());
        return m;
    }

    private static boolean heavy() {
        return RiftverseConfig.get(RiftverseConfig.HEAVY_EFFECTS, true);
    }

    private static void reward(ActiveEvent e, double radius, int research, String why) {
        for (ServerPlayer p : EventKit.playersNear(e, radius)) RealityOps.research(p, research, why);
    }

    private static void bossIntro(ActiveEvent e, Entity boss, String name, String sub, int color) {
        for (ServerPlayer p : EventKit.playersNear(e, 128)) {
            PacketDistributor.sendToPlayer(p, new Payloads.BossIntro(boss.getId(), name, sub, color));
            PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.8f, 40, 0.5f, color));
        }
    }

    // ------------------------------------------------------------------ 1. reality collapse

    static final class RealityCollapse implements MultiverseEvent {
        @Override
        public String start(ActiveEvent e) {
            e.level.playSound(null, e.center.x, e.center.y, e.center.z, RvSounds.GLITCH_NOISE.get(), SoundSource.AMBIENT, 4f, 0.5f);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            ServerLevel level = e.level;
            RandomSource r = level.random;
            float k = e.age / (float) e.duration;
            double radius = 6 + 34 * k;
            boolean alter = RiftverseConfig.get(RiftverseConfig.EVENTS_ALTER_TERRAIN, true) && level.dimension() == RvWorldgen.EXPANSE;
            if (e.age % 3 == 0) {
                int n = heavy() ? 4 : 2;
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(r.nextDouble()) * radius;
                    int x = (int) Math.floor(e.center.x + Math.cos(a) * d);
                    int z = (int) Math.floor(e.center.z + Math.sin(a) * d);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos p = new BlockPos(x, y, z);
                    level.sendParticles(RvParticles.GLITCH.get().with(EventType.REALITY_COLLAPSE.color, 0.6f, 16), x + 0.5, y + 1.2, z + 0.5, 6, 0.4, 0.6, 0.4, 0.05);
                    if (!alter || y <= level.getMinBuildHeight() + 2) continue;
                    for (int dy = 0; dy < 1 + r.nextInt(3); dy++) {
                        BlockPos q = p.below(dy);
                        BlockState s = level.getBlockState(q);
                        if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(level, q) < 0 || s.is(Blocks.BEDROCK)) break;
                        FallingBlockEntity fb = FallingBlockEntity.fall(level, q, s);
                        fb.setDeltaMovement((r.nextDouble() - 0.5) * 0.3, -0.2 - r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.3);
                        fb.dropItem = false;
                    }
                }
            }
            if (e.age % 40 == 0) {
                for (ServerPlayer p : EventKit.playersNear(e, radius + 16)) {
                    PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.4f + 0.5f * k, 30, 0.1f, EventType.REALITY_COLLAPSE.color));
                }
                level.sendParticles(RvParticles.RING.get().with(EventType.REALITY_COLLAPSE.color, (float) radius, 30), e.center.x, e.center.y, e.center.z, 1, 0, 0, 0, 0);
            }
            if (e.age % 80 == 40) {
                for (ServerPlayer p : EventKit.playersNear(e, radius)) {
                    if (!p.isCreative()) p.hurt(level.damageSources().magic(), 2f);
                }
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            for (ServerPlayer p : EventKit.playersNear(e, 64)) p.displayClientMessage(Component.literal("Reality settles... for now.").withColor(0xFF8A9A), true);
        }
    }

    // ------------------------------------------------------------------ 2. dimensional invasion

    static final class Invasion implements MultiverseEvent {
        private static final RiftType[] RIFTS = {RiftType.CRIMSON, RiftType.VOID, RiftType.GLITCH};

        @Override
        public String start(ActiveEvent e) {
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3 + e.level.random.nextDouble() * 0.6;
                BlockPos p = EventKit.ringPos(e, a, 14, 2);
                for (int up = 0; up < 6 && !EventKit.openRift(e, p.above(up), RIFTS[i], e.duration + 100, null); up++) {}
            }
            if (e.rifts.isEmpty()) return "no room to open hostile rifts here";
            e.focus = Vec3.atCenterOf(e.rifts.get(0));
            e.data.putInt("limit", 6 + 3 * Math.max(1, EventKit.playersNear(e, 64).size()));
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            EventKit.pruneRifts(e);
            int spawned = e.data.getInt("spawned");
            int limit = e.data.getInt("limit");
            int alive = EventKit.alive(e);
            if (e.age % 50 == 25 && spawned < limit && alive < 8 && !e.rifts.isEmpty()) {
                RandomSource r = e.level.random;
                BlockPos rift = e.rifts.get(r.nextInt(e.rifts.size()));
                int count = 1 + r.nextInt(2);
                for (int i = 0; i < count && spawned < limit; i++) {
                    EntityType<? extends Mob> type = switch (r.nextInt(3)) {
                        case 0 -> RvEntities.VOID_STALKER.get();
                        case 1 -> RvEntities.GLITCHLING.get();
                        default -> RvEntities.RIFT_WRAITH.get();
                    };
                    Mob mob = EventKit.spawn(e, type, Vec3.atBottomCenterOf(rift).add(r.nextDouble() - 0.5, -1, r.nextDouble() - 0.5));
                    if (mob != null) {
                        ServerPlayer target = EventKit.nearestPlayer(e, mob.position(), 48);
                        if (target != null) mob.setTarget(target);
                        spawned++;
                    }
                }
                e.data.putInt("spawned", spawned);
                e.level.sendParticles(RvParticles.SPARK.get().with(0xFF2440, 0.8f, 20), rift.getX() + 0.5, rift.getY() + 0.5, rift.getZ() + 0.5, 40, 0.5, 0.8, 0.5, 0.3);
                e.level.playSound(null, rift, RvSounds.RIFT_OPEN.get(), SoundSource.HOSTILE, 2f, 0.6f);
            }
            if (spawned >= limit && alive == 0) {
                Vec3 c = e.center.add(0, 1, 0);
                EventKit.drop(e, c, RvItems.RIFT_SHARD.get(), 6);
                EventKit.drop(e, c, RvItems.VOID_ESSENCE.get(), 3);
                EventKit.drop(e, c, RvItems.EXOTIC_INGOT.get(), 1);
                reward(e, 48, 30, "invasion repelled");
                for (ServerPlayer p : EventKit.playersNear(e, 64)) {
                    RealityOps.cinematic(p, dev.riftverse.multiverse.CinematicType.ANNOUNCE, 0, p.getEyePosition(), 0x7DF9FF, 0xFFFFFF, "INVASION REPELLED", "The rifts seal behind the last invader");
                }
                e.data.putBoolean("won", true);
                e.finished = true;
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            EventKit.closeRifts(e);
            if (forced || !e.data.getBoolean("won")) EventKit.discardAll(e);
        }
    }

    // ------------------------------------------------------------------ 3. cosmic leviathan

    static final class Leviathan implements MultiverseEvent {
        @Override
        public String start(ActiveEvent e) {
            Mob boss = EventKit.spawn(e, RvEntities.ABYSSAL_LEVIATHAN.get(), e.center.add(0, 18, 0));
            if (boss == null) return "the leviathan could not manifest";
            e.focus = boss.position();
            e.level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), RvSounds.LEVIATHAN_ROAR.get(), SoundSource.HOSTILE, 6f, 0.7f);
            e.level.sendParticles(RvParticles.RING.get().with(0x40FFE0, 20f, 30), boss.getX(), boss.getY(), boss.getZ(), 1, 0, 0, 0, 0);
            bossIntro(e, boss, "THE COSMIC LEVIATHAN", "It swam here from between the stars", 0x40FFE0);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            if (EventKit.alive(e) == 0) {
                reward(e, 96, 60, "leviathan event survived");
                e.finished = true;
                return;
            }
            Entity boss = EventKit.entity(e, e.entities.get(0));
            if (boss != null) e.center = boss.position();
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            if (!e.entities.isEmpty()) {
                for (ServerPlayer p : EventKit.playersNear(e, 128)) p.displayClientMessage(Component.literal("The Leviathan dives back into the dark between worlds.").withColor(0x40FFE0), true);
            }
            EventKit.discardAll(e);
        }
    }

    // ------------------------------------------------------------------ 4. ancient guardian

    static final class Guardian implements MultiverseEvent {
        @Override
        public String start(ActiveEvent e) {
            BlockPos g = EventKit.ground(e.level, (int) e.center.x, (int) e.center.z, (int) e.center.y);
            Mob warden = EventKit.spawn(e, RvEntities.RIFT_WARDEN.get(), Vec3.atBottomCenterOf(g).add(0, 8, 0));
            if (warden == null) return "the guardian could not manifest";
            e.focus = warden.position();
            Vec3 c = warden.position();
            e.level.sendParticles(RvParticles.RING.get().with(0xFFC14D, 14f, 30), c.x, c.y, c.z, 1, 0, 0, 0, 0);
            e.level.sendParticles(RvParticles.SPARK.get().with(0x8F6BFF, 0.8f, 40), c.x, c.y, c.z, heavy() ? 150 : 40, 2, 3, 2, 0.6);
            e.level.playSound(null, c.x, c.y, c.z, RvSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 5f, 0.8f);
            bossIntro(e, warden, "THE ANCIENT GUARDIAN", "Custodian of the Boundaries", 0xFFC14D);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            if (EventKit.alive(e) == 0) {
                reward(e, 96, 80, "guardian defeated");
                e.finished = true;
                return;
            }
            Entity boss = EventKit.entity(e, e.entities.get(0));
            if (boss != null) e.center = boss.position();
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            if (forced || e.age >= e.duration) EventKit.discardAll(e);
        }
    }

    // ------------------------------------------------------------------ 5. dimensional anomaly

    static final class Anomaly implements MultiverseEvent {
        private static final double RADIUS = 22;

        @Override
        public String start(ActiveEvent e) {
            e.level.playSound(null, e.center.x, e.center.y, e.center.z, RvSounds.GRAVITY_PULSE.get(), SoundSource.AMBIENT, 3f, 0.5f);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            ServerLevel level = e.level;
            int phase = (e.age / 120) % 3;
            if (e.age % 120 == 0) {
                String msg = switch (phase) {
                    case 0 -> "Gravity loosens its grip...";
                    case 1 -> "Gravity inverts!";
                    default -> "Space folds — matter begins to blink.";
                };
                for (ServerPlayer p : EventKit.playersNear(e, RADIUS + 8)) {
                    p.displayClientMessage(Component.literal(msg).withColor(0x7AF0D0), true);
                    PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.3f, 20, 0.2f, 0x7AF0D0));
                }
            }
            if (e.age % 10 == 0) {
                AABB box = new AABB(e.center, e.center).inflate(RADIUS, RADIUS, RADIUS);
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, le -> le.position().distanceToSqr(e.center) < RADIUS * RADIUS)) {
                    if (le instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                    switch (phase) {
                        case 0 -> le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false));
                        case 1 -> le.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15, 1, true, false));
                        default -> {
                            le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false));
                            if (level.random.nextInt(6) == 0) {
                                Vec3 from = le.position();
                                if (le.randomTeleport(from.x + (level.random.nextDouble() - 0.5) * 12, from.y + level.random.nextInt(4), from.z + (level.random.nextDouble() - 0.5) * 12, true)) {
                                    level.sendParticles(RvParticles.GLITCH.get().with(0x7AF0D0, 0.5f, 14), from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.05);
                                }
                            }
                        }
                    }
                }
            }
            if (e.age % 4 == 0) {
                double a = e.age * 0.21;
                for (int i = 0; i < (heavy() ? 6 : 2); i++) {
                    double ang = a + i * Math.PI / 3;
                    level.sendParticles(RvParticles.MOTE.get().with(0x7AF0D0, 0.9f, 40), e.center.x + Math.cos(ang) * RADIUS, e.center.y + 1 + (e.age % 40) * 0.1,
                            e.center.z + Math.sin(ang) * RADIUS, 1, 0, 0.2, 0, 0.02);
                }
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            e.level.sendParticles(RvParticles.RING.get().with(0x7AF0D0, (float) RADIUS, 20), e.center.x, e.center.y + 1, e.center.z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ 6. void wanderer

    static final class VoidWanderer implements MultiverseEvent {
        @Override
        public String start(ActiveEvent e) {
            double a = e.level.random.nextDouble() * Math.PI * 2;
            BlockPos g = EventKit.ringPos(e, a, 20, 0);
            Mob m = EventKit.spawn(e, RvEntities.VOID_STALKER.get(), Vec3.atBottomCenterOf(g));
            if (m == null) return "the wanderer could not manifest";
            AttributeInstance scale = m.getAttribute(Attributes.SCALE);
            if (scale != null) scale.setBaseValue(3.0);
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(240);
            AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 2);
            m.setHealth(m.getMaxHealth());
            m.setCustomName(Component.literal("The Void Wanderer").withColor(0x9B30FF));
            m.setGlowingTag(true);
            e.focus = m.position().add(0, 3, 0);
            e.level.playSound(null, m.getX(), m.getY(), m.getZ(), RvSounds.STALKER_HISS.get(), SoundSource.HOSTILE, 5f, 0.4f);
            bossIntro(e, m, "THE VOID WANDERER", "It walks between worlds, and it is hungry", 0x9B30FF);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            if (e.entities.isEmpty()) {
                e.finished = true;
                return;
            }
            Entity m = EventKit.entity(e, e.entities.get(0));
            if (m == null || !m.isAlive()) {
                if (m != null || e.data.contains("lx")) {
                    Vec3 at = m != null ? m.position() : new Vec3(e.data.getDouble("lx"), e.data.getDouble("ly"), e.data.getDouble("lz"));
                    EventKit.drop(e, at, RvItems.VOID_ESSENCE.get(), 8);
                    EventKit.drop(e, at, RvItems.SINGULARITY_FRAGMENT.get(), 1);
                    reward(e, 64, 50, "void wanderer banished");
                }
                e.entities.clear();
                e.finished = true;
                return;
            }
            e.center = m.position();
            e.data.putDouble("lx", m.getX());
            e.data.putDouble("ly", m.getY());
            e.data.putDouble("lz", m.getZ());
            if (e.age % 40 == 0) {
                for (ServerPlayer p : EventKit.playersNear(e, 28)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
            }
            if (e.age % 3 == 0) {
                e.level.sendParticles(RvParticles.DUST.get().with(0x1A0030, 1.5f, 40), m.getX(), m.getY() + 1.5, m.getZ(), heavy() ? 6 : 2, 1.2, 1.5, 1.2, 0.01);
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            for (java.util.UUID id : e.entities) {
                Entity m = e.level.getEntity(id);
                if (m != null) e.level.sendParticles(RvParticles.INFALL.get().with(0x9B30FF, 1f, 30), m.getX(), m.getY() + 2, m.getZ(), 80, 2, 2, 2, 0.1);
            }
            EventKit.discardAll(e);
        }
    }

    // ------------------------------------------------------------------ 7. universe birth

    static final class UniverseBirth implements MultiverseEvent {
        private static final int BIRTH = 300;

        @Override
        public String start(ActiveEvent e) {
            e.focus = e.center.add(0, 24, 0);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            ServerLevel level = e.level;
            Vec3 c = e.focus;
            if (e.age < BIRTH) {
                float k = e.age / (float) BIRTH;
                if (e.age % 2 == 0) {
                    level.sendParticles(RvParticles.INFALL.get().with(0xFFF0C8, 0.6f + k, 30), c.x, c.y, c.z, heavy() ? (int) (6 + 20 * k) : 4, 6 * (1 - k) + 1, 6 * (1 - k) + 1, 6 * (1 - k) + 1, 0.02);
                }
                if (e.age % 40 == 0) {
                    level.sendParticles(RvParticles.RING.get().with(0xFF7AF0, 4f + 14f * k, 24), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    level.playSound(null, c.x, c.y, c.z, RvSounds.JELLY_CHIME.get(), SoundSource.AMBIENT, 3f, 0.5f + k);
                }
                return;
            }
            if (e.age == BIRTH) {
                UniverseSpec born = UniverseRegistry.get(level.getServer()).randomUniverse(new Random(), null);
                e.data.putLong("born", born.id.pack());
                level.sendParticles(RvParticles.RING.get().with(born.accent, 30f, 40), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                level.sendParticles(RvParticles.SPARK.get().with(born.accent, 1.2f, 50), c.x, c.y, c.z, heavy() ? 300 : 80, 3, 3, 3, 1.2);
                level.playSound(null, c.x, c.y, c.z, RvSounds.BLACK_HOLE_COLLAPSE.get(), SoundSource.AMBIENT, 5f, 1.6f);
                BlockPos g = EventKit.ground(level, (int) e.center.x, (int) e.center.z, (int) e.center.y).above(1);
                for (int up = 0; up < 6 && !EventKit.openRift(e, g.above(up), RiftType.PRISMATIC, 12000, Destination.universe(born.id)); up++) {}
                e.rifts.clear(); // the birth rift outlives the event
                for (ServerPlayer p : EventKit.playersNear(e, 128)) {
                    PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.7f, 30, 1f, born.accent));
                    RealityOps.cinematic(p, dev.riftverse.multiverse.CinematicType.ANNOUNCE, 0, c, born.accent, 0xFFFFFF, born.name.toUpperCase(),
                            "A newborn reality [" + born.id.designation() + "] — a stable rift now leads there");
                }
                reward(e, 128, 30, "witnessed a universe being born");
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            EventKit.closeRifts(e);
        }
    }

    // ------------------------------------------------------------------ 8. black hole

    static final class Singularity implements MultiverseEvent {
        @Override
        public boolean canOccurNaturally(ServerLevel level, BlockPos pos) {
            return level.dimension() == RvWorldgen.EXPANSE;
        }

        @Override
        public String start(ActiveEvent e) {
            double a = e.level.random.nextDouble() * Math.PI * 2;
            Vec3 at = e.center.add(Math.cos(a) * 30, 14, Math.sin(a) * 30);
            BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), e.level);
            hole.moveTo(at.x, at.y, at.z, 0, 0);
            hole.setHorizonRadius(1.2f);
            hole.setNatural(true);
            e.level.addFreshEntity(hole);
            e.entities.add(hole.getUUID());
            e.focus = at;
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            if (e.entities.isEmpty()) {
                e.finished = true;
                return;
            }
            Entity ent = EventKit.entity(e, e.entities.get(0));
            if (!(ent instanceof BlackHoleEntity hole) || !hole.isAlive()) {
                e.finished = true;
                return;
            }
            if (e.age % 20 == 0) hole.setHorizonRadius(1.2f + 3.6f * (e.age / (float) e.duration));
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            for (java.util.UUID id : e.entities) {
                Entity ent = e.level.getEntity(id);
                if (ent != null) {
                    e.level.sendParticles(RvParticles.RING.get().with(0xFF8A3A, 18f, 25), ent.getX(), ent.getY(), ent.getZ(), 1, 0, 0, 0, 0);
                    e.level.playSound(null, ent.getX(), ent.getY(), ent.getZ(), RvSounds.BLACK_HOLE_COLLAPSE.get(), SoundSource.AMBIENT, 5f, 1f);
                    e.level.sendParticles(RvParticles.SPARK.get().with(0xFFF0C8, 1f, 40), ent.getX(), ent.getY(), ent.getZ(), 120, 1, 1, 1, 0.8);
                    ent.discard();
                }
            }
            e.entities.clear();
            if (!forced) reward(e, 96, 25, "survived a singularity");
        }
    }

    // ------------------------------------------------------------------ 9. rift storm

    static final class RiftStorm implements MultiverseEvent {
        @Override
        public String start(ActiveEvent e) {
            e.focus = e.center.add(0, 10, 0);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            ServerLevel level = e.level;
            RandomSource r = level.random;
            if (e.age % 15 != 0) return;
            EventKit.pruneRifts(e);
            if (e.rifts.size() >= 14) return;
            var near = EventKit.playersNear(e, 64);
            Vec3 around = near.isEmpty() ? e.center : near.get(r.nextInt(near.size())).position();
            int x = (int) Math.floor(around.x + (r.nextDouble() - 0.5) * 40);
            int z = (int) Math.floor(around.z + (r.nextDouble() - 0.5) * 40);
            BlockPos g = EventKit.ground(level, x, z, (int) around.y).above(1 + r.nextInt(4));
            RiftType[] all = RiftType.values();
            RiftType type = all[r.nextInt(all.length)];
            if (type == RiftType.RETURN) type = RiftType.STELLAR;
            if (EventKit.openRift(e, g, type, 300, null)) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                level.sendParticles(RvParticles.RING.get().with(type.colorA, 4f, 16), g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 1, 0, 0, 0, 0);
                level.playSound(null, g, RvSounds.RIFT_OPEN.get(), SoundSource.AMBIENT, 2f, 0.8f + r.nextFloat() * 0.4f);
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            EventKit.closeRifts(e);
            if (!forced) reward(e, 64, 20, "weathered a rift storm");
        }
    }

    // ------------------------------------------------------------------ 10. cosmic convergence

    static final class Convergence implements MultiverseEvent {
        @Override
        public String start(ActiveEvent e) {
            RiftType[] all = RiftType.values();
            for (int i = 0; i < 8; i++) {
                RiftType t = all[(i * 2 + e.level.random.nextInt(2)) % all.length];
                if (t == RiftType.RETURN) t = RiftType.PRISMATIC;
                BlockPos p = EventKit.ringPos(e, i * Math.PI / 4, 10, 2);
                for (int up = 0; up < 5 && !EventKit.openRift(e, p.above(up), t, e.duration + 200, null); up++) {}
            }
            e.focus = e.center.add(0, 6, 0);
            return null;
        }

        @Override
        public void tick(ActiveEvent e) {
            ServerLevel level = e.level;
            RandomSource r = level.random;
            if (e.age % 6 == 0) {
                RiftType t = RiftType.values()[(e.age / 6) % RiftType.values().length];
                double a = e.age * 0.05;
                level.sendParticles(RvParticles.STREAK.get().with(t.colorA, 1.2f, 40), e.center.x + Math.cos(a) * 10, e.center.y + 8, e.center.z + Math.sin(a) * 10,
                        heavy() ? 4 : 1, 0.5, 2, 0.5, 0.05);
            }
            if (e.age % 40 == 20 && EventKit.alive(e) < 10) {
                var near = EventKit.playersNear(e, 64);
                Vec3 around = near.isEmpty() ? e.center : near.get(r.nextInt(near.size())).position();
                EntityType<? extends Mob> type = switch (r.nextInt(3)) {
                    case 0 -> RvEntities.ASTRAL_JELLY.get();
                    case 1 -> RvEntities.SKY_WHALE.get();
                    default -> RvEntities.LUMEN_STRIDER.get();
                };
                double a = r.nextDouble() * Math.PI * 2;
                int x = (int) (around.x + Math.cos(a) * 18);
                int z = (int) (around.z + Math.sin(a) * 18);
                BlockPos g = EventKit.ground(level, x, z, (int) around.y);
                boolean flyer = type != RvEntities.LUMEN_STRIDER.get();
                Mob mob = EventKit.spawn(e, type, Vec3.atBottomCenterOf(g).add(0, flyer ? 10 + r.nextInt(10) : 0, 0));
                if (mob != null) level.sendParticles(RvParticles.RING.get().with(0xFF7AF0, 3f, 16), mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
            }
        }

        @Override
        public void end(ActiveEvent e, boolean forced) {
            EventKit.closeRifts(e);
            for (java.util.UUID id : e.entities) {
                Entity m = e.level.getEntity(id);
                if (m != null) e.level.sendParticles(RvParticles.SPARK.get().with(0xFF7AF0, 0.6f, 20), m.getX(), m.getY() + 1, m.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            }
            EventKit.discardAll(e);
            if (!forced) reward(e, 64, 50, "witnessed a cosmic convergence");
        }
    }
}
