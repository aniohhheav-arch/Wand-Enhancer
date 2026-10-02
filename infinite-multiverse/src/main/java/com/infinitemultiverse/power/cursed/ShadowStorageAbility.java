package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.Vec3;

/** Ten Shadows storage: open a pocket in your shadow. It is backed by your ender chest inventory, so it is shared with it. */
public final class ShadowStorageAbility extends PowerAbility {
    public ShadowStorageAbility() {
        super(MultiverseSystem.CURSED_TECHNIQUES);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ctx.player().openMenu(new SimpleMenuProvider((id, inventory, player) -> ChestMenu.threeRows(id, inventory, player.getEnderChestInventory()),
                Component.translatable("container.infinitemultiverse.shadow_storage")));
        Cinematics.scene(ctx.level(), SceneIds.SHADOW_SUMMON, ctx.player().position(), Vec3.ZERO, 0x14141F, 20, ctx.player(), 1.2f);
        return true;
    }
}
