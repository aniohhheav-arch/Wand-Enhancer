package dev.finalframe.client.dev;

import com.mojang.logging.LogUtils;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.FinisherManager;
import dev.finalframe.registry.FFRegistry;
import dev.finalframe.sheriff.SheriffFinisher;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

/**
 * Development-only director (enabled with {@code -Dfinalframe.autotest=true}, see the {@code autotest}
 * run in build.gradle). It builds a flat test world, stages a target, exercises the revolver and
 * performs the finisher while saving screenshots of every beat to {@code screenshots/}.
 */
public final class AutoDirector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Integer> BEATS = new TreeSet<>(Set.of(
        2, 7, 10, 13, 18, 25, 30, 38, 44, 52, 60, 66, 72, 77, 82, 86, 92, 98, 106, 110, 116, 121, 125, 130, 136, 141, 145,
        149, 153, 154, 156, 159, 165, 170, 176, 182, 190, 198, 204, 210));

    private static boolean worldRequested;
    private static int ticksInWorld;
    private static int sessionSeen = -1;
    private static int afterEnd = -1;
    private static int lastShotBeat = -1;

    private AutoDirector() {
    }

    public static void install(IEventBus bus) {
        if (!Boolean.getBoolean("finalframe.autotest")) {
            return;
        }
        LOGGER.info("Final Frame auto director enabled");
        bus.addListener(AutoDirector::onScreen);
        bus.addListener(AutoDirector::onTick);
    }

    private static void onScreen(ScreenEvent.Init.Post event) {
        if (worldRequested || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings("finalframe-autotest", GameType.CREATIVE, false, Difficulty.EASY, true, rules,
            WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("finalframe-autotest", settings, new WorldOptions(1234L, false, false),
            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            event.getScreen());
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            return;
        }
        ticksInWorld++;
        int k = ticksInWorld;
        if (k == 20) {
            mc.getSingleplayerServer().execute(AutoDirector::stage);
        }
        if (k == 25) {
            mc.player.getInventory().selected = 0;
        }
        if (k == 60) {
            shot("01_first_person");
        }
        if (k == 62) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (k == 72) {
            shot("02_third_person_front");
        }
        if (k == 74) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (k == 84) {
            shot("03_third_person_back");
        }
        if (k == 86) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setScreen(new Showcase());
        }
        if (k == 96) {
            shot("04_item_showcase");
            mc.setScreen(null);
        }
        if (k == 100) {
            mc.player.setXRot(-25f);
            mc.player.setYRot(30f);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            lastShotBeat = k;
        }
        if (k == 101) {
            shot("05_recoil");
        }
        if (k == 106) {
            mc.player.setYRot(0f);
            mc.player.setXRot(8f);
        }
        if (k == 115) {
            shot("06_prompt_view");
            mc.getSingleplayerServer().execute(AutoDirector::startFinisher);
        }

        ClientSession s = ClientFinisherManager.INSTANCE.local();
        if (s != null) {
            sessionSeen = s.tick();
            if (BEATS.contains(s.tick())) {
                shot(String.format("f%03d", s.tick()));
            }
        } else if (sessionSeen >= 0) {
            if (afterEnd < 0) {
                afterEnd = k;
            }
            if (k == afterEnd + 20) {
                shot("zz_after");
            }
            if (k == afterEnd + 30) {
                LOGGER.info("Final Frame auto director finished");
                mc.stop();
            }
        }
        if (k > 2400) {
            LOGGER.error("Final Frame auto director timed out");
            mc.stop();
        }
    }

    private static void stage() {
        Minecraft mc = Minecraft.getInstance();
        ServerLevel level = mc.getSingleplayerServer().overworld();
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        level.setDayTime(5200);
        level.setWeatherParameters(6000, 0, false, false);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        // A little frontier set dressing so the camera work has depth cues.
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 14; z++) {
                level.setBlock(new BlockPos(x, y - 1, z), (x * 7 + z * 13) % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : Blocks.SAND.defaultBlockState(), 3);
            }
        }
        for (int z = -6; z <= 12; z += 3) {
            level.setBlock(new BlockPos(-6, y, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(6, y, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(4, y, 8), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        level.setBlock(new BlockPos(-4, y, 9), Blocks.CACTUS.defaultBlockState(), 3);
        level.setBlock(new BlockPos(-4, y + 1, 9), Blocks.CACTUS.defaultBlockState(), 3);
        level.setBlock(new BlockPos(3, y, -5), Blocks.BARREL.defaultBlockState(), 3);

        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(FFRegistry.SHERIFFS_LAST_WORD.get()));
        player.getInventory().selected = 0;
        player.connection.teleport(0.5, y, 0.5, 0f, 8f);

        Vindicator v = EntityType.VINDICATOR.create(level);
        if (v != null) {
            v.moveTo(0.5, y, 2.6, 0f, 0f);
            v.setYHeadRot(0f);
            v.setYBodyRot(0f);
            v.setNoAi(true);
            v.setPersistenceRequired();
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.COMMAND, null);
            v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            level.addFreshEntity(v);
        }
    }

    private static void startFinisher() {
        Minecraft mc = Minecraft.getInstance();
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        Vindicator v = level.getEntitiesOfClass(Vindicator.class, player.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
        if (v == null) {
            LOGGER.error("Auto director: no target");
            return;
        }
        player.setShiftKeyDown(true);
        boolean ok = FinisherManager.tryStart(player, v, SheriffFinisher.INSTANCE);
        player.setShiftKeyDown(false);
        LOGGER.info("Auto director: finisher started = {}", ok);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> { });
    }

    private static final class Showcase extends Screen {
        Showcase() {
            super(Component.literal("Showcase"));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xFF2A2420);
            ItemStack stack = new ItemStack(FFRegistry.SHERIFFS_LAST_WORD.get());
            g.pose().pushPose();
            g.pose().translate(width / 2f - 96, height / 2f - 110, 0);
            g.pose().scale(12f, 12f, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            g.renderItem(stack, 20, height - 40);
            g.renderItemDecorations(font, stack, 20, height - 40);
            g.renderTooltip(font, stack, width / 2 + 40, height / 2 + 50);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
