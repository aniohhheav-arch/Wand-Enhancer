package dev.finalframe.mixin.client;

import dev.finalframe.client.session.ClientFinisherManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes mouse look for the performing player; the camera belongs to the cinematic. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void finalframe$lockLook(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && ClientFinisherManager.INSTANCE.localLocked()) {
            ci.cancel();
        }
    }
}
