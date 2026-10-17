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

/** GeckoLib block entity: a looping lamp animation plus triggerable door open/close animations. */
public class TardisBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation LAMP = RawAnimation.begin().thenLoop("lamp.pulse");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("door.open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("door.close");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean open;

    public TardisBlockEntity(BlockPos pos, BlockState state) { super(TardisMod.TARDIS_BE, pos, state); }

    /** Server-side: flip the doors and tell tracking clients which animation to play. */
    public void toggleDoors() {
        open = !open;
        triggerAnim("doors", open ? "open" : "close");
        setChanged();
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
