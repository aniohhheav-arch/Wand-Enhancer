package dev.riftverse.entity.creature;

import dev.riftverse.registry.RvParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;

/** A harmless moth with wings of starlight; flocks of them trail glittering dust through the night skies. */
public class StarMothEntity extends Bat {
    public StarMothEntity(EntityType<? extends StarMothEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Bat.createAttributes();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 6 == 0) {
            ((ServerLevel) level()).sendParticles(RvParticles.MOTE.get().with(0xFFE8A0, 0.5f, 30), getX(), getY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0.005);
        }
    }
}
