package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.TimeFieldEntity;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.RewindController;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Plays recorded movement backwards: the aimed creature or projectile, or (with nothing aimed) everything hostile
 * nearby. Sneak-cast rewinds yourself. Only movement is rewound; health, inventories and blocks are untouched.
 */
public final class RewindAbility extends Ability {
    public RewindAbility() {
        super("rewind", InfinityStone.TIME, 500);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        int frames = ServerConfig.REWIND_HISTORY_TICKS.get();
        List<Entity> targets;
        if (ctx.sneaking()) {
            targets = List.of(ctx.player());
        } else {
            Entity aimed = ctx.pickEntity(40, e -> (e instanceof LivingEntity || e instanceof Projectile) && TimeFieldEntity.affectedBy(ctx.player(), e));
            double r = ServerConfig.REWIND_RADIUS.get();
            targets = aimed != null ? List.of(aimed) : ctx.level().getEntities(ctx.player(), ctx.player().getBoundingBox().inflate(r),
                    e -> (e instanceof LivingEntity || e instanceof Projectile) && TimeFieldEntity.affectedBy(ctx.player(), e));
        }
        int rewound = 0;
        int longest = 0;
        for (Entity e : targets) {
            int ticks = RewindController.start(e, frames);
            if (ticks > 0) {
                rewound++;
                longest = Math.max(longest, ticks);
                Vfx.send(ctx.level(), VfxType.CLOCK_RINGS, e.position().add(0, e.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                        InfinityStone.TIME.highlight(), Math.max(0.6f, e.getBbWidth()), ticks);
            }
        }
        if (rewound == 0) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.rewind.nothing"));
        }
        Vfx.send(ctx.level(), VfxType.CLOCK_GLYPH, ctx.player().position().add(0, 0.05, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                InfinityStone.TIME.highlight(), 3f, longest + 10);
        Vfx.sound(ctx.level(), ctx.player().position(), ModSounds.TIME_REWIND, 2f, 1f);
        Vfx.camera(ctx.level(), ctx.player().position(), 24, 0.2f, 0.92f, longest, 0xB4FFC8, 0.15f, 8);
        return CastResult.success(Component.translatable("message.infinitygauntlet.rewind.done", rewound));
    }
}
