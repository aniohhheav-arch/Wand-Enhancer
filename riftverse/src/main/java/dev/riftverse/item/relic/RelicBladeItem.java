package dev.riftverse.item.relic;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/** A universe's own blade: its name, colour and elemental strike come from the reality that forged it. */
public class RelicBladeItem extends SwordItem {
    public RelicBladeItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4, -2.3f)));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Relics.name(stack, "Relic Blade");
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean r = super.hurtEnemy(stack, target, attacker);
        int tier = Relics.tier(stack);
        if (tier > 1) target.hurt(attacker.damageSources().indirectMagic(attacker, attacker), tier * 1.5f);
        Relics.strike(Relics.element(stack), target, attacker, tier);
        return r;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(Relics.origin(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(Relics.elementLine(stack)).withColor(Relics.element(stack).color));
    }
}
