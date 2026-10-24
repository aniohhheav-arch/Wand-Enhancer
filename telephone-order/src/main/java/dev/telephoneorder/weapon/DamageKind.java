package dev.telephoneorder.weapon;

import dev.telephoneorder.TelephoneOrder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public enum DamageKind {
    BULLET("bullet"), ENERGY("energy"), ARMOR_PIERCING("armor_piercing"), EXPLOSION("explosion_gun"), EMP("emp"), TURRET("turret"), ECHO("paradox_echo");

    private final ResourceKey<DamageType> key;

    DamageKind(String id) {
        this.key = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(TelephoneOrder.MOD_ID, id));
    }

    public DamageSource source(ServerLevel level, Entity direct, Entity cause) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), direct, cause);
    }
}
