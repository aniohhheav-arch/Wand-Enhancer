using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Combo;
using MarvelMod.Abilities.Thor;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>Thor: impostor character built on lightning, hammer combat, storms and mobility.</summary>
public class ThorCharacter : CharacterDef
{
    public static readonly ThorCharacter Instance = new();

    public override string Id => "thor";
    public override string DisplayName => "Thor";
    public override Color Color => ThorAbility.Bolt;

    private readonly List<AbilityTab> _tabs;
    public override IReadOnlyList<AbilityTab> Tabs => _tabs;

    private ThorCharacter()
    {
        _tabs = new List<AbilityTab>
        {
            new("THUNDER",   "mjolnir_throw",   new() { new MjolnirThrow(), new LightningStrike(), new Thunderclap(), new Stormflight(), new GodOfThunder() }),
            new("FINISHERS", "king_of_thunder", new() { new TheWorthy(), new BifrostArrival(), new StormbreakerFinisher(), new WrathOfAsgard(), new KingOfThunder() },
                _ => !CharacterRegistry.InLobby),
            new("TEAM-UP",   "end_of_reality",  new() { Combos.Chaosstorm, Combos.Thunderclap, Combos.EndOfReality }),
        };
    }
}
