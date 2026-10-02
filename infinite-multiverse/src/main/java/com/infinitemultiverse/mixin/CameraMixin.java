package com.infinitemultiverse.mixin;

import com.infinitemultiverse.client.cinematic.CinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets a running cutscene take over the camera: free position, rotation, and a detached view so the caster is visible. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void infinitemultiverse$cinematic(BlockGetter level, Entity entity, boolean detachedView, boolean mirrored, float partialTick, CallbackInfo ci) {
        CinematicCamera.Shot shot = CinematicCamera.current(partialTick);
        if (shot != null) {
            this.detached = true;
            setRotation(shot.yaw(), shot.pitch());
            setPosition(shot.pos());
        }
    }
}
