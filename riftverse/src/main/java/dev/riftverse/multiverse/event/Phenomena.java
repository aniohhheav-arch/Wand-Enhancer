package dev.riftverse.multiverse.event;

import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The phenomena engine: 48 data-light events built from shared primitives (area effects, particles, spawns, strikes,
 * temporary terrain). Every terrain change is remembered and put back when the event ends, so phenomena never scar a
 * world permanently. Radius, spawn counts and strength scale with the director's intensity.
 */
final class Phenomena implements MultiverseEvent {
    /** Temporary blocks per event id: position -> original state. */
    private static final Map<Integer, Map<BlockPos, BlockState>> TEMP = new HashMap<>();

    @Override
    public String start(ActiveEvent e) {
        TEMP.put(e.id, new HashMap<>());
        float k = EventDirector.scale();
        ServerLevel l = e.level;
        switch (e.type) {
            case ECLIPSE -> l.setDayTime(l.getDayTime() - l.getDayTime() % 24000 + 18000);
            case SKY_WHALE_MIGRATION -> {
                for (int i = 0; i < 3 * k; i++) EventKit.spawn(e, RvEntities.SKY_WHALE.get(), e.center.add(-40 + i * 8, 30 + i * 3, i * 6));
            }
            case HIVE_AWAKENING -> {
                for (int i = 0; i < 6 * k; i++) {
                    var bee = EventKit.spawn(e, EntityType.BEE, e.center.add(rnd(l, 4), 3, rnd(l, 4)));
                    if (bee != null) bee.setRemainingPersistentAngerTime(1200);
                }
            }
            case STAMPEDE -> {
                for (int i = 0; i < 10 * k; i++) {
                    EntityType<? extends Mob> t = i % 3 == 0 ? EntityType.HORSE : i % 3 == 1 ? EntityType.COW : EntityType.CAMEL;
                    var m = EventKit.spawn(e, t, e.center.add(-24, 0, rnd(l, 8)));
                    if (m != null) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 900, 3));
                }
            }
            case THE_WATCHER -> e.focus = e.center.add(0, 60, 0);
            default -> {}
        }
        return null;
    }

    private static double rnd(ServerLevel l, double r) {
        return (l.random.nextDouble() - 0.5) * 2 * r;
    }

    private static Vec3 around(ActiveEvent e, double r) {
        BlockPos g = EventKit.ground(e.level, (int) (e.center.x + rnd(e.level, r)), (int) (e.center.z + rnd(e.level, r)), (int) e.center.y);
        return Vec3.atBottomCenterOf(g);
    }

    private static void particles(ActiveEvent e, ParticleOptions p, int n, double r, double h, double y, double speed) {
        e.level.sendParticles(p, e.center.x, e.center.y + y, e.center.z, n, r, h, r, speed);
    }

    private static void effect(List<LivingEntity> who, Holder<MobEffect> eff, int ticks, int amp) {
        for (LivingEntity le : who) le.addEffect(new MobEffectInstance(eff, ticks, amp, true, true));
    }

    private static void temp(ActiveEvent e, BlockPos p, BlockState s) {
        Map<BlockPos, BlockState> m = TEMP.get(e.id);
        if (m == null || !e.level.isInWorldBounds(p)) return;
        BlockState old = e.level.getBlockState(p);
        if (old.hasBlockEntity() || old.getDestroySpeed(e.level, p) < 0) return;
        m.putIfAbsent(p.immutable(), old);
        e.level.setBlock(p, s, 2);
    }

    private static void strike(ActiveEvent e, Vec3 at, boolean visualOnly) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(e.level);
        if (b == null) return;
        b.moveTo(at);
        b.setVisualOnly(visualOnly);
        e.level.addFreshEntity(b);
    }

    private static List<LivingEntity> living(ActiveEvent e, double r) {
        return e.level.getEntitiesOfClass(LivingEntity.class, new AABB(e.center, e.center).inflate(r, r * 0.6, r), le -> le.isAlive() && !le.isSpectator());
    }

    @Override
    public void tick(ActiveEvent e) {
        ServerLevel l = e.level;
        RandomSource r = l.random;
        float k = EventDirector.scale();
        double R = 36 * Math.sqrt(k);
        int t = e.age;
        boolean pulse = t % 20 == 0;
        List<LivingEntity> all = pulse ? living(e, R) : List.of();
        List<ServerPlayer> players = pulse ? EventKit.playersNear(e, R) : List.of();
        switch (e.type) {
            // ------------------------------------------------------------ I cosmic
            case METEOR_SHOWER -> {
                if (t % Math.max(4, (int) (16 / k)) == 0) {
                    Vec3 hit = around(e, R);
                    Vec3 from = hit.add(30, 70, 10);
                    for (int i = 0; i <= 20; i++) {
                        Vec3 p = from.lerp(hit, i / 20.0);
                        l.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.02);
                        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.01);
                    }
                    l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
                    l.playSound(null, BlockPos.containing(hit), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 2f, 0.7f);
                    BlockPos g = BlockPos.containing(hit).below();
                    temp(e, g, Blocks.MAGMA_BLOCK.defaultBlockState());
                    for (LivingEntity le : l.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(3))) le.hurt(l.damageSources().onFire(), 5f * k);
                }
            }
            case SOLAR_FLARE -> {
                if (pulse) for (LivingEntity le : all) if (l.canSeeSky(le.blockPosition())) { le.igniteForSeconds(2); le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0)); }
                if (t % 5 == 0) particles(e, RvParticles.STREAK.get().with(0xFFE060, 1.5f, 20), 30, R, 4, 40, 0.3);
            }
            case ECLIPSE -> {
                if (pulse) effect(all, MobEffects.DARKNESS, 60, 0);
                if (t % 100 == 0) for (int i = 0; i < 2 * k; i++) { if (r.nextBoolean()) EventKit.spawn(e, EntityType.ZOMBIE, around(e, R).add(0, 1, 0)); else EventKit.spawn(e, EntityType.PHANTOM, around(e, R).add(0, 8, 0)); }
            }
            case AURORA_STORM -> {
                if (t % 4 == 0) for (int i = 0; i < 4; i++) particles(e, RvParticles.STREAK.get().with(i % 2 == 0 ? 0x40FFB0 : 0xB060FF, 2f, 40), 10, R, 1, 45 + i * 3, 0.02);
                if (pulse) { effect(players.stream().map(p -> (LivingEntity) p).toList(), MobEffects.REGENERATION, 60, 0); effect(players.stream().map(p -> (LivingEntity) p).toList(), MobEffects.MOVEMENT_SPEED, 60, 0); }
            }
            case COMET_PASSAGE -> {
                double f = (t % 200) / 200.0;
                Vec3 c = e.center.add(-80 + 160 * f, 60, -30 + 60 * f);
                l.sendParticles(RvParticles.RING.get().with(0x80E0FF, 4f, 6), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                l.sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 1.5f, 40), c.x, c.y, c.z, 12, 1, 1, 1, 0.05);
                if (t % 60 == 0) {
                    Vec3 g = around(e, R);
                    l.addFreshEntity(new ItemEntity(l, g.x, g.y + 1, g.z, new net.minecraft.world.item.ItemStack(dev.riftverse.registry.RvItems.STELLAR_DUST.get())));
                    l.sendParticles(RvParticles.RING.get().with(0x80E0FF, 2f, 14), g.x, g.y + 0.5, g.z, 1, 0, 0, 0, 0);
                }
            }
            case STARFALL -> {
                if (t % 30 == 0) {
                    Vec3 g = around(e, R);
                    for (int y = 0; y < 50; y += 2) l.sendParticles(RvParticles.STREAK.get().with(0xFFF0C8, 1.6f, 10), g.x, g.y + y, g.z, 1, 0.1, 0.4, 0.1, 0);
                    l.addFreshEntity(new ItemEntity(l, g.x, g.y + 1, g.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST, 1 + r.nextInt(3))));
                    for (ServerPlayer p : EventKit.playersNear(e, R)) if (p.distanceToSqr(g) < 64) dev.riftverse.multiverse.RealityOps.research(p, 5, "caught a fallen star");
                }
            }
            case GRAVITY_WELL -> {
                if (t % 5 == 0) for (LivingEntity le : living(e, R)) { le.setDeltaMovement(le.getDeltaMovement().add(0, -0.25, 0)); le.hurtMarked = true; }
                if (pulse) effect(all, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
                if (t % 10 == 0) particles(e, RvParticles.INFALL.get().with(0x6040C0, 1f, 20), 40, R, 6, 6, 0.1);
            }
            case NEBULA_DRIFT -> {
                if (t % 3 == 0) particles(e, RvParticles.MOTE.get().with(r.nextBoolean() ? 0xC050FF : 0x40A0FF, 3f, 80), 25, R, 6, 4, 0.01);
                if (pulse) effect(all, MobEffects.BLINDNESS, 40, 0);
            }
            // ------------------------------------------------------------ II dimensional
            case PHASE_SHIFT -> {
                if (pulse) for (LivingEntity le : all) if (!(le instanceof ServerPlayer)) le.setInvisible(r.nextBoolean());
                if (t % 6 == 0) particles(e, RvParticles.GLITCH.get().with(0x7AF0D0, 0.8f, 14), 20, R, 3, 2, 0.05);
            }
            case MIRROR_INVERSION -> {
                if (t % 100 == 50) {
                    List<LivingEntity> mobs = living(e, R).stream().filter(le -> !(le instanceof ServerPlayer)).toList();
                    for (int i = 0; i + 1 < mobs.size(); i += 2) {
                        Vec3 a = mobs.get(i).position(), b = mobs.get(i + 1).position();
                        mobs.get(i).teleportTo(b.x, b.y, b.z);
                        mobs.get(i + 1).teleportTo(a.x, a.y, a.z);
                    }
                    for (ServerPlayer p : EventKit.playersNear(e, R)) p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180f, p.getXRot());
                }
            }
            case ECHO_REALITY -> {
                if (t % 80 == 0) for (int i = 0; i < k; i++) {
                    var v = EventKit.spawn(e, EntityType.VEX, around(e, R).add(0, 2, 0));
                    if (v != null) { v.setCustomName(Component.literal("Echo").withColor(0xA0A0FF)); v.setLimitedLife(400); }
                }
            }
            case DIMENSIONAL_BLEED -> {
                if (t % 4 == 0) {
                    BlockState[] alien = {Blocks.CRIMSON_NYLIUM.defaultBlockState(), Blocks.WARPED_NYLIUM.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                            Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.SCULK.defaultBlockState()};
                    temp(e, BlockPos.containing(around(e, R)).below(), alien[(t / 200) % alien.length]);
                }
            }
            case PORTAL_SURGE -> {
                if (t % 60 == 0) {
                    BlockPos p = BlockPos.containing(around(e, R)).above(1);
                    EventKit.openRift(e, p, dev.riftverse.block.RiftType.values()[r.nextInt(dev.riftverse.block.RiftType.values().length)], 120, null);
                }
                if (t % 20 == 0) EventKit.pruneRifts(e);
            }
            case POCKET_COLLAPSE -> {
                double pull = 0.04 + 0.1 * (t / (double) e.duration);
                if (t % 2 == 0) for (Entity en : l.getEntities((Entity) null, new AABB(e.center, e.center).inflate(R), en -> !(en instanceof ServerPlayer sp && sp.isCreative()))) {
                    Vec3 d = e.center.subtract(en.position());
                    if (d.length() > 1) { en.setDeltaMovement(en.getDeltaMovement().add(d.normalize().scale(pull))); en.hurtMarked = true; }
                }
                if (t % 4 == 0) particles(e, RvParticles.INFALL.get().with(0x8A3AFF, 1.2f, 20), 50, R, R / 2, 2, 0.3);
            }
            case VOID_TIDE -> {
                if (pulse) for (LivingEntity le : all) if (le.getY() < e.center.y + (t / (double) e.duration) * 8) { le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1)); le.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0)); }
                if (t % 3 == 0) particles(e, RvParticles.MOTE.get().with(0x1A0030, 2f, 40), 40, R, 1, (t / (double) e.duration) * 8, 0.02);
            }
            case BOUNDARY_FRACTURE -> {
                if (t % 10 == 0) particles(e, RvParticles.GLITCH.get().with(0xFF3A5A, 1.4f, 16), 10, R, 2, 1, 0);
                if (t % 40 == 0) for (LivingEntity le : living(e, R)) if (r.nextInt(4) == 0) {
                    le.teleportTo(le.getX() + rnd(l, 8), le.getY() + 1, le.getZ() + rnd(l, 8));
                }
            }
            // ------------------------------------------------------------ III temporal
            case TIME_DILATION -> {
                if (pulse) { effect(all, MobEffects.MOVEMENT_SLOWDOWN, 40, 2); effect(all, MobEffects.DIG_SLOWDOWN, 40, 2); }
                if (t % 8 == 0) particles(e, RvParticles.RING.get().with(0x7DF9FF, (float) R, 30), 1, 0, 0, 1, 0);
            }
            case TIME_FREEZE -> {
                if (pulse) for (LivingEntity le : all) if (le instanceof Mob m) { m.setNoAi(true); m.setDeltaMovement(Vec3.ZERO); m.getPersistentData().putBoolean("riftverse_frozen", true); }
                if (t % 10 == 0) particles(e, RvParticles.MOTE.get().with(0xC0E8FF, 0.8f, 200), 30, R, 4, 2, 0);
            }
            case TEMPORAL_LOOP -> {
                if (t % 100 == 0) for (ServerPlayer p : EventKit.playersNear(e, R)) {
                    p.getPersistentData().putLong("riftverse_loop", p.blockPosition().asLong());
                }
                if (t % 100 == 99) for (ServerPlayer p : EventKit.playersNear(e, R)) {
                    if (!p.getPersistentData().contains("riftverse_loop")) continue;
                    BlockPos back = BlockPos.of(p.getPersistentData().getLong("riftverse_loop"));
                    dev.riftverse.multiverse.RealityOps.cinematic(p, dev.riftverse.multiverse.CinematicType.FLASH, 0, p.getEyePosition(), 0xFFB040, 0xFFB040, "", "");
                    p.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
                }
            }
            case CHRONO_STORM -> {
                if (pulse) for (LivingEntity le : all) le.addEffect(new MobEffectInstance(r.nextBoolean() ? MobEffects.MOVEMENT_SPEED : MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                if (t % 5 == 0) particles(e, RvParticles.STREAK.get().with(0xFF8A20, 1.2f, 16), 20, R, 6, 3, 0.4);
                if (t % 40 == 0) l.setDayTime(l.getDayTime() + 3000);
            }
            case FUTURE_ECHO -> {
                if (pulse) for (ServerPlayer p : EventKit.playersNear(e, R)) {
                    for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-8, -8, -8), p.blockPosition().offset(8, 4, 8))) {
                        BlockState s = l.getBlockState(q);
                        if (s.is(net.minecraft.tags.BlockTags.DIAMOND_ORES) || s.is(net.minecraft.tags.BlockTags.GOLD_ORES) || s.is(net.minecraft.tags.BlockTags.EMERALD_ORES) || s.is(Blocks.CHEST))
                            p.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(RvParticles.RING.get().with(0x80C0FF, 0.8f, 30), true, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 0, 0, 0, 0, 1));
                    }
                }
            }
            case PAST_ECHO -> {
                if (t % 120 == 0) for (int i = 0; i < k; i++) {
                    Mob m = r.nextBoolean() ? EventKit.spawn(e, EntityType.HUSK, around(e, R).add(0, 1, 0)) : EventKit.spawn(e, EntityType.SKELETON, around(e, R).add(0, 1, 0));
                    if (m != null) m.setCustomName(Component.literal("Echo of the Past").withColor(0xC27B4A));
                }
                if (t % 4 == 0) particles(e, ParticleTypes.ASH, 30, R, 4, 2, 0);
            }
            case AGE_SURGE -> {
                if (t % 2 == 0) l.setDayTime(l.getDayTime() + 120);
                if (t % 10 == 0) for (int i = 0; i < 30 * k; i++) {
                    BlockPos q = BlockPos.containing(around(e, R));
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos qq = q.above(dy);
                        if (l.getBlockState(qq).getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(l, qq, l.getBlockState(qq))) b.performBonemeal(l, r, qq, l.getBlockState(qq));
                    }
                }
            }
            case PARADOX_CASCADE -> {
                if (t % 10 == 0) particles(e, RvParticles.GLITCH.get().with(0xFF4050, 1f, 20), 30, R, 4, 2, 0.1);
                if (t % 100 == 0) for (ServerPlayer p : EventKit.playersNear(e, R)) dev.riftverse.temporal.TemporalManager.setParadox(p, dev.riftverse.temporal.TemporalManager.paradox(p) + (int) (8 * k));
                if (t == e.duration / 2) for (ServerPlayer p : EventKit.playersNear(e, R)) if (dev.riftverse.temporal.TemporalManager.paradox(p) >= 50) dev.riftverse.temporal.TemporalManager.dispatch(p, "PARADOX CASCADE");
            }
            // ------------------------------------------------------------ IV elemental
            case FIRESTORM -> {
                if (t % 3 == 0) particles(e, ParticleTypes.FLAME, (int) (40 * k), R, 10, 15, 0.05);
                if (t % 15 == 0) { BlockPos q = BlockPos.containing(around(e, R)); if (l.getBlockState(q).isAir()) temp(e, q, Blocks.FIRE.defaultBlockState()); }
                if (pulse) for (LivingEntity le : all) if (r.nextInt(3) == 0) le.igniteForSeconds(3);
            }
            case BLIZZARD -> {
                if (t % 2 == 0) particles(e, ParticleTypes.SNOWFLAKE, (int) (60 * k), R, 10, 10, 0.2);
                if (t % 6 == 0) { BlockPos q = BlockPos.containing(around(e, R)); if (l.getBlockState(q).isAir()) temp(e, q, Blocks.SNOW.defaultBlockState()); }
                if (pulse) for (LivingEntity le : all) { le.setTicksFrozen(Math.min(le.getTicksRequiredToFreeze() + 60, le.getTicksFrozen() + 60)); le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0)); }
            }
            case TOXIC_FOG -> {
                if (t % 3 == 0) particles(e, RvParticles.MOTE.get().with(0x80FF40, 3f, 60), 30, R, 3, 1.5, 0.01);
                if (pulse) effect(all, MobEffects.POISON, 60, (int) Math.min(2, k - 1));
            }
            case CRYSTAL_BLOOM -> {
                if (t % 8 == 0 && t < e.duration - 40) {
                    BlockPos base = BlockPos.containing(around(e, R));
                    int h = 2 + r.nextInt(5);
                    for (int y = 0; y < h; y++) temp(e, base.above(y), y == h - 1 ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
                    l.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2f, 0.8f + r.nextFloat() * 0.4f);
                }
            }
            case MAGNETIC_STORM -> {
                if (t % 2 == 0) for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, new AABB(e.center, e.center).inflate(R))) it.setDeltaMovement(it.getDeltaMovement().add(0, 0.08, 0));
                if (t % 5 == 0) particles(e, RvParticles.STREAK.get().with(0x8090A0, 1f, 14), 30, R, 10, 6, 0.4);
                if (t % 50 == 0) strike(e, around(e, R), true);
            }
            case EARTHQUAKE -> {
                if (t % 4 == 0) for (LivingEntity le : living(e, R)) if (le.onGround()) { le.setDeltaMovement(le.getDeltaMovement().add(rnd(l, 0.25), 0.25 * k, rnd(l, 0.25))); le.hurtMarked = true; }
                if (t % 4 == 0) for (ServerPlayer p : EventKit.playersNear(e, R)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new dev.riftverse.network.Payloads.Shake(0.6f * k, 6, 0f, 0));
                if (t % 3 == 0) particles(e, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), 60, R, 0.2, 0.2, 0.2);
            }
            case ACID_RAIN -> {
                if (t % 2 == 0) particles(e, ParticleTypes.FALLING_WATER, (int) (60 * k), R, 6, 18, 0);
                if (t % 3 == 0) particles(e, RvParticles.MOTE.get().with(0xC0FF60, 0.6f, 20), 20, R, 6, 12, 0.1);
                if (pulse) for (LivingEntity le : all) if (l.canSeeSky(le.blockPosition())) le.hurt(l.damageSources().magic(), 1f * k);
            }
            case LIGHTNING_STORM -> {
                if (t % Math.max(6, (int) (25 / k)) == 0) strike(e, around(e, R), false);
            }
            // ------------------------------------------------------------ V biological
            case SPORE_BLOOM -> {
                if (t % 3 == 0) particles(e, ParticleTypes.SPORE_BLOSSOM_AIR, 50, R, 4, 2, 0);
                if (t % 20 == 0) { BlockPos q = BlockPos.containing(around(e, R)); if (l.getBlockState(q).isAir()) temp(e, q, r.nextBoolean() ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState()); }
                if (pulse) effect(all, MobEffects.CONFUSION, 80, 0);
            }
            case SWARM -> {
                if (t % 60 == 0) for (int i = 0; i < 3 * k; i++) {
                    Mob s = r.nextBoolean() ? EventKit.spawn(e, EntityType.SILVERFISH, around(e, R / 2).add(0, 1, 0)) : EventKit.spawn(e, EntityType.ENDERMITE, around(e, R / 2).add(0, 1, 0));
                    if (s != null && !EventKit.playersNear(e, R).isEmpty()) s.setTarget(EventKit.playersNear(e, R).get(0));
                }
            }
            case STAMPEDE -> {
                if (t % 5 == 0) for (java.util.UUID id : e.entities) if (l.getEntity(id) instanceof Mob m) { m.getNavigation().moveTo(e.center.x + 60, m.getY(), m.getZ(), 2.2); }
                if (t % 4 == 0) for (ServerPlayer p : EventKit.playersNear(e, R)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new dev.riftverse.network.Payloads.Shake(0.25f, 6, 0f, 0));
            }
            case OVERGROWTH -> {
                if (t % 4 == 0) for (int i = 0; i < 4 * k; i++) {
                    BlockPos q = BlockPos.containing(around(e, R));
                    if (l.getBlockState(q).isAir()) temp(e, q, switch (r.nextInt(4)) { case 0 -> Blocks.TALL_GRASS.defaultBlockState(); case 1 -> Blocks.AZALEA.defaultBlockState(); case 2 -> Blocks.FERN.defaultBlockState(); default -> Blocks.FLOWERING_AZALEA.defaultBlockState(); });
                    if (r.nextInt(6) == 0) temp(e, q.below(), Blocks.MOSS_BLOCK.defaultBlockState());
                }
            }
            case MUTATION_WAVE -> {
                if (t % 60 == 0) for (LivingEntity le : living(e, R)) if (!(le instanceof ServerPlayer)) {
                    var sc = le.getAttribute(Attributes.SCALE);
                    if (sc != null) sc.setBaseValue(0.4 + r.nextDouble() * 2.0);
                    le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                }
                if (t % 10 == 0) particles(e, RvParticles.MOTE.get().with(0xFF60C0, 1f, 30), 30, R, 3, 2, 0.05);
            }
            case LIFE_SURGE -> {
                if (pulse) effect(all, MobEffects.REGENERATION, 60, 1);
                if (t % 200 == 0) for (int i = 0; i < 2 * k; i++) { if (r.nextBoolean()) EventKit.spawn(e, EntityType.SHEEP, around(e, R).add(0, 1, 0)); else EventKit.spawn(e, EntityType.RABBIT, around(e, R).add(0, 1, 0)); }
                if (t % 5 == 0) particles(e, ParticleTypes.HAPPY_VILLAGER, 30, R, 3, 1, 0);
            }
            case HIVE_AWAKENING -> {
                if (t % 100 == 0) for (int i = 0; i < 2 * k; i++) {
                    var bee = EventKit.spawn(e, EntityType.BEE, e.center.add(rnd(l, 4), 3, rnd(l, 4)));
                    if (bee != null) bee.setRemainingPersistentAngerTime(1200);
                }
            }
            case SKY_WHALE_MIGRATION -> {
                if (t % 10 == 0) for (java.util.UUID id : e.entities) if (l.getEntity(id) instanceof Mob m) m.setDeltaMovement(0.25, 0, 0.05);
            }
            // ------------------------------------------------------------ VI reality
            case COLOR_DRAIN -> {
                if (t % 4 == 0) {
                    BlockPos q = BlockPos.containing(around(e, R)).below();
                    temp(e, q, r.nextInt(3) == 0 ? Blocks.GRAY_CONCRETE.defaultBlockState() : r.nextBoolean() ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState());
                }
                if (t % 5 == 0) particles(e, RvParticles.MOTE.get().with(0x909090, 1.5f, 40), 20, R, 4, 2, 0);
            }
            case GLITCH_STORM -> {
                if (t % 3 == 0) particles(e, RvParticles.GLITCH.get().with(r.nextBoolean() ? 0x39FF14 : 0xFF0055, 1.2f, 12), 40, R, 6, 3, 0.2);
                if (t % 6 == 0) { BlockPos q = BlockPos.containing(around(e, R)).below(); temp(e, q, r.nextBoolean() ? Blocks.LIME_CONCRETE.defaultBlockState() : Blocks.MAGENTA_CONCRETE.defaultBlockState()); }
                if (t % 50 == 0) for (LivingEntity le : living(e, R)) if (!(le instanceof ServerPlayer) && r.nextInt(3) == 0) le.teleportTo(le.getX() + rnd(l, 6), le.getY(), le.getZ() + rnd(l, 6));
            }
            case SILENCE -> {
                if (pulse) effect(all, MobEffects.DARKNESS, 60, 0);
                if (t % 300 == 150) for (ServerPlayer p : EventKit.playersNear(e, R)) l.playSound(null, p.blockPosition().offset(6, 0, 6), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 0.5f);
            }
            case DREAM_LEAK -> {
                if (pulse) effect(all, MobEffects.SLOW_FALLING, 60, 0);
                if (pulse) effect(all, MobEffects.JUMP, 60, 3);
                if (t % 120 == 0) EventKit.spawn(e, RvEntities.ASTRAL_JELLY.get(), around(e, R).add(0, 6, 0));
                if (t % 3 == 0) particles(e, RvParticles.MOTE.get().with(0xFF8FD0, 2f, 60), 20, R, 6, 4, 0.02);
            }
            case GEOMETRY_FAILURE -> {
                if (t % 30 == 0) for (ServerPlayer p : EventKit.playersNear(e, R)) if (p.getDeltaMovement().horizontalDistance() > 0.05 && r.nextInt(3) == 0) {
                    Vec3 side = p.getLookAngle().yRot((float) (Math.PI / 2 * (r.nextBoolean() ? 1 : -1))).multiply(1, 0, 1).normalize().scale(4);
                    p.teleportTo(p.getX() + side.x, p.getY(), p.getZ() + side.z);
                }
                if (t % 5 == 0) particles(e, RvParticles.RING.get().with(0xFF7AF0, 3f, 20), 3, R, 3, 2, 0);
            }
            case NULL_ZONE -> {
                if (pulse) for (LivingEntity le : all) le.removeAllEffects();
                if (t % 10 == 0) particles(e, RvParticles.MOTE.get().with(0x202020, 2f, 30), 20, R, 3, 2, 0);
            }
            case REALITY_REWRITE -> {
                if (t % 100 == 0) {
                    int rule = r.nextInt(5);
                    Holder<MobEffect> eff = switch (rule) { case 0 -> MobEffects.LEVITATION; case 1 -> MobEffects.MOVEMENT_SPEED; case 2 -> MobEffects.JUMP; case 3 -> MobEffects.SLOW_FALLING; default -> MobEffects.INVISIBILITY; };
                    String name = switch (rule) { case 0 -> "GRAVITY: NEGATIVE"; case 1 -> "SPEED OF MATTER: x3"; case 2 -> "JUMP CONSTANT: x4"; case 3 -> "FALL RATE: 10%"; default -> "LIGHT: TRANSPARENT"; };
                    for (LivingEntity le : living(e, R)) le.addEffect(new MobEffectInstance(eff, rule == 0 ? 40 : 100, rule == 1 ? 2 : 1));
                    for (ServerPlayer p : EventKit.playersNear(e, R)) p.displayClientMessage(Component.literal("RULE REWRITTEN — " + name).withColor(0xFFFFFF), true);
                }
            }
            case THE_WATCHER -> {
                Vec3 eye = e.focus;
                if (t % 2 == 0) {
                    for (int i = 0; i < 24; i++) {
                        double a = i * Math.PI / 12;
                        l.sendParticles(RvParticles.MOTE.get().with(0xFFFFFF, 3f, 4), eye.x + Math.cos(a) * 12, eye.y + Math.sin(a) * 5, eye.z, 1, 0, 0, 0, 0);
                    }
                    l.sendParticles(RvParticles.MOTE.get().with(0xFF2040, 6f, 4), eye.x, eye.y, eye.z, 6, 2, 2, 0.2, 0);
                }
                if (pulse) for (ServerPlayer p : EventKit.playersNear(e, 120)) {
                    Vec3 to = eye.subtract(p.getEyePosition()).normalize();
                    if (p.getLookAngle().dot(to) > 0.9) {
                        p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                        p.displayClientMessage(Component.literal("IT SEES YOU").withColor(0xFF2040), true);
                    }
                }
            }
            default -> {}
        }
    }

    @Override
    public void end(ActiveEvent e, boolean forced) {
        Map<BlockPos, BlockState> m = TEMP.remove(e.id);
        if (m != null) m.forEach((p, s) -> e.level.setBlock(p, s, 2));
        // unfreeze, unphase and un-mutate anything this event touched, then dismiss its summons
        for (LivingEntity le : e.level.getEntitiesOfClass(LivingEntity.class, new AABB(e.center, e.center).inflate(80))) {
            if (le.getPersistentData().getBoolean("riftverse_frozen") && le instanceof Mob mob) {
                mob.setNoAi(false);
                le.getPersistentData().remove("riftverse_frozen");
            }
            if (e.type == EventType.PHASE_SHIFT && !(le instanceof ServerPlayer)) le.setInvisible(false);
            if (e.type == EventType.MUTATION_WAVE && !(le instanceof ServerPlayer)) {
                var sc = le.getAttribute(Attributes.SCALE);
                if (sc != null) sc.setBaseValue(1.0);
            }
        }
        EventKit.closeRifts(e);
        if (e.type != EventType.LIFE_SURGE) EventKit.discardAll(e);
    }
}
