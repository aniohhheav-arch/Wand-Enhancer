package dev.riftverse.universe;

import dev.riftverse.util.Hash;
import net.minecraft.core.BlockPos;

/**
 * Every universe lives in its own slot of the shared Expanse dimension. Slots are spaced far enough apart that a
 * player can never walk from one reality into the next.
 */
public record UniverseId(int gx, int gz) {
    public static final int SHIFT = 16;
    public static final int SPACING = 1 << SHIFT;
    public static final int MAX_SLOT = 450;
    /** Distance from the slot centre at which reality starts to fray. */
    public static final int BOUNDARY = SPACING / 2 - 512;

    public static UniverseId ofBlock(int x, int z) {
        return new UniverseId(Math.floorDiv(x + SPACING / 2, SPACING), Math.floorDiv(z + SPACING / 2, SPACING));
    }

    public static UniverseId unpack(long packed) {
        return new UniverseId((int) (packed >> 32), (int) packed);
    }

    public long pack() {
        return ((long) gx << 32) | (gz & 0xFFFFFFFFL);
    }

    public int centerX() {
        return gx * SPACING;
    }

    public int centerZ() {
        return gz * SPACING;
    }

    public BlockPos center(int y) {
        return new BlockPos(centerX(), y, centerZ());
    }

    public boolean isPrime() {
        return gz == 0 && gx >= 1 && gx <= Archetype.values().length;
    }

    public static UniverseId prime(Archetype archetype) {
        return new UniverseId(archetype.ordinal() + 1, 0);
    }

    /** Human readable catalogue designation, e.g. "U-48213-Ω". */
    public String designation() {
        long h = Hash.of(0x51A7E5L, gx, gz);
        int number = 10000 + (int) Long.remainderUnsigned(h, 89999);
        String[] glyphs = {"Ω", "Δ", "Σ", "Λ", "Ψ", "Φ", "Ξ", "Θ"};
        return "U-" + number + "-" + glyphs[(int) Long.remainderUnsigned(h >>> 20, glyphs.length)];
    }
}
