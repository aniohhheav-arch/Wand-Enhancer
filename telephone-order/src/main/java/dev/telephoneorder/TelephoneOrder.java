package dev.telephoneorder;

import dev.telephoneorder.delivery.DeliveryDirector;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.phone.Account;
import dev.telephoneorder.phone.PhoneService;
import dev.telephoneorder.weapon.DistortionField;
import dev.telephoneorder.weapon.GunLogic;
import dev.telephoneorder.weapon.ShieldField;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelephoneOrder implements ModInitializer {
    public static final String MOD_ID = "telephoneorder";
    public static final Logger LOGGER = LoggerFactory.getLogger("TelephoneOrder");

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModSounds.register();
        ModItems.register();          // guns, ammo, attachments, props, projectile sprites
        ModBlocks.register();
        ModItems.registerBlockItems();
        ModItems.registerTab();
        ModEntities.register();
        Account.register();
        Payloads.register();
        Sched.init();
        PhoneService.init();
        DeliveryDirector.init();
        DistortionField.init();
        ShieldField.init();
        ServerSetup.init();
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
            var p = h.getPlayer();
            Account.of(p);
            if (!Account.flag(p, "starter")) {
                Account.setFlag(p, "starter", true);
                var phone = new net.minecraft.world.item.ItemStack(ModItems.HANDHELD);
                if (!p.getInventory().add(phone)) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), phone));
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a76Telephone Order:\u00a7r a Pocket Handset has been added to your inventory. Right-click it to place orders."));
            }
        });
        SelfTest.init();
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { GunLogic.forget(h.getPlayer().getUUID()); PhoneService.forget(h.getPlayer().getUUID()); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { Sched.clear(); GunLogic.clearAll(); PhoneService.clear(); DistortionField.clear(); ShieldField.clear(); });
        LOGGER.info("Telephone Order loaded: {} weapons, {} attachments, {} ammo types, {} sounds", Catalog.GUNS.size(), Catalog.ATTS.size(), Catalog.AMMO.size(), Catalog.SOUNDS.size());
    }
}
