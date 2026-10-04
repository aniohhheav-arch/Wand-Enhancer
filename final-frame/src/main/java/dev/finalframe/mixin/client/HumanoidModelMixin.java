package dev.finalframe.mixin.client;

import dev.finalframe.client.render.EntityPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands humanoid limbs to the active choreography after vanilla animation ran. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void finalframe$choreograph(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                        float headPitch, CallbackInfo ci) {
        EntityPoses.applyModelPose((HumanoidModel<?>) (Object) this, entity);
    }
}
