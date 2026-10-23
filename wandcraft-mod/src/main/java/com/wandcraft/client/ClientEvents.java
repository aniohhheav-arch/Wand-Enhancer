package com.wandcraft.client;

import com.wandcraft.WandCraft;
import com.wandcraft.item.WandItem;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = WandCraft.ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (DevAutomation.ON) DevAutomation.tick(mc);
        if (mc.player == null) return;
        boolean holding = mc.player.getMainHandItem().getItem() instanceof WandItem;

        if (mc.screen == null && holding) {
            if (ClientSetup.WHEEL_KEY.isDown()) mc.setScreen(new WheelScreen());
            else if (ClientSetup.BOOK_KEY.consumeClick()) mc.setScreen(new SpellbookScreen());
        }
        while (ClientSetup.WHEEL_KEY.consumeClick()) { /* drain */ }

        // Phoenix Wings: gentle forward glide while airborne.
        PlayerMagic m = Magic.of(mc.player);
        if (m.wingsUntil > mc.player.level().getGameTime() && !mc.player.onGround() && !mc.player.isFallFlying() && mc.options.keyUp.isDown()) {
            Vec3 look = mc.player.getLookAngle();
            Vec3 h = new Vec3(look.x, 0, look.z);
            if (h.lengthSqr() > 1e-4) {
                Vec3 v = mc.player.getDeltaMovement();
                Vec3 add = h.normalize().scale(0.035);
                if (Math.sqrt(v.x * v.x + v.z * v.z) < 0.55) mc.player.setDeltaMovement(v.add(add));
            }
        }
    }

    /** Vanilla slows you to 20% while using an item; wand casting should not punish movement. */
    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        if (e.getEntity().isUsingItem() && e.getEntity().getUseItem().getItem() instanceof WandItem) {
            e.getInput().leftImpulse *= 5f;
            e.getInput().forwardImpulse *= 5f;
        }
    }
}
