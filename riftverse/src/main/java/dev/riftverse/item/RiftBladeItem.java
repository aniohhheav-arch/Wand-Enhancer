package dev.riftverse.item;

import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.network.PacketDistributor;

/** A blade forged from a stabilised rift. Strikes tear reality; right-click dashes through a micro-rift. */
public class RiftBladeItem extends SwordItem {
    public static final Tier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 9.0f, 4.0f, 18,
            () -> Ingredient.of(RvItems.EXOTIC_INGOT.get()));

    public RiftBladeItem(Properties properties) {
        super(TIER, properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return SwordItem.createAttributes(TIER, 4, -2.2f);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Level level = attacker.level();
        if (level instanceof ServerLevel server) {
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            server.sendParticles(RvParticles.STREAK.get().with(0xB070FF, 0.5f, 8), c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.4);
            server.playSound(null, c.x, c.y, c.z, RvSounds.BLADE_SLASH.get(), SoundSource.PLAYERS, 0.8f, 0.9f + level.random.nextFloat() * 0.3f);
            if (level.random.nextFloat() < 0.22f) {
                server.sendParticles(RvParticles.RING.get().with(0xD070FF, 3.5f, 12), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                for (Entity e : level.getEntities(attacker, new AABB(c, c).inflate(3.5), e -> e instanceof LivingEntity && e != attacker)) {
                    e.hurt(level.damageSources().indirectMagic(attacker, attacker), 4.0f);
                    ((LivingEntity) e).addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 1));
                }
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 end = start.add(dir.scale(10));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(dir.scale(0.8));
        Vec3 feet = target.subtract(0, player.getEyeHeight(), 0);
        for (int i = 0; i < 16; i++) {
            Vec3 p = player.position().lerp(feet, i / 16.0).add(0, 1, 0);
            server.sendParticles(RvParticles.STREAK.get().with(0xC080FF, 0.45f, 10), p.x, p.y, p.z, 2, 0.15, 0.3, 0.15, 0.05);
        }
        AABB path = player.getBoundingBox().minmax(player.getBoundingBox().move(feet.subtract(player.position()))).inflate(0.8);
        for (Entity e : level.getEntities(player, path, e -> e instanceof LivingEntity)) {
            e.hurt(level.damageSources().playerAttack(player), 7.0f);
        }
        sp.connection.teleport(feet.x, feet.y, feet.z, player.getYRot(), player.getXRot());
        sp.resetFallDistance();
        server.playSound(null, feet.x, feet.y, feet.z, RvSounds.BLADE_DASH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        PacketDistributor.sendToPlayer(sp, new Payloads.Shake(0.35f, 10, 0.25f, 0xC080FF));
        player.getCooldowns().addCooldown(this, 40);
        stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.rift_blade.tip1").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.riftverse.rift_blade.tip2").withStyle(ChatFormatting.GRAY));
    }
}
