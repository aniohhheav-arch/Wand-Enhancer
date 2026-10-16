using System.Collections;
using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.VFX;
using Reactor.Utilities;
using TMPro;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Meeting;

/// <summary>
/// Abilities usable only during meetings (discussion and voting), from the F2-style meeting panel.
/// Limited by UsesPerMeeting; Phase picks discussion, voting or both.
/// </summary>
public abstract class MeetingAbility : AbilityBase
{
    public override string Category => "Meeting";
    public override bool Silent => true;   // no world cast flash during meetings

    private static readonly Dictionary<string, int> Used = new();
    public static void NewMeeting() => Used.Clear();

    public int UsesLeft => Mathf.Max(0, Cfg.UsesPerMeeting - (Used.TryGetValue(Id, out var n) ? n : 0));

    public override bool CanUse(PlayerControl caster)
    {
        var meeting = MeetingHud.Instance;
        if (meeting == null || UsesLeft <= 0) return false;
        if (Cfg.PracticeOnly && !CharacterRegistry.IsFreeplay) return false;
        var state = meeting.state;
        bool discussion = state == MeetingHud.MeetingStates.Discussion;
        bool voting = state is MeetingHud.MeetingStates.NotVoted or MeetingHud.MeetingStates.Voted;
        return Cfg.MeetingPhase switch
        {
            "discussion" => discussion,
            "voting"     => voting,
            _            => discussion || voting,
        };
    }

    /// <summary>Uses are counted on the caster's client when the cast is sent.</summary>
    public bool Use(PlayerControl caster, PlayerControl? target)
    {
        if (!TryActivate(caster, target)) return false;
        Used[Id] = (Used.TryGetValue(Id, out var n) ? n : 0) + 1;
        return true;
    }

    protected static PlayerVoteArea? AreaOf(PlayerControl pc)
    {
        var meeting = MeetingHud.Instance;
        if (meeting == null) return null;
        foreach (var a in meeting.playerStates)
            if (a != null && (byte)a.PlayerId == pc.PlayerId) return a;
        return null;
    }

    protected static string NameOf(byte id) => Players.ById(id)?.Data?.PlayerName ?? "someone";

    /// <summary>A coloured line in the meeting panel log (local only).</summary>
    protected static void Report(string text, Color color) => MarvelMod.UI.MeetingPanel.Log(text, color);
}

/// <summary>Who has killed whom this game (real kills only — recorded from MurderPlayer).</summary>
public static class KillLog
{
    private static readonly Dictionary<byte, int> KillsBy = new();
    public static void Record(PlayerControl killer) => KillsBy[killer.PlayerId] = KillsOf(killer.PlayerId) + 1;
    public static int KillsOf(byte id) => KillsBy.TryGetValue(id, out var n) ? n : 0;
    public static void Clear() => KillsBy.Clear();
}

// ══ THANOS ═══════════════════════════════════════════════════════════════════

/// <summary>Mind Stone: see whether a player has voted, and for whom (real vote data).</summary>
public class ReadTheirMinds : MeetingAbility
{
    public override string Id => "read_their_minds";
    public override string Name => "Mind Stone: Read Their Minds";
    public override string Label => "READ MIND";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (!ctx.LocalIs(ctx.Caster) || ctx.Target == null) return;
        var area = AreaOf(ctx.Target);
        string who = ctx.Target.Data?.PlayerName ?? "?";
        if (area == null) { Report($"{who}: no vote area.", Color.gray); return; }
        byte v = area.VotedForId;
        string text = !area.DidVote ? $"{who} hasn't voted yet."
            : v == PlayerVoteArea.SkippedVote ? $"{who} voted to SKIP."
            : v == PlayerVoteArea.HasNotVoted || v == PlayerVoteArea.MissedVote ? $"{who} has voted (target hidden)."
            : ctx.Cfg.Damage >= 1f ? $"{who} voted for {NameOf(v)}."     // Damage >= 1 = full detail
            : $"{who} voted for a player.";
        Report("MIND STONE: " + text, Stones.Color(Stone.Mind));
        Fx.Toast(text, Stones.Color(Stone.Mind), 3f, 3.4f, -0.2f);
    }
}

/// <summary>Reality Stone: only Thanos sees the voting screen twisted (votes are not touched).</summary>
public class RewriteTheVote : MeetingAbility
{
    public override string Id => "rewrite_the_vote";
    public override string Name => "Reality Stone: Rewrite the Vote";
    public override string Label => "REWRITE";

    protected override void OnCast(CastContext ctx)
    {
        if (!ctx.LocalIs(ctx.Caster)) return;
        Coroutines.Start(Run(ctx.Cfg.Duration, ctx.Rng));
        Report("REALITY STONE: the ballot looks different to you (visual only, votes unchanged).", Stones.Color(Stone.Reality));
    }

    private static IEnumerator Run(float duration, System.Random rng)
    {
        var meeting = MeetingHud.Instance;
        if (meeting == null) yield break;
        Fx.ScreenTint(new Color(1f, 0.1f, 0.15f, 0.25f), duration, pulses: 3);
        var names = new List<(TextMeshPro text, string original)>();
        foreach (var a in meeting.playerStates)
            if (a != null && a.NameText != null) names.Add((a.NameText, a.NameText.text));
        for (float t = 0; t < duration; t += 0.25f)
        {
            if (MeetingHud.Instance == null) break;
            foreach (var (text, _) in names)
                if (text != null) text.text = $"<color=#ff3344>{names[rng.Next(names.Count)].original}</color>";
            for (float w = 0; w < 0.25f; w += Time.deltaTime) yield return null;
        }
        foreach (var (text, original) in names) if (text != null) text.text = original;
    }
}

/// <summary>Time Stone: the host clears Thanos's own vote so he can vote again (game's own RpcClearVote).</summary>
public class SecondChance : MeetingAbility
{
    public override string Id => "second_chance";
    public override string Name => "Time Stone: Second Chance";
    public override string Label => "REVOTE";

    public override bool CanUse(PlayerControl caster)
    {
        var area = AreaOf(caster);
        return base.CanUse(caster) && area != null && area.DidVote;
    }

    protected override void OnCast(CastContext ctx)
    {
        var meeting = MeetingHud.Instance;
        if (meeting == null) return;
        if (AmongUsClient.Instance != null && AmongUsClient.Instance.AmHost)
            meeting.RpcClearVote(ctx.Caster.PlayerId);
        if (ctx.LocalIs(ctx.Caster))
        {
            Fx.ScreenTint(new Color(0.2f, 0.9f, 0.4f, 0.25f), 1f);
            Report("TIME STONE: your vote was undone. Vote again.", Stones.Color(Stone.Time));
        }
    }
}

/// <summary>Soul Stone: a true clue — has this player killed anyone this game?</summary>
public class SoulVerdict : MeetingAbility
{
    public override string Id => "soul_verdict";
    public override string Name => "Soul Stone: Soul Verdict";
    public override string Label => "VERDICT";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (!ctx.LocalIs(ctx.Caster) || ctx.Target == null) return;
        int kills = KillLog.KillsOf(ctx.Target.PlayerId);
        string who = ctx.Target.Data?.PlayerName ?? "?";
        string text = kills == 0 ? $"{who}'s soul is clean: no kills recorded this game."
            : $"{who}'s soul is stained: {kills} kill{(kills == 1 ? "" : "s")} recorded this game.";
        Report("SOUL STONE: " + text, Stones.Color(Stone.Soul));
        Fx.Toast(text, Stones.Color(Stone.Soul), 3.5f, 3.2f, -0.2f);
    }
}

/// <summary>Ultimate: the Snap during voting. A cinematic for everyone; votes are not changed.</summary>
public class SnapVote : MeetingAbility
{
    public override string Id => "snap_vote";
    public override string Name => "The Snap Vote";
    public override string Label => "SNAP VOTE";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private static IEnumerator Run(CastContext ctx)
    {
        var g = Fx.ScreenSprite(Sprites.Icon("gauntlet", 256f) ?? Sprites.Glow, Color.white, -2f, "SnapVoteGauntlet");
        if (g != null) g.transform.localScale = Vector3.one * 2.5f;
        Fx.ScreenTint(new Color(0f, 0f, 0f, 0.5f), 2.6f);
        foreach (var s in Stones.Six)
        {
            Sfx.Play(SfxKind.Chime, 0.8f + (int)s * 0.12f);
            if (g != null) Fx.Punch(g.transform, 0.06f, 0.15f);
            for (float t = 0; t < 0.25f; t += Time.deltaTime) yield return null;
        }
        Sfx.Play(SfxKind.Snap, 1f);
        if (g != null) Object.Destroy(g.gameObject);
        Fx.ScreenTint(new Color(1f, 1f, 1f, 0.9f), 0.8f);
        Fx.Toast("THANOS SNAPPED. (no votes were changed)", Stones.Color(Stone.Infinity), 3f, 3.6f, 1.2f);

        // practice: show what a balanced outcome would have been against the dummies
        if (CharacterRegistry.IsFreeplay && ctx.LocalIs(ctx.Caster))
        {
            var others = new List<PlayerControl>();
            foreach (var pc in Players.All()) if (pc != ctx.Caster && Players.IsAlive(pc)) others.Add(pc);
            if (others.Count > 0)
                Report($"PRACTICE: the snap would have ejected {others[ctx.Rng.Next(others.Count)].Data?.PlayerName}.", Color.white);
        }
    }
}

// ══ LOKI ═════════════════════════════════════════════════════════════════════

/// <summary>Loki privately sees an illusion of testimony (clearly marked as an illusion).</summary>
public class IllusionaryTestimony : MeetingAbility
{
    private static readonly string[] Rooms = { "Electrical", "MedBay", "Navigation", "Reactor", "Storage", "Admin", "O2", "Security" };
    private static readonly string[] Acts = { "venting", "standing over a body", "running away", "faking a task", "following someone" };

    public override string Id => "illusionary_testimony";
    public override string Name => "Illusionary Testimony";
    public override string Label => "TESTIMONY";

    protected override void OnCast(CastContext ctx)
    {
        if (!ctx.LocalIs(ctx.Caster)) return;
        var looks = new List<PlayerControl>();
        foreach (var pc in Players.All()) if (pc != ctx.Caster && Players.IsAlive(pc)) looks.Add(pc);
        string who = looks.Count > 0 ? looks[ctx.Rng.Next(looks.Count)].Data?.PlayerName ?? "someone" : "someone";
        string line = $"ILLUSION: {who} was seen {Acts[ctx.Rng.Next(Acts.Length)]} in {Rooms[ctx.Rng.Next(Rooms.Length)]}.";
        Report(line + " (fabricated - only you see this)", Fx.LokiLight);
        Fx.Toast(line, Fx.LokiLight, 3.5f, 3f, -0.2f);
    }
}

/// <summary>On Loki's screen, his own ballot portrait wears the chosen player's look for a while.</summary>
public class ShapeshiftersAlibi : MeetingAbility
{
    public override string Id => "shapeshifters_alibi";
    public override string Name => "Shapeshifter's Alibi";
    public override string Label => "ALIBI";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (!ctx.LocalIs(ctx.Caster) || ctx.Target?.Data == null || ctx.Caster.Data == null) return;
        var mine = AreaOf(ctx.Caster);
        if (mine == null || mine.PlayerIcon == null) return;
        mine.PlayerIcon.UpdateFromPlayerOutfit(ctx.Target.Data.DefaultOutfit, PlayerMaterial.MaskType.ComplexUI, false, false, null, false);
        Fx.ScreenTint(new Color(0.2f, 0.9f, 0.35f, 0.2f), 0.8f);
        Report($"ALIBI: your portrait looks like {ctx.Target.Data.PlayerName} (only on your screen).", Fx.LokiLight);
        Coroutines.Start(Restore(mine, ctx.Caster, ctx.Cfg.Duration));
    }

    private static IEnumerator Restore(PlayerVoteArea area, PlayerControl me, float after)
    {
        for (float t = 0; t < after; t += Time.deltaTime) yield return null;
        if (area != null && area.PlayerIcon != null && me != null && me.Data != null)
            area.PlayerIcon.UpdateFromPlayerOutfit(me.Data.DefaultOutfit, PlayerMaterial.MaskType.ComplexUI, false, false, null, false);
    }
}

/// <summary>A private magical message that only the chosen player sees.</summary>
public class MeetingWhisper : MeetingAbility
{
    private static readonly string[] Lines =
    {
        "Vote with me and live.",
        "I know what you did.",
        "They're lying to you.",
        "Skip. Trust me.",
        "Look at who's quiet.",
    };

    public override string Id => "meeting_whisper";
    public override string Name => "Mind Whisper (Meeting)";
    public override string Label => "WHISPER";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        string line = Lines[ctx.Rng.Next(Lines.Length)];
        if (ctx.LocalIs(ctx.Target))
        {
            Fx.Toast($"<i>A voice in your head: \"{line}\"</i>", Fx.LokiLight, 4f, 3.4f, -0.2f);
            Sfx.Play(SfxKind.Whoosh, 1.5f, null, 0.4f);
        }
        if (ctx.LocalIs(ctx.Caster)) Report($"WHISPER to {ctx.Target.Data?.PlayerName}: \"{line}\"", Fx.LokiLight);
    }
}

/// <summary>Loki sees fake "VOTED" marks on every ballot (visual only, real votes untouched).</summary>
public class VoteIllusion : MeetingAbility
{
    public override string Id => "vote_illusion";
    public override string Name => "Vote Illusion";
    public override string Label => "FAKE VOTES";

    protected override void OnCast(CastContext ctx)
    {
        if (!ctx.LocalIs(ctx.Caster)) return;
        var meeting = MeetingHud.Instance;
        if (meeting == null) return;
        foreach (var a in meeting.playerStates)
        {
            if (a == null || a.NameText == null || ctx.Rng.Next(2) == 0) continue;
            var mark = UnityHelpers.CloneStripped(a.NameText, a.NameText.transform.parent);
            if (mark == null) continue;
            mark.text = "<color=#66ff88>VOTED?</color>";
            mark.transform.localPosition = a.NameText.transform.localPosition + new Vector3(0f, -0.25f, -0.1f);
            Object.Destroy(mark.gameObject, ctx.Cfg.Duration);
        }
        Report("VOTE ILLUSION: fake marks on your screen only. Real votes are unchanged.", Fx.LokiLight);
    }
}

/// <summary>Ultimate: a council of Lokis fills everyone's screen, then collapses (meeting keeps running).</summary>
public class CouncilOfVariants : MeetingAbility
{
    public override string Id => "council_of_variants";
    public override string Name => "Council of Variants";
    public override string Label => "COUNCIL";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx.Cfg.Duration));

    private static IEnumerator Run(float duration)
    {
        Fx.ScreenTint(new Color(0.1f, 0.85f, 0.3f, 0.35f), duration + 1f);
        Fx.Toast("THE COUNCIL OF VARIANTS", Fx.LokiGold, duration, 5f, 2f);
        Sfx.Play(SfxKind.Choir, 1f);
        var heads = new List<SpriteRenderer>();
        for (int i = 0; i < 12; i++)
        {
            var h = Fx.ScreenSprite(Sprites.Icon(i % 2 == 0 ? "possession" : "mirror_image", 256f) ?? Sprites.Glow, Color.white, -1.5f, "Variant");
            if (h != null) heads.Add(h);
        }
        for (float t = 0; t < duration; t += Time.deltaTime)
        {
            if (MeetingHud.Instance == null) break;
            for (int i = 0; i < heads.Count; i++)
            {
                var h = heads[i];
                if (h == null) continue;
                float a = i * Mathf.PI * 2f / heads.Count + t * 0.4f;
                h.transform.localPosition = new Vector3(Mathf.Cos(a) * 3.2f, Mathf.Sin(a) * 1.8f, h.transform.localPosition.z);
                h.transform.localScale = Vector3.one * (0.8f + 0.1f * Mathf.Sin(t * 4f + i));
            }
            yield return null;
        }
        // illusion collapse: everything shrinks into the centre and vanishes
        for (float t = 0; t < 0.6f; t += Time.deltaTime)
        {
            foreach (var h in heads)
                if (h != null) h.transform.localPosition *= 0.85f;
            yield return null;
        }
        foreach (var h in heads) if (h != null) Object.Destroy(h.gameObject);   // (its parent is the camera!)
        Sfx.Play(SfxKind.Snap, 1f);
        Fx.ScreenTint(new Color(0.6f, 1f, 0.7f, 0.7f), 0.5f);
    }
}
