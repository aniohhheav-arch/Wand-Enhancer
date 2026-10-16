using System;
using System.Collections.Generic;
using System.IO;
using BepInEx;
using System.Text.Json;

namespace MarvelMod.Config;

public static class ModConfig
{
    private static readonly string ConfigPath =
        Path.Combine(Paths.ConfigPath, "MarvelMod", "config.json");

    // System.Text.Json: Newtonsoft.Json resolves to the game's IL2CPP copy, which cannot serialize managed types.
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    private const int CurrentConfigVersion = 2;

    public static ConfigData Data { get; private set; } = new();

    public static void Load()
    {
        try
        {
            if (File.Exists(ConfigPath))
            {
                var json = File.ReadAllText(ConfigPath);
                Data = JsonSerializer.Deserialize<ConfigData>(json, JsonOptions) ?? new ConfigData();
            }
        }
        catch (Exception e)
        {
            BepInEx.Logging.Logger.CreateLogSource("MarvelMod").LogWarning($"Config load failed: {e.Message}");
            Data = new ConfigData();
        }
        if (Data.ConfigVersion < CurrentConfigVersion)
        {
            // ability tuning changed (ranges are now in world units) — drop old values
            Data.Abilities.Clear();
            Data.ConfigVersion = CurrentConfigVersion;
            EnsureDefaults();
            Save();
        }
        EnsureDefaults();
    }

    public static void Save()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(ConfigPath)!);
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(Data, JsonOptions));
    }

    public static void Reset()
    {
        Data = new ConfigData();
        EnsureDefaults();
        Save();
    }

    public static AbilityConfig GetAbility(string id)
    {
        if (!Data.Abilities.TryGetValue(id, out var cfg))
        {
            cfg = DefaultAbilities.Get(id);
            Data.Abilities[id] = cfg;
        }
        return cfg;
    }

    public static void SetAbility(string id, AbilityConfig cfg)
    {
        Data.Abilities[id] = cfg;
        Save();
    }

    private static void EnsureDefaults()
    {
        foreach (var kv in DefaultAbilities.All())
            Data.Abilities.TryAdd(kv.Key, kv.Value);
    }
}

public class ConfigData
{
    public int     ConfigVersion    { get; set; } = 0;
    /// <summary>Freeplay/lobby character: loki, thanos or none (pick on the freeplay laptop or with F3).</summary>
    public string  ActiveCharacter  { get; set; } = "loki";
    /// <summary>Online games: what impostors become — random, loki or thanos.</summary>
    public string  ImpostorCharacter { get; set; } = "random";
    public bool    VfxEnabled       { get; set; } = true;
    public int     VfxIntensity     { get; set; } = 100;
    public bool    VfxParticles     { get; set; } = true;
    public bool    VfxScreenShake   { get; set; } = true;
    public bool    VfxDistortion    { get; set; } = true;
    public string  VfxQuality       { get; set; } = "high";
    public bool    CinematicSkip    { get; set; } = false;
    public bool    PracticeUnlimited{ get; set; } = false;
    public bool    PracticeInvincible{ get; set; } = false;
    public int     PracticeDummies  { get; set; } = 3;
    public bool    HudEnabled        { get; set; } = true;
    /// <summary>Finishers: full, short (half length) or skip (instant).</summary>
    public string  FinisherMode     { get; set; } = "full";
    /// <summary>Finishers dissolve the victim; true leaves a reportable body instead.</summary>
    public bool    FinisherLeavesBody { get; set; } = false;
    public bool    SfxEnabled       { get; set; } = true;
    public float   SfxVolume        { get; set; } = 0.6f;
    /// <summary>Extra volume factor for ultimates / finishers / cinematics.</summary>
    public float   CinematicVolume  { get; set; } = 1f;
    /// <summary>The Snap outcome: visual, practice_kill, match_kill_half or match_stun.</summary>
    public string  SnapMode         { get; set; } = "visual";
    /// <summary>Stones Thanos starts with in a match (1-6); one more every StoneUnlockSeconds and per kill.</summary>
    public int     ThanosStartStones { get; set; } = 2;
    public float   StoneUnlockSeconds { get; set; } = 60f;
    /// <summary>Crewmates: how many become Doctor Strange each match (0 = off). Chosen by the host.</summary>
    public int     StrangeCount     { get; set; } = 1;
    public int     PortalMaxPairs   { get; set; } = 2;
    public float   PortalLifetime   { get; set; } = 25f;
    public float   PortalSize       { get; set; } = 1.1f;
    public float   PortalTeleportCooldown { get; set; } = 1.2f;
    public bool    DebugMode        { get; set; } = false;
    public string  ConsoleKey       { get; set; } = "F6";
    // Loki illusion settings
    public int     IllusionCount    { get; set; } = 3;
    public string  IllusionBehavior { get; set; } = "wander";
    public float   IllusionSpeed    { get; set; } = 2.2f;
    public float   IllusionLifetime { get; set; } = 30f;
    public bool    IllusionNameplates{ get; set; } = true;
    public bool    IllusionIndependentAI{ get; set; } = true;

    public Dictionary<string, AbilityConfig> Abilities { get; set; } = new();
}
