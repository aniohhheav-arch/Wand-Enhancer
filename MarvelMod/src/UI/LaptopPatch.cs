using System;
using AmongUs.GameOptions;
using HarmonyLib;
using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.UI;

/// <summary>
/// Freeplay laptop: adds one file per Marvel character next to the vanilla Impostor role file.
/// Clicking it makes you an impostor (via the vanilla Impostor file) and picks that character.
/// </summary>
[HarmonyPatch(typeof(TaskAdderGame), nameof(TaskAdderGame.ShowFolder))]
public static class LaptopPatch
{
    private static readonly BepInEx.Logging.ManualLogSource Log = BepInEx.Logging.Logger.CreateLogSource("MarvelMod.Laptop");

    public static void Postfix(TaskAdderGame __instance)
    {
        try
        {
            AddCharacterFiles(__instance);
        }
        catch (Exception e)
        {
            Log.LogWarning($"Could not add character files to the laptop: {e.Message}");
        }
    }

    private static void AddCharacterFiles(TaskAdderGame laptop)
    {
        TaskAddButton? impostor = null, crewmate = null;
        Transform? last = null, first = null;
        foreach (var item in laptop.ActiveItems)
        {
            if (item == null) continue;
            first ??= item;
            last = item;
            var file = item.GetComponent<TaskAddButton>();
            if (file != null && file.Role != null && file.Role.Role == RoleTypes.Impostor) impostor = file;
            if (file != null && file.Role != null && file.Role.Role == RoleTypes.Crewmate) crewmate = file;
        }
        if (impostor == null || last == null || first == null) return;   // not the roles folder

        int index = 0;
        foreach (var character in CharacterRegistry.All)
        {
            var source = character.IsCrewmateRole && crewmate != null ? crewmate : impostor;
            var copy = Object.Instantiate(source, source.transform.parent);
            copy.name = $"MarvelRole_{character.Id}";
            var text = copy.Text;
            var overlay = copy.Overlay;
            var button = copy.Button;
            var fileImage = copy.FileImage;
            var root = copy.transform;
            Object.DestroyImmediate(copy);   // drop the vanilla script; keep its visuals and button

            // next slot: continue the row, wrap after four files
            index++;
            var pos = last.localPosition + new Vector3(laptop.fileWidth * index, 0f, 0f);
            if (pos.x > first.localPosition.x + laptop.fileWidth * 3.5f)
                pos = new Vector3(first.localPosition.x + laptop.fileWidth * (index - 1), last.localPosition.y - laptop.lineWidth, pos.z);
            root.localPosition = pos;

            text.text = character.DisplayName;
            if (fileImage != null) fileImage.color = character.Color;
            bool chosen = string.Equals(Config.ModConfig.Data.ActiveCharacter, character.Id, StringComparison.OrdinalIgnoreCase);
            if (overlay != null) overlay.enabled = chosen;

            var def = character;
            var vanillaRole = source;
            button.OnClick = new UnityEngine.UI.Button.ButtonClickedEvent();
            button.OnClick.AddListener((Action)(() =>
            {
                vanillaRole.AddTask();   // become an impostor / crewmate the normal way
                CharacterRegistry.Choose(def.Id);
                Fx.Toast($"YOU ARE {def.DisplayName.ToUpperInvariant()}", def.Color, 2.5f, 5f, 1.6f);
                if (overlay != null) overlay.enabled = true;
            }));
            laptop.ActiveItems.Add(root);
        }
    }
}
