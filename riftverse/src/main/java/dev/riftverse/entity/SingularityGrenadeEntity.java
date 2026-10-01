package dev.riftverse.entity;

import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Collapses into a short-lived singularity wherever it lands. */
public class SingularityGrenadeEntity extends ThrowableItemProjectile {
    public SingularityGrenadeEntity(EntityType<? extends SingularityGrenadeEntity> type, Level level) {
        super(type, level);
    }

    public SingularityGrenadeEntity(Level level, LivingEntity shooter) {
        super(RvEntities.SINGULARITY_GRENADE.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return RvItems.SINGULARITY_GRENADE.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(2) == 0) {
            level().addParticle(RvParticles.MOTE.get().with(0x9A7CFF, 0.2f, 12), getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 at = result.getLocation().add(0, 1.6, 0);
        BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), server);
        hole.moveTo(at.x, at.y, at.z, 0, 0);
        hole.setHorizonRadius(1.1f);
        hole.setCaptures(false);
        hole.setLifetime(110);
        hole.setOwner(getOwner());
        server.addFreshEntity(hole);
        server.playSound(null, at.x, at.y, at.z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1.5f, 1.3f);
        server.sendParticles(RvParticles.RING.get().with(0xB0A0FF, 4.0f, 16), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        discard();
    }
}
