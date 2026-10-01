package com.infinitemultiverse.client.screen;

import com.infinitemultiverse.client.ClientMultiverseState;
import com.infinitemultiverse.client.AbilityIcons;
import com.infinitemultiverse.client.MultiverseKeyMappings;
import com.infinitemultiverse.client.hud.AbilityHudLayer;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.network.SetLoadoutSlotPayload;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Multiverse control nexus: browse every system, search abilities, inspect stats and bind the loadout.
 * All state shown here comes from the server sync; binding goes back through {@link SetLoadoutSlotPayload}.
 */
public final class MultiverseMenuScreen extends Screen {
    private static final MultiverseSystem[] SYSTEMS = MultiverseSystem.values();
    private static final int PAD = 6;
    private static final int HEADER_HEIGHT = 22;
    private static final int COLUMN_LABEL_HEIGHT = 12;
    private static final int FOOTER_HEIGHT = 52;
    private static final int SYSTEM_ROW = 14;
    private static final int ABILITY_ROW = 20;
    private static final int LOADOUT_SLOT = 22;
    private static final int LOADOUT_GAP = 4;

    private static final int COLOR_PANEL = 0xE6070A12;
    private static final int COLOR_HEADER = 0xFF0B1424;
    private static final int COLOR_BORDER = 0xFF1E3A5A;
    private static final int COLOR_ACCENT = 0x7FD6FF;
    private static final int COLOR_TEXT = 0xE6F0FF;
    private static final int COLOR_MUTED = 0x8A9BB0;
    private static final int COLOR_DIM = 0x5A6880;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int systemsX;
    private int systemsWidth;
    private int listX;
    private int listWidth;
    private int detailsX;
    private int detailsWidth;
    private int contentTop;
    private int contentBottom;
    private int footerTop;

    private EditBox search;
    private String query = "";
    private MultiverseSystem selectedSystem = MultiverseSystem.CORE;
    @Nullable
    private Ability selectedAbility;
    private List<Ability> visible = List.of();
    private final Map<MultiverseSystem, Integer> abilityCounts = new EnumMap<>(MultiverseSystem.class);
    private int systemScroll;
    private int abilityScroll;

    public MultiverseMenuScreen() {
        super(Component.translatable("screen.infinitemultiverse.menu"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 480);
        panelHeight = Math.min(height - 16, 300);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        systemsWidth = Mth.clamp(panelWidth * 26 / 100, 92, 124);
        detailsWidth = Mth.clamp(panelWidth * 34 / 100, 104, 176);
        systemsX = left + PAD;
        listX = systemsX + systemsWidth + PAD;
        detailsX = left + panelWidth - PAD - detailsWidth;
        listWidth = detailsX - PAD - listX;
        contentTop = top + HEADER_HEIGHT + COLUMN_LABEL_HEIGHT;
        footerTop = top + panelHeight - FOOTER_HEIGHT;
        contentBottom = footerTop - 4;

        abilityCounts.clear();
        for (MultiverseSystem system : SYSTEMS) {
            abilityCounts.put(system, 0);
        }
        MultiverseRegistries.ABILITIES.forEach(ability -> abilityCounts.merge(ability.system(), 1, Integer::sum));

        int searchWidth = Math.min(140, panelWidth / 3);
        search = new EditBox(font, left + panelWidth - PAD - searchWidth, top + 5, searchWidth, 12,
                Component.translatable("screen.infinitemultiverse.search"));
        search.setHint(Component.translatable("screen.infinitemultiverse.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(48);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            abilityScroll = 0;
            refreshVisible();
        });
        addRenderableWidget(search);
        refreshVisible();
    }

    private void refreshVisible() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        Stream<Ability> stream = MultiverseRegistries.ABILITIES.stream();
        stream = needle.isEmpty()
                ? stream.filter(ability -> ability.system() == selectedSystem)
                : stream.filter(ability -> matches(ability, needle));
        visible = stream.sorted(Comparator.comparing((Ability ability) -> ability.displayName().getString())).toList();
        if (selectedAbility == null && !visible.isEmpty()) {
            selectedAbility = visible.get(0);
        }
    }

    private static boolean matches(Ability ability, String needle) {
        return ability.displayName().getString().toLowerCase(Locale.ROOT).contains(needle)
                || ability.id().getPath().contains(needle)
                || ability.system().displayName().getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean isUnlocked(Ability ability) {
        return ability.isUnlockedByDefault()
                || (minecraft != null && minecraft.player != null && minecraft.player.isCreative())
                || ClientMultiverseState.hasUnlocked(ability.id());
    }

    // ---- rendering ----

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, COLOR_PANEL);
        graphics.fill(left, top, left + panelWidth, top + HEADER_HEIGHT, COLOR_HEADER);
        graphics.fillGradient(left, top + HEADER_HEIGHT - 1, left + panelWidth, top + HEADER_HEIGHT, 0xFF38D4FF, 0xFF7B4DFF);
        graphics.renderOutline(left, top, panelWidth, panelHeight, COLOR_BORDER);
        graphics.vLine(listX - PAD / 2, top + HEADER_HEIGHT, footerTop, COLOR_BORDER);
        graphics.vLine(detailsX - PAD / 2, top + HEADER_HEIGHT, footerTop, COLOR_BORDER);
        graphics.hLine(left, left + panelWidth - 1, footerTop, COLOR_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = search.getX() - left - 3 * PAD;
        String title = font.plainSubstrByWidth(Component.translatable("screen.infinitemultiverse.title").getString(), titleWidth);
        graphics.drawString(font, title, left + PAD, top + 7, COLOR_ACCENT, false);

        int labelY = top + HEADER_HEIGHT + 2;
        graphics.drawString(font, Component.translatable("screen.infinitemultiverse.systems"), systemsX, labelY, COLOR_MUTED, false);
        Component listLabel = query.isBlank()
                ? selectedSystem.displayName()
                : Component.translatable("screen.infinitemultiverse.search_results");
        graphics.drawString(font, font.plainSubstrByWidth(listLabel.getString(), listWidth - 20), listX, labelY, COLOR_MUTED, false);
        graphics.drawString(font, Component.translatable("screen.infinitemultiverse.details"), detailsX, labelY, COLOR_MUTED, false);

        renderSystems(graphics, mouseX, mouseY);
        renderAbilities(graphics, mouseX, mouseY);
        renderDetails(graphics);
        renderFooter(graphics, mouseX, mouseY);
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderSystems(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = Math.max(1, (contentBottom - contentTop) / SYSTEM_ROW);
        systemScroll = Mth.clamp(systemScroll, 0, Math.max(0, SYSTEMS.length - rows));
        graphics.enableScissor(systemsX, contentTop, systemsX + systemsWidth, contentBottom);
        for (int i = systemScroll; i < Math.min(SYSTEMS.length, systemScroll + rows); i++) {
            MultiverseSystem system = SYSTEMS[i];
            int y = contentTop + (i - systemScroll) * SYSTEM_ROW;
            boolean selected = system == selectedSystem && query.isBlank();
            if (selected) {
                graphics.fill(systemsX, y, systemsX + systemsWidth, y + SYSTEM_ROW, 0x40000000 | system.color());
            } else if (isInside(mouseX, mouseY, systemsX, y, systemsWidth, SYSTEM_ROW)) {
                graphics.fill(systemsX, y, systemsX + systemsWidth, y + SYSTEM_ROW, 0x20FFFFFF);
            }
            graphics.fill(systemsX, y + 2, systemsX + 2, y + SYSTEM_ROW - 2, 0xFF000000 | system.color());

            int count = abilityCounts.getOrDefault(system, 0);
            boolean enabled = MultiverseConfig.isSystemEnabled(system);
            String tag = !enabled ? "OFF" : count > 0 ? Integer.toString(count) : "P" + system.plannedPhase();
            int nameColor = count == 0 ? COLOR_DIM : enabled ? COLOR_TEXT : COLOR_MUTED;
            String name = font.plainSubstrByWidth(system.displayName().getString(), systemsWidth - 12 - font.width(tag));
            graphics.drawString(font, name, systemsX + 6, y + 3, nameColor, false);
            graphics.drawString(font, tag, systemsX + systemsWidth - 3 - font.width(tag), y + 3,
                    count > 0 && enabled ? system.color() : COLOR_DIM, false);
        }
        graphics.disableScissor();
    }

    private void renderAbilities(GuiGraphics graphics, int mouseX, int mouseY) {
        if (visible.isEmpty()) {
            Component message;
            if (!query.isBlank()) {
                message = Component.translatable("screen.infinitemultiverse.no_matches");
            } else {
                message = Component.translatable("screen.infinitemultiverse.system_empty", selectedSystem.plannedPhase());
            }
            int y = contentTop + 4;
            for (FormattedCharSequence line : font.split(message, listWidth - 4)) {
                graphics.drawString(font, line, listX + 2, y, COLOR_MUTED, false);
                y += 10;
            }
            return;
        }
        int rows = Math.max(1, (contentBottom - contentTop) / ABILITY_ROW);
        abilityScroll = Mth.clamp(abilityScroll, 0, Math.max(0, visible.size() - rows));
        graphics.enableScissor(listX, contentTop, listX + listWidth, contentBottom);
        for (int i = abilityScroll; i < Math.min(visible.size(), abilityScroll + rows); i++) {
            Ability ability = visible.get(i);
            int y = contentTop + (i - abilityScroll) * ABILITY_ROW;
            int color = ability.system().color();
            if (ability == selectedAbility) {
                graphics.fill(listX, y, listX + listWidth, y + ABILITY_ROW, 0x48000000 | color);
                graphics.fill(listX, y, listX + 1, y + ABILITY_ROW, 0xFF000000 | color);
            } else if (isInside(mouseX, mouseY, listX, y, listWidth, ABILITY_ROW)) {
                graphics.fill(listX, y, listX + listWidth, y + ABILITY_ROW, 0x20FFFFFF);
            } else if (i % 2 == 0) {
                graphics.fill(listX, y, listX + listWidth, y + ABILITY_ROW, 0x0CFFFFFF);
            }
            AbilityIcons.draw(graphics, ability, listX + 2, y + 2, 16);

            boolean unlocked = isUnlocked(ability);
            String tag = tagFor(ability, unlocked);
            int tagWidth = tag.isEmpty() ? 0 : font.width(tag) + 4;
            String name = font.plainSubstrByWidth(ability.displayName().getString(), listWidth - 24 - tagWidth);
            graphics.drawString(font, name, listX + 21, y + 2, unlocked ? COLOR_TEXT : COLOR_MUTED, false);
            String stats = statsLine(ability);
            graphics.drawString(font, font.plainSubstrByWidth(stats, listWidth - 24), listX + 21, y + 11, COLOR_DIM, false);
            if (!tag.isEmpty()) {
                graphics.drawString(font, tag, listX + listWidth - font.width(tag) - 2, y + 2,
                        ClientMultiverseState.isActive(ability.id()) ? 0x7CFC9A : unlocked ? color : 0xFF6B6B, false);
            }
        }
        graphics.disableScissor();
    }

    private String tagFor(Ability ability, boolean unlocked) {
        if (!unlocked) {
            return Component.translatable("screen.infinitemultiverse.tag.locked").getString();
        }
        if (ClientMultiverseState.isActive(ability.id())) {
            return Component.translatable("screen.infinitemultiverse.tag.active").getString();
        }
        int slot = ClientMultiverseState.slotOf(ability.id());
        return slot >= 0 ? "[" + (slot + 1) + "]" : "";
    }

    private static String statsLine(Ability ability) {
        StringBuilder out = new StringBuilder();
        out.append(Math.round(ability.energyCost())).append(" EN");
        if (ability.activationType() == ActivationType.TOGGLE) {
            out.append(" + ").append(formatNumber(ability.upkeepPerSecond())).append("/s");
        }
        out.append("  ").append(formatNumber(ability.cooldownTicks() / 20f)).append("s CD");
        return out.toString();
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = detailsX;
        int y = contentTop;
        if (selectedAbility == null) {
            for (FormattedCharSequence line : font.split(selectedSystem.description(), detailsWidth)) {
                if (y + 9 > contentBottom) {
                    break;
                }
                graphics.drawString(font, line, x, y, COLOR_MUTED, false);
                y += 10;
            }
            return;
        }
        Ability ability = selectedAbility;
        int color = ability.system().color();
        AbilityIcons.draw(graphics, ability, x, y, 16);
        graphics.drawString(font, font.plainSubstrByWidth(ability.displayName().getString(), detailsWidth - 20), x + 20, y, color, false);
        graphics.drawString(font, font.plainSubstrByWidth(ability.system().displayName().getString(), detailsWidth - 20), x + 20, y + 9, COLOR_MUTED, false);
        y += 22;

        y = statRow(graphics, x, y, "screen.infinitemultiverse.stat.type", ability.activationType().displayName().getString());
        String energy = Math.round(ability.energyCost()) + (ability.activationType() == ActivationType.TOGGLE
                ? " + " + formatNumber(ability.upkeepPerSecond()) + "/s" : "");
        y = statRow(graphics, x, y, "screen.infinitemultiverse.stat.energy", energy);
        y = statRow(graphics, x, y, "screen.infinitemultiverse.stat.cooldown", formatNumber(ability.cooldownTicks() / 20f) + "s");
        y = statRow(graphics, x, y, "screen.infinitemultiverse.stat.status", statusOf(ability).getString());
        int slot = ClientMultiverseState.slotOf(ability.id());
        String bound = slot >= 0
                ? (slot + 1) + " (" + MultiverseKeyMappings.ABILITY_SLOTS.get(slot).get().getTranslatedKeyMessage().getString() + ")"
                : Component.translatable("screen.infinitemultiverse.unbound").getString();
        y = statRow(graphics, x, y, "screen.infinitemultiverse.stat.slot", bound);
        y += 4;

        for (FormattedCharSequence line : font.split(ability.description(), detailsWidth)) {
            if (y + 9 > contentBottom) {
                break;
            }
            graphics.drawString(font, line, x, y, 0xC8D3E0, false);
            y += 10;
        }
    }

    private int statRow(GuiGraphics graphics, int x, int y, String labelKey, String value) {
        graphics.drawString(font, Component.translatable(labelKey), x, y, COLOR_DIM, false);
        graphics.drawString(font, font.plainSubstrByWidth(value, detailsWidth - 54), x + 54, y, COLOR_TEXT, false);
        return y + 10;
    }

    private Component statusOf(Ability ability) {
        if (!isUnlocked(ability)) {
            return Component.translatable("screen.infinitemultiverse.status.locked");
        }
        if (!MultiverseConfig.isSystemEnabled(ability.system())) {
            return Component.translatable("screen.infinitemultiverse.status.disabled");
        }
        if (ClientMultiverseState.isActive(ability.id())) {
            return Component.translatable("screen.infinitemultiverse.status.active");
        }
        int cooldown = ClientMultiverseState.cooldown(ability.id());
        if (cooldown > 0) {
            return Component.translatable("screen.infinitemultiverse.status.cooldown", formatNumber(cooldown / 20f));
        }
        return Component.translatable("screen.infinitemultiverse.status.ready");
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + PAD;
        graphics.drawString(font, Component.translatable("screen.infinitemultiverse.loadout"), x, footerTop + 4, COLOR_MUTED, false);
        int slotY = footerTop + 15;
        for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
            int slotX = loadoutSlotX(slot);
            Optional<Ability> ability = ClientMultiverseState.loadoutSlot(slot).map(MultiverseRegistries.ABILITIES::get);
            int accent = ability.map(a -> a.system().color()).orElse(0x3A4A60);
            boolean hovered = isInside(mouseX, mouseY, slotX, slotY, LOADOUT_SLOT, LOADOUT_SLOT);
            graphics.fill(slotX, slotY, slotX + LOADOUT_SLOT, slotY + LOADOUT_SLOT, hovered ? 0xE0182436 : 0xC0101826);
            graphics.renderOutline(slotX, slotY, LOADOUT_SLOT, LOADOUT_SLOT, 0xFF000000 | (hovered && selectedAbility != null ? 0xFFFFFF : accent));
            ability.ifPresent(a -> AbilityIcons.draw(graphics, a, slotX + 3, slotY + 3, 16));

            String key = MultiverseKeyMappings.ABILITY_SLOTS.get(slot).get().getTranslatedKeyMessage().getString();
            graphics.pose().pushPose();
            graphics.pose().translate(slotX + LOADOUT_SLOT / 2f, slotY + LOADOUT_SLOT + 2f, 200f);
            graphics.pose().scale(0.6f, 0.6f, 1f);
            graphics.drawCenteredString(font, key.length() > 4 ? key.substring(0, 4) : key, 0, 0, 0xA8C4E0);
            graphics.pose().popPose();
        }

        int energyX = loadoutSlotX(PlayerMultiverseData.LOADOUT_SIZE) + 8;
        int energyWidth = left + panelWidth - PAD - energyX;
        if (energyWidth >= 60) {
            AbilityHudLayer.drawEnergy(graphics, font, energyX, footerTop + 4, footerTop + 15, energyWidth);
            List<FormattedCharSequence> hint = font.split(Component.translatable("screen.infinitemultiverse.loadout_hint"), energyWidth);
            int y = footerTop + 25;
            for (FormattedCharSequence line : hint) {
                if (y + 9 > top + panelHeight - 2) {
                    break;
                }
                graphics.drawString(font, line, energyX, y, COLOR_DIM, false);
                y += 9;
            }
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int slotY = footerTop + 15;
        for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
            if (!isInside(mouseX, mouseY, loadoutSlotX(slot), slotY, LOADOUT_SLOT, LOADOUT_SLOT)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            Optional<Ability> bound = ClientMultiverseState.loadoutSlot(slot).map(MultiverseRegistries.ABILITIES::get);
            lines.add(bound.map(Ability::displayName).orElse(Component.translatable("screen.infinitemultiverse.empty_slot")));
            lines.add(Component.translatable("screen.infinitemultiverse.slot_key", slot + 1,
                    MultiverseKeyMappings.ABILITY_SLOTS.get(slot).get().getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
            if (selectedAbility != null) {
                lines.add(Component.translatable("screen.infinitemultiverse.click_to_bind", selectedAbility.displayName()).withStyle(ChatFormatting.AQUA));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, mouseY, systemsX, contentTop, systemsWidth, contentBottom - contentTop)) {
            int index = systemScroll + (mouseY - contentTop) / SYSTEM_ROW;
            if (index >= 0 && index < SYSTEMS.length) {
                graphics.renderTooltip(font, font.split(SYSTEMS[index].description(), 220), mouseX, mouseY);
            }
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        setFocused(null);

        if (isInside(mouseX, mouseY, systemsX, contentTop, systemsWidth, contentBottom - contentTop)) {
            int index = systemScroll + (int) (mouseY - contentTop) / SYSTEM_ROW;
            if (index >= 0 && index < SYSTEMS.length) {
                selectedSystem = SYSTEMS[index];
                selectedAbility = null;
                abilityScroll = 0;
                search.setValue("");
                refreshVisible();
                playUi(ModSounds.UI_SELECT.get());
                return true;
            }
        }

        if (isInside(mouseX, mouseY, listX, contentTop, listWidth, contentBottom - contentTop)) {
            int index = abilityScroll + (int) (mouseY - contentTop) / ABILITY_ROW;
            if (index >= 0 && index < visible.size()) {
                selectedAbility = visible.get(index);
                playUi(ModSounds.UI_SELECT.get());
                return true;
            }
        }

        int slotY = footerTop + 15;
        for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
            if (isInside(mouseX, mouseY, loadoutSlotX(slot), slotY, LOADOUT_SLOT, LOADOUT_SLOT)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    PacketDistributor.sendToServer(new SetLoadoutSlotPayload(slot, Optional.empty()));
                    playUi(ModSounds.UI_SELECT.get());
                } else {
                    bindSelected(slot);
                }
                return true;
            }
        }
        return false;
    }

    private void bindSelected(int slot) {
        if (selectedAbility == null) {
            return;
        }
        if (!isUnlocked(selectedAbility)) {
            playUi(ModSounds.UI_DENIED.get());
            return;
        }
        PacketDistributor.sendToServer(new SetLoadoutSlotPayload(slot, Optional.of(selectedAbility.id())));
        playUi(ModSounds.UI_BIND.get());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) Math.signum(scrollY);
        if (isInside(mouseX, mouseY, systemsX, contentTop, systemsWidth, contentBottom - contentTop)) {
            systemScroll -= delta;
            return true;
        }
        if (isInside(mouseX, mouseY, listX, contentTop, listWidth, contentBottom - contentTop)) {
            abilityScroll -= delta;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!search.isFocused()) {
            if (MultiverseKeyMappings.OPEN_MENU.get().matches(keyCode, scanCode)) {
                onClose();
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + PlayerMultiverseData.LOADOUT_SIZE) {
                bindSelected(keyCode - GLFW.GLFW_KEY_1);
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) && !visible.isEmpty()) {
                int current = selectedAbility == null ? -1 : visible.indexOf(selectedAbility);
                int next = Mth.clamp(current + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, visible.size() - 1);
                selectedAbility = visible.get(next);
                int rows = Math.max(1, (contentBottom - contentTop) / ABILITY_ROW);
                if (next < abilityScroll) {
                    abilityScroll = next;
                } else if (next >= abilityScroll + rows) {
                    abilityScroll = next - rows + 1;
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- helpers ----

    private int loadoutSlotX(int slot) {
        return left + PAD + slot * (LOADOUT_SLOT + LOADOUT_GAP);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String formatNumber(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private void playUi(SoundEvent sound) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
        }
    }
}
