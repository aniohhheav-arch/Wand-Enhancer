package dev.riftverse.block;

import dev.riftverse.registry.RvBlockEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RiftBlockEntity extends BlockEntity {
    public static final float TRIGGER_RADIUS = 1.35f;

    @Nullable
    private Destination destination;
    private int charges = 3;
    private long expireAt;
    private boolean harvestable = true;
    /** Client-side opening animation, 0..1. */
    public float openProgress;
    public float openProgressO;
    public long seed;
    /** Colours and name of the concrete world this rift leads to (resolved server-side, synced to clients). */
    public int tintA = -1;
    public int tintB = -1;
    public String destName = "";
    private boolean resolved;

    public RiftBlockEntity(BlockPos pos, BlockState state) {
        super(RvBlockEntities.RIFT.get(), pos, state);
        this.seed = pos.asLong() * 31L + 7L;
    }

    public RiftType type() {
        return getBlockState().getValue(RiftBlock.TYPE);
    }

    public boolean isHarvestable() {
        return harvestable && type() != RiftType.RETURN && charges > 0;
    }

    public void configure(@Nullable Destination destination, long expireAt, boolean harvestable) {
        this.destination = destination;
        this.expireAt = expireAt;
        this.harvestable = harvestable;
        this.resolved = false;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public Destination destination() {
        return destination != null ? destination : Destination.flavor(type());
    }

    public void harvest() {
        charges--;
        setChanged();
        if (charges <= 0 && level != null) collapse((ServerLevel) level, worldPosition);
    }

    public static void collapse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        RiftType type = state.hasProperty(RiftBlock.TYPE) ? state.getValue(RiftBlock.TYPE) : RiftType.AZURE;
        level.sendParticles(RvParticles.RING.get().with(type.colorA, 3.0f, 18), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.SPARK.get().with(type.colorB, 0.8f, 30), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 60, 0.6, 1.0, 0.6, 0.35);
        level.playSound(null, pos, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.BLOCKS, 0.7f, 1.5f);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Decides, once, exactly which world this rift opens onto, and takes on that world's colours, so a rift's colour
     * always tells you where it goes. Family rifts pick a concrete universe of their family; the choice is saved.
     */
    private void resolve(ServerLevel level) {
        resolved = true;
        RiftType type = type();
        var server = level.getServer();
        Destination d = destination;
        if (d == null && type != RiftType.NEXUS && type != RiftType.RETURN) d = Destination.flavor(type);
        if (d == null || d.kind() == Destination.Kind.NEXUS) {
            tintA = 0xFFC14D;
            tintB = 0xFFFFFF;
            destName = "The Multiverse Nexus";
        } else if (d.kind() == Destination.Kind.FLAVOR || d.kind() == Destination.Kind.RANDOM || d.kind() == Destination.Kind.ARCHETYPE) {
            var reg = dev.riftverse.universe.UniverseRegistry.get(server);
            java.util.Random r = new java.util.Random(seed ^ level.getGameTime());
            dev.riftverse.universe.UniverseSpec spec = d.kind() == Destination.Kind.ARCHETYPE
                    ? reg.primeSpec(dev.riftverse.universe.Archetype.byId(d.archetype()))
                    : reg.randomUniverse(r, d.kind() == Destination.Kind.FLAVOR ? RiftType.byId(d.flavor()).destinations() : null);
            destination = Destination.universe(spec.id);
            paint(spec);
        } else if (d.kind() == Destination.Kind.UNIVERSE) {
            paint(dev.riftverse.universe.UniverseRegistry.specFor(dev.riftverse.universe.UniverseId.unpack(d.universe())));
        } else {
            tintA = type.colorA;
            tintB = type.colorB;
            destName = d.kind() == Destination.Kind.HOME ? "Home" : "Return Passage";
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void paint(dev.riftverse.universe.UniverseSpec spec) {
        tintA = spec.accent;
        tintB = spec.archetype.signatureColor;
        destName = spec.name + " • " + spec.archetype.displayName;
    }

    public int colorA() {
        return tintA >= 0 ? tintA : type().colorA;
    }

    public int colorB() {
        return tintB >= 0 ? tintB : type().colorB;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftBlockEntity be) {
        if (!be.resolved || (be.destination != null && be.destination.kind() == Destination.Kind.UNIVERSE
                && !dev.riftverse.multiverse.RealityState.isAccessible(dev.riftverse.universe.UniverseId.unpack(be.destination.universe())))) {
            if (be.resolved) be.destination = null;
            be.resolve((ServerLevel) level);
        }
        if (level.getGameTime() % 5 == Math.floorMod(pos.hashCode(), 5) && !be.destName.isEmpty()) {
            Vec3 c = Vec3.atCenterOf(pos);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(20))) {
                // only when the player is actually looking at the rift
                Vec3 to = c.subtract(p.getEyePosition());
                double d = to.length();
                if (d < 0.5 || d > 20) continue;
                double cos = to.scale(1.0 / d).dot(p.getLookAngle());
                if (cos < Math.cos(Math.atan2(1.4, d))) continue;
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("Rift → " + be.destName).withColor(be.colorA()), true);
            }
        }
        if (be.expireAt > 0 && level.getGameTime() >= be.expireAt) {
            collapse((ServerLevel) level, pos);
            return;
        }
        if ((level.getGameTime() + pos.hashCode()) % 2 != 0) return;
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = new AABB(center, center).inflate(TRIGGER_RADIUS + 0.6, TRIGGER_RADIUS + 1.2, TRIGGER_RADIUS + 0.6);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (player.isSpectator()) continue;
            Vec3 body = player.position().add(0, player.getBbHeight() * 0.5, 0);
            if (body.distanceTo(center) > TRIGGER_RADIUS + 0.9) continue;
            RiftType type = state.getValue(RiftBlock.TYPE);
            TransitManager.begin(player, TransitKind.RIFT, be.destination(), center, be.colorA(), be.colorB(), type != RiftType.RETURN);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RiftBlockEntity be) {
        be.openProgressO = be.openProgress;
        be.openProgress = Math.min(1f, be.openProgress + 0.035f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (destination != null) tag.put("dest", destination.save());
        tag.putInt("charges", charges);
        tag.putLong("expireAt", expireAt);
        tag.putBoolean("harvestable", harvestable);
        tag.putBoolean("resolved", resolved);
        tag.putInt("tintA", tintA);
        tag.putInt("tintB", tintB);
        tag.putString("destName", destName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        destination = tag.contains("dest") ? Destination.load(tag.getCompound("dest")) : null;
        charges = tag.contains("charges") ? tag.getInt("charges") : 3;
        expireAt = tag.getLong("expireAt");
        harvestable = !tag.contains("harvestable") || tag.getBoolean("harvestable");
        resolved = tag.getBoolean("resolved");
        tintA = tag.contains("tintA") ? tag.getInt("tintA") : -1;
        tintB = tag.contains("tintB") ? tag.getInt("tintB") : -1;
        destName = tag.getString("destName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("expireAt", expireAt);
        tag.putInt("tintA", tintA);
        tag.putInt("tintB", tintB);
        tag.putString("destName", destName);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public long expireAt() {
        return expireAt;
    }
}
