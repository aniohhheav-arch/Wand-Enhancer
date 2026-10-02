package dev.riftverse.entity.vehicle;

import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.temporal.TemporalManager;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.UniverseTravel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A police box that is bigger on the inside. The exterior is a door into a private interior room built in the Nexus;
 * the console inside dematerialises the box and lands it in another universe and another year. Two roles share one
 * entity type: the exterior shell and the interior console.
 */
public class TardisEntity extends Entity {
    private static final EntityDataAccessor<Boolean> CONSOLE = SynchedEntityData.defineId(TardisEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(TardisEntity.class, EntityDataSerializers.INT);
    public static final int ROOM = 9;
    private int interior = -1;
    private UUID partner;
    private ResourceKey<Level> partnerDim;
    private BlockPos partnerPos = BlockPos.ZERO;

    public TardisEntity(EntityType<? extends TardisEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(CONSOLE, false);
        b.define(FADE, 0);
    }

    public boolean console() {
        return entityData.get(CONSOLE);
    }

    /** Dematerialisation phase: 0 = solid, otherwise ticks remaining of the fade cycle (60 out, then 60 in). */
    public int fade() {
        return entityData.get(FADE);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !console();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && fade() > 0) entityData.set(FADE, fade() - 1);
        if (!level().isClientSide && fade() > 0 && fade() % 20 == 0) {
            level().playSound(null, blockPosition(), RvSounds.BLACK_HOLE_PULL.get(), SoundSource.BLOCKS, 1.5f, 0.5f + (fade() % 40) / 80f);
        }
        if (!console() && !onGround() && !level().isClientSide && fade() == 0) {
            setDeltaMovement(0, Math.max(-0.5, getDeltaMovement().y - 0.04), 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || console()) return true;
        if (source.getEntity() instanceof Player p && p.isShiftKeyDown()) {
            if (!p.isCreative()) spawnAtLocation(new ItemStack(RvItems.TARDIS.get()));
            discard();
        }
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (fade() > 0) {
            sp.displayClientMessage(Component.literal("The box is between moments.").withColor(0x6090FF), true);
            return InteractionResult.CONSUME;
        }
        if (console()) {
            if (sp.isSecondaryUseActive()) exitTo(sp);
            else dematerialise(sp);
        } else {
            enter(sp);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ interior

    private static BlockPos roomOrigin(int index) {
        return new BlockPos(300_000 + index * 64, 120, 300_000);
    }

    private void enter(ServerPlayer p) {
        ServerLevel nexus = p.server.getLevel(RvWorldgen.NEXUS);
        if (nexus == null) return;
        if (interior < 0) interior = Math.floorMod(getUUID().hashCode(), 4000);
        BlockPos o = roomOrigin(interior);
        nexus.getChunk(o.getX() >> 4, o.getZ() >> 4);
        if (!nexus.getBlockState(o.below()).is(Blocks.POLISHED_DEEPSLATE)) build(nexus, o);
        TardisEntity console = findConsole(nexus, o);
        if (console == null) {
            console = RvEntities.TARDIS.get().create(nexus);
            if (console == null) return;
            console.entityData.set(CONSOLE, true);
            console.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);
            nexus.addFreshEntity(console);
        }
        console.partner = getUUID();
        console.partnerDim = level().dimension();
        console.partnerPos = blockPosition();
        console.interior = interior;
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1f, 1f);
        p.teleportTo(nexus, o.getX() + 0.5, o.getY(), o.getZ() + ROOM - 1.5, 180f, 0f);
        p.displayClientMessage(Component.literal("It's bigger on the inside. Use the console to travel, sneak-use it to step out.").withColor(0x6090FF), true);
    }

    private static TardisEntity findConsole(ServerLevel level, BlockPos o) {
        for (TardisEntity t : level.getEntitiesOfClass(TardisEntity.class, new net.minecraft.world.phys.AABB(o).inflate(4))) {
            if (t.console()) return t;
        }
        return null;
    }

    private static void build(ServerLevel l, BlockPos o) {
        int r = ROOM, h = 8;
        BlockState wall = Blocks.QUARTZ_BLOCK.defaultBlockState(), roundel = Blocks.SEA_LANTERN.defaultBlockState();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -1; y <= h; y++) {
                    BlockPos p = o.offset(x, y, z);
                    boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                    if (y == -1) l.setBlock(p, (x + z) % 4 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                    else if (y == h) l.setBlock(p, Math.abs(x) < 3 && Math.abs(z) < 3 ? Blocks.GLASS.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
                    else if (edge) l.setBlock(p, (y % 3 == 1 && (x + z) % 3 == 0) ? roundel : wall, 2);
                    else l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int y = 0; y < 3; y++) l.setBlock(o.offset(0, y + 5, 0), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 2);
        for (int[] c : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            for (int y = 0; y < h; y++) l.setBlock(o.offset(c[0] * 2, y, c[1] * 2), Blocks.CUT_COPPER.defaultBlockState(), 2);
        }
        l.setBlock(o.offset(0, 0, r), Blocks.BLUE_CONCRETE.defaultBlockState(), 2);
        l.setBlock(o.offset(0, 1, r), Blocks.BLUE_CONCRETE.defaultBlockState(), 2);
    }

    private TardisEntity exterior(ServerPlayer p) {
        if (partner == null || partnerDim == null) return null;
        ServerLevel level = p.server.getLevel(partnerDim);
        if (level == null) return null;
        level.getChunk(partnerPos.getX() >> 4, partnerPos.getZ() >> 4);
        return level.getEntity(partner) instanceof TardisEntity t ? t : null;
    }

    private void exitTo(ServerPlayer p) {
        TardisEntity ext = exterior(p);
        if (ext == null) {
            p.displayClientMessage(Component.literal("The doors open onto nothing. The exterior is lost.").withColor(0xFF6060), true);
            return;
        }
        Vec3 out = ext.position().add(new Vec3(0, 0, 1.6).yRot(-ext.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD));
        p.teleportTo((ServerLevel) ext.level(), out.x, out.y, out.z, ext.getYRot(), 0f);
    }

    /** The famous wheeze: the shell fades out, the interior shakes, the shell fades in elsewhere and elsewhen. */
    private void dematerialise(ServerPlayer p) {
        TardisEntity ext = exterior(p);
        if (ext == null) {
            p.displayClientMessage(Component.literal("No exterior to steer.").withColor(0xFF6060), true);
            return;
        }
        ServerLevel inside = (ServerLevel) level();
        ext.entityData.set(FADE, 120);
        entityData.set(FADE, 120);
        inside.playSound(null, blockPosition(), RvSounds.BLACK_HOLE_PULL.get(), SoundSource.BLOCKS, 2f, 0.5f);
        for (ServerPlayer o : inside.getPlayers(pl -> pl.distanceToSqr(this) < ROOM * ROOM * 2)) {
            dev.riftverse.multiverse.RealityOps.cinematic(o, dev.riftverse.multiverse.CinematicType.TIME_TRAVEL, 120, o.getEyePosition(), 0x2050FF, 0x80C0FF, "DEMATERIALISING", "");
        }
        int year = TemporalManager.PRESENT + (inside.random.nextBoolean() ? -1 : 1) * (50 + inside.random.nextInt(3000));
        Scheduler.later(60, () -> {
            UniverseTravel.Target t = UniverseTravel.resolveFor(p.server, Destination.random(), inside.random);
            if (t == null || ext.isRemoved()) return;
            BlockPos land = UniverseTravel.safeAround(t.level(), BlockPos.containing(t.pos()), 12);
            TardisEntity moved = (TardisEntity) ext.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(t.level(), Vec3.atBottomCenterOf(land), Vec3.ZERO,
                    ext.getYRot(), 0, net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
            TardisEntity shell = moved != null ? moved : ext;
            shell.entityData.set(FADE, 60);
            partner = shell.getUUID();
            partnerDim = t.level().dimension();
            partnerPos = land;
            for (ServerPlayer o : inside.getPlayers(pl -> pl.distanceToSqr(this) < ROOM * ROOM * 2)) {
                TemporalManager.setYear(o, year);
                dev.riftverse.multiverse.RealityOps.cinematic(o, dev.riftverse.multiverse.CinematicType.ANNOUNCE, 80, o.getEyePosition(), 0x2050FF, 0xFFFFFF,
                        "MATERIALISED", (t.spec() != null ? t.spec().name : t.title()) + " • " + TemporalManager.formatYear(year));
            }
        });
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(CONSOLE, t.getBoolean("console"));
        interior = t.contains("interior") ? t.getInt("interior") : -1;
        if (t.hasUUID("partner")) partner = t.getUUID("partner");
        if (t.contains("partnerDim")) partnerDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("partnerDim")));
        partnerPos = BlockPos.of(t.getLong("partnerPos"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putBoolean("console", console());
        t.putInt("interior", interior);
        if (partner != null) t.putUUID("partner", partner);
        if (partnerDim != null) t.putString("partnerDim", partnerDim.location().toString());
        t.putLong("partnerPos", partnerPos.asLong());
    }
}
