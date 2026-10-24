package dev.telephoneorder.mixin;

import dev.telephoneorder.client.ScopeInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aim-down-sights zoom: scales the camera field of view for the held weapon. */
@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void telephoneorder$ads(float partialTicks, CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float m = ScopeInfo.fovMultiplier(mc.player.getMainHandItem());
        if (m != 1f) cir.setReturnValue(cir.getReturnValue() * m);
    }
}
