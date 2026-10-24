package dev.telephoneorder.weapon;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Reads / writes the per-stack state of a gun (ammo, reserve, heat, attachments...) in CUSTOM_DATA. */
public final class GunData {
    private GunData() {}

    public static CompoundTag read(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void edit(ItemStack s, Consumer<CompoundTag> c) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, c);
    }

    public static int ammo(ItemStack s) { return read(s).getIntOr("ammo", 0); }
    public static int reserve(ItemStack s) { return read(s).getIntOr("reserve", 0); }
    public static float heat(ItemStack s) { return read(s).getFloatOr("heat", 0f); }
    public static String ammoMod(ItemStack s) { return read(s).getStringOr("amod", ""); }
    public static int mode(ItemStack s) { return read(s).getIntOr("mode", 0); }
    public static boolean overheated(ItemStack s) { return read(s).getBooleanOr("oh", false); }
    public static long reloadEnd(ItemStack s) { return read(s).getLongOr("rend", 0L); }
    public static boolean reloading(ItemStack s) { return read(s).getLongOr("rend", 0L) > 0; }

    public static String attachment(ItemStack s, String slot) {
        return read(s).getCompoundOrEmpty("att").getStringOr(slot, "");
    }

    public static List<AttDef> attachments(ItemStack s) {
        List<AttDef> out = new ArrayList<>();
        CompoundTag att = read(s).getCompoundOrEmpty("att");
        for (String k : att.keySet()) {
            AttDef a = Catalog.ATT_BY_ID.get(att.getStringOr(k, ""));
            if (a != null) out.add(a);
        }
        return out;
    }

    public static String finish(ItemStack s) {
        String f = attachment(s, "finish");
        AttDef a = Catalog.ATT_BY_ID.get(f);
        return a == null ? "" : a.finish();
    }

    /** Re-builds the CUSTOM_MODEL_DATA component the item models key on (reload stage, attachment flags, finish). */
    public static void syncModel(ItemStack s, int reloadStage) {
        CompoundTag att = read(s).getCompoundOrEmpty("att");
        List<Boolean> flags = new ArrayList<>();
        for (AttDef a : Catalog.ATTS) flags.add(att.getStringOr(a.slot(), "").equals(a.id()));
        CustomModelData next = new CustomModelData(List.of((float) reloadStage), flags, List.of(finish(s)), List.of());
        if (!next.equals(s.get(DataComponents.CUSTOM_MODEL_DATA))) s.set(DataComponents.CUSTOM_MODEL_DATA, next);
    }

    public static int stage(ItemStack s) {
        CustomModelData d = s.get(DataComponents.CUSTOM_MODEL_DATA);
        if (d == null || d.floats().isEmpty()) return 0;
        return Math.round(d.floats().get(0));
    }

    /** Fresh, full magazine and a starter reserve. */
    public static CompoundTag fresh(GunDef def) {
        CompoundTag t = new CompoundTag();
        t.putInt("ammo", def.mag());
        t.putInt("reserve", 0);
        t.putInt("mode", 0);
        return t;
    }

    public static ItemStack newStack(GunDef def, net.minecraft.world.item.Item item) {
        ItemStack s = new ItemStack(item);
        edit(s, t -> { t.putInt("ammo", def.mag()); t.putInt("reserve", 0); });
        syncModel(s, 0);
        return s;
    }
}
