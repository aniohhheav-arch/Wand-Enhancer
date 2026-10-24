package dev.telephoneorder.client;

import dev.telephoneorder.weapon.GunItem;
import dev.telephoneorder.weapon.GunStats;
import net.minecraft.world.item.ItemStack;

public final class ScopeInfo {
    private ScopeInfo() {}

    public static float zoom(ItemStack s) {
        if (!(s.getItem() instanceof GunItem g)) return 1f;
        return GunStats.of(g.def, s).zoomFactor(g.def);
    }

    public static boolean scoped(ItemStack s) { return zoom(s) < 0.5f; }

    public static float fovMultiplier(ItemStack s) {
        float b = ClientState.aimBlend;
        if (b <= 0.001f || !(s.getItem() instanceof GunItem)) return 1f;
        float z = zoom(s);
        return 1f + (z - 1f) * b;
    }
}
