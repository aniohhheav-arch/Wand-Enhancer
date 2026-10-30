package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.PlayerPowerData;
import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers the caster and nearby allies (players and owned pets) into a great rupture and moves the whole group to the
 * waypoint (sneak + cast sets it, works across dimensions) or, without a waypoint, to the aimed location.
 */
public final class UniversalTeleportationAbility extends Ability {
    private static final int CHARGE_TICKS = 40;

    public UniversalTeleportationAbility() {
        super("universal_teleportation", InfinityStone.SPACE, 1800);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerLevel level = ctx.level();
        ServerPlayer player = ctx.player();
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        if (ctx.sneaking()) {
            data.waypoint = GlobalPos.of(level.dimension(), player.blockPosition());
            Vfx.send(level, VfxType.GROUND_GLYPH, player.position().add(0, 0.05, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                    InfinityStone.SPACE.highlight(), 2.5f, 60);
            Vfx.sound(level, player.position(), ModSounds.GAUNTLET_SELECT, 1f, 0.7f);
            return CastResult.utility(Component.translatable("message.infinitygauntlet.waypoint.set",
                    player.blockPosition().toShortString(), level.dimension().location().toString()));
        }

        ServerLevel destLevel;
        Vec3 destination;
        if (data.waypoint != null) {
            destLevel = level.getServer().getLevel(data.waypoint.dimension());
            destination = Vec3.atBottomCenterOf(data.waypoint.pos());
            if (destLevel == null) {
                return CastResult.fail(Component.translatable("message.infinitygauntlet.waypoint.invalid"));
            }
        } else {
            destLevel = level;
            Vec3 aim = ctx.aimPoint(ServerConfig.GROUP_TELEPORT_RANGE.get());
            destination = TeleportUtil.groundBelow(level, aim.subtract(ctx.look().scale(1.5)).add(0, 1, 0), 32);
        }
        if (destLevel == level && destination.distanceTo(player.position()) < 4) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.waypoint.too_close"));
        }

        double radius = ServerConfig.GROUP_TELEPORT_RADIUS.get();
        if (ctx.gauntlet().getItem() instanceof InfinityGauntletItem gauntlet) {
            gauntlet.playAnimation(player, ctx.gauntlet(), "charge");
        }
        Vec3 origin = player.position();
        Vfx.send(level, VfxType.GROUND_GLYPH, origin.add(0, 0.05, 0), Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(),
                (float) radius, CHARGE_TICKS + 10);
        Vfx.send(level, VfxType.RUPTURE_BURST, origin.add(0, 1.2, 0), Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(),
                (float) radius * 0.8f, CHARGE_TICKS + 12);
        Vfx.sound(level, origin, ModSounds.GAUNTLET_CHARGE, 2f, 0.7f);
        Vfx.sound(level, origin, ModSounds.SPACE_PORTAL_OPEN, 3f, 0.8f);
        Vfx.camera(level, origin, (float) radius + 4, 0.25f, 0.8f, CHARGE_TICKS, 0, 0, 0);

        ServerScheduler.schedule(CHARGE_TICKS, () -> jump(player, level, origin, radius, destLevel, destination));
        return CastResult.success(Component.translatable("message.infinitygauntlet.group_teleport.charging"));
    }

    private static void jump(ServerPlayer player, ServerLevel level, Vec3 origin, double radius, ServerLevel destLevel, Vec3 destination) {
        if (!player.isAlive() || player.level() != level || player.position().distanceTo(origin) > radius + 2) {
            player.displayClientMessage(Component.translatable("message.infinitygauntlet.group_teleport.broken"), true);
            return;
        }
        List<Entity> group = new ArrayList<>();
        group.add(player);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator() && TargetRules.isAlly(player, e))) {
            group.add(e);
        }
        Vfx.send(level, VfxType.IMPLOSION, origin.add(0, 1.2, 0), Vec3.ZERO, InfinityStone.SPACE.color(), 0xDDF6FF, (float) radius, 10);
        Vfx.sound(level, origin, ModSounds.SPACE_PORTAL_CLOSE, 3f, 0.9f);
        Vfx.send(destLevel, VfxType.RUPTURE_BURST, destination.add(0, 1.2, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                InfinityStone.SPACE.highlight(), (float) radius * 0.8f, 30);
        for (int i = 0; i < group.size(); i++) {
            Entity e = group.get(i);
            double angle = i * 2.399963;
            double spread = i == 0 ? 0 : 1.2 + Math.sqrt(i) * 0.9;
            Vec3 spot = destination.add(Math.cos(angle) * spread, 0, Math.sin(angle) * spread);
            Vec3 safe = TeleportUtil.findSafeSpot(destLevel, e, spot, 5);
            if (safe == null) {
                safe = TeleportUtil.findSafeSpot(destLevel, e, destination, 8);
            }
            if (safe == null) {
                continue;
            }
            Vfx.dissolve(level, e, InfinityStone.SPACE);
            if (e instanceof ServerPlayer p) {
                Vfx.transition(p, 36, InfinityStone.SPACE);
            }
            TeleportUtil.teleport(e, destLevel, safe, e.getYRot(), e.getXRot(), Vec3.ZERO);
        }
        Vfx.shockwave(destLevel, destination.add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.SPACE, (float) radius * 2.2f, 18);
        Vfx.sound(destLevel, destination, ModSounds.SPACE_TELEPORT, 3f, 0.8f);
        Vfx.sound(destLevel, destination, ModSounds.SPACE_SHOCKWAVE, 2f, 1.2f);
    }
}
