package dev.finalframe.mixin.client;

import dev.finalframe.client.camera.CinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin implements CinematicCamera.Access {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void finalframe$cinematic(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        CinematicCamera.apply((Camera) (Object) this, partialTick);
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void finalframe$detached(CallbackInfoReturnable<Boolean> cir) {
        if (CinematicCamera.isDetached()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public void finalframe$place(Vec3 position, float yaw, float pitch, float roll) {
        setRotation(yaw, pitch, roll);
        setPosition(position);
    }
}
