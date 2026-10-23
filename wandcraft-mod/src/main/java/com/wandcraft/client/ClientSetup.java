package com.wandcraft.client;

import com.wandcraft.WandCraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.wandcraft.ModParticles;
import com.wandcraft.client.particle.MagicParticle;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = WandCraft.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping WHEEL_KEY = new KeyMapping("key.wandcraft.wheel", GLFW.GLFW_KEY_R, "key.categories.wandcraft");
    public static final KeyMapping BOOK_KEY = new KeyMapping("key.wandcraft.spellbook", GLFW.GLFW_KEY_B, "key.categories.wandcraft");

    private ClientSetup() {}

    static {
        com.wandcraft.entity.SpellBeam.firstPerson = e ->
                e == net.minecraft.client.Minecraft.getInstance().player && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(WHEEL_KEY);
        e.register(BOOK_KEY);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(WandCraft.SPELL_PROJECTILE.get(), Invisible::new);
        e.registerEntityRenderer(WandCraft.SPELL_ZONE.get(), Invisible::new);
        e.registerEntityRenderer(WandCraft.SPELL_BEAM.get(), BeamRenderer::new);
        e.registerEntityRenderer(WandCraft.DEBRIS.get(), DebrisRenderer::new);
        e.registerEntityRenderer(WandCraft.SHADOW_CLONE.get(), CloneRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.GLOW.get(), s -> new MagicParticle.Provider(s, MagicParticle.Style.GLOW));
        e.registerSpriteSet(ModParticles.SPARK.get(), s -> new MagicParticle.Provider(s, MagicParticle.Style.SPARK));
        e.registerSpriteSet(ModParticles.WISP.get(), s -> new MagicParticle.Provider(s, MagicParticle.Style.WISP));
        e.registerSpriteSet(ModParticles.RUNE.get(), s -> new MagicParticle.Provider(s, MagicParticle.Style.RUNE));
    }

    @SubscribeEvent
    public static void gui(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "hud"), Hud::render);
    }

    /** Spell entities draw themselves entirely with particles. */
    private static final class Invisible<T extends Entity> extends EntityRenderer<T> {
        Invisible(EntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        }
    }
}
