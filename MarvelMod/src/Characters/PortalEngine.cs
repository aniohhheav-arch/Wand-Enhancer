using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.Config;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Characters;

/// <summary>
/// Doctor Strange's sling-ring portals. A portal is opened alone (waiting for its link) and becomes
/// traversable once linked to a second one. Every client runs the same portals (created by casts);
/// each client teleports only the players it controls. Exits are pushed out of walls, a short
/// per-player cooldown prevents ping-ponging, and portals close on timeout, at meetings, or on command.
/// </summary>
public static class PortalEngine
{
    public sealed class Portal
    {
        public int Id;
        public byte Owner;
        public Vector2 Pos;
        public Portal? Link;
        public float EndsAt;
        /// <summary>Snare: teleports the first non-owner who touches it to TrapExit, then closes.</summary>
        public Vector2? TrapExit;
        public GameObject? Visual;
    }

    private static readonly List<Portal> Open = new();
    private static readonly Dictionary<byte, Portal> Pending = new();   // opened, waiting for a link
    private static readonly Dictionary<byte, float> Cooldown = new();
    private static int _nextId = 1;
    public static readonly Color Orange = new(1f, 0.6f, 0.15f, 1f);

    public static IReadOnlyList<Portal> All => Open;

    /// <summary>Opens a portal for owner at pos. If they have one waiting, the two are linked.</summary>
    public static Portal OpenPortal(PlayerControl owner, Vector2 pos, float lifetime, bool linkToPending)
    {
        var p = Make(owner, pos, lifetime);
        if (linkToPending && Pending.TryGetValue(owner.PlayerId, out var a) && Open.Contains(a))
        {
            Link(a, p);
            Pending.Remove(owner.PlayerId);
        }
        else Pending[owner.PlayerId] = p;
        EnforceLimit(owner.PlayerId);
        return p;
    }

    public static (Portal a, Portal b) OpenPair(PlayerControl owner, Vector2 a, Vector2 b, float lifetime)
    {
        var pa = Make(owner, a, lifetime);
        var pb = Make(owner, b, lifetime);
        Link(pa, pb);
        EnforceLimit(owner.PlayerId);
        return (pa, pb);
    }

    public static Portal OpenSnare(PlayerControl owner, Vector2 pos, Vector2 exit, float lifetime)
    {
        var p = Make(owner, pos, lifetime);
        p.TrapExit = exit;
        if (p.Visual != null) p.Visual.transform.localScale *= 0.8f;
        return p;
    }

    public static bool HasPending(PlayerControl owner) => Pending.TryGetValue(owner.PlayerId, out var p) && Open.Contains(p);

    /// <summary>The exit of the owner's most recent linked pair (Portal Redirect).</summary>
    public static Vector2? LatestExit(PlayerControl owner)
    {
        for (int i = Open.Count - 1; i >= 0; i--)
            if (Open[i].Owner == owner.PlayerId && Open[i].Link != null) return Open[i].Pos;
        return null;
    }

    private static void Link(Portal a, Portal b)
    {
        a.Link = b; b.Link = a;
        float end = Mathf.Max(a.EndsAt, b.EndsAt);
        a.EndsAt = b.EndsAt = end;
        Fx.Beam(a.Pos, b.Pos, new Color(Orange.r, Orange.g, Orange.b, 0.5f), 0.08f, 0.5f);
    }

    private static Portal Make(PlayerControl owner, Vector2 pos, float lifetime)
    {
        var p = new Portal { Id = _nextId++, Owner = owner.PlayerId, Pos = pos, EndsAt = Time.time + lifetime };
        p.Visual = Visual(pos);
        Open.Add(p);
        Sfx.Play(SfxKind.Whoosh, 0.9f, pos, 0.7f);
        return p;
    }

    /// <summary>Concentric rotating sling-ring circles with sparks, opening from nothing.</summary>
    private static GameObject Visual(Vector2 pos)
    {
        var root = new GameObject("StrangePortal");
        float size = Mathf.Clamp(ModConfig.Data.PortalSize, 0.5f, 3f);
        var glow = Fx.MakeSprite(Sprites.Glow, pos, new Color(1f, 0.5f, 0.1f, 0.55f), size * 1.3f, "PortalGlow");
        var inner = Fx.MakeSprite(Sprites.Glow, pos, new Color(0.1f, 0.05f, 0.15f, 0.85f), size * 0.8f, "PortalHole");
        var r1 = Fx.MakeSprite(Sprites.Runes, pos, Orange, size, "PortalRing1");
        var r2 = Fx.MakeSprite(Sprites.Ring, pos, new Color(1f, 0.85f, 0.4f, 1f), size * 0.85f, "PortalRing2");
        foreach (var sr in new[] { glow, inner, r1, r2 })
        {
            sr.transform.SetParent(root.transform, true);
            sr.transform.localScale = new Vector3(sr.transform.localScale.x * 0.8f, sr.transform.localScale.y, 1f);
        }
        root.transform.localScale = Vector3.one * 0.05f;
        root.AddComponent<PortalSpin>();
        return root;
    }

    public static void Close(Portal p, bool withEffect = true)
    {
        Open.Remove(p);
        if (p.Link != null && p.Link.Link == p) p.Link.Link = null;
        if (p.Visual != null)
        {
            if (withEffect)
            {
                Fx.Sparkles(p.Pos, Orange, 10, 1f, 0.5f);
                Sfx.Play(SfxKind.Chime, 1.4f, p.Pos, 0.4f);
            }
            Object.Destroy(p.Visual);
        }
        foreach (var kv in new List<KeyValuePair<byte, Portal>>(Pending))
            if (kv.Value == p) Pending.Remove(kv.Key);
    }

    public static void CloseAll(bool withEffect = true)
    {
        foreach (var p in new List<Portal>(Open)) Close(p, withEffect);
        Pending.Clear();
    }

    public static bool CloseById(int id)
    {
        var p = Open.Find(x => x.Id == id);
        if (p == null) return false;
        var link = p.Link;
        Close(p);
        if (link != null) Close(link);   // a broken link removes both
        return true;
    }

    /// <summary>Keep at most PortalMaxPairs pairs per owner (oldest close first).</summary>
    private static void EnforceLimit(byte owner)
    {
        int max = Mathf.Max(1, ModConfig.Data.PortalMaxPairs) * 2 + 1;   // +1 for a pending one
        var mine = Open.FindAll(p => p.Owner == owner && p.TrapExit == null);
        while (mine.Count > max)
        {
            Close(mine[0]);
            mine.RemoveAt(0);
        }
    }

    /// <summary>Every frame on every client.</summary>
    public static void Tick()
    {
        if (Open.Count == 0) return;
        if (MeetingHud.Instance != null) { CloseAll(false); return; }

        foreach (var p in new List<Portal>(Open))
            if (Time.time >= p.EndsAt) Close(p);

        foreach (var pc in Players.All())
        {
            if (!Players.IsAlive(pc) || !Players.Controls(pc) || pc.inVent) continue;
            if (Cooldown.TryGetValue(pc.PlayerId, out var until) && Time.time < until) continue;
            Vector2 pos = pc.transform.position;
            foreach (var p in Open)
            {
                if (Vector2.Distance(pos, p.Pos) > 0.45f) continue;
                if (p.TrapExit.HasValue)
                {
                    if (pc.PlayerId == p.Owner) continue;
                    Cooldown[pc.PlayerId] = Time.time + 1.5f;
                    var exit = p.TrapExit.Value;
                    Close(p);
                    if (pc == PlayerControl.LocalPlayer) Fx.Toast("A SLING-RING SNARE!", Orange, 1.5f, 3.5f);
                    Players.Teleport(pc, exit);
                    break;
                }
                if (p.Link == null) continue;
                Cooldown[pc.PlayerId] = Time.time + Mathf.Max(0.3f, ModConfig.Data.PortalTeleportCooldown);
                var vel = pc.MyPhysics != null && pc.MyPhysics.body != null ? pc.MyPhysics.body.velocity : Vector2.zero;
                var dir = vel.sqrMagnitude > 0.01f ? vel.normalized : Vector2.down;   // keep walking the same way
                Players.Teleport(pc, Players.SafeDestination(p.Link.Pos, dir, 0.6f));
                Sfx.Play(SfxKind.Whoosh, 1.3f, p.Link.Pos, 0.5f);
                break;
            }
        }
    }

    public static string Status()
    {
        if (Open.Count == 0) return "No open portals.";
        var lines = new List<string>();
        foreach (var p in Open)
            lines.Add($"  #{p.Id} owner {Players.ById(p.Owner)?.Data?.PlayerName ?? "?"} at ({p.Pos.x:F1},{p.Pos.y:F1}) " +
                      (p.TrapExit.HasValue ? "SNARE" : p.Link != null ? $"-> #{p.Link.Id}" : "unlinked") +
                      $" {Mathf.Max(0f, p.EndsAt - Time.time):F0}s left");
        return string.Join("\n", lines);
    }
}

/// <summary>Spins and opens the portal rings.</summary>
[Reactor.Utilities.Attributes.RegisterInIl2Cpp]
public class PortalSpin : MonoBehaviour
{
    public PortalSpin(System.IntPtr ptr) : base(ptr) { }
    private float _age;

    public void Update()
    {
        _age += Time.deltaTime;
        transform.localScale = Vector3.one * Mathf.Min(1f, _age / 0.35f);
        for (int i = 0; i < transform.childCount; i++)
        {
            var c = transform.GetChild(i);
            if (c.name == "PortalRing1") c.Rotate(0, 0, 70f * Time.deltaTime);
            else if (c.name == "PortalRing2") c.Rotate(0, 0, -120f * Time.deltaTime);
        }
        if (Random.value < 0.25f)
            Fx.Sparkles((Vector2)transform.position + Random.insideUnitCircle * 0.5f, PortalEngine.Orange, 1, 0.4f, 0.4f);
    }
}
