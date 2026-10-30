package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.PlayerPowerData;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.PortalNetwork;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Opens a Dimensional Rupture where the player aims. The first cast anchors a rupture; the next cast (anywhere, in any
 * dimension) opens its twin and links the pair. Sneak + cast collapses all of the player's ruptures.
 */
public final class DimensionalPortalAbility extends Ability {
    public DimensionalPortalAbility() {
        super("dimensional_portal", InfinityStone.SPACE, 40);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        MinecraftServer server = ctx.level().getServer();
        PortalNetwork network = PortalNetwork.get(server);
        PlayerPowerData data = ctx.player().getData(ModAttachments.POWER_DATA);
        if (ctx.sneaking()) {
            List<PortalNetwork.Entry> owned = network.ownedBy(ctx.player().getUUID());
            if (owned.isEmpty()) {
                return CastResult.fail(Component.translatable("message.infinitygauntlet.portal.none"));
            }
            owned.forEach(e -> network.close(server, e.id()));
            data.pendingPortal = null;
            return CastResult.utility(Component.translatable("message.infinitygauntlet.portal.closed_all", owned.size()));
        }

        float radius = ServerConfig.PORTAL_RADIUS.get().floatValue();
        Vec3 center = placement(ctx, radius);
        int lifetime = ServerConfig.PORTAL_DURATION_SECONDS.get() * 20;

        PortalNetwork.Entry pending = data.pendingPortal == null ? null : network.get(data.pendingPortal);
        if (pending != null && pending.partner() == null && pending.dimension() != ctx.level().dimension()
                && !ServerConfig.CROSS_DIMENSION_PORTALS.get()) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.portal.cross_dimension_disabled"));
        }
        if (pending == null || pending.partner() != null) {
            enforcePairLimit(server, network, ctx);
        }

        RupturePortalEntity portal = RupturePortalEntity.create(ctx.level(), center, radius, ctx.player().getUUID(), lifetime,
                RupturePortalEntity.STYLE_PORTAL, false);
        Vfx.link(ctx.level(), ctx.handPos(), center, InfinityStone.SPACE, 14);

        if (pending != null && pending.partner() == null) {
            network.link(pending.id(), portal.getUUID());
            data.pendingPortal = null;
            return CastResult.success(Component.translatable("message.infinitygauntlet.portal.linked"));
        }
        data.pendingPortal = portal.getUUID();
        return CastResult.success(Component.translatable("message.infinitygauntlet.portal.anchored"));
    }

    /** Centres the sphere off the aimed surface so it never intersects the block it was opened against. */
    static Vec3 placement(AbilityContext ctx, float radius) {
        double range = 48;
        BlockHitResult hit = ctx.pickBlock(range);
        if (hit.getType() == HitResult.Type.MISS) {
            return ctx.eye().add(ctx.look().scale(Math.min(range, 12)));
        }
        Direction face = hit.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 c = hit.getLocation().add(normal.scale(radius + 0.25));
        if (face.getAxis().isHorizontal()) {
            // on walls, rest the rupture on the ground so it can be walked into
            c = c.add(0, radius * 0.55, 0);
        }
        return c;
    }

    private static void enforcePairLimit(MinecraftServer server, PortalNetwork network, AbilityContext ctx) {
        List<PortalNetwork.Entry> pairs = network.pairsOwnedBy(ctx.player().getUUID());
        int max = ServerConfig.MAX_PORTAL_PAIRS.get();
        for (int i = 0; i <= pairs.size() - max; i++) {
            network.close(server, pairs.get(i).id());
        }
    }
}
