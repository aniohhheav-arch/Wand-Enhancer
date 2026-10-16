using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Loki;

namespace MarvelMod.Characters;

/// <summary>Registry of all 25 Loki abilities grouped by category.</summary>
public class LokiCharacter
{
    public static readonly LokiCharacter Instance = new();

    public readonly List<AbilityBase> All;
    public readonly List<AbilityBase> IllusionMagic;
    public readonly List<AbilityBase> Shapeshifting;
    public readonly List<AbilityBase> TricksterMobility;
    public readonly List<AbilityBase> SorceryAndCombat;
    public readonly List<AbilityBase> GodOfMischief;

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

        All = new List<AbilityBase>();
        All.AddRange(IllusionMagic);
        All.AddRange(Shapeshifting);
        All.AddRange(TricksterMobility);
        All.AddRange(SorceryAndCombat);
        All.AddRange(GodOfMischief);
    }

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
