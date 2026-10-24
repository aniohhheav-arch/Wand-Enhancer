package dev.telephoneorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON config in {@code config/telephoneorder.json}. Server fields govern balance, client fields govern presentation. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();

    // ---- server / balance ----
    public float damageMultiplier = 1.0f;
    public float headshotMultiplier = 1.0f;       // scales the bonus part of headshots
    public float credits = 1.0f;                  // multiplier on deposit values and bounties
    public int startingCredits = 150;
    public boolean killBounties = true;
    public boolean explosionsBreakBlocks = false;
    public boolean allowFriendlyDamage = true;    // PvP still honours the server pvp flag
    public float deliveryTimeMultiplier = 1.0f;   // 0.1 = very fast deliveries for testing
    public boolean clueLoot = true;
    // ---- client / presentation ----
    public boolean screenShake = true;
    public float shakeIntensity = 1.0f;
    public boolean reduceCameraMotion = false;    // disables shake, recoil kick and scope sway
    public float recoilVisual = 1.0f;
    public float particleScale = 1.0f;            // 0.25 .. 2.0
    public boolean hud = true;
    public int ringtone = 0;                      // default ringtone for new accounts

    public static ModConfig get() { return instance; }

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("telephoneorder.json");
        try {
            if (Files.exists(p)) {
                ModConfig c = GSON.fromJson(Files.readString(p), ModConfig.class);
                if (c != null) instance = c;
            }
            Files.writeString(p, GSON.toJson(instance));
        } catch (IOException | RuntimeException e) {
            TelephoneOrder.LOGGER.warn("Could not read/write config {}: {}", p, e.toString());
        }
    }

    public static void save() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("telephoneorder.json");
        try {
            Files.writeString(p, GSON.toJson(instance));
        } catch (IOException e) {
            TelephoneOrder.LOGGER.warn("Could not save config: {}", e.toString());
        }
    }
}
