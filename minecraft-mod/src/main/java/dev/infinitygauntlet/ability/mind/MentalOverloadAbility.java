package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Mental Overload: a golden eye sigil appears over the target and energy spirals into it for two seconds, then its
 * mind overloads in a single violent burst. Unlike Neural Blast it needs no line of fire once marked.
 */
public final class MentalOverloadAbility extends Ability {
    static final int DELAY = 40;

    public MentalOverloadAbility() {
        super("mental_overload", InfinityStone.MIND, 140);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = ctx.pickLiving(32);
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        var player = ctx.player();
        var level = ctx.level();
        PowerFx.pose(player, PowerPose.TEMPLE, 18);
        PowerFx.anim(ctx, "mind_focus");
        Vfx.send(level, VfxType.MIND_MARK, target.position(), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 1f, DELAY, target.getId());
        Vfx.sound(level, target.position(), ModSounds.MIND_MARK, 2f, 1f);
        ServerScheduler.schedule(DELAY, () -> {
            if (!target.isAlive() || target.level() != level) {
                return;
            }
            Vec3 c = target.getBoundingBox().getCenter();
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().indirectMagic(player, player), MindManager.damage(20f));
            target.setDeltaMovement(0, 0.5, 0);
            target.hurtMarked = true;
            Vfx.send(level, VfxType.IMPLOSION, c, Vec3.ZERO, MindManager.GOLD, 0xFFFFFF, 2.2f, 8);
            Vfx.send(level, VfxType.GEOMETRY_BURST, c, Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 3f, 20);
            Vfx.send(level, VfxType.SHOCKWAVE, c, new Vec3(0, 1, 0), MindManager.GOLD, MindManager.PALE, 5f, 12);
            Vfx.sound(level, c, ModSounds.MIND_BURST, 2.5f, 0.9f);
            Vfx.glitch(level, c, 16, 0.5f);
        });
        return CastResult.SUCCESS;
    }
}
