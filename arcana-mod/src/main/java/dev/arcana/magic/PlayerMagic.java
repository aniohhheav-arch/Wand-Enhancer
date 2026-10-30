package dev.arcana.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Persistent per-player magic state (mana, mastery, cooldowns) plus transient server-only bookkeeping. */
public class PlayerMagic {
    public float mana = -1;
    public final Map<String, Integer> mastery = new HashMap<>();
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<String, Long> recent = new HashMap<>();

    // transient
    public Spell lastSpell;
    public float lastCharge;
    public final ArrayDeque<Snapshot> history = new ArrayDeque<>();
    public int[] lastSent;

    public record Snapshot(ResourceKey<Level> dim, Vec3 pos, float health, float yRot, float xRot) {}

    public static final MapCodec<PlayerMagic> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.FLOAT.optionalFieldOf("mana", -1f).forGetter(p -> p.mana),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("mastery", Map.of()).forGetter(p -> p.mastery),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(p -> p.cooldowns),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("recent", Map.of()).forGetter(p -> p.recent)
    ).apply(i, PlayerMagic::new));

    public PlayerMagic() {}

    public PlayerMagic(float mana, Map<String, Integer> mastery, Map<String, Long> cooldowns, Map<String, Long> recent) {
        this.mana = mana;
        this.mastery.putAll(mastery);
        this.cooldowns.putAll(cooldowns);
        this.recent.putAll(recent);
    }

    public int xp(School s) {
        return mastery.getOrDefault(s.id, 0);
    }

    public int level(School s) {
        return Mastery.level(xp(s));
    }

    public long cooldownLeft(Spell s, long now) {
        return Math.max(0, cooldowns.getOrDefault(s.id, 0L) - now);
    }

    public void addXp(School s, int amount) {
        if (s == School.NONE || amount <= 0) return;
        mastery.merge(s.id, amount, Integer::sum);
    }
}
