using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Combo;
using MarvelMod.Abilities.Wanda;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>Scarlet Witch: impostor character built on chaos magic, telekinesis and reality manipulation.</summary>
public class WandaCharacter : CharacterDef
{
    public static readonly WandaCharacter Instance = new();

    public override string Id => "wanda";
    public override string DisplayName => "Scarlet Witch";
    public override Color Color => WandaAbility.Scarlet;

    private readonly List<AbilityTab> _tabs;
    public override IReadOnlyList<AbilityTab> Tabs => _tabs;
    public override IEnumerable<AbilityBase> Hidden => new AbilityBase[] { Combos.Announce };

    private WandaCharacter()
    {
        _tabs = new List<AbilityTab>
        {
            new("CHAOS MAGIC", "chaos_bolt",        new() { new ChaosBolt(), new TelekineticGrip(), new CrimsonBarrier(), new HexOfDeception(), new RealityRewrite() }),
            new("FINISHERS",   "no_more",           new() { new CrimsonExecution(), new HouseOfM(), new PuppetOfChaos(), new RealityFracture(), new NoMore() },
                _ => !CharacterRegistry.InLobby),
            new("TEAM-UP",     "end_of_reality",    new() { Combos.Chaosstorm, Combos.Thunderclap, Combos.EndOfReality }),
        };
    }
}
