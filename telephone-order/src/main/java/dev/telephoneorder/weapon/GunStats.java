package dev.telephoneorder.weapon;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;
import net.minecraft.world.item.ItemStack;

/** The effective stats of one gun stack after its attachments are applied. */
public final class GunStats {
    public float dmg = 1, spread = 1, recoil = 1, reload = 1, delay = 1, range = 1, noise = 1, zoom = 1, heat = 1, cool = 1, apAdd = 0;
    public int mag;
    public boolean flashHidden, laser, light;

    public static GunStats of(GunDef def, ItemStack stack) {
        GunStats s = new GunStats();
        float magMul = 1;
        for (AttDef a : GunData.attachments(stack)) {
            s.dmg *= (float) a.mod("dmg", 1);
            s.spread *= (float) a.mod("spread", 1);
            s.recoil *= (float) a.mod("recoil", 1);
            s.reload *= (float) a.mod("reload", 1);
            s.delay *= (float) a.mod("delay", 1);
            s.range *= (float) a.mod("range", 1);
            s.noise *= (float) a.mod("noise", 1);
            s.zoom *= (float) a.mod("zoom", 1);
            s.heat *= (float) a.mod("heat", 1);
            s.cool *= (float) a.mod("cool", 1);
            s.apAdd += (float) a.mod("ap", 0);
            magMul *= (float) a.mod("mag", 1);
            if (a.mods().containsKey("flash") && a.mod("flash", 1) == 0) s.flashHidden = true;
            s.laser |= a.laser();
            s.light |= a.light();
        }
        s.mag = Math.max(1, Math.round(def.mag() * magMul));
        if (def.p("zoom", 0) > 0) s.zoom *= (float) def.p("zoom", 1);
        return s;
    }

    public float zoomFactor(GunDef def) {
        // default ADS zoom when no optic: modest
        float base = def.p("zoom", 0) > 0 ? 1.0f : 0.88f;
        return Math.max(0.08f, Math.min(1.0f, base * zoom));
    }
}
