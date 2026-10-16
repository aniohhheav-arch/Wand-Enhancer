using BepInEx;
using BepInEx.Unity.IL2CPP;
using HarmonyLib;
using MarvelMod.Config;
using MarvelMod.Practice;
using MarvelMod.UI.Console;
using Reactor;

namespace MarvelMod;

[BepInPlugin(Id, ModName, Version)]
[BepInProcess("Among Us.exe")]
[BepInDependency(ReactorPlugin.Id)]
public class MarvelModPlugin : BasePlugin
{
    public const string Id      = "com.marvelmod.loki";
    public const string ModName = "MarvelMod - Loki";
    public const string Version = "1.1.0";

    public Harmony Harmony { get; } = new(Id);

    public override void Load()
    {
        ModConfig.Load();
        MarvelMod.Characters.CharacterRegistry.Register(MarvelMod.Characters.LokiCharacter.Instance);
        MarvelMod.Characters.CharacterRegistry.Register(MarvelMod.Characters.ThanosCharacter.Instance);
        MarvelMod.Characters.CharacterRegistry.Register(MarvelMod.Characters.StrangeCharacter.Instance);
        MarvelMod.Characters.CharacterRegistry.Register(MarvelMod.Characters.WandaCharacter.Instance);
        MarvelMod.Characters.CharacterRegistry.Register(MarvelMod.Characters.ThorCharacter.Instance);
        Harmony.PatchAll();

        // Ability buttons are driven by Patches.HudUpdatePatch (they live on the game's HUD).
        AddComponent<PracticeModeManager>();
        AddComponent<DevConsole>();
        AddComponent<MarvelMod.UI.Index.AbilityIndex>();
        AddComponent<MarvelMod.UI.MeetingPanel>();
        AddComponent<MarvelMod.Characters.IllusionManager>();
        AddComponent<MarvelMod.Characters.ShapeshiftManager>();
        AddComponent<MarvelMod.Characters.StatusManager>();
        AddComponent<MarvelMod.Characters.DamageSystem>();
        AddComponent<MarvelMod.Characters.InvisibilityManager>();

        Log.LogInfo($"MarvelMod {Version} loaded! Character: {ModConfig.Data.ActiveCharacter}");
    }
}
