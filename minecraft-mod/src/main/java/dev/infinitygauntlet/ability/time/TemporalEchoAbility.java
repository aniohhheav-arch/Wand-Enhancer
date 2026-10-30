package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.TemporalEchoEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.EntityHistory;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Peels an echo of the caster out of the recent past; it re-walks the caster's last few seconds and draws enemies. */
public final class TemporalEchoAbility extends Ability {
    public TemporalEchoAbility() {
        super("temporal_echo", InfinityStone.TIME, 400);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        List<EntityHistory.Frame> history = EntityHistory.history(ctx.player());
        if (history.size() < 20) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.echo.no_history"));
        }
        List<EntityHistory.Frame> frames = new ArrayList<>(history.subList(0, Math.min(ServerConfig.ECHO_RECORD_TICKS.get(), history.size())));
        Collections.reverse(frames);
        TemporalEchoEntity echo = new TemporalEchoEntity(ModEntities.TEMPORAL_ECHO.get(), ctx.level());
        echo.configure(ctx.player(), frames);
        ctx.level().addFreshEntity(echo);
        Vec3 at = frames.getFirst().pos();
        Vfx.send(ctx.level(), VfxType.CLOCK_RINGS, at.add(0, 1, 0), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 1f, 24);
        Vfx.link(ctx.level(), ctx.handPos(), at.add(0, 1, 0), InfinityStone.TIME, 14);
        Vfx.sound(ctx.level(), at, ModSounds.TIME_ECHO, 1.5f, 1f);
        return CastResult.SUCCESS;
    }
}
