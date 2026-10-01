package dev.riftverse.item;

import dev.riftverse.event.CommonEvents;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.world.MaterialSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reality manipulation. Transmute mode rewrites a sphere of matter into the substance of another universe; Stasis mode
 * freezes every creature and projectile around you in a bubble of stopped time.
 */
public class RealityShaperItem extends Item {
    public RealityShaperItem(Properties properties) {
        super(properties);
    }

    public static int mode(ItemStack stack) {
        return ItemData.read(stack).getInt("mode");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            int next = (mode(stack) + 1) % 2;
            ItemData.edit(stack, t -> t.putInt("mode", next));
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.riftverse.reality_shaper.mode" + next), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (mode(stack) == 1) {
            if (level instanceof ServerLevel server) stasis(server, player);
            player.getCooldowns().addCooldown(this, 300);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || player.isShiftKeyDown() || mode(ctx.getItemInHand()) != 0) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        UniverseSpec spec = CommonEvents.specOf(player);
        Archetype source = spec != null ? spec.materials : Archetype.byId(server.random.nextInt(Archetype.values().length));
        MaterialSet m = MaterialSet.of(source);
        BlockPos c = ctx.getClickedPos();
        int r = 3;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distSqr(c) > r * r + 1) continue;
            BlockState s = server.getBlockState(p);
            if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(server, p) < 0 || !s.getFluidState().isEmpty()) continue;
            boolean exposed = server.getBlockState(p.above()).isAir();
            BlockState to = exposed ? m.surface : (server.random.nextInt(9) == 0 ? m.accent : m.stone);
            if (to.isAir()) continue;
            server.setBlockAndUpdate(p, to);
            if (server.random.nextInt(4) == 0) server.sendParticles(RvParticles.SPARK.get().with(source.signatureColor, 0.35f, 14), p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.05);
        }
        server.sendParticles(RvParticles.RING.get().with(source.signatureColor, 4.5f, 16), c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 1, 0, 0, 0, 0);
        server.playSound(null, c, RvSounds.ABILITY_ACTIVATE.get(), SoundSource.PLAYERS, 1.0f, 0.7f);
        player.getCooldowns().addCooldown(this, 40);
        ctx.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(ctx.getHand()));
        return InteractionResult.CONSUME;
    }

    private static void stasis(ServerLevel level, Player player) {
        Vec3 c = player.position();
        for (Entity e : level.getEntities(player, new AABB(c, c).inflate(10))) {
            if (e instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 9, false, true));
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 4, false, true));
                living.setDeltaMovement(Vec3.ZERO);
                living.hurtMarked = true;
            } else if (e instanceof Projectile proj) {
                proj.setDeltaMovement(Vec3.ZERO);
                proj.setNoGravity(true);
                proj.hurtMarked = true;
            }
        }
        level.sendParticles(RvParticles.RING.get().with(0xA0E8FF, 10f, 30), c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.DUST.get().with(0xA0E8FF, 1.2f, 60), c.x, c.y + 1, c.z, 40, 5, 2, 5, 0.01);
        level.playSound(null, c.x, c.y, c.z, RvSounds.GRAVITY_PULSE.get(), SoundSource.PLAYERS, 1.2f, 2.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.reality_shaper.mode" + mode(stack)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.riftverse.reality_shaper.tip").withStyle(ChatFormatting.GRAY));
    }
}
