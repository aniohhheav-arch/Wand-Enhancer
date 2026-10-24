package dev.telephoneorder.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemDisplayContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live, per-view orientation tweaks for the GeckoLib gun models (rotation in degrees, translation in 1/16 block, scale multiplier).
 * Defaults are neutral; the display transforms in gun_base.json do the main work.  Adjust with {@code /toorient}.
 */
public final class GunOrient {
    public static final String[] VIEWS = {"first", "third", "gui", "ground", "fixed", "other"};
    public static final String[] FIELDS = {"yaw", "pitch", "roll", "x", "y", "z", "scale"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Map<String, Float>> data = new LinkedHashMap<>();

    static {
        load();
    }

    private GunOrient() { }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("telephoneorder_orient.json"); }

    private static void load() {
        try {
            if (Files.exists(file())) {
                Map<String, Map<String, Float>> m = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, Map<String, Float>>>() { }.getType());
                if (m != null) data = m;
            }
        } catch (Exception ignored) { }
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(data)); } catch (Exception ignored) { }
    }

    public static String view(ItemDisplayContext c) {
        if (c == null) return "other";
        return switch (c) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> "first";
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> "third";
            case GUI -> "gui";
            case GROUND -> "ground";
            case FIXED -> "fixed";
            default -> "other";
        };
    }

    public static float get(String view, String field) {
        Float v = data.getOrDefault(view, Map.of()).get(field);
        return v != null ? v : (field.equals("scale") ? 1f : 0f);
    }

    public static void set(String view, String field, float value) {
        data.computeIfAbsent(view, k -> new LinkedHashMap<>()).put(field, value);
        save();
    }

    public static void reset() { data.clear(); save(); }

    public static String describe() {
        StringBuilder b = new StringBuilder();
        for (String v : VIEWS) {
            b.append(v).append(':');
            for (String f : FIELDS) b.append(' ').append(f).append('=').append(get(v, f));
            b.append('\n');
        }
        return b.toString();
    }
}
