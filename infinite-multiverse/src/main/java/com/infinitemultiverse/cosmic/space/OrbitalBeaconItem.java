package com.infinitemultiverse.cosmic.space;

import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Orbital Beacon: on the ground, a launch cutscene carries you up through the sky into Orbit (a station deck is
 * built under you). In Orbit, a fiery re-entry cutscene brings you back down, slow-falling, above the same spot.
 */
public final class OrbitalBeaconItem extends Item {
    private static final int LAUNCH_AT = 44;
    private static final int REENTRY_AT = 36;

    public OrbitalBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 100);
        if (Space.inSpace(sp)) {
            reenter(sp, server);
        } else {
            launch(sp, server);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static void launch(ServerPlayer player, ServerLevel from) {
        ServerLevel orbit = player.server.getLevel(Space.ORBIT);
        if (orbit == null) {
            return;
        }
        Cinematics.scene(from, SceneIds.LAUNCH, player.position(), player.getLookAngle(), 0xFF9A3A, 60, player, 1f);
        MultiverseVfx.sound(from, player.position(), ModSounds.SHOCKWAVE, 1.6f, 0.5f);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LAUNCH_AT + 20, 4, false, false));
        StandScheduler.repeat(12, 1, LAUNCH_AT - 12, i -> {
            if (!player.isAlive() || player.level() != from) {
                return false;
            }
            player.setDeltaMovement(0, 0.3 + i * 0.06, 0);
            player.hurtMarked = true;
            return true;
        });
        StandScheduler.later(LAUNCH_AT, () -> {
            if (!player.isAlive() || player.level() != from) {
                return;
            }
            int x = player.getBlockX(), z = player.getBlockZ();
            Space.ensureStation(orbit, x, z);
            player.teleportTo(orbit, x + 0.5, Space.STATION_Y + 1, z + 0.5, Set.<RelativeMovement>of(), player.getYRot(), 0);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.displayClientMessage(Component.translatable("message.infinitemultiverse.orbit_arrived").withStyle(ChatFormatting.AQUA), true);
            if (!Space.wearingSuit(player)) {
                player.sendSystemMessage(Component.translatable("message.infinitemultiverse.no_suit_warning").withStyle(ChatFormatting.RED));
            }
        });
    }

    private static void reenter(ServerPlayer player, ServerLevel orbit) {
        ServerLevel home = player.server.overworld();
        Cinematics.scene(orbit, SceneIds.REENTRY, player.position(), player.getLookAngle(), 0xFF6A1A, 50, player, 1f);
        MultiverseVfx.sound(orbit, player.position(), ModSounds.TEMPORAL_DRAG, 1.4f, 0.5f);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, REENTRY_AT + 30, 4, false, false));
        StandScheduler.later(REENTRY_AT, () -> {
            if (!player.isAlive() || player.level() != orbit) {
                return;
            }
            int x = player.getBlockX(), z = player.getBlockZ();
            home.getChunk(x >> 4, z >> 4);
            int y = home.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 60;
            player.teleportTo(home, x + 0.5, y, z + 0.5, Set.<RelativeMovement>of(), player.getYRot(), 30);
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
            player.fallDistance = 0;
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infinitemultiverse.orbital_beacon.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
