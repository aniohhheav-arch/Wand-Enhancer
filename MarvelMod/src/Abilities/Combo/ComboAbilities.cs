using System.Collections;
using MarvelMod.Abilities.Thor;
using MarvelMod.Abilities.Wanda;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.VFX;
using Reactor.Utilities;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Combo;

/// <summary>
/// Wanda + Thor team-ups. In a match they need the partner character alive within range (characters are
/// announced to everyone when rolled). In freeplay / the lobby they always work: when no real partner is
/// present a tinted stand-in of the partner appears next to you (solo preview).
/// </summary>
public abstract class ComboAbility : AbilityBase
{
    public override string Category => "Team-Up";
    public override Color CastColor => Color.Lerp(WandaAbility.Scarlet, ThorAbility.Bolt, 0.5f);

    /// <summary>Partner of the caster's character ("wanda" ↔ "thor").</summary>
    protected static string PartnerOf(PlayerControl caster) =>
        CharacterRegistry.Of(caster)?.Id == "thor" ? "wanda" : "thor";

    protected static bool Solo => CharacterRegistry.IsFreeplay || CharacterRegistry.InLobby;

    public static PlayerControl? FindPartner(PlayerControl caster, float range)
    {
        string want = PartnerOf(caster);
        PlayerControl? best = null; float bestD = range;
        foreach (var pc in Players.All())
        {
            if (pc == caster || !Players.IsAlive(pc) || CharacterRegistry.Of(pc)?.Id != want) continue;
            float d = Vector2.Distance(Players.Pos(pc), Players.Pos(caster));
            if (d <= bestD) { best = pc; bestD = d; }
        }
        return best;
    }

    public override bool CanUse(PlayerControl caster) => Solo || FindPartner(caster, Cfg.Range) != null;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
    {
        target = FindPartner(caster, Cfg.Range);   // null in solo preview
        point = target != null ? Players.Pos(target) : (Vector2)caster.transform.position + new Vector2(1.6f, 0f);
    }

    /// <summary>Partner position: the real partner, or a stand-in spawned for the solo preview.</summary>
    protected static Transform PartnerAnchor(CastContext ctx, float life)
    {
        if (ctx.Target != null) return ctx.Target.transform;
        var stand = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, ctx.Point, quiet: true);
        stand.SetBehavior(IllusionBehavior.Idle);
        stand.Untouchable = true;
        stand.Lifetime = life;
        bool callerIsThor = CharacterRegistry.Of(ctx.Caster)?.Id == "thor";
        stand.SetTint(callerIsThor ? new Color(1f, 0.55f, 0.6f, 0.85f) : new Color(0.6f, 0.8f, 1f, 0.85f));
        Fx.Release(ctx.Point, callerIsThor ? WandaAbility.Scarlet : ThorAbility.Bolt, ImpactStyle.Cosmic, 0.8f);
        Fx.WorldText(stand.transform, callerIsThor ? "WANDA (preview)" : "THOR (preview)", Color.white, 1.6f, 2f);
        return stand.transform;
    }

    /// <summary>Which end is Wanda's and which is Thor's.</summary>
    protected static (Transform wanda, Transform thor) Roles(CastContext ctx, Transform partner) =>
        CharacterRegistry.Of(ctx.Caster)?.Id == "thor" ? (partner, ctx.Caster.transform) : (ctx.Caster.transform, partner);
}

/// <summary>Wanda lays down a red reality field, Thor floods it with lightning: red and blue-white energy collide inside.</summary>
public class Chaosstorm : ComboAbility
{
    public override string Id => "chaosstorm";
    public override string Name => "Chaosstorm";
    public override string Label => "CHAOSSTORM";

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var partner = PartnerAnchor(ctx, ctx.Cfg.Duration + 1.5f);
        var (wanda, thor) = Roles(ctx, partner);
        Vector2 c = ((Vector2)wanda.position + (Vector2)thor.position) * 0.5f;
        float r = ctx.Cfg.Radius, dur = ctx.Cfg.Duration;
        Fx.Sigil(c, WandaAbility.Scarlet, r * 1.8f, dur);
        Fx.Tendril(wanda.position, c, WandaAbility.Scarlet, 0.6f, 12);
        Fx.Lightning(thor.position, c, ThorAbility.Bolt, 0.4f, 2, 0.3f);
        Fx.Darken(0.25f, dur + 0.5f, new Color(0.15f, 0.02f, 0.12f));
        ThorAbility.Clouds(c, r, dur + 0.5f, 7);
        Sfx.Play(SfxKind.Thunder, 0.8f, c, 0.7f);
        Sfx.Play(SfxKind.Reverse, 0.9f, c, 0.6f);
        float tick = 0f, hurt = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (MeetingHud.Instance != null) break;
            tick -= Time.deltaTime; hurt -= Time.deltaTime;
            if (tick <= 0f)
            {
                tick = 0.22f;
                // a red thread and a blue bolt meet at a random point and burst purple
                var p = c + Random.insideUnitCircle * r;
                Fx.Tendril(c + Random.insideUnitCircle * r, p, WandaAbility.Scarlet, 0.35f, 7);
                Fx.Lightning(p + new Vector2(Random.Range(-1f, 1f), 3f), p, ThorAbility.Bolt, 0.2f, 1, 0.3f);
                Fx.Flare(p, new Color(0.75f, 0.35f, 1f), 0.8f, 0.2f);
                if (Random.value < 0.3f) Sfx.Play(SfxKind.Crackle, 1.2f * Sfx.Vary(), p, 0.35f);
            }
            if (hurt <= 0f)
            {
                hurt = 0.8f;
                foreach (var pc in Players.InRadius(c, r, 15, ctx.Caster))
                {
                    if (ctx.Target != null && pc == ctx.Target) continue;
                    StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, 1f);
                    StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, 0.35f);
                }
            }
            yield return null;
        }
        Fx.Release(c, new Color(0.75f, 0.35f, 1f), ImpactStyle.Cosmic, 1.6f);
    }
}

/// <summary>Thor hammers Wanda's barrier; the barrier amplifies the hit into a huge circular wave.</summary>
public class RealityThunderclap : ComboAbility
{
    public override string Id => "reality_thunderclap";
    public override string Name => "Reality Thunderclap";
    public override string Label => "R.THUNDERCLAP";

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var partner = PartnerAnchor(ctx, 3f);
        var (wanda, thor) = Roles(ctx, partner);
        // the barrier goes up
        Fx.Sigil(wanda.position, WandaAbility.Scarlet, 2f, 0.9f);
        Fx.Aura(wanda, Sprites.Ring, new Color(1f, 0.3f, 0.4f, 0.8f), 1.9f, 1.2f, 80f);
        Sfx.Play(SfxKind.Glass, 0.9f, wanda.position, 0.6f);
        yield return Fx.Charge(thor, ThorAbility.Bolt, 0.6f, 10, SfxKind.Rise);
        if (wanda == null || thor == null) yield break;
        // the strike into the barrier
        Fx.Lightning(thor.position, wanda.position, Color.white, 0.4f, 3, 0.3f);
        Fx.Lightning(thor.position, wanda.position, ThorAbility.Bolt, 0.4f, 2, 0.4f);
        Sfx.Play(SfxKind.Clang, 0.7f, wanda.position, 0.8f);
        yield return new WaitForSeconds(0.1f);
        if (wanda == null) yield break;
        // amplified: the wave comes out of Wanda, red outside, blue-white inside
        Vector2 c = wanda.position;
        Fx.Flash(new Color(1f, 0.8f, 0.9f, 0.5f), 0.2f);
        Fx.Release(c, WandaAbility.Scarlet, ImpactStyle.Chaos, 2f);
        Fx.Release(c, ThorAbility.Bolt, ImpactStyle.Electric, 1.6f);
        Fx.ShockRing(c, ThorAbility.Bolt, ctx.Cfg.Radius * 2f, 0.6f, squash: 0.7f);
        Sfx.Play(SfxKind.Thunder, 0.7f, c, 1f);
        Sfx.Play(SfxKind.Stab, 1f, c, 0.7f);
        Fx.Wave(c, WandaAbility.Scarlet, ctx.Cfg.Radius, 0.5f, pc =>
        {
            if (pc == ctx.Caster || (ctx.Target != null && pc == ctx.Target)) return;
            Players.Knockback(pc, c, 2f);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, ctx.Cfg.Duration);
        });
    }
}

/// <summary>
/// Dual ultimate: Wanda suspends the arena in chaos, Thor calls the biggest storm yet, fractures spread,
/// every bolt converges into a central rift and the combined energy goes off across the whole map.
/// Everyone else is stunned and slowed (no kills).
/// </summary>
public class EndOfReality : ComboAbility
{
    public override string Id => "end_of_reality";
    public override string Name => "End of Reality";
    public override string Label => "END OF REALITY";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var partner = PartnerAnchor(ctx, ctx.Cfg.CinematicDur + 6f);
        CinematicPlayer.Play("END OF REALITY", new Color(0.75f, 0.35f, 1f), ctx.Caster, ctx.Cfg.CinematicDur, () => Coroutines.Start(Run(ctx, partner)));
        // during the wind-up both heroes power up visibly
        var (wanda, thor) = Roles(ctx, partner);
        Fx.Levitate(wanda.position, WandaAbility.Scarlet, 3f, ctx.Cfg.CinematicDur + 4f, 16);
        Fx.Darken(0.5f, ctx.Cfg.CinematicDur + 5f, new Color(0.08f, 0.02f, 0.12f));
        ThorAbility.Clouds(thor.position, 6f, ctx.Cfg.CinematicDur + 5f, 14);
    }

    private IEnumerator Run(CastContext ctx, Transform partner)
    {
        var (wanda, thor) = Roles(ctx, partner);
        if (wanda == null || thor == null) yield break;
        Vector2 c = ((Vector2)wanda.position + (Vector2)thor.position) * 0.5f + new Vector2(0f, 0.8f);
        // fractures spread across the arena
        for (int i = 0; i < 6; i++)
        {
            var p = c + Random.insideUnitCircle * 5f;
            Fx.Cracks(p, WandaAbility.Scarlet, 8, 2f, 4f, ctx.Rng.Next());
            Fx.Release(p, WandaAbility.Scarlet, ImpactStyle.Glitch, 0.7f);
            Sfx.Play(SfxKind.Glass, 1f + i * 0.1f, p, 0.4f);
            yield return new WaitForSeconds(0.15f);
        }
        // the rift opens in the middle
        var rift = Fx.MakeSprite(Sprites.Glow, c, new Color(0.02f, 0f, 0.05f, 0.95f), 0.2f, "EndRift");
        var rim = Fx.MakeSprite(Sprites.Ring, c, new Color(0.8f, 0.3f, 1f, 0.9f), 0.2f, "EndRiftRim");
        Fx.Animate(rift, 3f, k => rift.transform.localScale = new Vector3(1f, 1.4f, 1f) * Mathf.Lerp(0.2f, 2.2f, Mathf.Min(1f, k * 1.4f)));
        Fx.Animate(rim, 3f, k => { rim.transform.localScale = new Vector3(1f, 1.4f, 1f) * Mathf.Lerp(0.2f, 2.4f, Mathf.Min(1f, k * 1.4f)); rim.transform.Rotate(0, 0, 300f * Time.deltaTime); });
        Sfx.Play(SfxKind.Warp, 0.5f, c, 0.9f, cinematic: true);
        // lightning converges into it from every direction, chaos threads too
        for (int i = 0; i < 14; i++)
        {
            float a = i * Mathf.PI * 2f / 14f;
            var from = c + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 6f;
            if (i % 2 == 0) Fx.Lightning(from, c, ThorAbility.Bolt, 0.35f, 2, 0.5f);
            else Fx.Tendril(from, c, WandaAbility.Scarlet, 0.5f, 12);
            if (i % 3 == 0) Sfx.Play(SfxKind.Crackle, 0.8f + i * 0.05f, c, 0.4f, cinematic: true);
            yield return new WaitForSeconds(0.1f);
        }
        Sfx.Play(SfxKind.Rise, 0.5f, c, 0.9f, cinematic: true);
        yield return new WaitForSeconds(0.6f);
        if (MeetingHud.Instance != null) yield break;
        yield return new WaitForSeconds(0.2f);   // the silence

        // the finale, map-wide
        Fx.Flash(new Color(1f, 0.9f, 1f, 0.9f), 0.5f);
        Fx.Release(c, new Color(0.75f, 0.35f, 1f), ImpactStyle.Cosmic, 3f, cinematic: true);
        Fx.Release(c, WandaAbility.Scarlet, ImpactStyle.Chaos, 2.6f, cinematic: true);
        Fx.Release(c, ThorAbility.Bolt, ImpactStyle.Electric, 2.6f, cinematic: true);
        Sfx.Play(SfxKind.Thunder, 0.4f, c, 1f, cinematic: true);
        Sfx.Play(SfxKind.Stab, 0.5f, c, 1f, cinematic: true);
        Fx.Shake(1.2f, 1.2f);
        foreach (var pc in Players.All())
        {
            if (!Players.IsAlive(pc) || pc == ctx.Caster || (ctx.Target != null && pc == ctx.Target)) continue;
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, ctx.Cfg.Duration);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, ctx.Cfg.Duration * 2f);
            if (pc == PlayerControl.LocalPlayer) Fx.Toast("REALITY ENDED. (BRIEFLY.)", new Color(0.85f, 0.6f, 1f), 2.5f, 4f);
        }
        Fx.Aftermath(c, new Color(0.75f, 0.35f, 1f), Residue.Embers, 2.5f);
        Fx.Aftermath(c, ThorAbility.Bolt, Residue.Static, 2f);
        yield return new WaitForSeconds(1f);
        if (rift != null) Object.Destroy(rift.gameObject);
        if (rim != null) Object.Destroy(rim.gameObject);
    }
}

/// <summary>Hidden: tells every client which character a player rolled (so team-ups can find each other).</summary>
public class AnnounceCharacter : AbilityBase
{
    public override string Id => "announce_character";
    public override string Name => "Announce Character";
    public override string Category => "Hidden";
    public override bool Silent => true;

    protected override void OnCast(CastContext ctx)
    {
        int idx = (int)ctx.Point.x;
        var all = CharacterRegistry.All;
        if (idx >= 0 && idx < all.Count) CharacterRegistry.Note(ctx.Caster.PlayerId, all[idx].Id);
    }
}

/// <summary>The three team-up abilities, shared by Wanda's and Thor's TEAM-UP tab.</summary>
public static class Combos
{
    public static readonly Chaosstorm Chaosstorm = new();
    public static readonly RealityThunderclap Thunderclap = new();
    public static readonly EndOfReality EndOfReality = new();
    public static readonly AnnounceCharacter Announce = new();
}
