package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The World hurls a fan of knives. Thrown during stopped time they hang in the air and fly when time resumes. */
public final class KnifeThrowAbility extends StandAbility {
    private static final int KNIVES = 5;
    private static final float SPREAD_DEGREES = 6f;
    private static final double DAMAGE = 3.5;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        for (int i = 0; i < KNIVES; i++) {
            float offset = (i - (KNIVES - 1) / 2f) * SPREAD_DEGREES;
            Arrow knife = new Arrow(ctx.level(), player, new ItemStack(Items.ARROW), null);
            knife.shootFromRotation(player, player.getXRot(), player.getYRot() + offset, 0f, 2.8f, 0.4f);
            knife.setBaseDamage(DAMAGE);
            knife.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            ctx.level().addFreshEntity(knife);
        }
        MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_KNIFE, 1.0f, 1.0f);
        return true;
    }
}
