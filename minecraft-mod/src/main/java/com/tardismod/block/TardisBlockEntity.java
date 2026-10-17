package com.tardismod.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.tardismod.TardisMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * GeckoLib block entity: a looping lamp animation plus triggerable door open/close animations.
 * The interior door additionally remembers which exterior TARDIS the player last entered through.
 */
public class TardisBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation LAMP = RawAnimation.begin().thenLoop("lamp.pulse");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("door.open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("door.close");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean open;
    private int interiorId = -1;
    private String returnDim = "";
    private int returnX, returnY, returnZ;
    private String destDim = "minecraft:overworld";
    private int destX, destZ;

    public TardisBlockEntity(BlockPos pos, BlockState state) { super(TardisMod.TARDIS_BE, pos, state); }

    public boolean isOpen() { return open; }

    private static final java.util.Map<java.util.UUID, Long> COOLDOWN = new java.util.HashMap<>();

    /** Doors open: anyone stepping into the doorway slab goes through (in -> interior, interior door -> outside). */
    public void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        if (!open || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.core.Direction f = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        double x0 = pos.getX(), z0 = pos.getZ(), y0 = pos.getY();
        net.minecraft.world.phys.AABB zone = switch (f) {
            case NORTH -> new net.minecraft.world.phys.AABB(x0 + 0.2, y0, z0, x0 + 0.8, y0 + 2, z0 + 0.3);
            case SOUTH -> new net.minecraft.world.phys.AABB(x0 + 0.2, y0, z0 + 0.7, x0 + 0.8, y0 + 2, z0 + 1);
            case WEST -> new net.minecraft.world.phys.AABB(x0, y0, z0 + 0.2, x0 + 0.3, y0 + 2, z0 + 0.8);
            default -> new net.minecraft.world.phys.AABB(x0 + 0.7, y0, z0 + 0.2, x0 + 1, y0 + 2, z0 + 0.8);
        };
        long now = level.getGameTime();
        for (net.minecraft.server.level.ServerPlayer p : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, zone)) {
            Long until = COOLDOWN.get(p.getUUID());
            if (until != null && until > now) continue;
            COOLDOWN.put(p.getUUID(), now + 40);
            if (level.dimension() == com.tardismod.Interior.KEY) com.tardismod.Interior.leave(p, this);
            else com.tardismod.Interior.enter(p, sl, pos);
        }
    }

    /** Server-side: set the doors and tell tracking clients which animation to play. */
    public void setOpen(boolean value) {
        if (open == value) return;
        open = value;
        triggerAnim("doors", open ? "open" : "close");
        setChanged();
    }

    public void setReturn(String dimension, BlockPos pos) { returnDim = dimension; returnX = pos.getX(); returnY = pos.getY(); returnZ = pos.getZ(); setChanged(); }
    public int interiorId() { return interiorId; }
    public void setInterior(int id) { interiorId = id; setChanged(); }
    public String destDimension() { return destDim; }
    public int destX() { return destX; }
    public int destZ() { return destZ; }
    public void setDestination(String dim, int x, int z) { destDim = dim; destX = x; destZ = z; setChanged(); }
    public String returnDimension() { return returnDim; }
    public BlockPos returnPos() { return new BlockPos(returnX, returnY, returnZ); }

    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("open", open); out.putInt("interior", interiorId); out.putString("returnDim", returnDim);
        out.putString("destDim", destDim); out.putInt("dx", destX); out.putInt("dz", destZ);
        out.putInt("rx", returnX); out.putInt("ry", returnY); out.putInt("rz", returnZ);
    }

    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        open = in.getBooleanOr("open", false); interiorId = in.getIntOr("interior", -1); returnDim = in.getStringOr("returnDim", "");
        destDim = in.getStringOr("destDim", "minecraft:overworld"); destX = in.getIntOr("dx", 0); destZ = in.getIntOr("dz", 0);
        returnX = in.getIntOr("rx", 0); returnY = in.getIntOr("ry", 64); returnZ = in.getIntOr("rz", 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TardisBlockEntity>("lamp", 0, test -> test.setAndContinue(LAMP)));
        controllers.add(new AnimationController<TardisBlockEntity>("doors", 3, test -> PlayState.STOP)
            .triggerableAnim("open", OPEN).triggerableAnim("close", CLOSE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
