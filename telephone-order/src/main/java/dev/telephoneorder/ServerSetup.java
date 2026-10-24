package dev.telephoneorder;

import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.phone.Account;
import dev.telephoneorder.weapon.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.Map;

/** Gun key-bind receivers, the attachment workbench, kill bounties and exploration loot (clue slips, credit chips). */
public final class ServerSetup {
    private ServerSetup() {}

    /** loot table path -> slip index (digit of the Archive number) */
    private static final Map<String, Integer> SLIP_TABLES = Map.of(
            "chests/desert_pyramid", 0, "chests/jungle_temple", 1, "chests/ancient_city", 2, "chests/simple_dungeon", 3,
            "chests/stronghold_library", 4, "chests/nether_bridge", 5, "chests/end_city_treasure", 6);
    private static final java.util.Set<String> CHIP_TABLES = java.util.Set.of("chests/abandoned_mineshaft", "chests/buried_treasure", "chests/shipwreck_treasure",
            "chests/woodland_mansion", "chests/bastion_other", "chests/pillager_outpost", "chests/village/village_weaponsmith", "chests/igloo_chest", "chests/underwater_ruin_big");

    static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Reload.TYPE, (p, ctx) -> GunLogic.requestReload(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.FireMode.TYPE, (p, ctx) -> GunLogic.cycleMode(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Aim.TYPE, (p, ctx) -> GunLogic.setAim(ctx.player(), p.aiming()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.WorkbenchAction.TYPE, (p, ctx) -> workbench(ctx.player(), p));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!ModConfig.get().killBounties) return;
            if (source.getEntity() instanceof ServerPlayer sp && entity instanceof Enemy) {
                int v = Math.max(1, Math.round((1 + (int) (entity.getMaxHealth() / 10)) * ModConfig.get().credits));
                Account.add(sp, v);
            }
        });

        LootTableEvents.MODIFY.register((key, builder, src, reg) -> {
            if (!ModConfig.get().clueLoot) return;
            String path = key.identifier().getPath();
            Integer idx = SLIP_TABLES.get(path);
            if (idx != null) {
                CompoundTag t = new CompoundTag();
                t.putInt("idx", idx);
                builder.pool(LootPool.lootPool().add(LootItem.lootTableItem(ModItems.CLUE_SLIP)
                        .apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(t))))
                        .when(LootItemRandomChanceCondition.randomChance(0.4f)).build());
            }
            if (CHIP_TABLES.contains(path)) {
                CompoundTag t = new CompoundTag();
                t.putInt("value", 40);
                builder.pool(LootPool.lootPool().add(LootItem.lootTableItem(ModItems.CREDIT_CHIP)
                        .apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(t))))
                        .when(LootItemRandomChanceCondition.randomChance(0.35f)).build());
            }
        });
    }

    public static void workbench(ServerPlayer p, Payloads.WorkbenchAction a) {
        ItemStack gun = p.getMainHandItem();
        if (!(gun.getItem() instanceof GunItem gi)) { p.sendOverlayMessage(Component.literal("Hold the weapon in your main hand.")); return; }
        GunDef def = gi.def;
        String slot = a.slot();
        if (!def.slots().contains(slot)) return;
        if (GunData.reloading(gun)) GunLogic.cancelReload(gun);
        String current = GunData.attachment(gun, slot);
        if (a.action().equals("detach")) {
            if (current.isEmpty()) return;
            giveBack(p, current);
            GunData.edit(gun, t -> { CompoundTag att = t.getCompoundOrEmpty("att"); att.remove(slot); t.put("att", att); });
        } else if (a.action().equals("attach")) {
            AttDef att = Catalog.ATT_BY_ID.get(a.id());
            if (att == null || !att.slot().equals(slot) || !Catalog.fits(att, def)) {
                p.sendOverlayMessage(Component.literal("That attachment does not fit this weapon."));
                Fx.sound(p, "ui_error", SoundSource.PLAYERS, 0.8f, 1f);
                return;
            }
            net.minecraft.world.item.Item item = ModItems.ATTACHMENTS.get(att.id());
            boolean taken = false;
            for (int i = 0; i < p.getInventory().getContainerSize() && !taken; i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() == item) { s.shrink(1); taken = true; }
            }
            if (!taken) { p.sendOverlayMessage(Component.literal("You do not have a " + att.name() + ".")); Fx.sound(p, "ui_error", SoundSource.PLAYERS, 0.8f, 1f); return; }
            if (!current.isEmpty()) giveBack(p, current);
            GunData.edit(gun, t -> { CompoundTag at = t.getCompoundOrEmpty("att"); at.putString(slot, att.id()); t.put("att", at); });
        } else return;
        // clamp magazine to the new capacity, returning the surplus to reserve
        GunStats st = GunStats.of(def, gun);
        GunData.edit(gun, t -> {
            int ammo = t.getIntOr("ammo", 0);
            if (ammo > st.mag) { t.putInt("reserve", t.getIntOr("reserve", 0) + (ammo - st.mag)); t.putInt("ammo", st.mag); }
        });
        GunData.syncModel(gun, 0);
        Fx.sound(p, "reload_bolt", SoundSource.PLAYERS, 0.9f, 1.3f);
        Fx.sound(p, "ui_click", SoundSource.PLAYERS, 0.6f, 1f);
    }

    private static void giveBack(ServerPlayer p, String attId) {
        net.minecraft.world.item.Item item = ModItems.ATTACHMENTS.get(attId);
        if (item == null) return;
        ItemStack s = new ItemStack(item);
        if (!p.getInventory().add(s)) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), s));
    }
}
