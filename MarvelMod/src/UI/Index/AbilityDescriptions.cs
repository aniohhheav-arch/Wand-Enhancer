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
        // ── THANOS: Power (purple) ──
        ["power_blast"]    = "Purple energy orb (aims at the closest player, otherwise where you walk). Hit = stunned + knocked back.",
        ["cosmic_beam"]    = "Charge for a moment, then a piercing beam that stuns everyone along it.",
        ["shockwave"]      = "Slam the ground: everyone around you is stunned and thrown back.",
        ["power_overload"] = "For a few seconds you move faster and your stuns last 50% longer.",
        ["planetbreaker"]  = "ULTIMATE. Float up, then a giant purple explosion: players very close die, the rest are stunned and thrown.",
        // ── Space (blue) ──
        ["space_blink"]    = "Short teleport in the direction you're walking (stops at walls).",
        ["space_rift"]     = "Opens two linked portals: here and at a random vent. Anyone who steps into one comes out of the other.",
        ["gravity_well"]   = "A black hole in front of you pulls everyone nearby toward it.",
        ["spatial_prison"] = "Needs a target. Traps them inside a glowing cube; they can't move.",
        ["cosmic_exile"]   = "ULTIMATE. Needs a target. Sends them to a starry dimension far from the map for a few seconds, then back.",
        // ── Reality (red) ──
        ["reality_warp"]   = "Everyone nearby is slowed and their screen warps red.",
        ["transmutation"]  = "Needs a target. They turn into a crate and can't move for a few seconds.",
        ["reality_shatter"]= "Red cracks appear in front of you, then explode, stunning anyone standing there.",
        ["false_world"]    = "Players near you see fake bodies and fake crewmates walking around (only they see it).",
        ["reality_collapse"]= "ULTIMATE. A crimson wave spreads across the whole map, stunning everyone it reaches.",
        // ── Soul (orange) ──
        ["soul_sight"]     = "For a few seconds you see where every player is, through walls.",
        ["soul_drain"]     = "Needs a target. Slows them and halves the cooldowns of your other non-ultimate abilities.",
        ["spirit_projection"] = "Leave your body as a spirit and scout with WASD / arrow keys. Your body stays rooted.",
        ["soul_chains"]    = "Needs a target. Orange chains root them in place.",
        ["soul_world"]     = "ULTIMATE. You and everyone near you are pulled into the orange Soul World for a few seconds.",
        // ── Time (green) ──
        ["time_freeze"]    = "Everyone near you is frozen in time for a few seconds.",
        ["rewind"]         = "Teleport back to where you were a few seconds ago.",
        ["temporal_echo"]  = "A green echo of you replays the path you just walked (a moving decoy).",
        ["age_manipulation"] = "Everyone near you is slowed as time drags; their screen turns sepia.",
        ["eternal_loop"]   = "ULTIMATE. Needs a target. They keep getting snapped back to the same spot for several seconds.",
        // ── Mind (yellow) ──
        ["thanos_mind_control"] = "Needs a target. Their body walks toward you on its own for a few seconds.",
        ["psychic_pulse"]  = "A yellow wave: everyone nearby is briefly stunned and their controls are inverted.",
        ["memory_erasure"] = "Needs a target. For a few seconds they can't see any other players.",
        ["illusion_army"]  = "Several copies of you appear and wander around.",
        ["absolute_domination"] = "ULTIMATE. Gold screen for all, fake players everywhere and a giant spectral Thanos.",
        // ── Infinity ──
        ["infinity_surge"] = "Six rainbow shockwaves: everyone around you is stunned and thrown back.",
        ["the_snap"]       = "ULTIMATE. Gauntlet close-up, the stones light up, SNAP. Outcome set by SnapMode in the config: visual (default, nobody hurt), practice_kill (freeplay only: half the dummies turn to dust), match_kill_half, match_stun.",
        ["titans_judgment"]= "ULTIMATE. A colossal six-coloured beam in your aim direction kills everyone it hits.",
        // ── Meeting powers (panel on the left during meetings) ──
        ["read_their_minds"] = "MEETING (Thanos). Pick a player: see whether they voted, skipped, or who they voted for. Real vote data.",
        ["rewrite_the_vote"] = "MEETING (Thanos). For a few seconds the names on YOUR ballot scramble red. Visual only: real votes are untouched.",
        ["second_chance"]    = "MEETING (Thanos). After you voted: the host clears your vote (the game's own clear-vote) so you can vote again.",
        ["soul_verdict"]     = "MEETING (Thanos). Pick a player: learn how many real kills they made this game. True information.",
        ["snap_vote"]        = "MEETING ULTIMATE (Thanos). Gauntlet snap cinematic for everyone. Votes are not changed; in freeplay it shows a simulated result.",
        ["illusionary_testimony"] = "MEETING (Loki). Shows you a made-up 'sighting' you can use to bluff. Only you see it; it's marked as fabricated.",
        ["shapeshifters_alibi"]   = "MEETING (Loki). Pick a player: on your screen your ballot portrait wears their look for a while.",
        ["meeting_whisper"]       = "MEETING (Loki). Pick a player: a voice-in-your-head message appears on their screen only.",
        ["vote_illusion"]         = "MEETING (Loki). Fake 'VOTED?' marks appear on ballots on your screen. Real votes are unchanged.",
        ["council_of_variants"]   = "MEETING ULTIMATE (Loki). A council of Lokis circles everyone's screen, then collapses. The meeting keeps running.",
        // ── DOCTOR STRANGE (crewmate) ──
        ["sling_ring"]       = "Open a portal where you stand. Walk somewhere else and use PORTAL B to link it. Anyone who steps in comes out the other side.",
        ["portal_link"]      = "Open the second portal here and link it to your waiting one (only lights up while one is waiting).",
        ["portal_redirect"]  = "Needs a target in front of you: they are shoved through to your latest portal (or a random vent).",
        ["portal_snare"]     = "Leave a trap portal. The first other player who steps on it is flung to a random vent.",
        ["multiversal_gateway"] = "ULTIMATE. Everyone near you is dragged through a chain of portals across the map, then returned.",
        ["crimson_bands"]    = "Needs a target. Crimson bands root them in place.",
        ["eldritch_whip"]    = "Needs a target. A glowing whip drags them right next to you.",
        ["eldritch_shield"]  = "A rune shield that blocks the next stun, bind, curse or kill used on you.",
        ["mystic_blades"]    = "Blades orbit you, then fly at the closest player (or where you're walking). Hit = stunned + knocked back.",
        ["crimson_judgment"] = "ULTIMATE KILL. Bands lift the target inside a rune circle that closes in golden light.",
        ["mirror_shard"]     = "A mirror projectile that bursts into shards, stunning anyone near the impact.",
        ["fractured_reality"]= "Reality cracks around you: everyone nearby is slowed and their screen fractures.",
        ["mirror_clone"]     = "A copy of you wanders around as a decoy.",
        ["dimensional_prison"] = "Needs a target. They're trapped in the Mirror Dimension (far off the map) for a few seconds, then returned.",
        ["mirror_world_collapse"] = "ULTIMATE. You and everyone near you fall into a folding mirror world full of Stranges, then return.",
        ["temporal_rewind"]  = "Teleport back to where you were a few seconds ago.",
        ["time_bubble"]      = "A green bubble ahead of you: everyone inside moves in slow motion.",
        ["strange_echo"]     = "An echo of you replays the path you just walked.",
        ["time_loop"]        = "Needs a target. They keep snapping back to the same spot for a few seconds.",
        ["eye_of_agamotto"]  = "ULTIMATE. Needs a target. The Eye opens: they're frozen in a sphere of runes while their echoes wander.",
        ["astral_projection"]= "Leave your body as an astral form (WASD / arrows) within a leash range. Your body stays put.",
        ["astral_sight"]     = "See every player AND every portal through walls for a while.",
        ["astral_bind"]      = "Needs a target. Spectral chains root them.",
        ["dimension_step"]   = "A quick portal hop in the direction you're walking.",
        ["astral_exile"]     = "ULTIMATE. Needs a target. Their soul is pulled into a cosmic dimension for a few seconds, then returned.",
        ["portal_maze"]      = "KILL FINISHER. A portal opens beneath them; they fall through portal after portal; you close it into a ring.",
        ["mirror_dimension_finisher"] = "KILL FINISHER. The world folds into mirrors, Stranges on every surface, the victim shatters.",
        ["crimson_bands_finisher"] = "KILL FINISHER. A crimson circle, bands and runes; the seal closes into golden sparks.",
        ["temporal_fracture"]= "KILL FINISHER. Time stops; three echoes of the victim converge into light and collapse.",
        ["sorcerer_supreme"] = "ULTIMATE KILL FINISHER. A cosmic chamber, a hundred portals, every construct merges into a golden sigil.",
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
        "Thanos: you start a match with 2 stones; you get one more every 60 s and one per kill. Locked pages unlock as you collect them.",
        "Green outline on a player = who a targeted ability will hit",
    };
}
