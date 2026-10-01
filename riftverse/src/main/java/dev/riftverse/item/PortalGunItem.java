package dev.riftverse.item;

import dev.riftverse.entity.PortalBoltEntity;
import dev.riftverse.entity.PortalEntity;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dimensional Portal Projector. Tap to fire a blue portal, sneak-tap for orange. Hold to charge a rift-tearing shot
 * (costs a Rift Shard) that opens a passage to another universe - or to the one imprinted on an off-hand Dimensional Key.
 */
public class PortalGunItem extends Item {
    public static final int CHARGE_TICKS = 30;

    public PortalGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public static float charge(LivingEntity entity, ItemStack stack) {
        if (!entity.isUsingItem() || entity.getUseItem() != stack) return 0f;
        return Math.min(1f, (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) CHARGE_TICKS);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack, entity) - remaining;
        if (!level.isClientSide) {
            if (used == CHARGE_TICKS) level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), RvSounds.WARDEN_CHARGE.get(), SoundSource.PLAYERS, 0.5f, 1.8f);
            return;
        }
        if (used < 8) return;
        Vec3 muzzle = entity.getEyePosition().add(entity.getLookAngle().scale(0.9)).add(0, -0.25, 0);
        float k = Math.min(1f, used / (float) CHARGE_TICKS);
        for (int i = 0; i < 2; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 0.8 * (1 - k) + 0.15;
            level.addParticle(RvParticles.INFALL.get().with(k >= 1f ? 0xD8B8FF : 0x8F6BFF, 0.15f + k * 0.15f, 12),
                    muzzle.x + Math.cos(a) * r, muzzle.y + Math.sin(a) * r, muzzle.z + (level.random.nextDouble() - 0.5) * r, muzzle.x, muzzle.y, muzzle.z);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || !(level instanceof ServerLevel server)) return;
        int used = getUseDuration(stack, entity) - timeLeft;
        if (used >= CHARGE_TICKS) {
            if (!player.getAbilities().instabuild) {
                int slot = findShard(player);
                if (slot < 0) {
                    player.displayClientMessage(Component.translatable("message.riftverse.need_shard"), true);
                    return;
                }
                player.getInventory().getItem(slot).shrink(1);
            }
            PortalBoltEntity bolt = new PortalBoltEntity(RvEntities.PORTAL_BOLT.get(), player, server, PortalBoltEntity.RIFT_SLOT);
            ItemStack off = player.getOffhandItem();
            Destination dest = off.is(RvItems.DIMENSIONAL_KEY.get()) ? DimensionalKeyItem.destination(off) : null;
            if (dest != null) bolt.setRiftDestination(dest);
            bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 2.2f, 0f);
            server.addFreshEntity(bolt);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), RvSounds.PORTAL_GUN_FIRE.get(), SoundSource.PLAYERS, 1.0f, 0.55f);
            player.getCooldowns().addCooldown(this, 40);
            return;
        }
        if (player.isShiftKeyDown() && player.getXRot() > 80f) {
            PortalEntity.closeAll(server, player.getUUID());
            server.playSound(null, player.getX(), player.getY(), player.getZ(), RvSounds.RIFT_ENTER.get(), SoundSource.PLAYERS, 0.6f, 1.8f);
            return;
        }
        int slot = player.isShiftKeyDown() ? 1 : 0;
        PortalBoltEntity bolt = new PortalBoltEntity(RvEntities.PORTAL_BOLT.get(), player, server, slot);
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 3.4f, 0f);
        server.addFreshEntity(bolt);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), RvSounds.PORTAL_GUN_FIRE.get(), SoundSource.PLAYERS, 0.9f, slot == 0 ? 1.2f : 0.95f);
        player.getCooldowns().addCooldown(this, 5);
    }

    private static int findShard(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(RvItems.RIFT_SHARD.get())) return i;
        }
        return -1;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.portal_gun.tip1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.riftverse.portal_gun.tip2").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.riftverse.portal_gun.tip3").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.riftverse.portal_gun.tip4").withStyle(ChatFormatting.GRAY));
    }
}
