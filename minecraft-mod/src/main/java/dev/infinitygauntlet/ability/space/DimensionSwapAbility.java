package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.PlayerPowerData;
import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Exchanges the positions of two things through paired ruptures.
 * <ul>
 *     <li>Sneak + cast at a block: place a location marker.</li>
 *     <li>Cast at an entity with no anchor: mark it as the swap anchor (no cooldown).</li>
 *     <li>Cast at an entity with an anchor: swap it with the anchored entity, or with the location marker
 *     (the marker moves to where the entity stood).</li>
 *     <li>Cast at nothing with an anchored entity: swap yourself with it.</li>
 * </ul>
 */
public final class DimensionSwapAbility extends Ability {
    public DimensionSwapAbility() {
        super("dimension_swap", InfinityStone.SPACE, 100);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerLevel level = ctx.level();
        PlayerPowerData data = ctx.player().getData(ModAttachments.POWER_DATA);
        LivingEntity target = ctx.pickEntity(48, e -> e instanceof LivingEntity && e.isAlive()) instanceof LivingEntity l ? l : null;
        if (target instanceof net.minecraft.world.entity.player.Player other && !TargetRules.isAlly(ctx.player(), other)
                && !TargetRules.canTarget(ctx.player(), other)) {
            target = null;
        }

        if (ctx.sneaking() && target == null) {
            Vec3 at = ctx.aimPoint(48);
            data.swapMarker = GlobalPos.of(level.dimension(), BlockPos.containing(at.add(0, 0.5, 0)));
            Vfx.send(level, VfxType.GROUND_GLYPH, Vec3.atBottomCenterOf(data.swapMarker.pos()), Vec3.ZERO,
                    InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), 1.4f, 60);
            return CastResult.utility(Component.translatable("message.infinitygauntlet.swap.marker_set"));
        }

        Entity anchor = data.swapEntity == null ? null : level.getEntity(data.swapEntity);
        if (anchor != null && !anchor.isAlive()) {
            anchor = null;
            data.swapEntity = null;
        }

        if (target != null) {
            if (anchor != null && anchor != target) {
                swap(level, target, anchor);
                data.swapEntity = null;
                return CastResult.success(Component.translatable("message.infinitygauntlet.swap.swapped"));
            }
            if (data.swapMarker != null && data.swapMarker.dimension() == level.dimension() && anchor == null) {
                Vec3 marker = Vec3.atBottomCenterOf(data.swapMarker.pos());
                Vec3 old = target.position();
                if (!moveWithPortals(level, target, marker)) {
                    return CastResult.fail(Component.translatable("message.infinitygauntlet.swap.blocked"));
                }
                data.swapMarker = GlobalPos.of(level.dimension(), BlockPos.containing(old));
                Vfx.link(level, old.add(0, 1, 0), marker.add(0, 1, 0), InfinityStone.SPACE, 16);
                return CastResult.success(Component.translatable("message.infinitygauntlet.swap.swapped"));
            }
            data.swapEntity = target.getUUID();
            Vfx.send(level, VfxType.GROUND_GLYPH, target.position(), Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(),
                    target.getBbWidth() + 0.8f, 40);
            Vfx.sound(level, target.position(), ModSounds.GAUNTLET_SELECT, 1f, 0.8f);
            return CastResult.utility(Component.translatable("message.infinitygauntlet.swap.anchored", target.getDisplayName()));
        }

        if (anchor != null) {
            swap(level, ctx.player(), anchor);
            data.swapEntity = null;
            return CastResult.success(Component.translatable("message.infinitygauntlet.swap.swapped"));
        }
        return CastResult.fail(Component.translatable("message.infinitygauntlet.swap.no_target"));
    }

    private static void swap(ServerLevel level, Entity a, Entity b) {
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        Vfx.link(level, pa.add(0, a.getBbHeight() / 2, 0), pb.add(0, b.getBbHeight() / 2, 0), InfinityStone.SPACE, 16);
        Vec3 safeA = TeleportUtil.findSafeSpot(level, a, pb, 3);
        Vec3 safeB = TeleportUtil.findSafeSpot(level, b, pa, 3);
        if (safeA == null || safeB == null) {
            return;
        }
        portalFx(level, a, pa);
        portalFx(level, b, pb);
        TeleportUtil.teleport(a, level, safeA, a.getYRot(), a.getXRot(), Vec3.ZERO);
        TeleportUtil.teleport(b, level, safeB, b.getYRot(), b.getXRot(), Vec3.ZERO);
        Vfx.sound(level, pa, ModSounds.SPACE_SWAP, 1.5f, 1f);
        Vfx.sound(level, pb, ModSounds.SPACE_SWAP, 1.5f, 1f);
    }

    private static boolean moveWithPortals(ServerLevel level, Entity e, Vec3 to) {
        Vec3 safe = TeleportUtil.findSafeSpot(level, e, to, 3);
        if (safe == null) {
            return false;
        }
        portalFx(level, e, e.position());
        portalFx(level, e, safe);
        Vfx.sound(level, e.position(), ModSounds.SPACE_SWAP, 1.5f, 1f);
        TeleportUtil.teleport(e, level, safe, e.getYRot(), e.getXRot(), Vec3.ZERO);
        Vfx.sound(level, safe, ModSounds.SPACE_SWAP, 1.5f, 1.1f);
        return true;
    }

    private static void portalFx(ServerLevel level, Entity e, Vec3 feet) {
        Vfx.send(level, VfxType.RUPTURE_BURST, feet.add(0, e.getBbHeight() / 2, 0), Vec3.ZERO, InfinityStone.SPACE.color(),
                InfinityStone.SPACE.highlight(), Math.max(0.9f, e.getBbHeight() * 0.75f), 24);
        Vfx.send(level, VfxType.DISSOLVE, feet, new Vec3(e.getBbWidth(), e.getBbHeight(), 0), InfinityStone.SPACE.color(),
                InfinityStone.SPACE.highlight(), 1f, 14);
    }
}
