using System.Collections.Generic;

namespace MarvelMod.UI.Index;

/// <summary>Plain-language explanation of every ability, shown in the F1 ability index.</summary>
public static class AbilityDescriptions
{
    private static readonly Dictionary<string, string> Text = new()
    {
        // ── Illusions ──
        ["mirror_image"]   = "Spawns a copy of you that follows you around. Confuses anyone chasing you.",
        ["phantom_squad"]  = "Spawns several copies of you that wander around near you.",
        ["false_emergency"]= "Players near you see a fake red REACTOR MELTDOWN alarm on their screen. No real sabotage happens.",
        ["illusion_trap"]  = "Needs a target. Fires a bolt that briefly stuns them; only they see Lokis surrounding them and their screen pulses green.",
        ["hall_of_loki"]   = "ULTIMATE. Short wind-up, then copies of you appear all over the map (at vents).",
        // ── Shapeshift ──
        ["disguise"]       = "Opens the player menu. You take the chosen player's look but keep your own name.",
        ["perfect_impersonation"] = "Opens the player menu. You take the chosen player's look AND name.",
        ["creature_form"]  = "Shrink into a tiny creature for a while. Harder to spot.",
        ["identity_swap"]  = "Opens the player menu. You and the chosen player swap looks and names.",
        ["the_other_you"]  = "ULTIMATE. Opens the player menu. You become that player while a fake copy of them walks around somewhere else.",
        // ── Trickster ──
        ["mystic_blink"]   = "Short teleport in the direction you're walking. Stops at walls.",
        ["swap_trick"]     = "Needs a target nearby. You and the target swap places.",
        ["decoy_escape"]   = "Leaves a walking copy of you behind and teleports you to a random vent on the map.",
        ["shadow_step"]    = "Turn invisible to everyone else for a few seconds (you see yourself faded).",
        ["master_of_misdirection"] = "ULTIMATE. Copies of you appear at several spots and you teleport to the last one.",
        // ── Sorcery ──
        ["green_bolt"]     = "Fires a bolt (aims at the closest player, otherwise where you're walking). Hit = stunned + knocked back.",
        ["binding_ribbons"]= "Needs a target. Golden ribbons root them in place for a few seconds.",
        ["mystic_shield"]  = "Bubble that blocks the next stun, bind, curse or finisher used on you.",
        ["enchanted_dagger"]= "Needs a target very close. Long stun + knockback.",
        ["asgardian_wrath"]= "ULTIMATE. Daggers fly out in every direction; everyone nearby is stunned and knocked back.",
        // ── Mischief ──
        ["fake_body"]      = "Drops a dead body that looks like you. It can't be reported.",
        ["mind_whisper"]   = "Opens the player menu. A creepy message appears on that player's screen only.",
        ["enchanted_witness"] = "Opens the player menu. That player alone sees a ghost of someone walking near them.",
        ["chaos_contract"] = "Needs a target. Their movement controls are inverted for a while.",
        ["the_grand_deception"] = "ULTIMATE. The screen turns green, fake crewmates appear around the map, and players near you see a fake kill.",
        // ── Finishers ──
        ["infinite_deception"] = "KILL. Six Lokis circle the victim; only one is real. The victim dissolves into green fragments.",
        ["kings_last_dance"]   = "KILL. A dagger duel: you dodge, blink around them, afterimages finish it. They vanish in gold sparks.",
        ["betrayal_paradox"]   = "KILL. You become their reflection, the reflection shatters, and they're pulled into a mirror.",
        ["puppet_master"]      = "KILL. Glowing threads lift them like a marionette; they turn into a wooden puppet you pocket.",
        ["kneel_before_trickster"] = "ULTIMATE KILL. A throne room full of kneeling Lokis folds into a tiny cube you flick away.",
        // ── Mind ──
        ["possession"]     = "Opens the player menu. You jump into that crewmate: you see through their eyes and move their body (your real body stays behind as a statue). Nobody sees any effect. While possessing, the buttons become STOP / FRAME KILL / KILL HOST. Can't possess impostors or your employees.",
        ["loki_contract"]  = "Opens the player menu. That crewmate becomes an IMPOSTOR employee who can do whatever they like. They don't know you can end them.",
        ["ultimate_betrayal"] = "KILL. Only works while you have employees alive (the number is shown on the button). Pick one from the menu and they die wherever they are on the map.",
        ["possess_stop"]   = "(While possessing) Leave the body and return to your own.",
        ["possess_frame"]  = "(While possessing) The body you control kills the nearest crewmate. It's THEIR kill, not yours.",
        ["possess_kill"]   = "(While possessing) Kill the body you're possessing, then return to your own.",
        // ── Hidden ──
        ["map_teleport"]   = "Open the map and left-click anywhere to teleport there. No cooldown.",
    };

    public static string For(string id) => Text.TryGetValue(id, out var t) ? t : "No description yet.";

    public static readonly string[] Controls =
    {
        "Left-click a button or press 1-5: use ability",
        "Bottom-right button or Q / E: switch ability page",
        "Map open + left-click: teleport there",
        "Mouse wheel: scroll down to zoom out, up to zoom back in. While zoomed out: right-click to teleport there",
        "F1: this index    H: hide buttons    F4: cooldowns on/off",
        "F3 (freeplay/lobby): switch character    F6: dev console",
        "Green outline on a player = who a targeted ability will hit",
    };
}
