package dev.infinitygauntlet.client;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.gui.AbilityWheelScreen;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.vfx.CameraEffects;
import dev.infinitygauntlet.client.vfx.ClientVfxManager;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!mc.isPaused()) {
            ClientVfxManager.tick();
            CameraEffects.tick();
            ClientCooldowns.tick();
            auraTick(mc.player);
        }
        while (Keybinds.NEXT_ABILITY.consumeClick()) {
            HeldGauntlet.cycleAbility(1);
        }
        while (Keybinds.PREVIOUS_ABILITY.consumeClick()) {
            HeldGauntlet.cycleAbility(-1);
        }
        while (Keybinds.NEXT_STONE.consumeClick()) {
            HeldGauntlet.cycleStone();
        }
        while (Keybinds.ABILITY_WHEEL.consumeClick()) {
            if (mc.screen == null && !HeldGauntlet.find(mc.player).isEmpty()
                    && !InfinityGauntletItem.state(HeldGauntlet.find(mc.player)).stones().isEmpty()) {
                mc.setScreen(new AbilityWheelScreen());
            }
        }
    }

    /** Energy around the gauntlet hand: each installed stone sheds its colour; the full set adds a cosmic aura. */
    private static void auraTick(Player player) {
        ItemStack stack = HeldGauntlet.find(player);
        if (stack.isEmpty()) {
            return;
        }
        GauntletState state = InfinityGauntletItem.state(stack);
        if (state.stones().isEmpty()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 hand = player.getEyePosition().add(look.scale(0.6)).add(new Vec3(-look.z, 0, look.x).normalize().scale(0.35)).add(0, -0.45, 0);
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (!firstPerson && Particles.chance(0.25f * state.stones().size() / 6f + 0.1f)) {
            InfinityStone stone = state.stones().get(player.getRandom().nextInt(state.stones().size()));
            Particles.mote(hand.add(player.getRandom().nextGaussian() * 0.15, player.getRandom().nextGaussian() * 0.15,
                    player.getRandom().nextGaussian() * 0.15), new Vec3(0, 0.02, 0), stone.color(), 0.06f, 16);
        }
        if (state.isComplete() && Particles.chance(0.35f)) {
            InfinityStone stone = InfinityStone.values()[player.getRandom().nextInt(6)];
            double a = player.getRandom().nextDouble() * Math.PI * 2;
            Vec3 at = player.position().add(Math.cos(a) * 0.9, player.getRandom().nextDouble() * 1.9, Math.sin(a) * 0.9);
            Particles.mote(at, new Vec3(-Math.sin(a) * 0.04, 0.015, Math.cos(a) * 0.04), stone.color(), 0.07f, 24);
        }
    }

    @SubscribeEvent
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientVfxManager.render(event.getPoseStack(), mc.renderBuffers().bufferSource(), event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Vec3 shake = CameraEffects.shakeAngles((float) event.getPartialTick());
        if (shake != Vec3.ZERO) {
            event.setYaw(event.getYaw() + (float) shake.x);
            event.setPitch(event.getPitch() + (float) shake.y);
            event.setRoll(event.getRoll() + (float) shake.z);
        }
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        float m = CameraEffects.fovMultiplier((float) event.getPartialTick());
        if (m != 1f) {
            event.setFOV(event.getFOV() * m);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientVfxManager.clear();
        CameraEffects.reset();
        ClientCooldowns.clear();
        ClientEffects.clear();
    }

    private ClientGameEvents() {
    }
}
