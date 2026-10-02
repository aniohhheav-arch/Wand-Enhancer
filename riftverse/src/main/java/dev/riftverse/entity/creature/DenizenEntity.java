package dev.riftverse.entity.creature;

import dev.riftverse.item.relic.Relics;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.world.MaterialSet;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A native of one universe. Every reality has its own people: their dress (skin), their title and the goods they trade
 * (relic blades and blasters of their own world, its building materials, rift supplies) come from the universe they
 * live in. They roam, gather in settlements and never despawn.
 */
public class DenizenEntity extends WanderingTrader {
    private static final EntityDataAccessor<Integer> CULTURE = SynchedEntityData.defineId(DenizenEntity.class, EntityDataSerializers.INT);
    private static final String[] ROLES = {"Merchant", "Wanderer", "Elder", "Scholar", "Smith", "Pilgrim", "Warden", "Mystic"};

    public DenizenEntity(EntityType<? extends DenizenEntity> type, Level level) {
        super(type, level);
        setDespawnDelay(0);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.MAX_HEALTH, 30.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CULTURE, 0);
    }

    public int culture() {
        return entityData.get(CULTURE);
    }

    /** Makes this denizen a native of the given universe, with a title from its culture. */
    public void belongTo(UniverseSpec spec) {
        entityData.set(CULTURE, spec.archetype.ordinal());
        String role = ROLES[getRandom().nextInt(ROLES.length)];
        String people = spec.archetype.epithets[getRandom().nextInt(spec.archetype.epithets.length)];
        setCustomName(Component.literal(people + " " + role).withColor(spec.accent));
    }

    @Nullable
    private UniverseSpec home() {
        if (level().dimension() != RvWorldgen.EXPANSE) return null;
        return UniverseRegistry.specAt(getBlockX(), getBlockZ());
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        UniverseSpec spec = home();
        Archetype culture = Archetype.byId(culture());
        if (spec != null) {
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10 + Relics.tier(spec) * 3), Optional.of(new ItemCost(RvItems.RIFT_SHARD.get(), 2)),
                    Relics.forge(spec, false, getRandom()), 3, 15, 0.05f));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 14 + Relics.tier(spec) * 3), Optional.of(new ItemCost(RvItems.VOID_ESSENCE.get(), 1)),
                    Relics.forge(spec, true, getRandom()), 3, 15, 0.05f));
        }
        MaterialSet m = MaterialSet.of(spec != null ? spec.materials : culture);
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(m.structure.getBlock().asItem(), 12), 16, 2, 0.05f));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(m.accent.getBlock().asItem(), 6), 16, 2, 0.05f));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(RvItems.STELLAR_DUST.get(), 4), 12, 5, 0.05f));
        offers.add(new MerchantOffer(new ItemCost(RvItems.RIFT_SHARD.get(), 3), new ItemStack(Items.EMERALD, 2), 16, 5, 0.05f));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(RvItems.HOMEWARD_RIFT.get(), 1), 6, 8, 0.05f));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("culture", culture());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CULTURE, tag.getInt("culture"));
        setDespawnDelay(0);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
