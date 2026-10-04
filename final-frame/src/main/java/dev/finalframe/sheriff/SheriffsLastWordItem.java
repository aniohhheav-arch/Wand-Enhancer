package dev.finalframe.sheriff;

import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import dev.finalframe.finisher.FinisherDefinition;
import dev.finalframe.finisher.FinisherWeaponItem;
import dev.finalframe.registry.FFRegistry;
import dev.finalframe.registry.FFSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Sheriff's Last Word. Works as a six-shot hitscan revolver in ordinary play; sneak-using it on a
 * foe from behind starts the cinematic finisher (see {@link dev.finalframe.finisher.FinisherManager}).
 */
public final class SheriffsLastWordItem extends FinisherWeaponItem {
    public static final int CAPACITY = 6;
    public static final int FIRE_COOLDOWN = 7;
    public static final int RELOAD_TICKS = 32;
    public static final double RANGE = 64.0;
    public static final ResourceKey<DamageType> REVOLVER = ResourceKey.create(Registries.DAMAGE_TYPE, FinalFrame.id("revolver"));
    private static final int TITLE_COLOR = 0xE8B04A;

    /** Client hook for recoil/reload animation; installed by the client entry point. */
    public static volatile UseListener clientListener = (player, fired) -> {
    };

    public SheriffsLastWordItem(Properties properties) {
        super(properties);
    }

    @Override
    public FinisherDefinition finisher() {
        return SheriffFinisher.INSTANCE;
    }

    public static int rounds(ItemStack stack) {
        return stack.getOrDefault(FFRegistry.ROUNDS.get(), CAPACITY);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int rounds = rounds(stack);
        boolean fire = rounds > 0;
        if (level.isClientSide) {
            clientListener.onUse(player, fire);
            return InteractionResultHolder.consume(stack);
        }
        if (fire) {
            shoot((ServerLevel) level, player);
            stack.set(FFRegistry.ROUNDS.get(), rounds - 1);
            player.getCooldowns().addCooldown(this, FIRE_COOLDOWN);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), FFSounds.DRY_FIRE.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), FFSounds.RELOAD.value(), SoundSource.PLAYERS, 0.9f, 1.0f);
            stack.set(FFRegistry.ROUNDS.get(), CAPACITY);
            player.getCooldowns().addCooldown(this, RELOAD_TICKS);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static void shoot(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, stop, sweep,
            e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && e != player, 0.3f);
        if (entityHit != null) {
            stop = entityHit.getLocation();
        }

        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = eye.add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.22, 0);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, FFSounds.SHOT.value(), SoundSource.PLAYERS, 2.5f,
            0.95f + level.random.nextFloat() * 0.1f);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.02, 0.02, 0.02, 0.02);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.2);
        Vec3 dir = stop.subtract(muzzle);
        double len = dir.length();
        dir = dir.normalize();
        for (double d = 0.4; d < Math.min(len, 24); d += 0.5) {
            Vec3 p = muzzle.add(dir.scale(d));
            level.sendParticles(SheriffFinisher.EMBER_DUST, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(REVOLVER);
            target.hurt(new DamageSource(type, player, player), FFConfig.get(FFConfig.SHOT_DAMAGE).floatValue());
            level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 8, 0.1, 0.1, 0.1, 0.3);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(block.getBlockPos());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), stop.x, stop.y, stop.z, 10, 0.05, 0.05, 0.05, 0.15);
            level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 3, 0.02, 0.02, 0.02, 0.01);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(s -> s.withColor(TITLE_COLOR).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.finalframe.sheriffs_last_word.quote").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.empty());
        lines.add(Component.translatable("item.finalframe.sheriffs_last_word.class").withStyle(s -> s.withColor(TITLE_COLOR)));
        lines.add(Component.translatable("item.finalframe.sheriffs_last_word.how").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("item.finalframe.sheriffs_last_word.rounds", rounds(stack), CAPACITY).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @FunctionalInterface
    public interface UseListener {
        void onUse(Player player, boolean fired);
    }
}
