package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An unstable rift torn into the ground. It drags nearby entities and loose items toward its centre; whatever reaches
 * the centre is swallowed and spat back out of a small rupture high above, then the rift collapses with a pulse.
 */
public class SpatialRiftEntity extends TimedEffectEntity {
    public static final int FORM_TICKS = 16;
    private final Set<UUID> swallowed = new HashSet<>();

    public SpatialRiftEntity(EntityType<? extends SpatialRiftEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.SPACE_RIFT, 2.5f, 1f);
            Vfx.shake(level, position(), 18, 0.35f);
        }
        if (tickCount < FORM_TICKS) {
            return;
        }
        ServerPlayer owner = owner();
        float pullRadius = visualRadius * 2.4f;
        AABB box = new AABB(position(), position()).inflate(pullRadius, 4, pullRadius);
        for (Entity e : level.getEntities(this, box, e -> isAffected(owner, e))) {
            Vec3 to = position().subtract(e.position());
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            if (horizontal > pullRadius) {
                continue;
            }
            if (horizontal < 1.3 && Math.abs(to.y) < 2.5) {
                swallow(level, e);
                continue;
            }
            double strength = 0.05 + 0.13 * (1 - horizontal / pullRadius);
            Vec3 pull = new Vec3(to.x / horizontal, 0, to.z / horizontal).scale(strength);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(pull));
            e.hurtMarked = true;
        }
        if (tickCount % 12 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 edge = position().add(Math.cos(a) * visualRadius, 0.2, Math.sin(a) * visualRadius);
            Vfx.arc(level, position().add(0, 0.2, 0), edge, InfinityStone.SPACE, 6);
        }
    }

    private boolean isAffected(ServerPlayer owner, Entity e) {
        if (swallowed.contains(e.getUUID()) || e instanceof TimedEffectEntity || e instanceof RupturePortalEntity || e.isSpectator()) {
            return false;
        }
        if (e instanceof ItemEntity || e instanceof Projectile) {
            return true;
        }
        return e instanceof LivingEntity && (owner == null || TargetRules.canTarget(owner, e));
    }

    private void swallow(ServerLevel level, Entity e) {
        swallowed.add(e.getUUID());
        Vec3 exit = position().add(random.nextGaussian() * 2, ServerConfig.RIFT_EXIT_HEIGHT.get(), random.nextGaussian() * 2);
        Vec3 safe = TeleportUtil.findSafeSpot(level, e, exit, 4);
        if (safe == null) {
            return;
        }
        Vfx.send(level, VfxType.DISSOLVE, e.position(), new Vec3(e.getBbWidth(), e.getBbHeight(), 0), 0x2A6EFF, 0x9FE8FF, 1f, 14);
        Vfx.send(level, VfxType.RUPTURE_BURST, safe.add(0, e.getBbHeight() * 0.5, 0), Vec3.ZERO, 0x2A6EFF, 0x9FE8FF,
                Math.max(1.0f, e.getBbHeight() * 0.7f), 26);
        Vfx.sound(level, position(), ModSounds.SPACE_TELEPORT, 1.2f, 0.7f);
        Vfx.sound(level, safe, ModSounds.SPACE_BLINK, 1.5f, 0.8f);
        TeleportUtil.teleport(e, level, safe, e.getYRot(), e.getXRot(), new Vec3(random.nextGaussian() * 0.1, -0.1, random.nextGaussian() * 0.1));
    }

    @Override
    protected void onExpire(ServerLevel level) {
        Vfx.send(level, VfxType.IMPLOSION, position().add(0, 0.3, 0), Vec3.ZERO, 0x2A6EFF, 0xDDF6FF, visualRadius, 10);
        Vfx.shockwave(level, position().add(0, 0.15, 0), new Vec3(0, 1, 0), InfinityStone.SPACE, visualRadius * 3.2f, 16);
        Vfx.sound(level, position(), ModSounds.SPACE_SHOCKWAVE, 2f, 1.2f);
        Vfx.camera(level, position(), 24, 0.6f, 0, 0, 0x9FE8FF, 0.15f, 5);
    }

    @Override
    protected void clientTick() {
        ClientEffects.riftTick(this);
    }

    /** Visual collapse in the last 12 ticks. */
    public float collapse(float partialTick) {
        return Math.max(0f, (tickCount + partialTick - (maxLife - 12)) / 12f);
    }
}
