package dev.riftverse.item;

import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The TSA Temporal Pruning Staff.
 * Use: PRUNE (erase the target from the timeline). Sneak-use: TEMPORAL SNARE (freeze everything nearby).
 * Use looking up: TIME DOOR (step through a door to a point ahead). Use looking down: RESET CHARGE (rewind yourself
 * five seconds). Sneak-use looking down: ULTIMATE — FULL TIMELINE PRUNING, a wave that prunes every hostile around.
 */
public class PruningStaffItem extends Item {
    private static final int ORANGE = 0xFF8A20;
    private static final Map<UUID, Deque<Vec3[]>> HISTORY = new HashMap<>();
    private static final Map<UUID, Float> HEALTH = new HashMap<>();

    public PruningStaffItem(Properties p) {
        super(p);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level.isClientSide || !(holder instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        Deque<Vec3[]> h = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        h.addLast(new Vec3[] {p.position(), new Vec3(p.getYRot(), p.getXRot(), p.getHealth())});
        while (h.size() > 10) h.removeFirst();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.consume(stack);
        float pitch = p.getXRot();
        boolean sneak = p.isShiftKeyDown();
        if (sneak && pitch > 60) {
            if (cool(p, 600)) ultimate(p);
        } else if (sneak) {
            if (cool(p, 160)) snare(p);
        } else if (pitch < -60) {
            if (cool(p, 60)) door(p);
        } else if (pitch > 60) {
            if (cool(p, 400)) rewind(p);
        } else if (cool(p, 30)) {
            prune(p);
        }
        return InteractionResultHolder.consume(stack);
    }

    private boolean cool(ServerPlayer p, int ticks) {
        if (p.getCooldowns().isOnCooldown(this)) return false;
        if (!p.isCreative()) p.getCooldowns().addCooldown(this, ticks);
        else p.getCooldowns().addCooldown(this, 8);
        return true;
    }

    private static void beam(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = (int) (d.length() * 3);
        for (int i = 0; i < n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            l.sendParticles(RvParticles.MOTE.get().with(i % 4 == 0 ? 0xFFFFFF : ORANGE, 0.6f, 10), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    /** The pruned don't die, they simply stop having existed: an orange outline that collapses into embers. */
    public static void pruneEntity(ServerLevel l, LivingEntity e) {
        Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
        l.sendParticles(RvParticles.GLITCH.get().with(ORANGE, 1f, 20), c.x, c.y, c.z, 40, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.02);
        l.sendParticles(RvParticles.RING.get().with(ORANGE, 1.5f + e.getBbWidth(), 14), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        l.playSound(null, e.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1f, 1.9f);
        if (e instanceof Player pl) {
            pl.hurt(l.damageSources().magic(), 12f);
            pl.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
        } else if (e.getMaxHealth() > 150f) {
            e.hurt(l.damageSources().magic(), e.getMaxHealth() * 0.15f);
        } else {
            e.discard();
        }
    }

    private void prune(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(40));
        BlockHitResult block = l.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 lim = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, lim, new AABB(eye, lim).inflate(1), e -> e instanceof LivingEntity && e != p && e.isAlive(), 1600);
        Vec3 to = hit != null ? hit.getLocation() : lim;
        beam(l, eye.add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0), to);
        l.playSound(null, p.blockPosition(), RvSounds.BLACK_HOLE_PULL.get(), SoundSource.PLAYERS, 1f, 2f);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) pruneEntity(l, le);
    }

    private void snare(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(RvParticles.RING.get().with(ORANGE, 12f, 30), p.getX(), p.getY() + 0.2, p.getZ(), 2, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1.5f, 0.7f);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12), e -> e != p)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 4, false, true));
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob m) {
                m.setNoAi(true);
                dev.riftverse.multiverse.Scheduler.later(100, () -> {
                    if (m.isAlive()) m.setNoAi(false);
                });
            }
            l.sendParticles(RvParticles.MOTE.get().with(ORANGE, 0.5f, 100), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 10, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0);
        }
    }

    private void door(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 target = p.position().add(look.scale(14));
        BlockHitResult block = l.clip(new ClipContext(p.getEyePosition(), target.add(0, p.getEyeHeight(), 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) target = block.getLocation().subtract(look.scale(1.2)).subtract(0, p.getEyeHeight(), 0);
        for (Vec3 at : new Vec3[] {p.position(), target}) {
            for (int y = 0; y < 3; y++) {
                l.sendParticles(RvParticles.STREAK.get().with(ORANGE, 0.8f, 20), at.x, at.y + y, at.z, 10, 0.5, 0.3, 0.5, 0.01);
            }
            l.sendParticles(RvParticles.RING.get().with(ORANGE, 2f, 16), at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        }
        float yaw = p.getYRot();
        for (Vec3 at : new Vec3[] {p.position().add(look.scale(1.2)), target}) {
            var door = dev.riftverse.registry.RvEntities.TIME_DOOR.get().create(l);
            if (door != null) {
                door.moveTo(at.x, at.y, at.z, yaw, 0);
                l.addFreshEntity(door);
            }
        }
        Vec3 dest = target;
        dev.riftverse.multiverse.Scheduler.later(8, () -> {
            if (p.isRemoved()) return;
            p.teleportTo(dest.x, dest.y, dest.z);
            p.fallDistance = 0;
            l.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.PLAYERS, 1f, 0.6f);
        });
        p.fallDistance = 0;
        l.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.WOODEN_DOOR_OPEN, SoundSource.PLAYERS, 1f, 0.6f);
    }

    private void rewind(ServerPlayer p) {
        Deque<Vec3[]> h = HISTORY.get(p.getUUID());
        if (h == null || h.isEmpty()) {
            p.displayClientMessage(Component.literal("No recorded past to return to.").withColor(ORANGE), true);
            return;
        }
        Vec3[] then = h.peekFirst();
        h.clear();
        ServerLevel l = p.serverLevel();
        RealityOps.cinematic(p, CinematicType.TIME_TRAVEL, 30, p.getEyePosition(), ORANGE, 0xFFC24D, "", "");
        l.sendParticles(RvParticles.RING.get().with(ORANGE, 3f, 14), p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        p.teleportTo(l, then[0].x, then[0].y, then[0].z, (float) then[1].x, (float) then[1].y);
        p.setHealth(Math.max(p.getHealth(), (float) then[1].z));
        p.fallDistance = 0;
        p.clearFire();
        l.sendParticles(RvParticles.RING.get().with(ORANGE, 3f, 14), p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1f, 0.5f);
    }

    private void ultimate(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        for (ServerPlayer o : l.getPlayers(o -> o.distanceToSqr(p) < 48 * 48)) {
            RealityOps.cinematic(o, CinematicType.TSA_ARREST, 70, o.getEyePosition(), ORANGE, 0xFFC24D, "FULL TIMELINE PRUNING", "by order of the Time and Space Authority");
        }
        l.playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 3f, 0.4f);
        for (int ring = 1; ring <= 6; ring++) {
            int r = ring * 4;
            dev.riftverse.multiverse.Scheduler.later(ring * 5, () -> {
                l.sendParticles(RvParticles.RING.get().with(ORANGE, r, 18), p.getX(), p.getY() + 0.3, p.getZ(), 1, 0, 0, 0, 0);
                List<LivingEntity> hit = l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e instanceof Enemy && e.distanceToSqr(p) <= r * r && e.distanceToSqr(p) > (r - 4) * (r - 4));
                for (LivingEntity e : hit) pruneEntity(l, e);
            });
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Use: Prune • Sneak-use: Temporal Snare").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Use looking up: Time Door • looking down: Reset Charge").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Sneak-use looking down: FULL TIMELINE PRUNING").withStyle(ChatFormatting.RED));
    }
}
