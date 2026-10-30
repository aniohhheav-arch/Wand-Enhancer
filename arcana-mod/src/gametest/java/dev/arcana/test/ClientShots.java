package dev.arcana.test;

import dev.arcana.ArcanaMod;
import dev.arcana.entity.ArchmageEntity;
import dev.arcana.magic.CastService;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.registry.ModAttachments;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModEntities;
import dev.arcana.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Development-only client driver (gametest source set, never shipped). Run with {@code gradle runClient -PquickPlay=<world>}: it
 * gives wands, casts spells, spawns the boss and writes screenshots to run/screenshots.
 */
@EventBusSubscriber(modid = ArcanaMod.MODID, value = Dist.CLIENT)
public final class ClientShots {
    private record Step(int tick, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int t = -1, next = 0;

    private static ItemStack wand(School s, int stage) {
        ItemStack w = new ItemStack(ModItems.WANDS.get(s).get());
        w.set(ModComponents.STAGE.get(), stage);
        return w;
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static void server(Consumer<ServerPlayer> r) {
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

    private static void crowd(ServerPlayer sp) {
        ServerLevel l = sp.serverLevel();
        for (var z : l.getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(60))) z.discard();
        for (int i = 0; i < 8; i++) {
            Zombie zom = EntityType.ZOMBIE.create(l);
            double zx = sp.getX() - 6 + (i % 4) * 4, zz = sp.getZ() + 10 + (i / 4) * 4;
            zom.moveTo(zx, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) zx, (int) zz), zz, 0, 0);
            zom.setNoAi(true);
            zom.setPersistenceRequired();
            l.addFreshEntity(zom);
        }
    }

    private static void at(int tick, Runnable r) {
        STEPS.add(new Step(tick, r));
    }

    private static void bossDebug() {
        Minecraft mc = Minecraft.getInstance();
        int n = 0;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof ArchmageEntity b) {
                n++;
                ArcanaMod.LOGGER.info("[arcana-shots] boss#{} pos={} dist={} invisible={} alive={} health={}", n, b.position(), b.distanceTo(mc.player), b.isInvisible(), b.isAlive(), b.getHealth());
            }
        }
        ArcanaMod.LOGGER.info("[arcana-shots] bosses seen by client: {}", n);
    }

    static {
        if ("timestop".equals(System.getProperty("arcana.shots.mode"))) {
            at(40, () -> server(sp -> {
                sp.setGameMode(GameType.SURVIVAL);
                ServerLevel l = sp.serverLevel();
                l.setDayTime(18000);
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                sp.teleportTo(l, 0.5, y, 0.5, 0f, 8f);
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20000, 5, false, false));
                var m = sp.getData(ModAttachments.MAGIC);
                for (School s : School.real()) m.mastery.put(s.id, 40 * 5 * 5);
                sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.CHRONOS, 2));
                crowd(sp);
            }));
            at(70, () -> server(sp -> cast(sp, Spell.TIME_STOP)));
            at(90, () -> shot("50_timestop_a"));
            at(120, () -> shot("51_timestop_b"));
            at(160, () -> {
                ArcanaMod.LOGGER.info("[arcana-shots] done");
                Minecraft.getInstance().stop();
            });
        } else if ("boss".equals(System.getProperty("arcana.shots.mode"))) {
            at(40, () -> server(sp -> {
                sp.setGameMode(GameType.SURVIVAL);
                ServerLevel l = sp.serverLevel();
                l.setDayTime(6000);
                for (var old : l.getEntitiesOfClass(ArchmageEntity.class, sp.getBoundingBox().inflate(200))) old.discard();
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                sp.teleportTo(l, 0.5, y, 0.5, 0f, 4f);
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20000, 5, false, false));
                ArchmageEntity boss = ModEntities.ARCHMAGE.get().create(l);
                boss.moveTo(0.5, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 7), 7.5, 180f, 0f);
                boss.setNoAi(true);
                l.addFreshEntity(boss);
            }));
            at(70, ClientShots::bossDebug);
            at(80, () -> shot("40_boss_close_idle"));
            at(84, () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            at(96, () -> shot("41_boss_from_front"));
            at(100, () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
            at(110, () -> server(sp -> sp.serverLevel().getEntitiesOfClass(ArchmageEntity.class, sp.getBoundingBox().inflate(60)).forEach(b -> { b.setNoAi(false); b.hurt(sp.damageSources().genericKill(), 1000f); })));
            at(150, () -> shot("42_boss_death_a"));
            at(200, () -> shot("43_boss_death_b"));
            at(235, () -> shot("44_boss_death_c"));
            at(250, () -> {
                ArcanaMod.LOGGER.info("[arcana-shots] done");
                Minecraft.getInstance().stop();
            });
        } else {
        at(40, () -> server(sp -> {
            sp.setGameMode(GameType.SURVIVAL);
            ServerLevel l = sp.serverLevel();
            l.setDayTime(18000); // night makes the glowing 3D effects readable
            int x = 0, z = 0, y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            sp.teleportTo(l, x + 0.5, y, z + 0.5, 0f, 8f);
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20000, 5, false, false));
            var m = sp.getData(ModAttachments.MAGIC);
            for (School s : School.real()) m.mastery.put(s.id, 40 * 5 * 5);
            sp.setItemInHand(InteractionHand.MAIN_HAND, wand(School.STORM, 2));
            crowd(sp);
        }));
        Minecraft mc = Minecraft.getInstance();
        at(60, () -> mc.setScreen(new WandGallery(2)));
        at(68, () -> shot("00_gallery_ascended"));
        at(72, () -> mc.setScreen(new WandGallery(0)));
        at(80, () -> shot("00_gallery_dormant"));
        at(84, () -> mc.setScreen(null));
        at(95, () -> shot("01_first_person_wand"));
        at(98, () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        at(108, () -> shot("02_third_person_wand"));
        at(110, () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));

        Object[][] ults = {
            {School.VOID, Spell.EVENT_HORIZON, "event_horizon"}, {School.SOLAR, Spell.SUPERNOVA, "supernova"},
            {School.GLACIER, Spell.BLIZZARD, "blizzard"}, {School.CRIMSON, Spell.CRIMSON_CATACLYSM, "crimson_cataclysm"},
            {School.STORM, Spell.TEMPEST, "tempest"}, {School.CHRONOS, Spell.TIME_STOP, "time_stop"},
            {School.ARCANE, Spell.ARCANE_CATACLYSM, "arcane_cataclysm"}, {School.VERDANT, Spell.GROVE_GUARDIANS, "grove_guardians"},
            {School.NONE, Spell.RIFT_COLLAPSE, "rift_collapse"}};
        int base = 130;
        for (int i = 0; i < ults.length; i++) {
            final School sc = (School) ults[i][0];
            final Spell sp0 = (Spell) ults[i][1];
            final String nm = String.format("%02d_%s", 10 + i, ults[i][2]);
            int t0 = base + i * 170;
            at(t0, () -> server(sp -> {
                crowd(sp);
                sp.setItemInHand(InteractionHand.MAIN_HAND, wand(sc, 2));
            }));
            at(t0 + 8, () -> server(sp -> cast(sp, sp0)));
            at(t0 + 26, () -> shot(nm + "_a_title"));
            at(t0 + 62, () -> shot(nm + "_b_effect"));
            at(t0 + 100, () -> shot(nm + "_c_late"));
        }
        int tb = base + ults.length * 170 + 20;
        at(tb, () -> server(sp -> {
            ServerLevel l = sp.serverLevel();
            for (var z : l.getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(60))) z.discard();
            ArchmageEntity boss = ModEntities.ARCHMAGE.get().create(l);
            boss.moveTo(sp.getX(), l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) sp.getX(), (int) sp.getZ() + 9), sp.getZ() + 9, 180f, 0f);
            l.addFreshEntity(boss);
        }));
        at(tb + 30, () -> shot("30_boss_idle"));
        at(tb + 80, () -> shot("31_boss_attack"));
        at(tb + 110, () -> shot("32_boss_attack2"));
        at(tb + 120, () -> server(sp -> sp.serverLevel().getEntitiesOfClass(ArchmageEntity.class, sp.getBoundingBox().inflate(60)).forEach(b -> b.hurt(sp.damageSources().genericKill(), 1000f))));
        at(tb + 150, () -> shot("33_boss_death_a"));
        at(tb + 200, () -> shot("34_boss_death_b"));
        at(tb + 235, () -> shot("35_boss_death_c"));
        at(tb + 260, () -> {
            ArcanaMod.LOGGER.info("[arcana-shots] done");
            Minecraft.getInstance().stop();
        });
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("arcana.shots")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (t < 0) t = 0;
        t++;
        while (next < STEPS.size() && STEPS.get(next).tick() <= t) STEPS.get(next++).action().run();
    }

    private ClientShots() {}
}
