package com.infinitemultiverse.cosmic.portal;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModEntities;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Right-click: blue portal. Sneak + right-click: orange portal. Portals need a flat 1×2 surface with open space in front. */
public final class PortalGunItem extends Item {
    private static final double RANGE = 96;

    public PortalGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(gun);
        }
        if (!MultiverseConfig.isSystemEnabled(MultiverseSystem.PORTALS)) {
            return InteractionResultHolder.fail(gun);
        }
        int color = player.isShiftKeyDown() ? PortalEntity.ORANGE : PortalEntity.BLUE;
        int rgb = color == PortalEntity.BLUE ? 0x3DA0FF : 0xFF8A1A;
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 muzzle = eye.add(player.getLookAngle().scale(0.6)).subtract(0, 0.25, 0);
        player.getCooldowns().addCooldown(this, 6);
        if (hit.getType() == HitResult.Type.MISS) {
            Cinematics.scene(server, SceneIds.PORTAL_SHOT, muzzle, player.getLookAngle().scale(RANGE), rgb, 8, player, 0f);
            return InteractionResultHolder.fail(gun);
        }
        Cinematics.scene(server, SceneIds.PORTAL_SHOT, muzzle, hit.getLocation().subtract(muzzle), rgb, 8, player, 1f);
        Placement place = placement(server, hit, player);
        if (place == null) {
            sp.displayClientMessage(Component.translatable("message.infinitemultiverse.portal_no_surface").withStyle(ChatFormatting.GRAY), true);
            MultiverseVfx.sound(server, hit.getLocation(), ModSounds.UI_DENIED, 0.6f, 1.2f);
            return InteractionResultHolder.fail(gun);
        }
        PortalNetwork net = PortalNetwork.get(server.getServer());
        PortalNetwork.End old = net.get(player.getUUID(), color);
        if (old != null) {
            ServerLevel oldLevel = server.getServer().getLevel(old.dimension());
            if (oldLevel != null && oldLevel.getEntity(old.entity()) instanceof PortalEntity e) {
                e.discard();
            }
        }
        PortalEntity portal = ModEntities.PORTAL.get().create(server);
        if (portal == null) {
            return InteractionResultHolder.fail(gun);
        }
        portal.setPos(place.center.x, place.center.y, place.center.z);
        portal.setup(player.getUUID(), color, place.facing, place.up);
        server.addFreshEntity(portal);
        net.set(player.getUUID(), color, new PortalNetwork.End(server.dimension(), place.center, place.facing, place.up, portal.getUUID()));
        MultiverseVfx.sound(server, place.center, ModSounds.PHASE_STEP_DEPART, 0.9f, color == PortalEntity.BLUE ? 1.3f : 1.0f);
        return InteractionResultHolder.consume(gun);
    }

    private record Placement(Vec3 center, Direction facing, Direction up) {
    }

    /** Finds a 1×2 patch of solid face around the hit with air in front; null if none. */
    private static Placement placement(ServerLevel level, BlockHitResult hit, Player player) {
        Direction facing = hit.getDirection();
        BlockPos wall = hit.getBlockPos();
        Direction up = facing.getAxis() == Direction.Axis.Y ? player.getDirection() : Direction.UP;
        if (facing == Direction.DOWN) {
            up = player.getDirection();
        }
        Vec3 face = Vec3.atCenterOf(wall).add(PortalEntity.vec(facing).scale(0.5 + 0.02));
        // Wall portals slide down to stand on the floor when there is one within a couple of blocks, so you can walk in.
        if (facing.getAxis() != Direction.Axis.Y) {
            for (int drop = 0; drop < 3; drop++) {
                BlockPos below = wall.below();
                if (solid(level, below, facing) && open(level, below.relative(facing))) {
                    wall = below;
                } else {
                    break;
                }
            }
        }
        for (int shift : new int[]{0, -1}) {
            BlockPos a = wall.relative(up, shift), b = a.relative(up);
            if (solid(level, a, facing) && solid(level, b, facing) && open(level, a.relative(facing)) && open(level, b.relative(facing))) {
                Vec3 base = Vec3.atCenterOf(a).add(PortalEntity.vec(facing).scale(0.5 + 0.02));
                return new Placement(base.add(PortalEntity.vec(up).scale(0.5)), facing, up);
            }
        }
        return null;
    }

    private static boolean solid(ServerLevel level, BlockPos pos, Direction face) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, face);
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infinitemultiverse.portal_gun.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
