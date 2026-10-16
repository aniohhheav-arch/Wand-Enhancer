using System;
using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Meeting;
using MarvelMod.Characters;
using Reactor.Utilities.Attributes;
using UnityEngine;

namespace MarvelMod.UI;

/// <summary>
/// During meetings: a panel on the left with your character's meeting abilities. Targeted ones
/// show a list of players to pick from. Results and private info appear in the panel's log.
/// </summary>
[RegisterInIl2Cpp]
public class MeetingPanel : MonoBehaviour
{
    public MeetingPanel(IntPtr ptr) : base(ptr) { }

    private static readonly List<(string text, Color color)> Lines = new();
    private static MeetingAbility? _picking;
    private static bool _collapsed;
    private GUIStyle? _title, _btn, _log, _small;

    public static void Log(string text, Color color)
    {
        Lines.Add((text, color));
        if (Lines.Count > 8) Lines.RemoveAt(0);
    }

    public static void Clear()
    {
        Lines.Clear();
        _picking = null;
    }

    public void OnGUI()
    {
        var lp = PlayerControl.LocalPlayer;
        var character = CharacterRegistry.Local;
        if (MeetingHud.Instance == null || lp == null || character == null || character.MeetingAbilities.Count == 0) return;
        if (lp.Data == null || lp.Data.IsDead) return;
        Styles();

        float w = 300f, x = 12f, y = 120f;
        var abilities = character.MeetingAbilities;
        float h = _collapsed ? 34f : 50f + abilities.Count * 34f + 18f + Lines.Count * 34f + (_picking != null ? 30f + PlayerCount() * 28f : 0f);
        GUI.Box(new Rect(x, y, w, h), "");
        GUI.Box(new Rect(x, y, w, h), "");
        GUI.Label(new Rect(x + 10, y + 6, w - 60, 24), $"{character.DisplayName.ToUpperInvariant()} - MEETING POWERS", _title!);
        if (GUI.Button(new Rect(x + w - 40, y + 6, 30, 22), _collapsed ? "+" : "-")) _collapsed = !_collapsed;
        if (_collapsed) return;

        float cy = y + 40;
        foreach (var raw in abilities)
        {
            if (raw is not MeetingAbility a) continue;
            bool usable = a.CanUse(lp);
            string uses = a.UsesLeft > 0 ? $"{a.UsesLeft} left" : "used";
            GUI.enabled = usable;
            if (GUI.Button(new Rect(x + 10, cy, w - 20, 30), $"{a.Name}   <size=11>({uses})</size>", _btn!))
            {
                if (a.Targeting == TargetMode.Menu) _picking = _picking == a ? null : a;
                else { _picking = null; a.Use(lp, null); }
            }
            GUI.enabled = true;
            cy += 34;
        }

        if (_picking != null)
        {
            GUI.Label(new Rect(x + 10, cy, w - 20, 24), $"Pick a player for {_picking.Label}:", _small!);
            cy += 26;
            foreach (var pc in Players.All())
            {
                if (pc == lp || !Players.IsAlive(pc)) continue;
                if (GUI.Button(new Rect(x + 20, cy, w - 40, 24), pc.Data?.PlayerName ?? "?", _btn!))
                {
                    _picking.Use(lp, pc);
                    _picking = null;
                    break;
                }
                cy += 28;
            }
        }

        cy += 8;
        foreach (var (text, color) in Lines)
        {
            _log!.normal.textColor = color;
            GUI.Label(new Rect(x + 10, cy, w - 20, 32), text, _log);
            cy += 34;
        }
    }

    private static int PlayerCount()
    {
        int n = 0;
        foreach (var pc in Players.All()) if (pc != PlayerControl.LocalPlayer && Players.IsAlive(pc)) n++;
        return n;
    }

    private void Styles()
    {
        if (_title != null) return;
        _title = new GUIStyle(GUI.skin.label) { fontSize = 14, fontStyle = FontStyle.Bold };
        _title.normal.textColor = new Color(1f, 0.82f, 0.3f);
        _btn = new GUIStyle(GUI.skin.button) { fontSize = 13, richText = true, alignment = TextAnchor.MiddleLeft };
        _log = new GUIStyle(GUI.skin.label) { fontSize = 12, wordWrap = true, richText = true };
        _small = new GUIStyle(GUI.skin.label) { fontSize = 12 };
        _small.normal.textColor = Color.white;
    }
}
