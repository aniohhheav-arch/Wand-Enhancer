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
