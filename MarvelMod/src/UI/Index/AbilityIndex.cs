using System;
using MarvelMod.Characters;
using MarvelMod.UI.Console;
using Reactor.Utilities.Attributes;
using UnityEngine;

namespace MarvelMod.UI.Index;

/// <summary>F1: on-screen list of every ability for your character, what it does and its numbers.</summary>
[RegisterInIl2Cpp]
public class AbilityIndex : MonoBehaviour
{
    public AbilityIndex(IntPtr ptr) : base(ptr) { }

    public static bool IsOpen { get; private set; }
    private Vector2 _scroll;
    private GUIStyle? _title, _head, _body, _dim;

    public void Update()
    {
        if (DevConsole.IsOpen) return;
        if (Input.GetKeyDown(KeyCode.F1)) IsOpen = !IsOpen;
        if (IsOpen && Input.GetKeyDown(KeyCode.Escape)) IsOpen = false;
    }

    public void OnGUI()
    {
        if (!IsOpen) return;
        Styles();

        float w = Mathf.Min(900f, Screen.width - 40f), h = Screen.height - 80f;
        var rect = new Rect((Screen.width - w) / 2f, 40f, w, h);
        GUI.Box(rect, "");
        GUI.Box(rect, "");   // darker

        var character = CharacterRegistry.Local ?? CharacterRegistry.ById(Config.ModConfig.Data.ActiveCharacter);
        string title = character != null ? $"{character.DisplayName.ToUpperInvariant()} — ABILITY INDEX" : "ABILITY INDEX";
        GUI.Label(new Rect(rect.x + 16, rect.y + 10, w - 140, 30), title, _title!);
        if (GUI.Button(new Rect(rect.xMax - 110, rect.y + 10, 95, 26), "Close (F1)")) IsOpen = false;

        var view = new Rect(rect.x + 10, rect.y + 48, w - 20, h - 58);
        float contentH = Measure(character, view.width - 30);
        _scroll = GUI.BeginScrollView(view, _scroll, new Rect(0, 0, view.width - 20, contentH));
        float y = 0;

        GUI.Label(new Rect(0, y, view.width - 30, 22), "CONTROLS", _head!); y += 24;
        foreach (var line in AbilityDescriptions.Controls)
        {
            GUI.Label(new Rect(10, y, view.width - 40, 20), "• " + line, _body!);
            y += 20;
        }
        y += 10;

        if (character == null)
        {
            GUI.Label(new Rect(0, y, view.width - 30, 40),
                "You don't have a character right now. Impostors get one at the start of a game; in freeplay pick one on the laptop or press F3.", _body!);
        }
        else
        {
            foreach (var tab in character.Tabs)
            {
                GUI.Label(new Rect(0, y, view.width - 30, 24), $"── {tab.Name} ──", _head!); y += 26;
                foreach (var a in tab.Abilities) y = DrawAbility(a, y, view.width - 30);
                y += 6;
            }
            if (character.MeetingAbilities.Count > 0)
            {
                GUI.Label(new Rect(0, y, view.width - 30, 24), "── MEETING POWERS ──", _head!); y += 26;
                foreach (var a in character.MeetingAbilities) y = DrawAbility(a, y, view.width - 30);
            }
            foreach (var a in character.Hidden) y = DrawAbility(a, y, view.width - 30);
        }
        GUI.EndScrollView();
    }

    private float DrawAbility(Abilities.AbilityBase a, float y, float width)
    {
        var cfg = a.Cfg;
        string stats = $"cooldown {cfg.Cooldown:0.#}s";
        if (cfg.Duration > 0) stats += $" · duration {cfg.Duration:0.#}s";
        if (cfg.Range > 0) stats += $" · range {cfg.Range:0.#}";
        if (!cfg.Enabled) stats += " · DISABLED";
        GUI.Label(new Rect(10, y, width, 20), $"<b>{a.Name}</b>  <color=#9fd9a8>[{a.Label}]</color>   <color=#999999>{stats}</color>", _body!);
        y += 20;
        var desc = AbilityDescriptions.For(a.Id);
        float dh = _dim!.CalcHeight(new GUIContent(desc), width - 30);
        GUI.Label(new Rect(28, y, width - 30, dh), desc, _dim!);
        return y + dh + 6;
    }

    private float Measure(CharacterDef? c, float width)
    {
        float y = 24 + AbilityDescriptions.Controls.Length * 20 + 10;
        if (c == null) return y + 60;
        foreach (var tab in c.Tabs)
        {
            y += 26 + 6;
            foreach (var a in tab.Abilities)
                y += 20 + _dim!.CalcHeight(new GUIContent(AbilityDescriptions.For(a.Id)), width - 30) + 6;
        }
        if (c.MeetingAbilities.Count > 0) y += 26;
        foreach (var a in c.MeetingAbilities) y += 20 + _dim!.CalcHeight(new GUIContent(AbilityDescriptions.For(a.Id)), width - 30) + 6;
        foreach (var a in c.Hidden) y += 20 + _dim!.CalcHeight(new GUIContent(AbilityDescriptions.For(a.Id)), width - 30) + 6;
        return y + 20;
    }

    private void Styles()
    {
        if (_title != null) return;
        _title = new GUIStyle(GUI.skin.label) { fontSize = 20, fontStyle = FontStyle.Bold, richText = true };
        _title.normal.textColor = new Color(0.45f, 1f, 0.55f);
        _head = new GUIStyle(GUI.skin.label) { fontSize = 15, fontStyle = FontStyle.Bold, richText = true };
        _head.normal.textColor = new Color(1f, 0.82f, 0.3f);
        _body = new GUIStyle(GUI.skin.label) { fontSize = 14, richText = true, wordWrap = true };
        _body.normal.textColor = Color.white;
        _dim = new GUIStyle(GUI.skin.label) { fontSize = 13, richText = true, wordWrap = true };
        _dim.normal.textColor = new Color(0.82f, 0.86f, 0.9f);
    }
}
