package dev.mysticarts.mixin.client;

import dev.mysticarts.client.CastAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blends spell-casting poses (and cloak flight) into every humanoid player model after vanilla animation. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mysticarts$castPose(T entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        CastAnimations.apply((HumanoidModel<?>) (Object) this, entity, age, headYaw, headPitch);
    }
}
