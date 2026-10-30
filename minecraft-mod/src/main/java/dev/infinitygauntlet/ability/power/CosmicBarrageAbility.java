package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.power.PowerOrbEntity;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Cosmic Barrage: five orbs, each with its own detonation, orbit the caster. Casting again fires the next one at the
 * aim; sneak-casting fires every remaining orb in a rapid volley. The cooldown starts once the last orb is gone.
 * Cluster splits into bomblets, Chain arcs lightning between victims, Gravity becomes a collapsing well, Lance pierces
 * everything in a line and Shatter bursts into a nova of crystal shards.
 */
public final class CosmicBarrageAbility extends Ability {
    static final int LIFETIME = 600;

    public CosmicBarrageAbility() {
        super("cosmic_barrage", InfinityStone.POWER, 240);
    }

    static List<PowerOrbEntity> orbs(ServerPlayer player) {
        List<PowerOrbEntity> list = player.serverLevel().getEntitiesOfClass(PowerOrbEntity.class, new AABB(player.blockPosition()).inflate(8),
                orb -> orb.orbiting() && orb.owner() == player);
        list.sort(Comparator.comparingInt(PowerOrbEntity::variant));
        return list;
    }

    @Override
    public boolean bypassesCooldown(AbilityContext ctx) {
        return !orbs(ctx.player()).isEmpty();
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        List<PowerOrbEntity> orbs = orbs(player);
        if (orbs.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                PowerOrbEntity orb = new PowerOrbEntity(ModEntities.POWER_ORB.get(), ctx.level());
                orb.setup(player, LIFETIME, 0.5f);
                orb.barrage(i, i);
                Vec3 at = player.position().add(0, 1.3, 0);
                orb.moveTo(at.x, at.y, at.z);
                ctx.level().addFreshEntity(orb);
                Vfx.send(ctx.level(), VfxType.SPARK_BURST, at, Vec3.ZERO, PowerOrbEntity.COLORS[i], 0xFFFFFF, 0.5f, 8);
            }
            PowerFx.pose(player, PowerPose.HOLD, 14);
            PowerFx.anim(ctx, "power_charge");
            Vfx.sound(ctx.level(), player.position(), ModSounds.POWER_ORB, 2f, 0.7f);
            return CastResult.utility(Component.translatable("message.infinitygauntlet.barrage_ready"));
        }
        PowerFx.pose(player, PowerPose.THRUST, 8);
        PowerFx.anim(ctx, "power_fire");
        if (ctx.sneaking()) {
            for (int i = 0; i < orbs.size(); i++) {
                PowerOrbEntity orb = orbs.get(i);
                ServerScheduler.schedule(1 + i * 3, () -> {
                    if (orb.isAlive() && player.isAlive()) {
                        orb.fire(ctx.aimPoint(56).subtract(orb.position()));
                        PowerFx.pose(player, PowerPose.THRUST, 6);
                    }
                });
            }
            return CastResult.SUCCESS;
        }
        PowerOrbEntity next = orbs.getFirst();
        next.fire(ctx.aimPoint(56).subtract(next.position()));
        int left = orbs.size() - 1;
        return left == 0 ? CastResult.SUCCESS : CastResult.utility(Component.translatable("message.infinitygauntlet.barrage_left", left));
    }
}
