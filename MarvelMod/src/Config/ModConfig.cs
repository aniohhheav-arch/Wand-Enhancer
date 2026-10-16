using System;
using System.Collections.Generic;
using System.IO;
using BepInEx;
using Newtonsoft.Json;

namespace MarvelMod.Config;

public static class ModConfig
{
    private static readonly string ConfigPath =
        Path.Combine(Paths.ConfigPath, "MarvelMod", "config.json");

    public static ConfigData Data { get; private set; } = new();

    public static void Load()
    {
        try
        {
            if (File.Exists(ConfigPath))
            {
                var json = File.ReadAllText(ConfigPath);
                Data = JsonConvert.DeserializeObject<ConfigData>(json) ?? new ConfigData();
            }
        }
        catch (Exception e)
        {
            BepInEx.Logging.Logger.CreateLogSource("MarvelMod").LogWarning($"Config load failed: {e.Message}");
            Data = new ConfigData();
        }
        EnsureDefaults();
    }

    public static void Save()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(ConfigPath)!);
        File.WriteAllText(ConfigPath, JsonConvert.SerializeObject(Data, Formatting.Indented));
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
    public string  ActiveCharacter  { get; set; } = "loki";
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
    public bool    DebugMode        { get; set; } = false;
    public string  ConsoleKey       { get; set; } = "F6";
    // Loki illusion settings
    public int     IllusionCount    { get; set; } = 3;
    public string  IllusionBehavior { get; set; } = "wander";
    public float   IllusionSpeed    { get; set; } = 1.5f;
    public float   IllusionLifetime { get; set; } = 30f;
    public bool    IllusionNameplates{ get; set; } = true;
    public bool    IllusionIndependentAI{ get; set; } = true;

    public Dictionary<string, AbilityConfig> Abilities { get; set; } = new();
}
