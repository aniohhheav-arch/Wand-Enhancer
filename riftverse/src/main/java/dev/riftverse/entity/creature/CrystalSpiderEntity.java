package dev.riftverse.entity.creature;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

/** An amethyst-shelled spider whose bite crystallises the blood: victims are slowed and weakened. */
public class CrystalSpiderEntity extends Spider {
    public CrystalSpiderEntity(EntityType<? extends CrystalSpiderEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ARMOR, 6.0).add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
        }
        return hit;
    }
}
