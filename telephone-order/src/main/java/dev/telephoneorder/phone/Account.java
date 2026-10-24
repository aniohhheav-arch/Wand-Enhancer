package dev.telephoneorder.phone;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.ModItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Per-player persistent account: credits, clearance, unlocked secret lines, orders and order history. */
public final class Account {
    public static AttachmentType<CompoundTag> TYPE;

    private Account() {}

    public static void register() {
        TYPE = AttachmentRegistry.create(ModItems.id("account"), b -> b.persistent(CompoundTag.CODEC).copyOnDeath().initializer(CompoundTag::new));
    }

    public static CompoundTag of(ServerPlayer p) {
        CompoundTag t = p.getAttachedOrCreate(TYPE);
        if (!t.contains("init")) {
            t.putBoolean("init", true);
            t.putInt("credits", ModConfig.get().startingCredits);
            t.putInt("spent", 0);
            t.putInt("ringtone", ModConfig.get().ringtone);
            t.putInt("nextOid", 1);
        }
        return t;
    }

    public static int credits(ServerPlayer p) { return of(p).getIntOr("credits", 0); }
    public static int spent(ServerPlayer p) { return of(p).getIntOr("spent", 0); }

    public static void add(ServerPlayer p, int n) {
        CompoundTag t = of(p);
        t.putInt("credits", Math.max(0, t.getIntOr("credits", 0) + n));
    }

    public static boolean pay(ServerPlayer p, int n) {
        CompoundTag t = of(p);
        int c = t.getIntOr("credits", 0);
        if (c < n) return false;
        t.putInt("credits", c - n);
        t.putInt("spent", t.getIntOr("spent", 0) + n);
        return true;
    }

    /** clearance level 0..3 (Standard..Prototype). */
    public static int tier(ServerPlayer p) { return tierFor(spent(p)); }

    public static int tierFor(int spent) {
        int lvl = 0;
        for (int i = 0; i < Catalog.TIER_THRESHOLDS.length; i++) if (spent >= Catalog.TIER_THRESHOLDS[i]) lvl = i;
        return lvl;
    }

    public static boolean has(ServerPlayer p, String key) {
        for (Tag t : of(p).getListOrEmpty("unlocks")) if (t instanceof StringTag s && s.value().equals(key)) return true;
        return false;
    }

    public static void unlock(ServerPlayer p, String key) {
        if (has(p, key)) return;
        CompoundTag t = of(p);
        ListTag l = t.getListOrEmpty("unlocks");
        ListTag n = new ListTag();
        n.addAll(l);
        n.add(StringTag.valueOf(key));
        t.put("unlocks", n);
    }

    public static List<String> unlocks(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (Tag t : of(p).getListOrEmpty("unlocks")) if (t instanceof StringTag s) out.add(s.value());
        return out;
    }

    public static boolean flag(ServerPlayer p, String k) { return of(p).getBooleanOr("f_" + k, false); }
    public static void setFlag(ServerPlayer p, String k, boolean v) { of(p).putBoolean("f_" + k, v); }

    public static int clues(ServerPlayer p) { return of(p).getIntOr("clues", 0); }
    public static void addClue(ServerPlayer p, int idx) { CompoundTag t = of(p); t.putInt("clues", t.getIntOr("clues", 0) | (1 << idx)); }

    public static List<CompoundTag> list(ServerPlayer p, String key) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag t : of(p).getListOrEmpty(key)) if (t instanceof CompoundTag c) out.add(c);
        return out;
    }

    public static void saveList(ServerPlayer p, String key, List<CompoundTag> items) {
        ListTag l = new ListTag();
        l.addAll(items);
        of(p).put(key, l);
    }
}
