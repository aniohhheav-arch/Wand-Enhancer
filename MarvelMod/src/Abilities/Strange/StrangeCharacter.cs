using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Strange;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>Doctor Strange: a crewmate role (one per match, chosen by the host) built around portals.</summary>
public class StrangeCharacter : CharacterDef
{
    public static readonly StrangeCharacter Instance = new();

    public override string Id => "strange";
    public override string DisplayName => "Doctor Strange";
    public override Color Color => StrangeAbility.Gold;
    public override bool IsCrewmateRole => true;

    public readonly AssignStrange Assign = new();
    private readonly List<AbilityTab> _tabs;
    public override IReadOnlyList<AbilityTab> Tabs => _tabs;
    public override IEnumerable<AbilityBase> Hidden => new AbilityBase[] { Assign };

    private StrangeCharacter()
    {
        _tabs = new List<AbilityTab>
        {
            new("SLING RING", "sling_ring",        new() { new SlingRing(), new PortalLink(), new PortalRedirect(), new PortalSnare(), new MultiversalGateway() }),
            new("COMBAT",     "crimson_bands",     new() { new CrimsonBands(), new EldritchWhip(), new EldritchShield(), new MysticBlades(), new CrimsonJudgment() }),
            new("MIRROR",     "mirror_shard",      new() { new MirrorShard(), new FracturedReality(), new MirrorClone(), new DimensionalPrison(), new MirrorWorldCollapse() }),
            new("TIME STONE", "eye_of_agamotto",   new() { new TemporalRewind(), new TimeBubble(), new StrangeTemporalEcho(), new TimeLoop(), new EyeOfAgamotto() }),
            new("ASTRAL",     "astral_projection", new() { new AstralProjection(), new AstralSight(), new AstralBind(), new DimensionStep(), new AstralExile() }),
            new("FINISHERS",  "sorcerer_supreme",  new() { new PortalMaze(), new MirrorDimensionFinisher(), new CrimsonBandsFinisher(), new TemporalFracture(), new SorcererSupreme() },
                _ => !CharacterRegistry.InLobby),
        };
    }
}
