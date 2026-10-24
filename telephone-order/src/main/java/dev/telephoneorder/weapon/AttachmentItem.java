package dev.telephoneorder.weapon;

import dev.telephoneorder.Catalog.AttDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Map;
import java.util.function.Consumer;

public class AttachmentItem extends Item {
    public final AttDef def;
    public AttachmentItem(Item.Properties p, AttDef def) { super(p); this.def = def; }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Slot: " + def.slot().toUpperCase()).withStyle(ChatFormatting.DARK_AQUA));
        out.accept(Component.literal(def.desc()).withStyle(ChatFormatting.GRAY));
        for (Map.Entry<String, Double> e : def.mods().entrySet()) {
            String k = e.getKey();
            double v = e.getValue();
            if (k.equals("flash")) { out.accept(Component.literal("  No muzzle flash").withStyle(ChatFormatting.GREEN)); continue; }
            boolean good = switch (k) { case "dmg", "range", "mag", "cool" -> v >= 1; case "zoom" -> v < 1; default -> v <= 1; };
            String pct = (v >= 1 ? "+" : "") + Math.round((v - 1) * 100) + "%";
            String name = switch (k) { case "dmg" -> "Damage"; case "spread" -> "Spread"; case "recoil" -> "Recoil"; case "reload" -> "Reload time"; case "delay" -> "Cycle time";
                case "range" -> "Range"; case "noise" -> "Noise"; case "zoom" -> "Zoom field"; case "heat" -> "Heat"; case "cool" -> "Cooling"; case "mag" -> "Capacity"; default -> k; };
            out.accept(Component.literal("  " + name + " " + pct).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        out.accept(Component.literal("Fits: " + String.join(", ", def.fits())).withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.literal("Install at a Weapons Workbench.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
