package com.infinitemultiverse.mixin;

import com.infinitemultiverse.power.mystic.AstralState;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An astral projection passes through blocks: re-enable no-clip right after vanilla resets it each tick. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void infinitemultiverse$astralNoClip(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (AstralState.isProjecting(self)) {
            self.noPhysics = true;
            self.setOnGround(false);
        }
    }
}
