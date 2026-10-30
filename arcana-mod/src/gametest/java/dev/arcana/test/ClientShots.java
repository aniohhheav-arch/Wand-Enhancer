package dev.arcana.test;

import net.minecraft.client.Screenshot;
import dev.arcana.ArcanaMod;
import dev.arcana.client.SpellWheelScreen;
import dev.arcana.client.WandScreen;
import dev.arcana.magic.CastService;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.registry.ModAttachments;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only client driver (lives in the gametest source set, never shipped). Enabled with -Pquickplay=<world>
 * on runClient: gives wands, casts spells, opens screens and writes screenshots to run/screenshots.
 */
@EventBusSubscriber(modid = ArcanaMod.MODID, value = Dist.CLIENT)
public final class ClientShots {
    private static int t = -1;

    private static ItemStack wand(School s, int stage) {
        ItemStack w = new ItemStack(ModItems.WANDS.get(s).get());
        w.set(ModComponents.STAGE.get(), stage);
        return w;
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static void server(java.util.function.Consumer<ServerPlayer> r) {
        Minecraft mc = Minecraft.getInstance();
        var srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) r.accept(sp);
        });
    }

    private static void cast(ServerPlayer sp, Spell s) {
        PlayerMagic m = sp.getData(ModAttachments.MAGIC);
        m.mana = 1000;
        m.cooldowns.clear();
        CastService.cast(sp, CastService.heldWand(sp), s, 1f, InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("arcana.shots")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (t < 0) t = 0;
        t++;
        switch (t) {
            case 40 -> server(sp -> {
                sp.setGameMode(GameType.SURVIVAL);
                ServerLevel l = sp.serverLevel();
                l.setDayTime(6000);
                int x = 0, z = 0;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                sp.teleportTo(l, x + 0.5, y, z + 0.5, 0f, 8f);
                for (int i = 0; i < 8; i++) {
                    Zombie zom = EntityType.ZOMBIE.create(l);
                    double zx = x - 6 + (i % 4) * 4, zz = z + 10 + (i / 4) * 4;
                    zom.moveTo(zx, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) zx, (int) zz), zz, 0, 0);
                    zom.setNoAi(true);
                    zom.setPersistenceRequired();
                    l.addFreshEntity(zom);
                }
                var m = sp.getData(ModAttachments.MAGIC);
                for (School s : School.real()) m.mastery.put(s.id, 40 * 5 * 5);
                sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.STORM, 2));
                m.mana = 70;
            });
            case 80 -> shot("01_hud_storm");
            case 85 -> server(sp -> cast(sp, Spell.THUNDERBOLT));
            case 92 -> shot("02_thunderbolt");
            case 100 -> server(sp -> cast(sp, Spell.CHAIN_LIGHTNING));
            case 104 -> shot("03_chain_lightning");
            case 120 -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.VOID, 2)));
            case 130 -> server(sp -> cast(sp, Spell.EVENT_HORIZON));
            case 136 -> shot("04_event_horizon_cinematic");
            case 170 -> shot("05_event_horizon_late");
            case 175 -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.SOLAR, 2)));
            case 185 -> server(sp -> cast(sp, Spell.SOLAR_BEAM));
            case 188 -> shot("06_solar_beam");
            case 200 -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.GLACIER, 2)));
            case 210 -> server(sp -> cast(sp, Spell.ICE_SPIKES));
            case 216 -> shot("07_ice_spikes");
            case 225 -> server(sp -> cast(sp, Spell.FROZEN_PRISON));
            case 235 -> shot("08_frozen_prison");
            case 245 -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.CRIMSON, 2)));
            case 255 -> server(sp -> cast(sp, Spell.CRIMSON_CATACLYSM));
            case 266 -> shot("09_crimson_cataclysm");
            case 300 -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.CHRONOS, 2)));
            case 310 -> server(sp -> cast(sp, Spell.TIME_STOP));
            case 318 -> shot("10_time_stop");
            case 340 -> mc.setScreen(new WandScreen());
            case 348 -> shot("11_wand_screen");
            case 350 -> mc.setScreen(null);
            case 355 -> server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.NONE, 2)));
            case 365 -> {
                SpellWheelScreen.keepOpenForTests = true;
                mc.setScreen(new SpellWheelScreen(mc.player.getMainHandItem()));
            }
            case 378 -> shot("12_forbidden_wheel");
            case 380 -> mc.setScreen(null);
            case 400 -> {
                ArcanaMod.LOGGER.info("[arcana-shots] done");
                mc.stop();
            }
            default -> {}
        }
    }

    private ClientShots() {}
}
