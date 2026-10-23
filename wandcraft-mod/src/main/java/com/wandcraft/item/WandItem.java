package com.wandcraft.item;

import com.wandcraft.magic.CastMode;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.util.Fx;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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

public class WandItem extends Item {
    public WandItem(Properties p) {
        super(p.stacksTo(1).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        Spell s = Spells.get(Magic.of(player).selected);
        if (s == null) return InteractionResultHolder.fail(stack);
        String why = Casting.blocker(player, s);
        if (why != null) {
            if (player instanceof ServerPlayer sp) Casting.fail(sp, why);
            return InteractionResultHolder.fail(stack);
        }
        if (s.mode == CastMode.INSTANT) {
            if (player instanceof ServerPlayer sp) Casting.cast(sp, s, 1f);
            return InteractionResultHolder.success(stack);
        }
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer sp && s.mode == CastMode.CHANNEL && !Casting.startChannel(sp, s)) {
            player.stopUsingItem();
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer sp)) return;
        PlayerMagic m = Magic.of(sp);
        Spell s = Spells.get(m.channelSpell != null ? m.channelSpell : m.selected);
        if (s == null) return;
        int used = getUseDuration(stack, entity) - remaining;
        if (s.mode == CastMode.CHANNEL) {
            if (!Casting.tickChannel(sp, s)) sp.stopUsingItem();
        } else if (s.mode == CastMode.CHARGE) {
            float f = Math.min(1f, used / (float) s.chargeTicks);
            ServerLevel sl = sp.serverLevel();
            if (used % 3 == 0) {
                Vec3 tip = sp.getEyePosition().add(sp.getLookAngle().scale(0.9)).add(0, -0.25, 0);
                Fx.burst(sl, Fx.dust(s.category.color, 0.8f + f), tip, 1 + (int) (f * 4), 0.08 + f * 0.1, 0.01);
                if (f >= 1f) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
            if (used % 5 == 0) {
                sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 0.35f + f * 0.4f, 0.6f + f * 1.1f);
            }
            if (used == s.chargeTicks) {
                sl.playSound(null, sp.blockPosition(), SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.8f);
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer sp)) return;
        PlayerMagic m = Magic.of(sp);
        boolean stillHeld = sp.getMainHandItem() == stack;
        if (m.channelSpell != null) {
            Casting.endChannel(sp);
            return;
        }
        Spell s = Spells.get(m.selected);
        if (s == null || s.mode != CastMode.CHARGE) return;
        if (!stillHeld) {
            Casting.fail(sp, "Spell cancelled");
            return;
        }
        int used = getUseDuration(stack, entity) - timeLeft;
        Casting.cast(sp, s, Math.min(1f, used / (float) s.chargeTicks));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Hold the spell wheel key to choose a spell.").withStyle(net.minecraft.ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click to cast. Hold to charge or channel.").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
