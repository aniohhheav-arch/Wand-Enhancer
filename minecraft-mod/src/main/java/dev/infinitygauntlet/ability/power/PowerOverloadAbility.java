package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerOverload;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Power Overload: ten seconds of overloaded melee, shockwave sprinting and a closing energy vent (see {@link PowerOverload}). */
public final class PowerOverloadAbility extends Ability {
    static final int DURATION = 200;

    public PowerOverloadAbility() {
        super("power_overload", InfinityStone.POWER, 400);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        PowerOverload.start(ctx.player(), DURATION);
        ctx.player().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1, false, false, true));
        PowerFx.pose(ctx.player(), PowerPose.OVERLOAD, DURATION);
        PowerFx.anim(ctx, "power_charge");
        Vec3 c = ctx.player().position().add(0, 1, 0);
        Vfx.send(ctx.level(), VfxType.SPHERE_PULSE, c, Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 3.5f, 10);
        Vfx.send(ctx.level(), VfxType.SHOCKWAVE, ctx.player().position().add(0, 0.1, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, 5f, 12);
        for (int i = 0; i < 6; i++) {
            Vec3 dir = new Vec3(Math.cos(i), 0.4 + 0.3 * Math.sin(i * 2), Math.sin(i)).normalize();
            Vfx.send(ctx.level(), VfxType.ARC, ctx.handPos(), ctx.handPos().add(dir.scale(2.5)), PowerFx.PURPLE, 0xFFFFFF, 1.2f, 8);
        }
        Vfx.sound(ctx.level(), c, ModSounds.POWER_OVERLOAD, 2.5f, 0.9f);
        Vfx.camera(ctx.level(), c, 6, 0.4f, 0.94f, 10, 0xD080FF, 0.2f, 4);
        return CastResult.success(Component.translatable("message.infinitygauntlet.overload"));
    }
}
