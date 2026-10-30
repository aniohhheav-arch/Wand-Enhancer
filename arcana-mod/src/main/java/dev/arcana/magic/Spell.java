package dev.arcana.magic;

import dev.arcana.spells.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Every castable spell. The enum name lower-cased is the stable id used in data components, packets,
 * config and localisation keys ({@code spell.arcana.<id>}).
 */
public enum Spell {
    // ---- Void
    SINGULARITY(School.VOID, SpellSlot.PRIMARY, 0, 12, 30, 28, 0, VoidSpells::singularity),
    RIFT_STEP(School.VOID, SpellSlot.SECONDARY, 0, 20, 140, 0, 1, VoidSpells::riftStep),
    GRAVITY_CRUSH(School.VOID, SpellSlot.SPECIAL, 1, 35, 320, 0, 2, VoidSpells::gravityCrush),
    EVENT_HORIZON(School.VOID, SpellSlot.ULTIMATE, 2, 85, 1800, 0, 3, VoidSpells::eventHorizon),
    // ---- Verdant
    ROOT_SNARE(School.VERDANT, SpellSlot.PRIMARY, 0, 10, 30, 14, 4, VerdantSpells::rootSnare),
    THORN_BARRIER(School.VERDANT, SpellSlot.SECONDARY, 0, 25, 240, 0, 5, VerdantSpells::thornBarrier),
    SPORE_BLOOM(School.VERDANT, SpellSlot.SPECIAL, 1, 35, 360, 0, 6, VerdantSpells::sporeBloom),
    GROVE_GUARDIANS(School.VERDANT, SpellSlot.ULTIMATE, 2, 80, 2100, 0, 7, VerdantSpells::groveGuardians),
    // ---- Glacier
    ICE_SPIKES(School.GLACIER, SpellSlot.PRIMARY, 0, 11, 32, 18, 8, GlacierSpells::iceSpikes),
    FROST_NOVA(School.GLACIER, SpellSlot.SECONDARY, 0, 22, 200, 0, 9, GlacierSpells::frostNova),
    FROZEN_PRISON(School.GLACIER, SpellSlot.SPECIAL, 1, 32, 340, 0, 10, GlacierSpells::frozenPrison),
    BLIZZARD(School.GLACIER, SpellSlot.ULTIMATE, 2, 85, 1900, 0, 11, GlacierSpells::blizzard),
    // ---- Crimson
    CRIMSON_CHAINS(School.CRIMSON, SpellSlot.PRIMARY, 0, 12, 40, 10, 12, CrimsonSpells::chains),
    CRIMSON_WAVE(School.CRIMSON, SpellSlot.SECONDARY, 0, 20, 160, 0, 13, CrimsonSpells::wave),
    BLOOD_SEAL(School.CRIMSON, SpellSlot.SPECIAL, 1, 34, 300, 0, 14, CrimsonSpells::bloodSeal),
    CRIMSON_CATACLYSM(School.CRIMSON, SpellSlot.ULTIMATE, 2, 85, 1800, 0, 15, CrimsonSpells::cataclysm),
    // ---- Solar
    SOLAR_BEAM(School.SOLAR, SpellSlot.PRIMARY, 0, 14, 36, 30, 16, SolarSpells::beam),
    MINI_SUN(School.SOLAR, SpellSlot.SECONDARY, 0, 26, 260, 0, 17, SolarSpells::miniSun),
    DAWNLIGHT(School.SOLAR, SpellSlot.SPECIAL, 1, 30, 300, 0, 18, SolarSpells::dawnlight),
    SUPERNOVA(School.SOLAR, SpellSlot.ULTIMATE, 2, 90, 2000, 0, 19, SolarSpells::supernova),
    // ---- Storm
    THUNDERBOLT(School.STORM, SpellSlot.PRIMARY, 0, 12, 34, 15, 20, StormSpells::thunderbolt),
    CHAIN_LIGHTNING(School.STORM, SpellSlot.SECONDARY, 0, 20, 150, 0, 21, StormSpells::chainLightning),
    STATIC_FIELD(School.STORM, SpellSlot.SPECIAL, 1, 30, 320, 0, 22, StormSpells::staticField),
    TEMPEST(School.STORM, SpellSlot.ULTIMATE, 2, 85, 1850, 0, 23, StormSpells::tempest),
    // ---- Chronos
    TEMPORAL_SLOW(School.CHRONOS, SpellSlot.PRIMARY, 0, 12, 40, 10, 24, ChronosSpells::temporalSlow),
    REWIND(School.CHRONOS, SpellSlot.SECONDARY, 0, 28, 500, 0, 25, ChronosSpells::rewind),
    CHRONO_SURGE(School.CHRONOS, SpellSlot.SPECIAL, 1, 32, 380, 0, 26, ChronosSpells::chronoSurge),
    TIME_STOP(School.CHRONOS, SpellSlot.ULTIMATE, 2, 95, 2400, 0, 27, ChronosSpells::timeStop),
    // ---- Arcane
    ARCANE_BOLT(School.ARCANE, SpellSlot.PRIMARY, 0, 9, 24, 10, 28, ArcaneSpells::bolt),
    ECHO(School.ARCANE, SpellSlot.SECONDARY, 0, 22, 180, 0, 29, ArcaneSpells::echo),
    ARCANE_BARRAGE(School.ARCANE, SpellSlot.SPECIAL, 1, 34, 300, 0, 30, ArcaneSpells::barrage),
    ARCANE_CATACLYSM(School.ARCANE, SpellSlot.ULTIMATE, 2, 85, 1850, 0, 31, ArcaneSpells::cataclysm),
    // ---- Forbidden (no school): mimic any school's primary spell you cast recently
    MIMIC_VOID(School.NONE, SpellSlot.PRIMARY, 0, 18, 40, 28, 32, c -> ForbiddenSpells.mimic(c, School.VOID)),
    MIMIC_VERDANT(School.NONE, SpellSlot.PRIMARY, 0, 15, 45, 14, 33, c -> ForbiddenSpells.mimic(c, School.VERDANT)),
    MIMIC_GLACIER(School.NONE, SpellSlot.PRIMARY, 0, 16, 45, 18, 34, c -> ForbiddenSpells.mimic(c, School.GLACIER)),
    MIMIC_CRIMSON(School.NONE, SpellSlot.PRIMARY, 0, 18, 50, 10, 35, c -> ForbiddenSpells.mimic(c, School.CRIMSON)),
    MIMIC_SOLAR(School.NONE, SpellSlot.PRIMARY, 0, 21, 50, 30, 36, c -> ForbiddenSpells.mimic(c, School.SOLAR)),
    MIMIC_STORM(School.NONE, SpellSlot.PRIMARY, 0, 18, 45, 15, 37, c -> ForbiddenSpells.mimic(c, School.STORM)),
    MIMIC_CHRONOS(School.NONE, SpellSlot.PRIMARY, 0, 18, 50, 10, 38, c -> ForbiddenSpells.mimic(c, School.CHRONOS)),
    MIMIC_ARCANE(School.NONE, SpellSlot.PRIMARY, 0, 14, 35, 10, 39, c -> ForbiddenSpells.mimic(c, School.ARCANE)),
    UNSTABLE_RIFT(School.NONE, SpellSlot.SECONDARY, 0, 35, 260, 0, 40, ForbiddenSpells::unstableRift),
    TERRAIN_WARP(School.NONE, SpellSlot.SPECIAL, 0, 40, 380, 0, 41, ForbiddenSpells::terrainWarp),
    RIFT_COLLAPSE(School.NONE, SpellSlot.ULTIMATE, 0, 100, 2400, 0, 42, ForbiddenSpells::riftCollapse);

    @FunctionalInterface
    public interface Impl {
        boolean cast(SpellContext ctx);
    }

    public final School school;
    public final SpellSlot slot;
    public final int minStage;
    public final int baseMana;
    public final int baseCooldown;
    /** Ticks needed for a full charge; 0 = instant cast. */
    public final int chargeTicks;
    public final int iconIndex;
    public final Impl impl;
    public final String id;

    Spell(School school, SpellSlot slot, int minStage, int baseMana, int baseCooldown, int chargeTicks, int iconIndex, Impl impl) {
        this.school = school;
        this.slot = slot;
        this.minStage = minStage;
        this.baseMana = baseMana;
        this.baseCooldown = baseCooldown;
        this.chargeTicks = chargeTicks;
        this.iconIndex = iconIndex;
        this.impl = impl;
        this.id = name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("spell.arcana." + id);
    }

    public Component description() {
        return Component.translatable("spell.arcana." + id + ".desc");
    }

    public boolean isMimic() {
        return school == School.NONE && slot == SpellSlot.PRIMARY;
    }

    /** For mimic spells, the school being copied. */
    public School mimicked() {
        return isMimic() ? School.real()[ordinal() - MIMIC_VOID.ordinal()] : school;
    }

    /** Index used by the Forbidden Wand to pick its adaptive model (0 = neutral). */
    public int lookIndex() {
        if (isMimic()) return 1 + (ordinal() - MIMIC_VOID.ordinal());
        return switch (this) {
            case UNSTABLE_RIFT -> 9;
            case TERRAIN_WARP -> 10;
            case RIFT_COLLAPSE -> 11;
            default -> 0;
        };
    }

    public static Spell byId(String id) {
        if (id == null) return null;
        for (Spell s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    public static Spell of(School school, SpellSlot slot) {
        for (Spell s : values()) if (s.school == school && s.slot == slot && !s.isMimic()) return s;
        return null;
    }

    /** Spells selectable on the radial wheel for a wand of this school and stage. */
    public static List<Spell> forWand(School school, int stage) {
        List<Spell> out = new ArrayList<>();
        for (Spell s : values()) {
            if (s.school != school) continue;
            if (school != School.NONE && s.minStage > stage) continue;
            out.add(s);
        }
        return out;
    }

    public static Spell defaultFor(School school) {
        return school == School.NONE ? UNSTABLE_RIFT : of(school, SpellSlot.PRIMARY);
    }
}
