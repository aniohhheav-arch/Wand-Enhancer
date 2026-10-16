using Reactor.Utilities.Attributes;
using System;
using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Characters;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.UI.HUD;

// ── In-game ability bar ───────────────────────────────────────────────────────
// Layout: bottom-center panel with 5 category tabs + 5 ability slots.
// Activate: click a button OR press keys 1-5.
// Switch category: Q / E  or click the tab.
// ─────────────────────────────────────────────────────────────────────────────

[RegisterInIl2Cpp]
public class AbilityHud : MonoBehaviour
{
    public AbilityHud(IntPtr ptr) : base(ptr) { }

    public static AbilityHud? Instance { get; private set; }

    // ── sizing ────────────────────────────────────────────────────────────────
    private const float PanelW   = 620f;
    private const float TabH     = 30f;
    private const float SlotH    = 64f;
    private const float PanelH   = TabH + SlotH + 16f;
    private const float SlotW    = PanelW / 5f;

    // ── state ─────────────────────────────────────────────────────────────────
    private int  _tab   = 0;   // 0-4
    private bool _visible = true;

    private static readonly string[] TabNames =
        { "✦ ILLUSION", "◈ SHIFT", "⚡ TRICK", "⚔ SORCERY", "⚗ MISCHIEF" };

    private static readonly KeyCode[] SlotKeys =
        { KeyCode.Alpha1, KeyCode.Alpha2, KeyCode.Alpha3, KeyCode.Alpha4, KeyCode.Alpha5 };

    // tab switch keys
    private static readonly KeyCode KeyPrevTab = KeyCode.Q;
    private static readonly KeyCode KeyNextTab = KeyCode.E;

    // toggle HUD
    private static readonly KeyCode KeyToggle  = KeyCode.H;

    // ── styles (created once) ─────────────────────────────────────────────────
    private GUIStyle? _stylePanel;
    private GUIStyle? _styleTab;
    private GUIStyle? _styleTabActive;
    private GUIStyle? _styleSlot;
    private GUIStyle? _styleSlotReady;
    private GUIStyle? _styleSlotCD;
    private GUIStyle? _styleName;
    private GUIStyle? _styleKey;
    private GUIStyle? _styleCooldown;
    private bool      _stylesBuilt;

    public void Awake()
    {
        Instance = this;
        DontDestroyOnLoad(gameObject);
    }

    public void Update()
    {
        if (Input.GetKeyDown(KeyToggle))
            _visible = !_visible;

        if (!_visible) return;

        // category switch
        if (Input.GetKeyDown(KeyPrevTab)) _tab = (_tab + 4) % 5;
        if (Input.GetKeyDown(KeyNextTab)) _tab = (_tab + 1) % 5;

        // number key activation
        var slots = GetCurrentSlots();
        for (int i = 0; i < slots.Count && i < 5; i++)
        {
            if (Input.GetKeyDown(SlotKeys[i]))
                TryFire(slots[i]);
        }
    }

    public void OnGUI()
    {
        if (!_visible) return;
        if (!ModConfig.Data.HudEnabled) return;

        BuildStyles();

        float px = (Screen.width  - PanelW) * 0.5f;
        float py =  Screen.height - PanelH  - 12f;

        // ── outer panel ───────────────────────────────────────────────────────
        GUI.Box(new Rect(px - 6, py - 6, PanelW + 12, PanelH + 12), "", _stylePanel!);

        // ── category tabs ─────────────────────────────────────────────────────
        for (int t = 0; t < 5; t++)
        {
            var tabRect = new Rect(px + t * (PanelW / 5), py, PanelW / 5 - 2, TabH);
            var style   = t == _tab ? _styleTabActive! : _styleTab!;
            if (GUI.Button(tabRect, TabNames[t], style))
                _tab = t;
        }

        // ── ability slots ─────────────────────────────────────────────────────
        var slots = GetCurrentSlots();
        for (int i = 0; i < slots.Count && i < 5; i++)
        {
            float sx = px + i * SlotW;
            float sy = py + TabH + 4;
            DrawSlot(new Rect(sx, sy, SlotW - 4, SlotH), slots[i], i + 1);
        }

        // ── hint line ─────────────────────────────────────────────────────────
        var hint = new Rect(px, py + TabH + SlotH + 4, PanelW, 14);
        GUI.Label(hint, "  [1-5] Activate   [Q/E] Switch tab   [H] Toggle HUD   [F6] Console", _styleName!);
    }

    private void DrawSlot(Rect r, AbilityBase ability, int keyNum)
    {
        bool ready = ability.IsReady;
        float cd   = ability.CooldownRemaining;

        // background button — click fires ability
        var style = ready ? _styleSlotReady! : _styleSlotCD!;
        if (GUI.Button(r, "", style))
            TryFire(ability);

        // key badge [1]..[5]
        GUI.Label(new Rect(r.x + 4, r.y + 4, 22, 18), $"[{keyNum}]", _styleKey!);

        // ability name (word-wrap at 2 lines)
        var nameRect = new Rect(r.x + 4, r.y + 20, r.width - 8, 24);
        GUI.Label(nameRect, ability.Name, _styleSlot!);

        // cooldown badge or READY
        var cdRect = new Rect(r.x + 4, r.y + r.height - 18, r.width - 8, 16);
        if (ready)
            GUI.Label(cdRect, "● READY", _styleSlotReady!);
        else
            GUI.Label(cdRect, $"⏱ {cd:F1}s", _styleCooldown!);
    }

    private static void TryFire(AbilityBase ability)
    {
        var caster = PlayerControl.LocalPlayer;
        if (caster == null) return;
        ability.TryActivate(caster, null);
    }

    private List<AbilityBase> GetCurrentSlots() => _tab switch
    {
        0 => LokiCharacter.Instance.IllusionMagic,
        1 => LokiCharacter.Instance.Shapeshifting,
        2 => LokiCharacter.Instance.TricksterMobility,
        3 => LokiCharacter.Instance.SorceryAndCombat,
        4 => LokiCharacter.Instance.GodOfMischief,
        _ => LokiCharacter.Instance.IllusionMagic,
    };

    // ── style factory (built once after first OnGUI) ─────────────────────────
    private void BuildStyles()
    {
        if (_stylesBuilt && _stylePanel!.normal.background != null) return;
        _stylesBuilt = true;

        var panelTex  = MakeTex(1, 1, new Color(0.04f, 0.04f, 0.08f, 0.88f));
        var tabTex    = MakeTex(1, 1, new Color(0.12f, 0.12f, 0.20f, 0.92f));
        var tabActTex = MakeTex(1, 1, new Color(0.10f, 0.55f, 0.25f, 0.95f));
        var slotTex   = MakeTex(1, 1, new Color(0.08f, 0.08f, 0.14f, 0.90f));
        var rdyTex    = MakeTex(1, 1, new Color(0.06f, 0.30f, 0.12f, 0.90f));
        var cdTex     = MakeTex(1, 1, new Color(0.16f, 0.16f, 0.22f, 0.90f));

        _stylePanel = new GUIStyle(GUI.skin.box)
            { normal = { background = panelTex } };

        _styleTab = new GUIStyle(GUI.skin.button)
        {
            normal   = { background = tabTex,    textColor = new Color(0.7f,0.9f,0.7f) },
            hover    = { background = tabActTex, textColor = Color.white },
            fontSize = 10, fontStyle = FontStyle.Bold,
            alignment = TextAnchor.MiddleCenter,
        };
        _styleTabActive = new GUIStyle(_styleTab)
        {
            normal   = { background = tabActTex, textColor = Color.white },
            fontSize = 11,
        };

        _styleSlotReady = new GUIStyle(GUI.skin.button)
        {
            normal   = { background = rdyTex,  textColor = new Color(0.5f,1f,0.6f) },
            hover    = { background = tabActTex, textColor = Color.white },
            fontSize = 9, alignment = TextAnchor.LowerLeft,
            padding  = new RectOffset { left = 4, right = 4, top = 2, bottom = 2 },
        };
        _styleSlotCD = new GUIStyle(GUI.skin.button)
        {
            normal   = { background = cdTex,   textColor = new Color(0.5f,0.5f,0.5f) },
            hover    = { background = slotTex, textColor = new Color(0.7f,0.7f,0.7f) },
            fontSize = 9, alignment = TextAnchor.LowerLeft,
            padding  = new RectOffset { left = 4, right = 4, top = 2, bottom = 2 },
        };

        _styleSlot = new GUIStyle(GUI.skin.label)
        {
            normal   = { textColor = new Color(0.9f,0.95f,0.9f) },
            fontSize = 10, wordWrap = true,
            alignment = TextAnchor.UpperLeft,
        };
        _styleKey = new GUIStyle(GUI.skin.label)
        {
            normal   = { textColor = new Color(0.4f,0.9f,0.5f) },
            fontSize = 9, fontStyle = FontStyle.Bold,
        };
        _styleCooldown = new GUIStyle(GUI.skin.label)
        {
            normal   = { textColor = new Color(0.9f,0.5f,0.3f) },
            fontSize = 9,
        };
        _styleName = new GUIStyle(GUI.skin.label)
        {
            normal   = { textColor = new Color(0.5f,0.5f,0.5f) },
            fontSize = 9,
        };
    }

    private static Texture2D MakeTex(int w, int h, Color c)
    {
        // Without DontSave flags Unity unloads these on scene change and the buttons turn invisible.
        var t = new Texture2D(w, h) { hideFlags = HideFlags.HideAndDontSave };
        t.SetPixel(0, 0, c);
        t.Apply();
        return t;
    }
}
