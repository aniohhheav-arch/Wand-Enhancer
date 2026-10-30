package dev.infinitygauntlet.client.gui;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.client.ClientCooldowns;
import dev.infinitygauntlet.client.HeldGauntlet;
import dev.infinitygauntlet.client.Keybinds;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Radial ability wheel (hold the wheel key). The inner ring picks a stone, the outer ring that stone's abilities;
 * releasing the key or clicking confirms. The game keeps running underneath.
 */
public class AbilityWheelScreen extends Screen {
    private static final ResourceLocation GLYPH = InfinityGauntlet.id("textures/vfx/glyph_space.png");
    private static final int INNER = 34;
    private static final int OUTER = 92;

    private InfinityStone stone;
    private int hoveredAbility = -1;
    private InfinityStone hoveredStone;
    private int age;

    public AbilityWheelScreen() {
        super(Component.translatable("screen.infinitygauntlet.wheel"));
    }

    @Override
    protected void init() {
        ItemStack stack = HeldGauntlet.find(Minecraft.getInstance().player);
        stone = InfinityGauntletItem.state(stack).selected();
    }

    @Override
    public void tick() {
        age++;
        if (HeldGauntlet.find(Minecraft.getInstance().player).isEmpty()) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GauntletState state = InfinityGauntletItem.state(HeldGauntlet.find(Minecraft.getInstance().player));
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.atan2(dy, dx);
        float t = age + partialTick;

        g.fill(0, 0, width, height, 0x66000000);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(t * 0.01f));
        g.setColor(VfxColor.r(stone.color()), VfxColor.g(stone.color()), VfxColor.b(stone.color()), 0.5f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(GLYPH, -OUTER - 30, -OUTER - 30, (OUTER + 30) * 2, (OUTER + 30) * 2, 0, 0, 128, 128, 128, 128);
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();

        // inner ring: stones
        hoveredStone = null;
        List<InfinityStone> stones = state.stones();
        for (int i = 0; i < stones.size(); i++) {
            InfinityStone s = stones.get(i);
            double a = -Math.PI / 2 + i * Math.PI * 2 / stones.size();
            int x = cx + (int) (Math.cos(a) * INNER);
            int y = cy + (int) (Math.sin(a) * INNER);
            boolean hovered = dist < INNER + 12 && dist > 8 && angularDistance(angle, a) < Math.PI / Math.max(1, stones.size());
            if (hovered) {
                hoveredStone = s;
            }
            if (s == stone || hovered) {
                g.fill(x - 9, y - 9, x + 9, y + 9, (s == stone ? 0xC0 : 0x70) << 24 | s.color());
            }
            g.blit(InfinityGauntlet.id("textures/item/" + s.getSerializedName() + "_stone.png"), x - 6, y - 6, 0, 0, 12, 12, 12, 12);
        }

        // outer ring: abilities of the selected stone
        hoveredAbility = -1;
        List<Ability> abilities = Abilities.forStone(stone);
        for (int i = 0; i < abilities.size(); i++) {
            Ability ability = abilities.get(i);
            double a = -Math.PI / 2 + i * Math.PI * 2 / abilities.size();
            int x = cx + (int) (Math.cos(a) * OUTER);
            int y = cy + (int) (Math.sin(a) * OUTER);
            boolean hovered = dist > INNER + 14 && angularDistance(angle, a) < Math.PI / abilities.size();
            if (hovered) {
                hoveredAbility = i;
            }
            boolean selected = state.selected() == stone && state.abilityIndex(stone) == i;
            Component name = ability.displayName();
            int tw = font.width(name);
            int bg = hovered ? 0xE0000000 | stone.color() : selected ? 0xC0000000 | (stone.color() >> 1 & 0x7F7F7F) : 0xB0101018;
            g.fill(x - tw / 2 - 4, y - 7, x + tw / 2 + 4, y + 7, bg);
            float cd = ClientCooldowns.fraction(ability.id());
            if (cd > 0) {
                g.fill(x - tw / 2 - 4, y + 5, x - tw / 2 - 4 + (int) ((tw + 8) * (1 - cd)), y + 7, 0xFFFF5555);
            }
            g.drawString(font, name, x - tw / 2, y - 4, hovered ? 0xFFFFFFFF : 0xFFD0D8FF);
        }

        Component title = hoveredAbility >= 0 ? abilities.get(hoveredAbility).description()
                : hoveredStone != null ? hoveredStone.displayName() : stone.displayName();
        int maxW = Math.min(260, width - 20);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(title, maxW);
        int ty = cy + OUTER + 22;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(font, line, cx - font.width(line) / 2, ty, 0xFFFFFFFF);
            ty += 10;
        }
        g.drawCenteredString(font, Component.translatable("screen.infinitygauntlet.wheel.hint"), cx, cy - OUTER - 36, 0xFF9AA4C0);
    }

    private static double angularDistance(double a, double b) {
        return Math.abs(Mth.wrapDegrees(Math.toDegrees(a - b))) * Math.PI / 180;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (Keybinds.ABILITY_WHEEL.matches(keyCode, scanCode)) {
            confirm();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredStone != null) {
            stone = hoveredStone;
            return true;
        }
        if (hoveredAbility >= 0) {
            confirm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void confirm() {
        if (hoveredStone != null && hoveredStone != stone) {
            stone = hoveredStone;
        }
        GauntletState state = InfinityGauntletItem.state(HeldGauntlet.find(Minecraft.getInstance().player));
        if (hoveredAbility >= 0) {
            HeldGauntlet.select(stone, hoveredAbility);
        } else if (stone != state.selected() && state.has(stone)) {
            HeldGauntlet.select(stone, state.abilityIndex(stone));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** Tiny colour helpers so the wheel can tint by stone. */
    private static final class VfxColor {
        static float r(int c) {
            return (c >> 16 & 255) / 255f;
        }

        static float g(int c) {
            return (c >> 8 & 255) / 255f;
        }

        static float b(int c) {
            return (c & 255) / 255f;
        }
    }
}
