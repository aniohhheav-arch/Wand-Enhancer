package dev.riftverse.item.relic;

import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.registry.RvSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A universe's own gun: fires bolts in its world's colour carrying its world's elemental nature. */
public class RelicBlasterItem extends Item {
    public RelicBlasterItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Relics.name(stack, "Relic Blaster");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int tier = Relics.tier(stack);
            EnergyBoltEntity bolt = EnergyBoltEntity.create(level, player, Relics.color(stack), 0.35f + tier * 0.06f, 4f + tier * 2.5f);
            bolt.setElement(Relics.element(stack).ordinal(), tier);
            Vec3 look = player.getLookAngle();
            bolt.shoot(look.x, look.y, look.z, 2.2f, 0.5f);
            level.addFreshEntity(bolt);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), RvSounds.ENERGY_FIRE.get(), SoundSource.PLAYERS, 0.8f, 1.6f - tier * 0.1f);
            player.getCooldowns().addCooldown(this, Math.max(4, 12 - tier * 2));
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(Relics.origin(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(Relics.elementLine(stack)).withColor(Relics.element(stack).color));
    }
}
