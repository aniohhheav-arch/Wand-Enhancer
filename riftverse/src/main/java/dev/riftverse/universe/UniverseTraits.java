package dev.riftverse.universe;

/** The orthogonal building blocks every universe is assembled from. */
public final class UniverseTraits {
    private UniverseTraits() {}

    public enum TerrainMode {
        ROLLING, MOUNTAINS, OCEAN, ARCHIPELAGO, FLOATING, CITY, SPIRES, CRATERS, CANYONS, DUNES, FRAGMENTS, INVERTED, SHATTERED, VOLCANIC, GLACIAL;

        public static TerrainMode byId(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    public enum WeatherKind {
        CLEAR, RAIN, STORM, SNOW, ASH, SPORES, EMBERS, DATA_RAIN, STARDUST, PETALS;

        public static WeatherKind byId(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    public enum TimeMode {
        CYCLE, ETERNAL_NIGHT, ETERNAL_DAY, ETERNAL_DUSK;

        public static TimeMode byId(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    public enum MegaKind {
        NONE, NEON_MEGATOWER, CRYSTAL_SPIRE, ZIGGURAT, COLOSSAL_PILLARS, HALO_RING, GIANT_TREE, GIANT_MUSHROOM, MONOLITH, GLITCH_CUBES, RIBCAGE, ICE_SPIRE, VOLCANO, ARCH;

        public static MegaKind byId(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    public enum CreatureKind {
        ASTRAL_JELLY(false), SKY_WHALE(false), LUMEN_STRIDER(false), NEON_DRONE(true), GLITCHLING(true), VOID_STALKER(true), CRYSTAL_SENTINEL(true), RIFT_WRAITH(true), ABYSSAL_LEVIATHAN(true);

        public final boolean hostile;

        CreatureKind(boolean hostile) {
            this.hostile = hostile;
        }

        public static CreatureKind byId(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    public enum MusicKind {
        COSMIC, NEON, DREAM, VOID, NEXUS, OCEAN, ANCIENT;

        public static MusicKind byId(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }
}
