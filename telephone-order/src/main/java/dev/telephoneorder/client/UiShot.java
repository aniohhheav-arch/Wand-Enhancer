package dev.telephoneorder.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Developer-only: opens the phone UI with fake data so screenshots can be taken without a world (-Dtelephoneorder.uishot). */
final class UiShot {
    private UiShot() {}
    private static int t;
    private static PhoneScreen screen;

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            t++;
            if (t < 400) return;
            if (screen == null) {
                CompoundTag s = new CompoundTag();
                s.putInt("credits", 1234); s.putInt("spent", 900); s.putInt("tier", 1); s.putInt("nextAt", 2000); s.putInt("model", 3); s.putString("dim", "overworld");
                s.putLong("day", 3);
                ListTag un = new ListTag(); un.add(StringTag.valueOf("proto_lab")); s.put("unlocks", un);
                s.putInt("clues", 0b0010101);
                ListTag orders = new ListTag();
                CompoundTag o = new CompoundTag(); o.putInt("oid", 4); o.putString("listing", "warden_ar4"); o.putInt("pct", 55); o.putInt("remain", 14); o.putString("method", "drone"); orders.add(o);
                CompoundTag o2 = new CompoundTag(); o2.putInt("oid", 5); o2.putString("listing", "sunder_pr"); o2.putInt("pct", 12); o2.putInt("remain", 40); o2.putString("method", "teleport"); orders.add(o2);
                s.put("orders", orders);
                CompoundTag call = new CompoundTag(); call.putString("id", "welcome"); call.putString("speaker", "T.O. AUTOMATED ASSISTANT"); call.putString("style", "normal");
                ListTag ln = new ListTag(); ln.add(StringTag.valueOf("Thank you for calling TELEPHONE ORDER, a subsidiary of itself.")); ln.add(StringTag.valueOf("Please select an option.")); call.put("lines", ln);
                ListTag ch = new ListTag(); ch.add(StringTag.valueOf("Browse the catalogue")); ch.add(StringTag.valueOf("Hang up")); call.put("choices", ch);
                s.put("call", call);
                s.putString("toast", "ORDER #4 CONFIRMED  -245 cr  ETA 30s"); s.putInt("toastColor", 0x7CFFB0);
                ClientState.phone = s;
                screen = new PhoneScreen(3);
                mc.gui.setScreen(screen);
            }
            int phase = (t - 400) / 90;
            String[][] shots = {{"call", "grid", "vesper_p9", "ALL"}, {"dial", "grid", "vesper_p9", "ALL"}, {"catalog", "grid", "vesper_p9", "ALL"}, {"catalog", "product", "warden_ar4", "ALL"},
                    {"catalog", "cart", "vesper_p9", "ALL"}, {"orders", "grid", "vesper_p9", "ALL"}, {"account", "grid", "vesper_p9", "ALL"}};
            if ((t - 400) % 90 == 70 && phase < shots.length) net.minecraft.client.Screenshot.grab(mc, false);
            if ((t - 400) % 90 == 0 && phase < shots.length) screen.debugGo(shots[phase][0], shots[phase][1], shots[phase][2], shots[phase][3]);
        });
    }
}
