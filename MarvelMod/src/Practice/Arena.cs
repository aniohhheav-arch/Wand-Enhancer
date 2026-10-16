using MarvelMod.Characters;
using MarvelMod.VFX;

namespace MarvelMod.Practice;

/// <summary>Puts the arena back to normal after big effects: visuals, players, camera, sounds.</summary>
public static class Arena
{
    /// <summary>Returns a short summary of what was cleaned up.</summary>
    public static string Reset()
    {
        int fx = Fx.ClearAll();                                  // also stops sounds
        Abilities.AbilityBase.ClearToggles();
        Abilities.Loki.Possessions.EndAll();
        CharacterVisuals.RestoreAll();
        IllusionManager.Instance?.DespawnAll();
        ShapeshiftManager.Instance?.RestoreAll();
        StatusManager.Instance?.ClearAll();
        InvisibilityManager.Instance?.RestoreAll();
        PortalEngine.CloseAll();
        Dimensions.ReturnAll();
        CinematicDirector.RestoreLocalView();
        var lp = PlayerControl.LocalPlayer;
        if (lp != null) lp.moveable = true;
        return $"Arena reset: {fx} effect objects removed, players/camera/portals/statuses restored.";
    }
}
