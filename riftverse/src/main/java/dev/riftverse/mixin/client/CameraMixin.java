package dev.riftverse.mixin.client;

import dev.riftverse.client.cinematic.CameraRig;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets cinematic sequences fly the camera independently of the player's eyes. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void riftverse$applyRig(BlockGetter level, Entity entity, boolean detachedIn, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        CameraRig.Pose pose = CameraRig.current();
        if (pose == null) return;
        setRotation(pose.yaw(), pose.pitch(), pose.roll());
        setPosition(pose.position());
        if (pose.detached()) detached = true;
    }
}
