using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Loki;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>Registry of all 25 Loki abilities grouped by category.</summary>
public class LokiCharacter : CharacterDef
{
    public static readonly LokiCharacter Instance = new();

    public readonly List<AbilityBase> All;
    public readonly List<AbilityBase> IllusionMagic;
    public readonly List<AbilityBase> Shapeshifting;
    public readonly List<AbilityBase> TricksterMobility;
    public readonly List<AbilityBase> SorceryAndCombat;
    public readonly List<AbilityBase> GodOfMischief;
    public readonly List<AbilityBase> Executions;
    public readonly List<AbilityBase> Mind;
    /// <summary>Buttons that replace the bar while you're inside someone (see OverrideTab).</summary>
    public readonly List<AbilityBase> Possessing;
    private AbilityTab? _possessTab;
    public readonly MapTeleport MapTeleport = new();

    private LokiCharacter()
    {
        IllusionMagic = new List<AbilityBase>
        {
            new MirrorImage(), new PhantomSquad(), new FalseEmergency(),
            new IllusionTrap(), new HallOfLoki(),
        };
        Shapeshifting = new List<AbilityBase>
        {
            new Disguise(), new PerfectImpersonation(), new CreatureForm(),
            new IdentitySwap(), new TheOtherYou(),
        };
        TricksterMobility = new List<AbilityBase>
        {
            new MysticBlink(), new SwapTrick(), new DecoyEscape(),
            new ShadowStep(), new MasterOfMisdirection(),
        };
        SorceryAndCombat = new List<AbilityBase>
        {
            new GreenBolt(), new BindingRibbons(), new MysticShield(),
            new EnchantedDagger(), new AsgardianWrath(),
        };
        GodOfMischief = new List<AbilityBase>
        {
            new FakeBody(), new MindWhisper(), new EnchantedWitness(),
            new ChaosContract(), new TheGrandDeception(),
        };

        Executions = new List<AbilityBase>
        {
            new InfiniteDeception(), new KingsLastDance(), new BetrayalParadox(),
            new PuppetMaster(), new KneelBeforeTheTrickster(),
        };

        Mind = new List<AbilityBase> { new Possession(), new LokiContract(), new UltimateBetrayal() };
        Possessing = new List<AbilityBase> { new PossessStop(), new PossessFrameKill(), new PossessKillHost() };

        All = new List<AbilityBase>();
        All.AddRange(IllusionMagic);
        All.AddRange(Shapeshifting);
        All.AddRange(TricksterMobility);
        All.AddRange(SorceryAndCombat);
        All.AddRange(GodOfMischief);
        All.AddRange(Executions);
        All.AddRange(Mind);
        All.AddRange(Possessing);
        All.Add(MapTeleport);
    }

    public override string Id => "loki";
    public override string DisplayName => "Loki";
    public override Color Color => VFX.Fx.LokiGreen;

    private List<AbilityTab>? _tabs;
    public override IReadOnlyList<AbilityTab> Tabs => _tabs ??= new List<AbilityTab>
    {
        new("ILLUSIONS",  "phantom_squad",    IllusionMagic),
        new("SHAPESHIFT", "disguise",         Shapeshifting),
        new("TRICKSTER",  "mystic_blink",     TricksterMobility),
        new("SORCERY",    "green_bolt",       SorceryAndCombat),
        new("MISCHIEF",   "mind_whisper",     GodOfMischief),
        new("MIND",       "possession",       Mind),
        new("FINISHERS",  "infinite_deception",         Executions, _ => !CharacterRegistry.InLobby),
    };

    public override IEnumerable<AbilityBase> Hidden
    {
        get
        {
            yield return MapTeleport;
            foreach (var a in Possessing) yield return a;
        }
    }

    public override AbilityTab? OverrideTab(PlayerControl lp) =>
        Possessions.IsPossessing(lp) ? _possessTab ??= new AbilityTab("POSSESSING", "possession", Possessing) : null;

    public AbilityBase? GetById(string id)
    {
        foreach (var a in All)
            if (a.Id == id) return a;
        return null;
    }

    public void ResetAllCooldowns()
    {
        foreach (var a in All) a.ResetCooldown();
    }
}
