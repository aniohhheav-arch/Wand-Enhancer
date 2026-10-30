package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.power.PowerOrbEntity;
import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmic Artillery: six orbs spiral up out of the gauntlet and hang above the caster while lock-on reticles close in
 * on the nearest enemies in front of them. One by one the orbs fall as screaming meteors, cratering the ground.
 */
public final class CosmicArtilleryAbility extends Ability {
    static final int ORBS = 6;
    static final int LOCK = 18;

    public CosmicArtilleryAbility() {
        super("cosmic_artillery", InfinityStone.POWER, 260);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : CombatFx.targets(player, player.position(), 30)) {
            Vec3 to = e.position().subtract(player.position()).normalize();
            if (to.dot(ctx.look()) > 0.1 && player.hasLineOfSight(e)) {
                targets.add(e);
            }
            if (targets.size() == ORBS) {
                break;
            }
        }
        Vec3 aim = ctx.groundTarget(40, 20);
        PowerFx.pose(player, PowerPose.RAISE, 24);
        PowerFx.anim(ctx, "power_raise");
        Vfx.sound(level, player.position(), ModSounds.POWER_CHARGE, 2f, 1.4f);
        PowerFx.cinematic(player, null, player.position().add(0, 6.5, 0), PowerFx.rel(player, 2.2, -5.4, 3.2), PowerFx.rel(player, 3.4, -5.8, 5.5), 30, 4f, 1.1f);
        for (int i = 0; i < ORBS; i++) {
            double a = i * Math.PI * 2 / ORBS;
            Vec3 from = player.position().add(Math.cos(a) * 0.8, 1.3, Math.sin(a) * 0.8);
            Vec3 hover = player.position().add(Math.cos(a) * 3.4, 6.5 + (i % 2) * 0.8, Math.sin(a) * 3.4);
            PowerOrbEntity orb = new PowerOrbEntity(ModEntities.POWER_ORB.get(), level);
            orb.setup(player, 140, 0.8f);
            orb.artillery(from, hover, 12 + i * 2, LOCK + 10 + i * 4);
            LivingEntity target = i < targets.size() ? targets.get(i) : targets.isEmpty() ? null : targets.get(i % targets.size());
            Vec3 fallback = TeleportUtil.groundBelow(level, aim.add(level.random.nextGaussian() * 3.5, 2, level.random.nextGaussian() * 3.5), 12);
            orb.lock(target, fallback);
            level.addFreshEntity(orb);
            int delay = i * 2;
            ServerScheduler.schedule(1 + delay, () -> Vfx.sound(level, from, ModSounds.POWER_ORB, 1.4f, 0.8f + delay * 0.04f));
        }
        ServerScheduler.schedule(LOCK, () -> {
            for (LivingEntity t : targets) {
                Vfx.send(level, VfxType.LOCK_RETICLE, t.position(), Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 1.6f, 40, t.getId());
            }
            if (targets.isEmpty()) {
                Vfx.send(level, VfxType.GROUND_GLYPH, aim.add(0, 0.05, 0), Vec3.ZERO, PowerFx.MAGENTA, PowerFx.PINK, 4f, 40);
            }
            Vfx.sound(level, player.position(), ModSounds.GAUNTLET_SELECT, 1.5f, 0.6f);
        });
        return CastResult.SUCCESS;
    }
}
