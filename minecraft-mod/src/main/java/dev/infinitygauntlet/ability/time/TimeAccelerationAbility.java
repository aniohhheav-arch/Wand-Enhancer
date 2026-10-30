package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Speeds up the caster's personal time: faster movement, attacks and block breaking, with a trail of green
 * afterimages. Modifiers are transient, so they never persist past a relog.
 */
public final class TimeAccelerationAbility extends Ability {
    private static final ResourceLocation MODIFIER = InfinityGauntlet.id("time_acceleration");

    public TimeAccelerationAbility() {
        super("time_acceleration", InfinityStone.TIME, 900);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        int ticks = ServerConfig.ACCELERATION_SECONDS.get() * 20;
        double bonus = ServerConfig.ACCELERATION_SPEED_BONUS.get();
        set(player, Attributes.MOVEMENT_SPEED, bonus);
        set(player, Attributes.ATTACK_SPEED, bonus * 1.25);
        set(player, Attributes.BLOCK_BREAK_SPEED, bonus);
        set(player, Attributes.STEP_HEIGHT, 0.4);
        Vfx.send(ctx.level(), VfxType.AFTERIMAGES, player.position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 1f,
                ticks, player.getId());
        Vfx.send(ctx.level(), VfxType.CLOCK_RINGS, player.position().add(0, 1, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                InfinityStone.TIME.highlight(), 1.1f, 24);
        Vfx.sound(ctx.level(), player.position(), ModSounds.TIME_ACCELERATE, 1.5f, 1f);
        Vfx.camera(ctx.level(), player.position(), 4, 0.1f, 1.12f, 14, 0, 0, 0);

        UUID id = player.getUUID();
        ServerScheduler.schedule(ticks, () -> {
            ServerPlayer p = player.server.getPlayerList().getPlayer(id);
            if (p == null) {
                return;
            }
            for (Holder<Attribute> a : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED, Attributes.BLOCK_BREAK_SPEED,
                    Attributes.STEP_HEIGHT)) {
                AttributeInstance inst = p.getAttribute(a);
                if (inst != null) {
                    inst.removeModifier(MODIFIER);
                }
            }
            Vfx.send(p.serverLevel(), VfxType.SPARK_BURST, p.position().add(0, 1, 0), Vec3.ZERO, InfinityStone.TIME.color(),
                    InfinityStone.TIME.highlight(), 1f, 12);
            Vfx.sound(p.serverLevel(), p.position(), ModSounds.TIME_RESUME, 1f, 1.4f);
        });
        return CastResult.SUCCESS;
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, double amount) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(MODIFIER);
        AttributeModifier.Operation op = attribute == Attributes.STEP_HEIGHT ? AttributeModifier.Operation.ADD_VALUE
                : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        inst.addTransientModifier(new AttributeModifier(MODIFIER, amount, op));
    }
}
