package dev.riftverse.block;

import dev.riftverse.registry.RvBlockEntities;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.transit.Destination;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the destination of a portal. The lowest-corner block is the "controller" that the client renders as one large
 * window into the reality on the other side.
 */
public class PortalFieldBlockEntity extends BlockEntity {
    private Destination destination = Destination.nexus();
    private BlockPos origin = BlockPos.ZERO;
    private int width = 1;
    private int height = 1;
    private boolean controller;
    private String label = "";
    private int color = 0x7DF9FF;

    public PortalFieldBlockEntity(BlockPos pos, BlockState state) {
        super(RvBlockEntities.PORTAL_FIELD.get(), pos, state);
        this.origin = pos;
    }

    public Destination destination() {
        return destination;
    }

    public BlockPos origin() {
        return origin;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isController() {
        return controller;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }

    public Direction.Axis axis() {
        BlockState s = getBlockState();
        return s.hasProperty(PortalFieldBlock.AXIS) ? s.getValue(PortalFieldBlock.AXIS) : Direction.Axis.X;
    }

    public void configure(Destination destination, BlockPos origin, int width, int height, String label, int color) {
        this.destination = destination;
        this.origin = origin.immutable();
        this.width = width;
        this.height = height;
        this.controller = origin.equals(worldPosition);
        this.label = label;
        this.color = color;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Fills a rectangle with portal membrane in a live level. */
    public static void fill(Level level, BlockPos min, Direction.Axis axis, int w, int h, Destination dest, String label, int color) {
        BlockState state = RvBlocks.PORTAL_FIELD.get().defaultBlockState().setValue(PortalFieldBlock.AXIS, axis);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                BlockPos p = axis == Direction.Axis.X ? min.offset(i, j, 0) : min.offset(0, j, i);
                level.setBlock(p, state, 2 | 16);
            }
        }
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                BlockPos p = axis == Direction.Axis.X ? min.offset(i, j, 0) : min.offset(0, j, i);
                if (level.getBlockEntity(p) instanceof PortalFieldBlockEntity be) be.configure(dest, min, w, h, label, color);
            }
        }
    }

    /** Places a portal during world generation, writing block-entity data directly into the proto-chunk. */
    public static void placeInWorldgen(WorldGenLevel level, BlockPos min, Direction.Axis axis, int w, int h, Destination dest, String label, int color) {
        BlockState state = RvBlocks.PORTAL_FIELD.get().defaultBlockState().setValue(PortalFieldBlock.AXIS, axis);
        String id = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(RvBlockEntities.PORTAL_FIELD.get()));
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                BlockPos p = axis == Direction.Axis.X ? min.offset(i, j, 0) : min.offset(0, j, i);
                level.setBlock(p, state, 2);
                CompoundTag tag = new CompoundTag();
                tag.putString("id", id);
                tag.putInt("x", p.getX());
                tag.putInt("y", p.getY());
                tag.putInt("z", p.getZ());
                write(tag, dest, min, w, h, p.equals(min), label, color);
                level.getChunk(p).setBlockEntityNbt(tag);
            }
        }
    }

    private static void write(CompoundTag tag, Destination dest, BlockPos origin, int w, int h, boolean controller, String label, int color) {
        tag.put("dest", dest.save());
        tag.putLong("origin", origin.asLong());
        tag.putInt("w", w);
        tag.putInt("h", h);
        tag.putBoolean("controller", controller);
        tag.putString("label", label);
        tag.putInt("color", color);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, destination, origin, width, height, controller, label, color);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("dest")) destination = Destination.load(tag.getCompound("dest"));
        origin = BlockPos.of(tag.getLong("origin"));
        width = Math.max(1, tag.getInt("w"));
        height = Math.max(1, tag.getInt("h"));
        controller = tag.getBoolean("controller");
        label = tag.getString("label");
        color = tag.contains("color") ? tag.getInt("color") : 0x7DF9FF;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, destination, origin, width, height, controller, label, color);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
