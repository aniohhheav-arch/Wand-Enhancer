package dev.riftverse.universe;

import dev.riftverse.universe.UniverseTraits.CreatureKind;
import dev.riftverse.universe.UniverseTraits.MegaKind;
import dev.riftverse.universe.UniverseTraits.MusicKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.universe.UniverseTraits.WeatherKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

/**
 * The full description of one reality: terrain, materials, sky, atmosphere, rules and inhabitants. Specs are stored on
 * the server and mirrored to clients so the sky, fog, colour grade and ambience match the world being generated.
 */
public final class UniverseSpec {
    public UniverseId id = new UniverseId(0, 0);
    public String name = "Unnamed Reality";
    public String prompt = "";
    public Archetype archetype = Archetype.ASTRAL;
    public Archetype materials = Archetype.ASTRAL;
    public long seed;

    public TerrainMode terrain = TerrainMode.ROLLING;
    public int baseHeight = 70;
    public float amplitude = 20f;
    public float roughness = 1f;
    public int seaLevel = 62;
    public boolean hasSea = true;
    public float islandDensity = 0f;
    public MegaKind mega = MegaKind.NONE;
    public MegaKind mega2 = MegaKind.NONE;
    public float megaDensity = 0.3f;
    public float decorDensity = 1f;

    public int skyTop = 0x0A0A20;
    public int skyHorizon = 0x403070;
    public int fogColor = 0x302050;
    public int nebulaA = 0xFF40C0;
    public int nebulaB = 0x4060FF;
    public int sunColor = 0xFFF0D0;
    public int accent = 0x8F6BFF;
    public float starDensity = 0.6f;
    public float nebulaIntensity = 0.4f;
    public float auroraIntensity = 0f;
    public float galaxyIntensity = 0.3f;
    public float stormIntensity = 0f;
    public int moons = 0;
    public float planetSize = 0f;
    public boolean planetRings = false;
    public boolean skyBlackHole = false;
    public boolean binarySun = false;
    public float sunSize = 1f;
    public TimeMode time = TimeMode.CYCLE;

    public WeatherKind weather = WeatherKind.CLEAR;
    public float fogDensity = 0.2f;
    public float gravity = 1f;
    public boolean vacuum = false;
    public float glitch = 0f;
    public boolean dreamlike = false;
    public float hostility = 1f;
    public float creatureScale = 1f;
    public boolean naturalBlackHoles = false;
    public List<CreatureKind> creatures = new ArrayList<>();
    public MusicKind music = MusicKind.COSMIC;
    public int gradeTint = 0xFFFFFF;
    public float gradeStrength = 0f;
    public float saturation = 1f;
    /** Visual medium of this reality (see {@link ArtStyle}); artStyle2 layers a second style over the first. */
    public int artStyle;
    public int artStyle2;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id.pack());
        t.putString("name", name);
        t.putString("prompt", prompt);
        t.putInt("archetype", archetype.ordinal());
        t.putInt("materials", materials.ordinal());
        t.putLong("seed", seed);
        t.putInt("terrain", terrain.ordinal());
        t.putInt("baseHeight", baseHeight);
        t.putFloat("amplitude", amplitude);
        t.putFloat("roughness", roughness);
        t.putInt("seaLevel", seaLevel);
        t.putBoolean("hasSea", hasSea);
        t.putFloat("islandDensity", islandDensity);
        t.putInt("mega", mega.ordinal());
        t.putInt("mega2", mega2.ordinal());
        t.putFloat("megaDensity", megaDensity);
        t.putFloat("decorDensity", decorDensity);
        t.putInt("skyTop", skyTop);
        t.putInt("skyHorizon", skyHorizon);
        t.putInt("fogColor", fogColor);
        t.putInt("nebulaA", nebulaA);
        t.putInt("nebulaB", nebulaB);
        t.putInt("sunColor", sunColor);
        t.putInt("accent", accent);
        t.putFloat("starDensity", starDensity);
        t.putFloat("nebulaIntensity", nebulaIntensity);
        t.putFloat("auroraIntensity", auroraIntensity);
        t.putFloat("galaxyIntensity", galaxyIntensity);
        t.putFloat("stormIntensity", stormIntensity);
        t.putInt("moons", moons);
        t.putFloat("planetSize", planetSize);
        t.putBoolean("planetRings", planetRings);
        t.putBoolean("skyBlackHole", skyBlackHole);
        t.putBoolean("binarySun", binarySun);
        t.putFloat("sunSize", sunSize);
        t.putInt("time", time.ordinal());
        t.putInt("weather", weather.ordinal());
        t.putFloat("fogDensity", fogDensity);
        t.putFloat("gravity", gravity);
        t.putBoolean("vacuum", vacuum);
        t.putFloat("glitch", glitch);
        t.putBoolean("dreamlike", dreamlike);
        t.putFloat("hostility", hostility);
        t.putFloat("creatureScale", creatureScale);
        t.putBoolean("naturalBlackHoles", naturalBlackHoles);
        int[] c = new int[creatures.size()];
        for (int i = 0; i < c.length; i++) c[i] = creatures.get(i).ordinal();
        t.put("creatures", new IntArrayTag(c));
        t.putInt("music", music.ordinal());
        t.putInt("gradeTint", gradeTint);
        t.putFloat("gradeStrength", gradeStrength);
        t.putFloat("saturation", saturation);
        t.putInt("artStyle", artStyle);
        t.putInt("artStyle2", artStyle2);
        return t;
    }

    public static UniverseSpec load(CompoundTag t) {
        UniverseSpec s = new UniverseSpec();
        s.id = UniverseId.unpack(t.getLong("id"));
        s.name = t.getString("name");
        s.prompt = t.getString("prompt");
        s.archetype = Archetype.byId(t.getInt("archetype"));
        s.materials = Archetype.byId(t.getInt("materials"));
        s.seed = t.getLong("seed");
        s.terrain = TerrainMode.byId(t.getInt("terrain"));
        s.baseHeight = t.getInt("baseHeight");
        s.amplitude = t.getFloat("amplitude");
        s.roughness = t.getFloat("roughness");
        s.seaLevel = t.getInt("seaLevel");
        s.hasSea = t.getBoolean("hasSea");
        s.islandDensity = t.getFloat("islandDensity");
        s.mega = MegaKind.byId(t.getInt("mega"));
        s.mega2 = MegaKind.byId(t.getInt("mega2"));
        s.megaDensity = t.getFloat("megaDensity");
        s.decorDensity = t.getFloat("decorDensity");
        s.skyTop = t.getInt("skyTop");
        s.skyHorizon = t.getInt("skyHorizon");
        s.fogColor = t.getInt("fogColor");
        s.nebulaA = t.getInt("nebulaA");
        s.nebulaB = t.getInt("nebulaB");
        s.sunColor = t.getInt("sunColor");
        s.accent = t.getInt("accent");
        s.starDensity = t.getFloat("starDensity");
        s.nebulaIntensity = t.getFloat("nebulaIntensity");
        s.auroraIntensity = t.getFloat("auroraIntensity");
        s.galaxyIntensity = t.getFloat("galaxyIntensity");
        s.stormIntensity = t.getFloat("stormIntensity");
        s.moons = t.getInt("moons");
        s.planetSize = t.getFloat("planetSize");
        s.planetRings = t.getBoolean("planetRings");
        s.skyBlackHole = t.getBoolean("skyBlackHole");
        s.binarySun = t.getBoolean("binarySun");
        s.sunSize = t.getFloat("sunSize");
        s.time = TimeMode.byId(t.getInt("time"));
        s.weather = WeatherKind.byId(t.getInt("weather"));
        s.fogDensity = t.getFloat("fogDensity");
        s.gravity = t.contains("gravity", Tag.TAG_FLOAT) ? t.getFloat("gravity") : 1f;
        s.vacuum = t.getBoolean("vacuum");
        s.glitch = t.getFloat("glitch");
        s.dreamlike = t.getBoolean("dreamlike");
        s.hostility = t.getFloat("hostility");
        s.creatureScale = Math.max(0.5f, t.getFloat("creatureScale"));
        s.naturalBlackHoles = t.getBoolean("naturalBlackHoles");
        s.creatures = new ArrayList<>();
        for (int i : t.getIntArray("creatures")) s.creatures.add(CreatureKind.byId(i));
        s.music = MusicKind.byId(t.getInt("music"));
        s.gradeTint = t.getInt("gradeTint");
        s.gradeStrength = t.getFloat("gradeStrength");
        s.saturation = t.contains("saturation", Tag.TAG_FLOAT) ? t.getFloat("saturation") : 1f;
        s.artStyle = t.getInt("artStyle");
        s.artStyle2 = t.getInt("artStyle2");
        return s;
    }

    public UniverseSpec copy() {
        return load(save());
    }

    /** A short human description used by the Multiverse browser. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(archetype.displayName);
        switch (terrain) {
            case FLOATING -> sb.append(" • floating isles");
            case OCEAN -> sb.append(" • endless ocean");
            case CITY -> sb.append(" • megacity");
            case INVERTED -> sb.append(" • inverted terrain");
            case FRAGMENTS -> sb.append(" • shattered void");
            case MOUNTAINS -> sb.append(" • colossal peaks");
            case CRATERS -> sb.append(" • cratered plains");
            case VOLCANIC -> sb.append(" • volcanic");
            case GLACIAL -> sb.append(" • glacial");
            default -> {}
        }
        if (moons > 0) sb.append(" • ").append(moons).append(moons == 1 ? " moon" : " moons");
        if (planetSize > 0.2f) sb.append(planetRings ? " • ringed giant" : " • giant planet");
        if (skyBlackHole) sb.append(" • black hole sky");
        if (stormIntensity > 0.5f) sb.append(" • eternal storm");
        if (gravity < 0.7f) sb.append(" • low gravity");
        if (gravity > 1.3f) sb.append(" • crushing gravity");
        if (vacuum) sb.append(" • no atmosphere");
        if (glitch > 0.3f) sb.append(" • unstable reality");
        if (artStyle != 0) sb.append(" • ").append(ArtStyle.byId(artStyle).displayName.toLowerCase())
                .append(artStyle2 != 0 ? " + " + ArtStyle.byId(artStyle2).displayName.toLowerCase() : "").append(" reality");
        return sb.toString();
    }
}
