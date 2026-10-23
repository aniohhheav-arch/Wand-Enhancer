package com.wandcraft.magic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-level Wandcraft settings, stored with the overworld so they survive restarts and apply to everyone. */
public final class WandSettings extends SavedData {
    private static final String KEY = "wandcraft_settings";

    private boolean cooldownsDisabled;

    public static WandSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WandSettings::new, WandSettings::load, null), KEY);
    }

    private static WandSettings load(CompoundTag tag, HolderLookup.Provider lookup) {
        WandSettings s = new WandSettings();
        s.cooldownsDisabled = tag.getBoolean("cooldownsDisabled");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("cooldownsDisabled", cooldownsDisabled);
        return tag;
    }

    public boolean cooldownsDisabled() { return cooldownsDisabled; }

    public void setCooldownsDisabled(boolean off) {
        cooldownsDisabled = off;
        setDirty();
    }
}
