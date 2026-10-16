using System;
using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.UI.Console;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.UI.HUD;

/// <summary>
/// Real Among Us action buttons (cloned from the Kill button) placed next to Use / Report.
///
///   [1] [2] [3]
///   [4] [5] [tab]   [vanilla buttons]
///
/// Left-click or press 1-5 to cast. The tab button (or Q / E) switches page.
/// H hides the buttons, F4 toggles cooldowns, F3 switches character (freeplay / lobby).
/// </summary>
public static class AbilityButtons
{
    private const int AbilitySlots = 5;
    private const int SwitchSlot = 5;

    private static readonly KeyCode[] SlotKeys = { KeyCode.Alpha1, KeyCode.Alpha2, KeyCode.Alpha3, KeyCode.Alpha4, KeyCode.Alpha5 };
    private static readonly Color TargetOutline = new(0.25f, 1f, 0.4f, 1f);
    private static readonly BepInEx.Logging.ManualLogSource Log = BepInEx.Logging.Logger.CreateLogSource("MarvelMod.HUD");

    private static readonly ActionButton?[] Buttons = new ActionButton?[AbilitySlots + 1];
    private static HudManager? _hud;
    private static HudManager? _failedHud;   // never retry creation every frame on a HUD where it failed
    private static CharacterDef? _character;
    private static int _tab;
    private static string _shownKey = "";
    private static bool _visible = true;
    private static PlayerControl? _outlined;

    /// <summary>Last direction the local player walked in (used by aimed abilities).</summary>
    public static Vector2 LastMoveDirection { get; private set; } = Vector2.right;

    public static void Update(HudManager hud)
    {
        try
        {
            UpdateInner(hud);
        }
        catch (Exception e)
        {
            // Log once per HUD instead of spamming the console every frame.
            if (_failedHud != hud) Log.LogError($"Ability buttons disabled for this HUD: {e}");
            _failedHud = hud;
        }
    }

    private static void UpdateInner(HudManager hud)
    {
        var lp = PlayerControl.LocalPlayer;
        if (lp == null || hud.KillButton == null || _failedHud == hud) return;

        if (_hud != hud || !AllCreated()) Create(hud);

        CharacterRegistry.UpdateLocal(lp);
        if (_character != CharacterRegistry.Local)
        {
            _character = CharacterRegistry.Local;
            _tab = 0;
            _shownKey = "";
        }

        TrackMovement(lp);
        HandleKeys(lp);
        _character?.LocalUpdate(lp, hud);

        bool show = _character != null && ShouldShow(hud, lp);
        foreach (var b in Buttons)
            if (b != null && b.gameObject.activeSelf != show) b.gameObject.SetActive(show);
        if (!show)
        {
            SetOutline(null);
            return;
        }

        EnsureTabUnlocked(lp);
        var key = $"{_character!.Id}:{_tab}";
        if (_shownKey != key) RefreshTab(key);
        Layout(hud);
        UpdateStates(lp);
    }

    // ── creation ─────────────────────────────────────────────────────────────

    private static bool AllCreated()
    {
        foreach (var b in Buttons)
            if (b == null) return false;
        return true;
    }

    private static void Create(HudManager hud)
    {
        _hud = hud;
        _failedHud = hud;   // cleared below only if every button was built
        _shownKey = "";
        var template = hud.KillButton;

        for (int i = 0; i < Buttons.Length; i++)
        {
            if (Buttons[i] != null) Object.Destroy(Buttons[i]!.gameObject);

            var b = Object.Instantiate(template, hud.transform);
            b.name = $"MarvelButton_{i}";
            Buttons[i] = b;   // track immediately so a failure below can never leak clones

            // match the on-screen size of the original regardless of parent scaling
            var want = template.transform.lossyScale;
            var parent = hud.transform.lossyScale;
            b.transform.localScale = new Vector3(want.x / parent.x, want.y / parent.y, want.z / parent.z);

            var aspect = b.GetComponent<AspectPosition>();
            if (aspect != null) Object.Destroy(aspect);

            var passive = b.GetComponent<PassiveButton>();
            passive.OnClick = new UnityEngine.UI.Button.ButtonClickedEvent();
            int slot = i;
            passive.OnClick.AddListener((Action)(() => OnClick(slot)));

            // The Kill button has no uses counter (SetInfiniteUses() throws on it), so only hide it if present.
            if (b.usesRemainingSprite != null) b.usesRemainingSprite.gameObject.SetActive(false);
            if (b.usesRemainingText != null) b.usesRemainingText.gameObject.SetActive(false);
            b.gameObject.SetActive(false);
        }
        _failedHud = null;
    }

    private static List<AbilityBase> Slots() =>
        _character == null ? new List<AbilityBase>() : _character.Tabs[_tab].Abilities;

    private static void RefreshTab(string key)
    {
        _shownKey = key;
        var slots = Slots();
        for (int i = 0; i < AbilitySlots; i++)
        {
            var b = Buttons[i];
            if (b == null) continue;
            if (i < slots.Count)
            {
                SetIcon(b, slots[i].Id);
                b.OverrideText(slots[i].Label);
            }
            else b.OverrideText("");
        }

        var sw = Buttons[SwitchSlot];
        if (sw != null && _character != null)
        {
            SetIcon(sw, _character.Tabs[_tab].IconId);
            sw.OverrideText(_character.Tabs[_tab].Name);
            sw.SetCoolDown(0f, 1f);
            sw.SetEnabled();
        }
    }

    private static void SetIcon(ActionButton b, string id)
    {
        // Size our 256 px icons to the same world size as the game's own button art.
        float ppu = 100f;
        var original = _hud != null && _hud.KillButton.graphic != null ? _hud.KillButton.graphic.sprite : null;
        if (original != null && original.bounds.size.x > 0.01f)
            ppu = 256f / original.bounds.size.x;
        var icon = Sprites.Icon(id, ppu);
        if (icon != null) b.graphic.sprite = icon;
    }

    // ── per-frame ────────────────────────────────────────────────────────────

    private static bool ShouldShow(HudManager hud, PlayerControl lp)
    {
        if (!_visible || !ModConfig.Data.HudEnabled) return false;
        if (lp.Data == null || lp.Data.IsDead) return false;
        if (MeetingHud.Instance != null || ExileController.Instance != null || hud.IsIntroDisplayed) return false;
        if (MapBehaviour.Instance != null && MapBehaviour.Instance.IsOpen) return false;
        if (hud.Chat != null && hud.Chat.IsOpenOrOpening) return false;
        if (TargetMenu.IsOpen || Minigame.Instance != null || CinematicDirector.LocalActive) return false;
        return CharacterRegistry.InLobby
            || (hud.UseButton != null && hud.UseButton.gameObject.activeInHierarchy)
            || (hud.ReportButton != null && hud.ReportButton.gameObject.activeInHierarchy);
    }

    private static void TrackMovement(PlayerControl lp)
    {
        if (lp.MyPhysics == null || lp.MyPhysics.body == null) return;
        var v = lp.MyPhysics.body.velocity;
        if (v.sqrMagnitude > 0.01f) LastMoveDirection = v.normalized;
    }

    private static bool TypingSomewhere() =>
        DevConsole.IsOpen || (_hud != null && _hud.Chat != null && _hud.Chat.IsOpenOrOpening);

    private static void HandleKeys(PlayerControl lp)
    {
        if (TypingSomewhere()) return;

        if (Input.GetKeyDown(KeyCode.H)) _visible = !_visible;

        if (Input.GetKeyDown(KeyCode.F4))
        {
            ModConfig.Data.PracticeUnlimited = !ModConfig.Data.PracticeUnlimited;
            ModConfig.Save();
            if (!ModConfig.Data.PracticeUnlimited)
                foreach (var a in CharacterRegistry.AllAbilities()) a.ResetCooldown();
            Fx.Toast(ModConfig.Data.PracticeUnlimited ? "COOLDOWNS OFF" : "COOLDOWNS ON", Fx.LokiGold, 1.5f, 4f, 1.2f);
        }

        if (Input.GetKeyDown(KeyCode.F3) && (CharacterRegistry.IsFreeplay || CharacterRegistry.InLobby))
        {
            var id = CharacterRegistry.CycleFreeplayCharacter();
            var def = CharacterRegistry.ById(id);
            Fx.Toast(def != null ? $"CHARACTER: {def.DisplayName.ToUpperInvariant()}" : "CHARACTER: NONE",
                def?.Color ?? Color.white, 1.8f, 4f, 1.2f);
        }

        if (!_visible || _character == null) return;
        if (Input.GetKeyDown(KeyCode.Q)) CycleTab(lp, -1);
        if (Input.GetKeyDown(KeyCode.E)) CycleTab(lp, +1);
        for (int i = 0; i < AbilitySlots; i++)
            if (Input.GetKeyDown(SlotKeys[i])) OnClick(i);
    }

    private static void CycleTab(PlayerControl lp, int dir)
    {
        if (_character == null) return;
        int n = _character.Tabs.Count;
        for (int step = 1; step <= n; step++)
        {
            int t = ((_tab + dir * step) % n + n) % n;
            if (_character.Tabs[t].Unlocked(lp)) { _tab = t; return; }
        }
    }

    private static void EnsureTabUnlocked(PlayerControl lp)
    {
        if (_character == null) return;
        if (_tab >= _character.Tabs.Count) _tab = 0;
        if (!_character.Tabs[_tab].Unlocked(lp)) CycleTab(lp, +1);
    }

    /// <summary>Places the buttons in a 3x2 block directly left of the vanilla button grid.</summary>
    private static void Layout(HudManager hud)
    {
        var vanilla = new List<Vector3>();
        foreach (var v in new MonoBehaviour?[]
                 { hud.UseButton, hud.ReportButton, hud.KillButton, hud.ImpostorVentButton, hud.SabotageButton, hud.AbilityButton })
            if (v != null && v.gameObject.activeInHierarchy) vanilla.Add(v.transform.position);

        var first = Buttons[0]!;
        float size = 1f;
        if (first.graphic != null && first.graphic.sprite != null)
            size = first.graphic.sprite.bounds.size.x * first.graphic.transform.lossyScale.x;

        // spacing = the gap the game itself uses between neighbouring buttons
        float spacing = size * 1.3f;
        for (int i = 0; i < vanilla.Count; i++)
        for (int j = i + 1; j < vanilla.Count; j++)
        {
            float dx = Mathf.Abs(vanilla[i].x - vanilla[j].x);
            if (Mathf.Abs(vanilla[i].y - vanilla[j].y) < 0.1f && dx > size * 0.8f)
                spacing = Mathf.Min(spacing, dx);
        }
        spacing = Mathf.Max(spacing, size * 1.15f);

        float minX = float.MaxValue, minY = float.MaxValue, z = first.transform.position.z;
        foreach (var p in vanilla)
        {
            minX = Mathf.Min(minX, p.x);
            minY = Mathf.Min(minY, p.y);
            z = p.z;
        }
        if (vanilla.Count == 0)   // vanilla grid hidden (lobby): anchor to the bottom-right corner
        {
            var cam = Camera.main;
            if (cam == null) return;
            var c = cam.transform.position;
            minX = c.x + cam.orthographicSize * cam.aspect - size * 0.1f;
            minY = c.y - cam.orthographicSize + size * 0.7f;
        }

        // (row, column-from-right) for slots 0..5
        (int row, int col)[] grid = { (1, 2), (1, 1), (1, 0), (0, 2), (0, 1), (0, 0) };
        for (int i = 0; i < Buttons.Length; i++)
        {
            var b = Buttons[i];
            if (b == null) continue;
            var (row, col) = grid[i];
            b.transform.position = new Vector3(minX - spacing * (col + 1), minY + spacing * row, z);
        }
    }

    private static void UpdateStates(PlayerControl lp)
    {
        var slots = Slots();
        PlayerControl? outline = null;
        bool canAct = lp.CanMove && !lp.inVent;

        for (int i = 0; i < AbilitySlots; i++)
        {
            var b = Buttons[i];
            if (b == null) continue;
            if (i >= slots.Count) { b.SetCoolDown(0f, 1f); b.SetDisabled(); continue; }

            var a = slots[i];
            b.SetCoolDown(a.CooldownRemaining, Mathf.Max(0.01f, a.Cfg.Cooldown));

            var target = a.FindTarget(lp);
            if (target != null && a.IsReady && (outline == null || Closer(lp, target, outline))) outline = target;

            bool usable = canAct && a.IsReady && (a.Targeting != TargetMode.Required || target != null);
            if (usable) b.SetEnabled(); else b.SetDisabled();
        }
        SetOutline(outline);
    }

    private static bool Closer(PlayerControl me, PlayerControl a, PlayerControl b) =>
        Vector2.Distance(Players.Pos(me), Players.Pos(a)) < Vector2.Distance(Players.Pos(me), Players.Pos(b));

    private static void SetOutline(PlayerControl? target)
    {
        if (_outlined == target) return;
        if (_outlined != null && _outlined.cosmetics != null)
            _outlined.cosmetics.SetOutline(false, new Il2CppSystem.Nullable<Color>(TargetOutline));
        if (target != null && target.cosmetics != null)
            target.cosmetics.SetOutline(true, new Il2CppSystem.Nullable<Color>(TargetOutline));
        _outlined = target;
    }

    // ── input ────────────────────────────────────────────────────────────────

    private static void OnClick(int slot)
    {
        var lp = PlayerControl.LocalPlayer;
        if (lp == null || _hud == null || _character == null || !ShouldShow(_hud, lp)) return;
        var button = Buttons[slot];
        if (button != null) Fx.Punch(button.transform);

        if (slot == SwitchSlot)
        {
            CycleTab(lp, +1);
            return;
        }

        var slots = Slots();
        if (slot >= slots.Count || !lp.CanMove || lp.inVent) return;
        var ability = slots[slot];
        if (!ability.IsReady) return;

        if (ability.Targeting == TargetMode.Menu)
        {
            TargetMenu.Open(picked => ability.TryActivate(PlayerControl.LocalPlayer, picked));
            return;
        }
        ability.TryActivate(lp, ability.FindTarget(lp));
    }
}
