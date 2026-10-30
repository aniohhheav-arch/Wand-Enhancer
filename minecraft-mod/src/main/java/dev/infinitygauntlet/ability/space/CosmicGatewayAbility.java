package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.PortalNetwork;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Charges, then tears an enormous rupture into the sky above the aimed battlefield and links it to a standard rupture
 * in front of the caster. Anything sent into the near rupture (players, mobs, arrows, TNT...) drops out of the sky.
 */
public final class CosmicGatewayAbility extends Ability {
    private static final int CHARGE_TICKS = 30;
    private static final double MAX_RANGE = 64;
    private static final double MISS_RANGE = 40;

    public CosmicGatewayAbility() {
        super("cosmic_gateway", InfinityStone.SPACE, 1200);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerLevel level = ctx.level();
        ServerPlayer player = ctx.player();
        // aiming past the ground (horizon, open sky) targets the ground beneath a point at mid range
        BlockHitResult aim = ctx.pickBlock(MAX_RANGE);
        Vec3 aimPoint = aim.getType() == HitResult.Type.MISS ? ctx.eye().add(ctx.look().scale(MISS_RANGE)) : aim.getLocation();
        Vec3 target = TeleportUtil.groundBelow(level, aimPoint, 160);
        double height = ServerConfig.GATEWAY_HEIGHT.get();
        // keep the gateway below any ceiling over the target
        HitResult ceiling = level.clip(new ClipContext(target.add(0, 1, 0), target.add(0, height, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double usable = ceiling.getType() == HitResult.Type.MISS ? height : ceiling.getLocation().y - target.y - 1;
        float radius = ServerConfig.GATEWAY_RADIUS.get().floatValue();
        if (usable < radius * 2 + 3) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.gateway.no_sky"));
        }
        Vec3 sky = target.add(0, Math.min(height, usable - radius), 0);
        Vec3 flat = new Vec3(ctx.look().x, 0, ctx.look().z).normalize();
        Vec3 near = player.position().add(flat.scale(4)).add(0, ServerConfig.PORTAL_RADIUS.get() + 0.3, 0);

        if (ctx.gauntlet().getItem() instanceof InfinityGauntletItem gauntlet) {
            gauntlet.playAnimation(player, ctx.gauntlet(), "charge");
        }
        Vfx.sound(level, player.position(), ModSounds.GAUNTLET_CHARGE, 1.5f, 0.8f);
        Vfx.send(level, VfxType.GROUND_GLYPH, target.add(0, 0.05, 0), Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(),
                radius * 1.4f, CHARGE_TICKS + 30);
        Vfx.link(level, ctx.handPos(), sky, InfinityStone.SPACE, CHARGE_TICKS);
        Vfx.camera(level, player.position(), 8, 0.2f, 0.85f, CHARGE_TICKS, 0, 0, 0);

        ServerScheduler.schedule(CHARGE_TICKS, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            int life = ServerConfig.GATEWAY_DURATION_SECONDS.get() * 20;
            RupturePortalEntity gateway = RupturePortalEntity.create(level, sky, radius, player.getUUID(), life,
                    RupturePortalEntity.STYLE_GATEWAY, true);
            RupturePortalEntity entrance = RupturePortalEntity.create(level, near, ServerConfig.PORTAL_RADIUS.get().floatValue(),
                    player.getUUID(), life, RupturePortalEntity.STYLE_PORTAL, false);
            PortalNetwork.get(level.getServer()).link(gateway.getUUID(), entrance.getUUID());
            Vfx.send(level, VfxType.SPHERE_PULSE, sky, Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), radius * 3, 24);
            Vfx.camera(level, sky, 96, 1.2f, 0, 0, 0xBFEFFF, 0.3f, 10);
            Vfx.sound(level, sky, ModSounds.SPACE_SHOCKWAVE, 6f, 0.6f);
        });
        return CastResult.success(Component.translatable("message.infinitygauntlet.gateway.opening"));
    }
}
