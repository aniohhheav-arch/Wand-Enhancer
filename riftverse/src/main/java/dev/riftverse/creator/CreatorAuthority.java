package dev.riftverse.creator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftverse.Riftverse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side creator authorisation for the Reality Rupture. The secret credential is never stored or sent in clear:
 * only a salted PBKDF2-HMAC-SHA256 hash lives in {@code <world>/serverconfig/riftverse_creator.json}, which clients
 * never see. Passwords arrive through a masked screen in a custom packet (never chat, so never logged) and are compared
 * in constant time, with a lockout after repeated failures. Authentication opens a per-login session; weapons are
 * bound to a registered serial and an authorised holder, so dupes and /give copies crumble.
 */
public final class CreatorAuthority {
    private static final int ITERATIONS = 150_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String salt = "";
    private static String hash = "";
    private static final Set<UUID> CREATORS = new HashSet<>();
    private static final Set<UUID> GRANTED = new HashSet<>();
    private static final Set<Long> SERIALS = new HashSet<>();
    private static final Set<UUID> AURA = new HashSet<>();
    private static final Set<UUID> SESSIONS = new HashSet<>();
    private static final Map<UUID, long[]> FAILURES = new HashMap<>();
    private static boolean loaded;

    private CreatorAuthority() {}

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("riftverse_creator.json");
    }

    public static synchronized void load(MinecraftServer server) {
        salt = "";
        hash = "";
        CREATORS.clear();
        GRANTED.clear();
        SERIALS.clear();
        AURA.clear();
        SESSIONS.clear();
        FAILURES.clear();
        loaded = true;
        Path f = file(server);
        if (!Files.exists(f)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            salt = o.has("salt") ? o.get("salt").getAsString() : "";
            hash = o.has("hash") ? o.get("hash").getAsString() : "";
            if (o.has("creators")) o.getAsJsonArray("creators").forEach(e -> CREATORS.add(UUID.fromString(e.getAsString())));
            if (o.has("granted")) o.getAsJsonArray("granted").forEach(e -> GRANTED.add(UUID.fromString(e.getAsString())));
            if (o.has("serials")) o.getAsJsonArray("serials").forEach(e -> SERIALS.add(e.getAsLong()));
            if (o.has("aura")) o.getAsJsonArray("aura").forEach(e -> AURA.add(UUID.fromString(e.getAsString())));
        } catch (IOException | RuntimeException e) {
            Riftverse.LOGGER.error("Could not read the creator authority file", e);
        }
    }

    private static synchronized void save(MinecraftServer server) {
        JsonObject o = new JsonObject();
        o.addProperty("salt", salt);
        o.addProperty("hash", hash);
        JsonArray c = new JsonArray();
        CREATORS.forEach(u -> c.add(u.toString()));
        o.add("creators", c);
        JsonArray g = new JsonArray();
        GRANTED.forEach(u -> g.add(u.toString()));
        o.add("granted", g);
        JsonArray s = new JsonArray();
        SERIALS.forEach(s::add);
        o.add("serials", s);
        JsonArray a = new JsonArray();
        AURA.forEach(u -> a.add(u.toString()));
        o.add("aura", a);
        try {
            Path f = file(server);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Riftverse.LOGGER.error("Could not save the creator authority file", e);
        }
    }

    private static String derive(String secret, String saltB64) {
        try {
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), Base64.getDecoder().decode(saltB64), ITERATIONS, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    public static boolean credentialSet() {
        return !hash.isEmpty();
    }

    /** May this player even attempt authorisation? (designated creators, or level-4 operators) */
    public static boolean eligible(ServerPlayer p) {
        return CREATORS.contains(p.getUUID()) || p.hasPermissions(4);
    }

    public static boolean isCreator(ServerPlayer p) {
        return CREATORS.contains(p.getUUID());
    }

    /** May this player wield the Rupture right now? */
    public static boolean mayWield(ServerPlayer p) {
        return (CREATORS.contains(p.getUUID()) || GRANTED.contains(p.getUUID()));
    }

    public static boolean inSession(ServerPlayer p) {
        return SESSIONS.contains(p.getUUID());
    }

    public static void endSession(UUID id) {
        SESSIONS.remove(id);
    }

    /** Verifies a password. Returns null on success, or a reason. */
    @Nullable
    public static String authenticate(ServerPlayer p, String secret) {
        if (!eligible(p)) return "This interface does not answer you.";
        if (!credentialSet()) return "No creator credential has been configured on this server.";
        long now = System.currentTimeMillis();
        long[] f = FAILURES.computeIfAbsent(p.getUUID(), k -> new long[2]);
        if (f[1] > now) return "Locked. Try again in " + ((f[1] - now) / 1000 + 1) + "s.";
        boolean ok = MessageDigest.isEqual(derive(secret, salt).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            f[0]++;
            if (f[0] >= 3) {
                f[1] = now + 60_000L * Math.min(30, f[0] - 2);
            }
            Riftverse.LOGGER.warn("Failed creator authorisation attempt by {}", p.getGameProfile().getName());
            return "Authority not recognised.";
        }
        f[0] = 0;
        SESSIONS.add(p.getUUID());
        if (CREATORS.isEmpty()) CREATORS.add(p.getUUID());
        save(p.server);
        return null;
    }

    /**
     * Sets or changes the credential. The first credential may be set by any level-4 operator (who becomes the
     * creator); changing it requires the current secret.
     */
    @Nullable
    public static String setCredential(ServerPlayer p, String current, String next) {
        if (!p.hasPermissions(4) && !isCreator(p)) return "Only the creator or a level-4 operator may set the credential.";
        if (next.length() < 8) return "The new secret must be at least 8 characters.";
        if (credentialSet() && !MessageDigest.isEqual(derive(current, salt).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
            return "The current secret is wrong.";
        }
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        salt = Base64.getEncoder().encodeToString(s);
        hash = derive(next, salt);
        CREATORS.add(p.getUUID());
        save(p.server);
        return null;
    }

    public static long issueSerial(MinecraftServer server) {
        long serial = new SecureRandom().nextLong() & Long.MAX_VALUE;
        SERIALS.add(serial);
        save(server);
        return serial;
    }

    public static boolean validSerial(long serial) {
        return SERIALS.contains(serial);
    }

    public static void grant(MinecraftServer server, UUID id) {
        GRANTED.add(id);
        save(server);
    }

    public static void revoke(MinecraftServer server, UUID id) {
        GRANTED.remove(id);
        SESSIONS.remove(id);
        save(server);
    }

    public static boolean aura(UUID id) {
        return AURA.contains(id);
    }

    public static void toggleAura(MinecraftServer server, UUID id) {
        if (!AURA.remove(id)) AURA.add(id);
        save(server);
    }

    public static int creatorCount() {
        return CREATORS.size();
    }

    public static int grantedCount() {
        return GRANTED.size();
    }

    public static int serialCount() {
        return SERIALS.size();
    }

    public static boolean loaded() {
        return loaded;
    }
}
