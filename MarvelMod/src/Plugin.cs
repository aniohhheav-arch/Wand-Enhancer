using BepInEx;
using BepInEx.Unity.IL2CPP;
using HarmonyLib;
using MarvelMod.Config;
using MarvelMod.Practice;
using MarvelMod.UI.Console;
using MarvelMod.UI.HUD;
using Reactor;

namespace MarvelMod;

[BepInPlugin("com.marvelmod.loki", "MarvelMod - Loki", "1.0.0")]
[BepInProcess("Among Us.exe")]
[BepInDependency(ReactorPlugin.Id)]
public class MarvelModPlugin : BasePlugin
{
    public const string Id      = "com.marvelmod.loki";
    public const string ModName = "MarvelMod - Loki";
    public const string Version = "1.0.0";

    public Harmony Harmony { get; } = new(Id);

    public override void Load()
    {
        ModConfig.Load();
        Harmony.PatchAll();

        AddComponent<PracticeModeManager>();
        AddComponent<DevConsole>();
        AddComponent<AbilityHud>();
        AddComponent<MarvelMod.Characters.IllusionManager>();
        AddComponent<MarvelMod.Characters.ShapeshiftManager>();
        AddComponent<MarvelMod.Characters.StatusManager>();
        AddComponent<MarvelMod.Characters.DamageSystem>();
        AddComponent<MarvelMod.Characters.InvisibilityManager>();
        AddComponent<MarvelMod.Characters.ProjectileManager>();

        Log.LogInfo($"MarvelMod loaded! Character: {ModConfig.Data.ActiveCharacter}");
    }
}
