package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Groundbreaker: the caster leaps, hangs at the apex and hammers the gauntlet into the ground. Three rings of force
 * roll outward one after another, splitting the earth into glowing fissures and throwing chunks of it skyward; the
 * innermost ring pops enemies into the air, the outer ones bowl them over.
 */
public final class GroundbreakerAbility extends Ability {
    private static final float[] RING_RADIUS = {5f, 9f, 13f};
    private static final float[] RING_DAMAGE = {14f, 9f, 6f};

    public GroundbreakerAbility() {
        super("groundbreaker", InfinityStone.POWER, 200);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        PowerFx.pose(player, PowerPose.SLAM, 26);
        PowerFx.anim(ctx, "power_slam");
        player.setDeltaMovement(ctx.look().multiply(0.35, 0, 0.35).add(0, 1.05, 0));
        player.hurtMarked = true;
        Vfx.sound(level, player.position(), ModSounds.POWER_WHOOSH, 2f, 0.6f);
        Vfx.send(level, VfxType.SHOCKWAVE, player.position().add(0, 0.1, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, 2.5f, 8);
        Vfx.send(level, VfxType.CHARGE, player.position().add(0, 1, 0), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 2f, 10);
        boolean[] landed = {false};
        ServerScheduler.schedule(10, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            player.setDeltaMovement(0, -2.8, 0);
            player.hurtMarked = true;
            Vfx.send(level, VfxType.ENTITY_TRAIL, player.position(), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 1.2f, 10, player.getId());
            Vfx.sound(level, player.position(), ModSounds.POWER_LAUNCH, 1.5f, 0.5f);
        });
        for (int i = 10; i < 34; i++) {
            int tick = i;
            ServerScheduler.schedule(i + 1, () -> {
                if (landed[0] || !player.isAlive() || player.level() != level) {
                    return;
                }
                player.fallDistance = 0;
                if (player.onGround() || tick == 33) {
                    landed[0] = true;
                    slam(player, level, player.position());
                }
            });
        }
        return CastResult.SUCCESS;
    }

    private static void slam(ServerPlayer player, ServerLevel level, Vec3 feet) {
        Vec3 ground = feet.add(0, 0.05, 0);
        PowerFx.cracks(level, ground, 14f, 200, PowerFx.PURPLE);
        PowerFx.crater(level, feet, 3f);
        Vfx.send(level, VfxType.POWER_EXPLOSION, feet.add(0, 0.3, 0), Vec3.ZERO, 0x5A1AA8, 0xE6B0FF, 1.8f, 16);
        Vfx.camera(level, feet, 48, 1.5f, 0.88f, 8, 0xD8A0FF, 0.25f, 5);
        Vfx.sound(level, feet, ModSounds.POWER_GROUND_CRACK, 4f, 0.8f);
        Vfx.sound(level, feet, ModSounds.POWER_PUNCH_IMPACT, 3f, 0.55f);
        CombatFx.hitstop(level, feet, 6, 3, player);
        for (int ring = 0; ring < RING_RADIUS.length; ring++) {
            int r = ring;
            ServerScheduler.schedule(1 + ring * 6, () -> ring(player, level, feet, r));
        }
    }

    private static void ring(ServerPlayer player, ServerLevel level, Vec3 feet, int ring) {
        float outer = RING_RADIUS[ring];
        float inner = ring == 0 ? 0 : RING_RADIUS[ring - 1];
        int color = ring == 0 ? PowerFx.MAGENTA : ring == 1 ? PowerFx.PURPLE : 0x6A2AD0;
        Vfx.send(level, VfxType.SHOCKWAVE, feet.add(0, 0.15, 0), new Vec3(0, 1, 0), color, PowerFx.PINK, outer + 2, 12 + ring * 3);
        for (int k = 0; k < 6 + ring * 3; k++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = inner + (outer - inner) * level.random.nextDouble();
            Vec3 at = feet.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            PowerFx.debris(level, at, 0.7f + 0.2f * (2 - ring), 3);
        }
        Vfx.sound(level, feet, ModSounds.POWER_EXPLOSION, 2.5f, 0.7f + ring * 0.2f);
        Vfx.shake(level, feet, 40, 0.8f - ring * 0.2f);
        for (LivingEntity e : CombatFx.targets(player, feet.add(0, 1, 0), outer)) {
            Vec3 flat = e.position().subtract(feet).multiply(1, 0, 1);
            if (flat.length() < inner - 0.5) {
                continue;
            }
            Vec3 away = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
            Vec3 push = switch (ring) {
                case 0 -> away.scale(0.35).add(0, 1.25, 0);
                case 1 -> away.scale(1.0).add(0, 0.8, 0);
                default -> away.scale(1.4).add(0, 0.45, 0);
            };
            CombatFx.strike(player, e, RING_DAMAGE[ring], push, 1.4f - ring * 0.3f, CombatFx.DeathStyle.RAGDOLL, false);
            CombatFx.react(level, e, ring == 0 ? CombatFx.Reaction.SUSPEND : ring == 1 ? CombatFx.Reaction.TUMBLE : CombatFx.Reaction.STAGGER, away,
                    ring == 0 ? 18 : 14);
        }
    }
}
