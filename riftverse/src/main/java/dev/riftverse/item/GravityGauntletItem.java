package dev.riftverse.item;

import dev.riftverse.entity.GravityWellEntity;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Seize anything in a gravity beam, hold it aloft, hurl it. Sneak-use to open a crushing gravity well. */
public class GravityGauntletItem extends Item {
    public static final String GRAB = "grab";
    public static final double RANGE = 24;

    public GravityGauntletItem(Properties properties) {
        super(properties);
    }

    public static int grabbedId(ItemStack stack) {
        var tag = ItemData.read(stack);
        return tag.contains(GRAB) ? tag.getInt(GRAB) : -1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel server) {
                Vec3 start = player.getEyePosition();
                Vec3 end = start.add(player.getLookAngle().scale(30));
                BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 at = hit.getLocation().add(0, 3.5, 0);
                GravityWellEntity well = new GravityWellEntity(RvEntities.GRAVITY_WELL.get(), server);
                well.moveTo(at.x, at.y, at.z, 0, 0);
                well.setOwner(player);
                server.addFreshEntity(well);
                server.playSound(null, at.x, at.y, at.z, RvSounds.GRAVITY_PULSE.get(), SoundSource.PLAYERS, 1.2f, 1.4f);
                player.getCooldowns().addCooldown(this, 200);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) return;
        int used = getUseDuration(stack, user) - remaining;
        if (used % 24 == 1) server.playSound(null, player.getX(), player.getY(), player.getZ(), RvSounds.GRAVITY_BEAM.get(), SoundSource.PLAYERS, 0.5f, 1.0f);
        Entity target = server.getEntity(grabbedId(stack));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (target == null || !target.isAlive() || target.distanceToSqr(player) > (RANGE + 8) * (RANGE + 8)) {
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, eye.add(look.scale(RANGE)), player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.5),
                    e -> !(e instanceof Player) && !e.isSpectator() && e.isPickable() || e instanceof ItemEntity);
            if (hit != null) {
                int id = hit.getEntity().getId();
                ItemData.edit(stack, t -> t.putInt(GRAB, id));
                server.sendParticles(RvParticles.RING.get().with(0x6FD8FF, 1.5f, 10), hit.getEntity().getX(), hit.getEntity().getY() + hit.getEntity().getBbHeight() * 0.5, hit.getEntity().getZ(), 1, 0, 0, 0, 0);
            } else {
                for (Entity e : level.getEntities(player, new AABB(player.blockPosition()).inflate(8), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                    Vec3 to = player.position().add(0, 1, 0).subtract(e.position());
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(to.normalize().scale(0.18)));
                    e.hurtMarked = true;
                }
            }
            return;
        }
        double hold = 3.0 + target.getBbWidth();
        Vec3 point = eye.add(look.scale(hold));
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 v = point.subtract(center).scale(0.35);
        target.setDeltaMovement(v);
        target.hurtMarked = true;
        target.resetFallDistance();
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!(level instanceof ServerLevel server)) return;
        Entity target = server.getEntity(grabbedId(stack));
        ItemData.edit(stack, t -> t.remove(GRAB));
        if (target == null || !target.isAlive()) return;
        int used = getUseDuration(stack, user) - timeLeft;
        float power = 1.2f + 2.4f * Math.min(1f, used / 40f);
        target.setDeltaMovement(user.getLookAngle().scale(power).add(0, 0.25, 0));
        target.hurtMarked = true;
        server.playSound(null, user.getX(), user.getY(), user.getZ(), RvSounds.GRAVITY_PULSE.get(), SoundSource.PLAYERS, 1.0f, 1.2f);
        server.sendParticles(RvParticles.RING.get().with(0x6FD8FF, 2.5f, 12), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.gravity_gauntlet.tip1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.riftverse.gravity_gauntlet.tip2").withStyle(ChatFormatting.GRAY));
    }
}
