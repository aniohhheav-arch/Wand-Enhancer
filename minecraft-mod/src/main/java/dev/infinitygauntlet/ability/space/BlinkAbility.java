package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Instant short-range teleport along the look direction, stopping short of walls and never inside blocks. */
public final class BlinkAbility extends Ability {
    public BlinkAbility() {
        super("blink", InfinityStone.SPACE, 30);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        int distance = ServerConfig.BLINK_DISTANCE.get();
        BlockHitResult hit = ctx.pickBlock(distance);
        Vec3 look = ctx.look();
        Vec3 eyeOffset = new Vec3(0, player.getEyeHeight(), 0);
        Vec3 targetEye = hit.getType() == HitResult.Type.MISS ? ctx.eye().add(look.scale(distance)) : hit.getLocation().subtract(look.scale(0.7));
        Vec3 feet = targetEye.subtract(eyeOffset);
        Vec3 safe = null;
        // walk back along the ray until the player fits
        for (double back = 0; back < distance && safe == null; back += 0.75) {
            safe = TeleportUtil.findSafeSpot(ctx.level(), player, feet.subtract(look.scale(back)), 1);
        }
        if (safe == null || safe.distanceTo(player.position()) < 1.5) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.blink.blocked"));
        }
        Vec3 from = player.position();
        Vfx.dissolve(ctx.level(), player, InfinityStone.SPACE);
        Vfx.send(ctx.level(), VfxType.SPARK_BURST, from.add(0, 1, 0), Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), 0.8f, 10);
        Vfx.send(ctx.level(), VfxType.BEAM, from.add(0, 1, 0), safe.add(0, 1, 0), InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), 0.35f, 6);
        Vfx.sound(ctx.level(), from, ModSounds.SPACE_BLINK, 1f, 1f);

        TeleportUtil.teleport(player, ctx.level(), safe, player.getYRot(), player.getXRot(), player.getDeltaMovement().scale(0.4));

        Vfx.shockwave(ctx.level(), safe.add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.SPACE, 3.2f, 12);
        Vfx.shockwave(ctx.level(), safe.add(0, 1, 0), look, InfinityStone.SPACE, 2.0f, 10);
        Vfx.sound(ctx.level(), safe, ModSounds.SPACE_BLINK, 1f, 1.3f);
        Vfx.camera(ctx.level(), safe, 6, 0.15f, 0.9f, 6, 0x9FE8FF, 0.12f, 4);
        return CastResult.SUCCESS;
    }
}
