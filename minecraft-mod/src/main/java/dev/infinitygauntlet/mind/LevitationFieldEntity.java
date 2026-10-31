package dev.infinitygauntlet.mind;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.entity.TimedEffectEntity;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Levitation Field: a golden sigil hangs over a wide circle in which everything slowly rises and drifts in orbit,
 * along with chunks of the ground. Recasting releases everything at once.
 */
public class LevitationFieldEntity extends TimedEffectEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mind_sigil.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<Entity> held = new HashSet<>();
    /** Ground blocks that float in orbit (visual). */
    private int[] floating = new int[0];

    public LevitationFieldEntity(EntityType<? extends LevitationFieldEntity> type, Level level) {
        super(type, level);
    }

    public int[] floating() {
        return floating;
    }

    public void sampleGround(ServerLevel level) {
        int[] states = new int[8];
        int n = 0;
        for (int i = 0; i < 24 && n < states.length; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * visualRadius;
            BlockPos pos = BlockPos.containing(getX() + Math.cos(a) * r, getY() - 0.5, getZ() + Math.sin(a) * r);
            BlockState s = level.getBlockState(pos);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                states[n++] = Block.getId(s);
            }
        }
        floating = java.util.Arrays.copyOf(states, n);
    }

    private boolean affects(ServerPlayer owner, Entity e) {
        if (e == owner || e instanceof TimedEffectEntity || e.isSpectator()) {
            return false;
        }
        if (e instanceof ItemEntity) {
            return true;
        }
        if (e instanceof LivingEntity) {
            return owner == null || TargetRules.canTarget(owner, e);
        }
        return false;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        ServerPlayer owner = owner();
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.MIND_FIELD, 2.5f, 1f);
        }
        if (tickCount % 60 == 1) {
            Vfx.sound(level, position(), ModSounds.MIND_FIELD, 1.2f, 0.9f);
        }
        AABB box = new AABB(position(), position()).inflate(visualRadius, 0, visualRadius).expandTowards(0, 7, 0);
        for (Entity e : level.getEntities(this, box, e -> affects(owner, e))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > visualRadius) {
                continue;
            }
            held.add(e);
            double targetY = getY() + 1.6 + (Math.abs(e.getId()) % 5) * 0.7;
            double vy = Math.max(-0.1, Math.min(0.12, (targetY - e.getY()) * 0.08));
            // a slow orbit around the sigil
            Vec3 tangent = dist < 0.1 ? Vec3.ZERO : new Vec3(-dz / dist, 0, dx / dist).scale(0.035);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.5 + tangent.x, vy, e.getDeltaMovement().z * 0.5 + tangent.z);
            e.fallDistance = 0;
            e.hurtMarked = true;
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
        }
        held.removeIf(e -> !e.isAlive());
    }

    /** Drops everything at once. */
    public void release() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        for (Entity e : held) {
            if (e.isAlive()) {
                e.setDeltaMovement(0, -0.2, 0);
                e.hurtMarked = true;
            }
        }
        held.clear();
        Vfx.send(level, VfxType.GEOMETRY_BURST, position().add(0, 3, 0), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, visualRadius * 0.6f, 16);
        Vfx.send(level, VfxType.SHOCKWAVE, position().add(0, 0.1, 0), new Vec3(0, 1, 0), MindManager.GOLD, MindManager.PALE, visualRadius * 1.3f, 14);
        Vfx.sound(level, position(), ModSounds.MIND_PULSE, 2f, 1.3f);
        discard();
    }

    @Override
    protected void onExpire(ServerLevel level) {
        for (Entity e : held) {
            if (e.isAlive() && e instanceof LivingEntity living && !(e instanceof Player)) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0));
            }
        }
        Vfx.sound(level, position(), ModSounds.MIND_SHIELD_HIT, 1.5f, 0.6f);
    }

    @Override
    protected void clientTick() {
        dev.infinitygauntlet.client.mind.MindClient.fieldTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeVarIntArray(floating);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        floating = buf.readVarIntArray();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
