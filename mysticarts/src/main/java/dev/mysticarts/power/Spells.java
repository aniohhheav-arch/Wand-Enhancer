package dev.mysticarts.power;

import dev.mysticarts.MaConfig;
import dev.mysticarts.item.Vestments;
import dev.mysticarts.power.spells.ComboSpells;
import dev.mysticarts.power.spells.MindSpells;
import dev.mysticarts.power.spells.MysticSpells;
import dev.mysticarts.power.spells.PowerSpells;
import dev.mysticarts.power.spells.RealitySpells;
import dev.mysticarts.power.spells.SoulSpells;
import dev.mysticarts.power.spells.SpaceSpells;
import dev.mysticarts.power.spells.TimeSpells;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Registry of every spell implementation, plus completions for spells that wind up before resolving. */
public final class Spells {
    private static final Map<Ability, Spell> SPELLS = new EnumMap<>(Ability.class);
    private static final Map<Ability, Spell> FINISHERS = new EnumMap<>(Ability.class);

    static {
        MysticSpells.register();
        SpaceSpells.register();
        MindSpells.register();
        RealitySpells.register();
        PowerSpells.register();
        TimeSpells.register();
        SoulSpells.register();
        ComboSpells.register();
        for (Ability a : Ability.values()) {
            if (!SPELLS.containsKey(a)) throw new IllegalStateException("No spell registered for " + a);
        }
    }

    private Spells() {}

    public static void register(Ability a, Spell spell) {
        SPELLS.put(a, spell);
    }

    /** A spell whose cast starts a wind-up of {@code ticks}; {@code finish} resolves it. */
    public static void windup(Ability a, int ticks, Spell start, Spell finish) {
        SPELLS.put(a, c -> {
            if (!start.cast(c)) return false;
            c.data().windupAbility = a.ordinal();
            c.data().windupTicks = ticks;
            c.data().casterDirty = true;
            return true;
        });
        FINISHERS.put(a, finish);
    }

    @Nullable
    public static Spell get(Ability a) {
        return SPELLS.get(a);
    }

    public static void finish(Cast c) {
        Spell f = FINISHERS.get(c.ability());
        if (f != null) f.cast(c);
    }

    public static float damage(Player player, Ability a, float base) {
        float d = base * MaConfig.DAMAGE_MULTIPLIER.get().floatValue();
        if (a.source == Source.MYSTIC && Vestments.worn(player) == Vestments.DARK_DIMENSION) d *= 1.25f;
        return d;
    }

    /** Forces class loading so a missing implementation fails at startup rather than on first cast. */
    public static void bootstrap() {}
}
