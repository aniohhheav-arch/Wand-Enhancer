package dev.arcana.magic;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/** Magic schools. {@link #NONE} is the school-less Forbidden Wand. */
public enum School {
    VOID("void", 0x7B2CBF),
    VERDANT("verdant", 0x3FB950),
    GLACIER("glacier", 0x6EC6FF),
    CRIMSON("crimson", 0xC1121F),
    SOLAR("solar", 0xFFB703),
    STORM("storm", 0xF2E94E),
    CHRONOS("chronos", 0x2EC4B6),
    ARCANE("arcane", 0xE056FD),
    NONE("forbidden", 0xFF2BD6);

    public final String id;
    public final int color;

    School(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public static School byId(String id) {
        for (School s : values()) if (s.id.equals(id)) return s;
        return NONE;
    }

    public static School[] real() {
        return new School[] {VOID, VERDANT, GLACIER, CRIMSON, SOLAR, STORM, CHRONOS, ARCANE};
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), size);
    }

    public DustParticleOptions dust(float size) {
        return dust(color, size);
    }
}
