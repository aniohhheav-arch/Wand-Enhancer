package com.infinitemultiverse.client.hud;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.client.ClientMultiverseState;
import com.infinitemultiverse.client.AbilityIcons;
import com.infinitemultiverse.client.MultiverseKeyMappings;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.core.energy.EnergyPool;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Compact energy bar + 5-slot ability loadout. Drawn procedurally so it scales cleanly with any GUI scale. */
public final class AbilityHudLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = InfiniteMultiverse.id("ability_hud");

    public static final int SLOT_SIZE = 20;
    public static final int SLOT_GAP = 3;
    private static final int WIDTH = PlayerMultiverseData.LOADOUT_SIZE * SLOT_SIZE + (PlayerMultiverseData.LOADOUT_SIZE - 1) * SLOT_GAP;
    private static final int BAR_HEIGHT = 5;
    private static final int POOL_ROW = 9 + BAR_HEIGHT + 3;
    private static final int SLOTS_HEIGHT = SLOT_SIZE + 8;
    private static final float OVERLAY_Z = 200f;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()
                || minecraft.getDebugOverlay().showDebugScreen()
                || !MultiverseConfig.CLIENT.hudEnabled.get() || !ClientMultiverseState.isSynced()) {
            return;
        }
        int offsetX = MultiverseConfig.CLIENT.hudOffsetX.get();
        int offsetY = MultiverseConfig.CLIENT.hudOffsetY.get();
        List<EnergyPool> pools = poolsInUse();
        int height = pools.size() * POOL_ROW + SLOTS_HEIGHT;
        int x = switch (MultiverseConfig.CLIENT.hudAnchor.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - WIDTH - offsetX;
        };
        int y = switch (MultiverseConfig.CLIENT.hudAnchor.get()) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - height - offsetY;
        };
        float time = (minecraft.player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20f;

        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + height, 0x90080B14);
        graphics.renderOutline(x - 3, y - 3, WIDTH + 6, height + 3, 0x5038D4FF);

        Font font = minecraft.font;
        int rowY = y;
        for (EnergyPool pool : pools) {
            drawEnergy(graphics, font, pool, x, rowY, rowY + 9, WIDTH);
            rowY += POOL_ROW;
        }

        int slotY = rowY;
        for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
            int slotX = x + slot * (SLOT_SIZE + SLOT_GAP);
            drawSlot(graphics, font, slot, slotX, slotY, time);
        }
    }

    /** MULTIVERSE always, plus any other pool used by a bound ability. */
    public static List<EnergyPool> poolsInUse() {
        EnumSet<EnergyPool> pools = EnumSet.of(EnergyPool.MULTIVERSE);
        for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
            ClientMultiverseState.loadoutSlot(slot).map(MultiverseRegistries.ABILITIES::get).ifPresent(a -> pools.add(a.energyPool()));
        }
        return List.copyOf(pools);
    }

    public static void drawEnergy(GuiGraphics graphics, Font font, EnergyPool pool, int x, int labelY, int barY, int width) {
        float max = ClientMultiverseState.maxEnergy();
        float shown = Mth.clamp(ClientMultiverseState.displayedEnergy(pool) / max, 0f, 1f);
        String amount = String.format(Locale.ROOT, "%d/%d", Math.round(ClientMultiverseState.energy(pool)), Math.round(max));
        String label = font.plainSubstrByWidth(pool.displayName().getString().toUpperCase(Locale.ROOT), width - font.width(amount) - 4);
        graphics.drawString(font, label, x, labelY, pool.topColor(), false);
        graphics.drawString(font, amount, x + width - font.width(amount), labelY, 0xE6F7FF, false);

        graphics.fill(x, barY, x + width, barY + BAR_HEIGHT, 0xFF0D1626);
        int filled = Math.round(width * shown);
        if (filled > 0) {
            graphics.fillGradient(x, barY, x + filled, barY + BAR_HEIGHT, 0xFF000000 | pool.topColor(), 0xFF000000 | pool.bottomColor());
            graphics.fill(x, barY, x + filled, barY + 1, 0x80FFFFFF);
        }
        graphics.renderOutline(x - 1, barY - 1, width + 2, BAR_HEIGHT + 2, 0xFF1F3550);
    }

    private static void drawSlot(GuiGraphics graphics, Font font, int slot, int x, int y, float time) {
        Optional<Ability> ability = ClientMultiverseState.loadoutSlot(slot).map(MultiverseRegistries.ABILITIES::get);
        int accent = ability.map(a -> a.system().color()).orElse(0x3A4A60);

        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xC0101826);
        graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF000000 | accent);

        if (ability.isPresent()) {
            Ability a = ability.get();
            AbilityIcons.draw(graphics, a, x + 2, y + 2, 16);

            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, OVERLAY_Z);
            if (ClientMultiverseState.isActive(a.id())) {
                int alpha = (int) (140 + 110 * Math.sin(time * Math.PI * 2.0));
                graphics.renderOutline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, (alpha << 24) | accent);
                graphics.renderOutline(x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, (alpha / 2 << 24) | 0xFFFFFF);
            }
            int cooldown = ClientMultiverseState.cooldown(a.id());
            if (cooldown > 0) {
                float fraction = Mth.clamp(cooldown / (float) Math.max(1, a.cooldownTicks()), 0f, 1f);
                int top = y + SLOT_SIZE - Math.round(SLOT_SIZE * fraction);
                graphics.fill(x + 1, top, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xB0000000);
                String seconds = cooldown >= 200 ? Integer.toString(cooldown / 20) : String.format(Locale.ROOT, "%.1f", cooldown / 20f);
                graphics.drawCenteredString(font, seconds, x + SLOT_SIZE / 2, y + 6, 0xFFFFFF);
            } else if (!ClientMultiverseState.isActive(a.id()) && ClientMultiverseState.energy(a.energyPool()) < a.energyCost()
                    && !Minecraft.getInstance().player.isCreative()) {
                graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x60FF2020);
            }
            graphics.pose().popPose();
        }

        String key = MultiverseKeyMappings.ABILITY_SLOTS.get(slot).get().getTranslatedKeyMessage().getString();
        if (key.length() > 3) {
            key = key.substring(0, 3);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + SLOT_SIZE / 2f, y + SLOT_SIZE + 2f, OVERLAY_Z);
        graphics.pose().scale(0.6f, 0.6f, 1f);
        graphics.drawCenteredString(font, key, 0, 0, 0xA8C4E0);
        graphics.pose().popPose();
    }
}
