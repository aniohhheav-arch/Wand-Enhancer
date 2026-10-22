package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.ability.StandType;
import com.stickyfingers.world.StandArrowItem;
import com.stickyfingers.world.TypedStandArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(StickyFingersMod.MODID);

    public static final DeferredItem<Item> STAND_ARROW = REGISTER.register("stand_arrow",
            () -> new StandArrowItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> SP_ARROW = REGISTER.register("sp_arrow",
            () -> new TypedStandArrowItem(StandType.STAR_PLATINUM, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> D4C_ARROW = REGISTER.register("d4c_arrow",
            () -> new TypedStandArrowItem(StandType.D4C, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {}
}
