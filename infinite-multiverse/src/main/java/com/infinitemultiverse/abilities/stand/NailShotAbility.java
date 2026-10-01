package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Tusk fires a spinning nail. Variants: plain nail, Golden Rotation, and Act 4's Infinite Rotation. Effects on hit live in StandEvents. */
public final class NailShotAbility extends StandAbility {
    public static final String VARIANT_TAG = "infinitemultiverse_tusk_nail";

    public enum Variant {
        NAIL(5.0, 3.4f, null),
        GOLDEN(11.0, 3.8f, "message.infinitemultiverse.shout.golden"),
        INFINITE(6.0, 3.0f, "message.infinitemultiverse.shout.infinite");

        final double damage;
        final float speed;
        final String shout;

        Variant(double damage, float speed, String shout) {
            this.damage = damage;
            this.speed = speed;
            this.shout = shout;
        }
    }

    private final Variant variant;

    public NailShotAbility(Variant variant) {
        this.variant = variant;
    }

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        Arrow nail = new Arrow(ctx.level(), player, new ItemStack(Items.ARROW), null);
        nail.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, variant.speed, 0f);
        nail.setBaseDamage(variant.damage);
        nail.setNoGravity(true);
        nail.pickup = AbstractArrow.Pickup.DISALLOWED;
        nail.getPersistentData().putString(VARIANT_TAG, variant.name());
        ctx.level().addFreshEntity(nail);
        MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_NAIL, 1.0f, variant == Variant.NAIL ? 1.4f : 0.9f);
        if (variant.shout != null) {
            MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable(variant.shout)
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), 24.0);
        }
        return true;
    }
}
