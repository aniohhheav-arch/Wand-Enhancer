package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.world.StandArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(StickyFingersMod.MODID);

    public static final DeferredItem<Item> STAND_ARROW = REGISTER.register("stand_arrow",
            () -> new StandArrowItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {}
}
