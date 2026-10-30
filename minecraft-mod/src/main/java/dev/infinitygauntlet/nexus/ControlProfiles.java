package dev.infinitygauntlet.nexus;

import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Creature-specific remote control. Each kind of mob gets its own natural attack (attack key) and special action
 * (use key): zombies punch, skeletons draw and shoot along the view, spiders leap and climb walls, creepers
 * detonate, endermen teleport where they look, iron golems slam the ground, villagers work doors and switches.
 */
public final class ControlProfiles {
    public static boolean isCompatible(Entity e) {
        return e instanceof Mob && e.isAlive() && !(e instanceof EnderDragon) && !(e instanceof WitherBoss);
    }

    /** Translation key suffix for the attack action of {@code e} ("none" if it has none). */
    public static String attackKey(Entity e) {
        if (e instanceof AbstractSkeleton) {
            return "shoot";
        }
        if (e instanceof Creeper) {
            return "detonate";
        }
        if (e instanceof IronGolem) {
            return "smash";
        }
        return e instanceof LivingEntity l && l.getAttribute(Attributes.ATTACK_DAMAGE) != null ? "melee" : "none";
    }

    public static String specialKey(Entity e) {
        if (e instanceof Creeper) {
            return "detonate";
        }
        if (e instanceof EnderMan) {
            return "teleport";
        }
        if (e instanceof Spider) {
            return "leap";
        }
        if (e instanceof IronGolem) {
            return "ground_slam";
        }
        if (e instanceof AbstractVillager) {
            return "interact";
        }
        if (e instanceof Zombie) {
            return "groan";
        }
        return "none";
    }

    /** Fires the attack action. Returns the cooldown in ticks (0 if nothing happened). */
    public static int attack(ServerLevel level, ServerPlayer operator, Mob mob, Vec3 look) {
        switch (attackKey(mob)) {
            case "shoot" -> {
                AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, new ItemStack(Items.ARROW), 1f, mob.getMainHandItem());
                arrow.setPos(mob.getEyePosition().add(look.scale(0.6)));
                arrow.shoot(look.x, look.y, look.z, 1.8f, 1f);
                level.addFreshEntity(arrow);
                mob.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, mob.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1f, 1f);
                return 18;
            }
            case "detonate" -> {
                return detonate(mob) ? 60 : 0;
            }
            case "melee", "smash" -> {
                LivingEntity target = target(mob, operator, look, mob instanceof IronGolem ? 4.0 : 3.2);
                mob.swing(InteractionHand.MAIN_HAND);
                if (target != null) {
                    mob.doHurtTarget(target);
                }
                return mob instanceof IronGolem ? 20 : 12;
            }
            default -> {
                return 0;
            }
        }
    }

    public static int special(ServerLevel level, ServerPlayer operator, Mob mob, Vec3 look) {
        switch (specialKey(mob)) {
            case "detonate" -> {
                return detonate(mob) ? 60 : 0;
            }
            case "teleport" -> {
                BlockHitResult hit = level.clip(new ClipContext(mob.getEyePosition(), mob.getEyePosition().add(look.scale(32)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
                Vec3 at = hit.getLocation().subtract(look.scale(1.0));
                Vec3 from = mob.position();
                if (mob.randomTeleport(at.x, at.y, at.z, true)) {
                    level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1f);
                    mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f);
                    return 30;
                }
                return 5;
            }
            case "leap" -> {
                mob.setDeltaMovement(mob.getDeltaMovement().add(look.x * 1.1, 0.55 + Math.max(0, look.y) * 0.5, look.z * 1.1));
                mob.hurtMarked = true;
                level.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1f, 1.3f);
                return 20;
            }
            case "ground_slam" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(4.5),
                        e -> e != mob && e != operator && e.isAlive())) {
                    Vec3 away = e.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.scale(1.2).add(0, 0.7, 0));
                    e.hurtMarked = true;
                    e.hurt(level.damageSources().mobAttack(mob), 6f);
                }
                mob.swing(InteractionHand.MAIN_HAND);
                level.broadcastEntityEvent(mob, (byte) 4);
                Vfx.shockwave(level, mob.position().add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.MIND, 5f, 14);
                level.playSound(null, mob.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 1.5f, 0.6f);
                return 50;
            }
            case "interact" -> {
                return interact(level, mob, look) ? 8 : 0;
            }
            case "groan" -> {
                level.playSound(null, mob.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.8f);
                return 30;
            }
            default -> {
                return 0;
            }
        }
    }

    private static boolean detonate(Mob mob) {
        if (!(mob instanceof Creeper creeper) || !ServerConfig.POSSESSION_CREEPER_DETONATION.get() || creeper.isIgnited()) {
            return false;
        }
        creeper.ignite();
        return true;
    }

    /** Villager interactions: doors, trapdoors, fence gates, buttons, levers and bells in front of it. */
    private static boolean interact(ServerLevel level, Mob mob, Vec3 look) {
        BlockHitResult hit = level.clip(new ClipContext(mob.getEyePosition(), mob.getEyePosition().add(look.scale(4.5)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mob));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean done = true;
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(mob, level, state, pos, !state.getValue(BlockStateProperties.OPEN));
        } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.cycle(BlockStateProperties.OPEN), 10);
            level.gameEvent(mob, GameEvent.BLOCK_OPEN, pos);
        } else if (state.getBlock() instanceof ButtonBlock button && !state.getValue(BlockStateProperties.POWERED)) {
            button.press(state, level, pos, null);
        } else if (state.getBlock() instanceof LeverBlock lever) {
            lever.pull(state, level, pos, null);
        } else if (state.getBlock() instanceof BellBlock bell) {
            bell.attemptToRing(mob, level, pos, hit.getDirection());
        } else {
            done = false;
        }
        if (done) {
            level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            Vfx.send(level, VfxType.SPARK_BURST, hit.getLocation(), Vec3.ZERO, InfinityStone.MIND.color(), InfinityStone.MIND.highlight(), 0.4f, 8);
        }
        return done;
    }

    @Nullable
    private static LivingEntity target(Mob mob, ServerPlayer operator, Vec3 look, double reach) {
        Vec3 eye = mob.getEyePosition();
        Vec3 end = eye.add(look.scale(reach));
        AABB box = mob.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mob.level(), mob, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != operator && e.isPickable(), 0.3f);
        return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
    }

    private ControlProfiles() {
    }
}
