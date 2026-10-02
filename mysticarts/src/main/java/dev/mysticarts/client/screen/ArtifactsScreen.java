package dev.mysticarts.client.screen;

import dev.mysticarts.client.CasterStates;
import dev.mysticarts.client.ClientPower;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.registry.MaItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shows the four bound artifacts and your mystic training; unbind artifacts or toggle the cloak's flight here. */
public class ArtifactsScreen extends Screen {
    public ArtifactsScreen() {
        super(Component.translatable("screen.mysticarts.artifacts"));
    }

    private static ItemStack iconFor(Artifact a) {
        return new ItemStack(switch (a) {
            case CLOAK -> MaItems.CLOAK_OF_LEVITATION.get();
            case AMULET -> MaItems.EYE_OF_AGAMOTTO.get();
            case RING -> MaItems.SLING_RING.get();
            case BRACERS -> MaItems.MYSTIC_WRIST_WRAPS.get();
        });
    }

    @Override
    protected void init() {
        int cx = width / 2, top = height / 2 - 60;
        for (Artifact a : Artifact.values()) {
            int y = top + a.ordinal() * 26;
            Button b = Button.builder(Component.translatable("screen.mysticarts.unbind"), btn -> {
                PacketDistributor.sendToServer(new Payloads.ArtifactAction(Payloads.ArtifactAction.UNBIND, a.ordinal()));
                btn.active = false;
            }).bounds(cx + 50, y, 70, 20).build();
            b.active = bound(a);
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(flightLabel(), btn -> {
            PacketDistributor.sendToServer(new Payloads.ArtifactAction(Payloads.ArtifactAction.TOGGLE_FLIGHT, 0));
            btn.setMessage(Component.translatable(ClientPower.has(Payloads.PowerSync.F_CLOAK_FLIGHT) ? "screen.mysticarts.flight_off" : "screen.mysticarts.flight_on"));
        }).bounds(cx - 100, top + 4 * 26 + 6, 200, 20).build());
    }

    private Component flightLabel() {
        return Component.translatable(ClientPower.has(Payloads.PowerSync.F_CLOAK_FLIGHT) ? "screen.mysticarts.flight_on" : "screen.mysticarts.flight_off");
    }

    private boolean bound(Artifact a) {
        return minecraft != null && minecraft.player != null && CasterStates.hasArtifact(minecraft.player.getId(), a.ordinal());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2, top = height / 2 - 60;
        g.drawCenteredString(font, title, cx, top - 34, 0xFFD27A);
        int tiers = ClientPower.tiers();
        Component training = Component.translatable((tiers & PowerData.TIER_ADEPT) != 0 ? "screen.mysticarts.tier_adept"
                : (tiers & PowerData.TIER_INITIATE) != 0 ? "screen.mysticarts.tier_initiate" : "screen.mysticarts.tier_none");
        g.drawCenteredString(font, training, cx, top - 20, 0xC8C8C8);
        for (Artifact a : Artifact.values()) {
            int y = top + a.ordinal() * 26;
            boolean has = bound(a);
            g.fill(cx - 120, y, cx + 45, y + 20, has ? 0x66000000 | (a.color & 0x333333) : 0x44000000);
            g.renderItem(iconFor(a), cx - 116, y + 2);
            g.drawString(font, Component.translatable("artifact.mysticarts." + a.id), cx - 94, y + 2, has ? 0xFFFFFF : 0x777777);
            g.drawString(font, Component.translatable(has ? "screen.mysticarts.bound" : "screen.mysticarts.empty"), cx - 94, y + 11, has ? a.color : 0x555555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
