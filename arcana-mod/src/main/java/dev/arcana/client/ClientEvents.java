package dev.arcana.client;

import dev.arcana.ArcanaConfig;
import dev.arcana.ArcanaMod;
import dev.arcana.item.WandItem;
import dev.arcana.magic.CastService;
import dev.arcana.magic.Spell;
import dev.arcana.magic.SpellSlot;
import dev.arcana.net.Net;
import dev.arcana.registry.ModEntities;
import dev.arcana.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEvents {

    @EventBusSubscriber(modid = ArcanaMod.MODID, value = Dist.CLIENT)
    public static final class Mod {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(ClientKeys.SECONDARY);
            e.register(ClientKeys.SPECIAL);
            e.register(ClientKeys.ULTIMATE);
            e.register(ClientKeys.WHEEL);
            e.register(ClientKeys.MENU);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, "hud"), new Hud());
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ModEntities.ARCHMAGE.get(), ArchmageRenderer::new);
            e.registerEntityRenderer(ModEntities.SPELL_FX.get(), SpellFxRenderer::new);
        }

        @SubscribeEvent
        public static void extensions(RegisterClientExtensionsEvent e) {
            IClientItemExtensions ext = new IClientItemExtensions() {
                @Override
                public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.player.LocalPlayer player,
                                                       net.minecraft.world.entity.HumanoidArm arm, ItemStack stack, float partial, float equip, float swing) {
                    int side = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
                    pose.translate(side * 0.56f, -0.52f + equip * -0.6f, -0.72f);
                    if (player.isUsingItem() && player.getUseItem() == stack) {
                        float used = (player.getTicksUsingItem() + partial);
                        Spell s = WandItem.selectedOf(stack);
                        float charge = s.chargeTicks <= 0 ? 0f : Math.min(1f, used / s.chargeTicks);
                        pose.translate(-side * 0.12f * charge, 0.1f * charge, 0.1f * charge);
                        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-25f * charge));
                        float shake = charge * 0.012f;
                        pose.translate(Math.sin(used * 1.7f) * shake, Math.cos(used * 2.3f) * shake, 0);
                    } else if (swing > 0) {
                        float sw = (float) Math.sin(Math.sqrt(swing) * Math.PI);
                        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-sw * 55f));
                        pose.translate(0, -sw * 0.05f, -sw * 0.25f);
                    }
                    return true;
                }
            };
            for (var w : ModItems.WANDS.values()) e.registerItem(ext, w.get());
        }
    }

    @EventBusSubscriber(modid = ArcanaMod.MODID, value = Dist.CLIENT)
    public static final class Game {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            ClientState.clientTick();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            cinematicCamera(mc);
            ItemStack wand = CastService.heldWand(mc.player);
            boolean inGame = mc.screen == null;
            sendSlot(ClientKeys.SECONDARY, SpellSlot.SECONDARY, wand, inGame);
            sendSlot(ClientKeys.SPECIAL, SpellSlot.SPECIAL, wand, inGame);
            sendSlot(ClientKeys.ULTIMATE, SpellSlot.ULTIMATE, wand, inGame);
            while (ClientKeys.WHEEL.consumeClick()) {
                if (inGame && !wand.isEmpty()) mc.setScreen(new SpellWheelScreen(wand));
            }
            while (ClientKeys.MENU.consumeClick()) {
                if (inGame && !wand.isEmpty()) mc.setScreen(new WandScreen());
            }
        }

        private static void sendSlot(net.minecraft.client.KeyMapping key, SpellSlot slot, ItemStack wand, boolean inGame) {
            while (key.consumeClick()) {
                if (inGame && !wand.isEmpty()) PacketDistributor.sendToServer(new Net.CastSlot(slot.ordinal()));
            }
        }

        private static net.minecraft.client.CameraType savedCamera;

        private static boolean casterCinematic(Minecraft mc) {
            return mc.player != null && ClientState.cinematicSpell >= 0 && ClientState.cinematicCaster == mc.player.getId()
                && ArcanaConfig.CINEMATIC_CAMERA.get() && ArcanaConfig.CINEMATICS.get();
        }

        /** Own ultimates: hero camera behind the caster while the 3D effect plays, restored afterwards. */
        static void cinematicCamera(Minecraft mc) {
            boolean active = casterCinematic(mc) && ClientState.cinematicProgress(0f) > 0f;
            if (active && savedCamera == null) {
                savedCamera = mc.options.getCameraType();
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            } else if (!active && savedCamera != null) {
                mc.options.setCameraType(savedCamera);
                savedCamera = null;
            }
        }

        @SubscribeEvent
        public static void fov(ViewportEvent.ComputeFov e) {
            Minecraft mc = Minecraft.getInstance();
            if (!casterCinematic(mc)) return;
            float p = ClientState.cinematicProgress((float) e.getPartialTick());
            if (p <= 0f) return;
            float push = (float) Math.sin(Math.min(1f, p) * Math.PI) * 0.22f; // pull in, then ease back out
            float punch = Math.max(0f, 0.12f - p * 1.2f);                      // sharp widen on the cast
            e.setFOV(e.getFOV() * (1f - push + punch));
        }

        @SubscribeEvent
        public static void camera(ViewportEvent.ComputeCameraAngles e) {
            if (!ArcanaConfig.SCREEN_SHAKE.get() || ClientState.cinematicKind != 0) return;
            float s = ClientState.cinematicStrength((float) e.getPartialTick());
            if (s <= 0) return;
            float t = ClientState.tick + (float) e.getPartialTick();
            float amp = s * 1.4f;
            e.setYaw(e.getYaw() + (float) Math.sin(t * 2.1f) * amp);
            e.setPitch(e.getPitch() + (float) Math.cos(t * 2.7f) * amp);
            e.setRoll(e.getRoll() + (float) Math.sin(t * 1.3f) * amp * 0.6f);
        }
    }

    private ClientEvents() {}
}
