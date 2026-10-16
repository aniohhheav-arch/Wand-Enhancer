# MarvelMod ability presentation sheet

Generated from the source by `tools/presentation_sheet.py`. Each row lists what the ability's code actually uses:
impact styles (`Fx.Release`), extra shapes, residue (`Fx.Aftermath`) and sounds. The activation sound comes from
`SoundBook` (a different main sound per slot on each page, plus the page's texture layer); the activation motif
comes from `Fx.Motif` (one shape per family).

## Families

| Family | Palette | Visual language | Sound identity |
|---|---|---|---|
| Thanos / Power | purple | heavy slams, flattened shock rings, cracks, flying debris | boom, rise, thud, crackle, gong over a rumble |
| Thanos / Space | blue | implosions, folding tears, rifts | whoosh, warp, rise, shimmer, gong over a hum |
| Thanos / Reality | red | glitch bars, glass, ghost rings | warp, glass, crackle, zap, gong over a rumble |
| Thanos / Soul | orange | threads, rising wisps, dark pulses | heartbeat, hum, whoosh, creak, choir over a shimmer |
| Thanos / Time | green | counter-rotating clock rings that freeze and rewind | tick, warp, chime, hum, bell over ticking |
| Thanos / Mind | yellow | concentric pulses, hypnotic spirals | shimmer, heartbeat, warp, chime, choir over a hum |
| Thanos / Infinity | all six stones | six-colour rings, stone energy into the gauntlet | rise, snap, gong over a choir |
| Loki / Illusions | green + gold | duplicates unfolding from gold seams, rolling reveals | shimmer, chime, zap, warp, choir |
| Loki / Shapeshift | green + gold | smoke curtain, sigil snap, mirror-flash reveal | warp, whoosh, creak, shimmer, gong |
| Loki / Trickster | green + gold | folds away, smoke bombs, decoys | whoosh, warp, shimmer, tick, rise |
| Loki / Sorcery | green + gold | spinning daggers, ribbons, sigil shield | zap, creak, hum, slash, gong |
| Loki / Mischief | green + purple | fake events only the victim sees | thud, heartbeat, shimmer, warp, choir |
| Loki / Finishers | green + gold | camera-directed cinematics, no gore | hum, slash, glass, creak, choir |
| Loki / Mind | none (silent) | deliberately no effects: possession must be undetectable | none |
| Loki / Possessing | none (silent) | deliberately no effects while possessing | none |
| Strange / Sling Ring | orange-gold | portals with depth, rim sparks, distortion ripples | whoosh, shimmer, warp, crackle, gong |
| Strange / Combat | crimson + gold | bands, whip lash, orbiting blades | crackle, slash, hum, shimmer, gong |
| Strange / Mirror | pale blue | kaleidoscope cracks, shattering panes | glass, warp, shimmer, gong |
| Strange / Time | green | clock runes, slow ticking bubbles | tick, warp, chime, bell |
| Strange / Astral | pale gold | souls leaving bodies, rising light | hum, shimmer, heartbeat, whoosh, choir |
| Scarlet Witch | scarlet | wavy tendrils (never straight beams), hex sigils, floating debris | sizzle, reversed swells, glass, heartbeat, stabs |
| Scarlet Witch / Finishers | scarlet | lifts, sigils, collapsing illusions, folding worlds | reverse, choir, creak, glass, stab |
| Thor | blue-white | branching bolts from the sky, hammer arcs, scorch marks | clang, crackle, thud, whoosh, thunder |
| Thor / Finishers | blue-white + rainbow | storm clouds, tightening strikes, lightning columns | clang, gong, crackle, rumble, thunder |
| Team-ups | scarlet + blue-white = purple | red threads and blue bolts colliding into purple bursts | thunder, glass, stab over reversed swells |

## Abilities

| Ability | Family | Impact styles | Shapes and environment | Residue | Sounds in code | Description |
|---|---|---|---|---|---|---|
| **Absolute Domination** `absolute_domination` | Thanos / Mind | Magic | ScreenTint | - | Choir | ULTIMATE. Gold screen for all, fake players everywhere and a giant spectral Thanos. |
| **Age Manipulation** `age_manipulation` | Thanos / Time | - | Rising, ScreenTint, body pose (CharacterVisuals) | - | Creak | Everyone near you is slowed as time drags; their screen turns sepia. |
| **Asgardian Wrath** `asgardian_wrath` | Loki / Sorcery | - | Sigil, Streaks, Projectile, preset LokiDaggerStorm | - | (activation only) | ULTIMATE. Daggers fly out in every direction; everyone nearby is stunned and knocked back. |
| **Astral Bind** `astral_bind` | Strange / Astral | - | Bind, Rising | - | Choir | Needs a target. Spectral chains root them. |
| **Astral Exile** `astral_exile` | Strange / Astral | Eerie | Rising | - | (activation only) | ULTIMATE. Needs a target. Their soul is pulled into a cosmic dimension for a few seconds, then returned. |
| **Astral Projection** `astral_projection` | Strange / Astral | - | Aura, Sparkles, Toast | - | Shimmer | TOGGLE. Press to leave your body as an astral form (WASD / arrows) within a leash range; press again (RETURN) to snap back. Your body stays put. Cooldown starts when you return. |
| **Astral Sight** `astral_sight` | Strange / Astral | Eerie | ScreenTint | - | (activation only) | See every player AND every portal through walls for a while. |
| **The Betrayal Paradox** `betrayal_paradox` | Loki / Finishers | Glitch | Cracks, Fragments, Aura, Smoke, WorldText | - | Chime, Creak, Glass, Hum, Whoosh | KILL. You become their reflection, the reflection shatters, and they're pulled into a mirror. |
| **Bifrost Arrival** `bifrost_arrival` | Thor / Finishers | Cosmic, Heavy | Streaks, Trail, Beam, WorldText, Flash | - | Gong, Rise, Stab, Thunder, Whoosh | KILL FINISHER. A rainbow Bifrost beam slams down, Thor arrives in it and launches through the victim. |
| **Binding Ribbons** `binding_ribbons` | Loki / Sorcery | - | Projectile, Aura, preset LokiRibbons | - | (activation only) | Needs a target. Golden ribbons root them in place for a few seconds. |
| **Chaos Bolt** `chaos_bolt` | Scarlet Witch | Chaos | Trail | Embers | Sizzle | A wobbling hex-orb that curves and homes in on the closest player (or flies where you walk). Hit = stunned + knocked back. |
| **Chaos Contract** `chaos_contract` | Loki / Mischief | - | Projectile, Aura, Sparkles, WorldText | - | Boing | Needs a target. Their movement controls are inverted for a while. |
| **Chaosstorm** `chaosstorm` | Team-ups | Cosmic | Lightning, Tendril, Sigil, Darken, Clouds, Flare | - | Crackle, Reverse, Thunder | TEAM-UP. Wanda's red field + Thor's lightning: anyone inside keeps getting slowed and zapped. Needs your partner nearby in matches; freeplay uses a stand-in. |
| **Cosmic Beam** `cosmic_beam` | Thanos / Power | - | StoneToGauntlet, Charge, Beam, Sparkles | Embers | Hum, Rise, Thud, Zap | TOGGLE. Press to fire a sustained beam that swings toward the closest player and stuns everyone along it; press again to stop (max 6 s). You stay rooted while it fires. Cooldown starts when it stops. |
| **Cosmic Exile** `cosmic_exile` | Thanos / Space | Cosmic | Debris, body pose (CharacterVisuals) | - | Warp | ULTIMATE. Needs a target. Sends them to a starry dimension far from the map for a few seconds, then back. |
| **Creature Form** `creature_form` | Loki / Shapeshift | Comedy | preset LokiShapeshift | - | Boing | Shrink into a tiny creature for a while. Harder to spot. |
| **Crimson Bands of Cyttorak** `crimson_bands` | Strange / Combat | - | Tendril, Projectile, Aura | - | Clang | Needs a target. Crimson bands root them in place. |
| **Crimson Bands** `crimson_bands_finisher` | Loki / Finishers | Magic | Aura, Sparkles | - | Bell, Chime, Clang, Creak, Hum | KILL FINISHER. A crimson circle, bands and runes; the seal closes into golden sparks. |
| **Crimson Barrier** `crimson_barrier` | Scarlet Witch | - | Sigil, Aura | - | Glass, Hum | A red hex dome around you that blocks the next hostile ability. |
| **Crimson Execution** `crimson_execution` | Scarlet Witch / Finishers | Chaos | Tendril, Bind, Sigil, Levitate, Fragments, Flash | Embers | Reverse, Rise, Sizzle, Stab | KILL FINISHER. Scarlet energy wraps and lifts the target, a giant sigil forms, a burst of red light. |
| **Crimson Judgment** `crimson_judgment` | Strange / Combat | Magic | Aura, Sparkles, Flash | - | Bell | ULTIMATE KILL. Bands lift the target inside a rune circle that closes in golden light. |
| **Decoy Escape** `decoy_escape` | Loki / Trickster | - | Comic, preset LokiSmoke, preset LokiTeleport | - | (activation only) | Leaves a walking copy of you behind and teleports you to a random vent on the map. |
| **Dimension Step** `dimension_step` | Strange / Astral | - | Trail | - | (activation only) | A quick portal hop in the direction you're walking. |
| **Dimensional Prison** `dimensional_prison` | Strange / Mirror | Glitch | Fragments | - | (activation only) | Needs a target. They're trapped in the Mirror Dimension (far off the map) for a few seconds, then returned. |
| **Disguise** `disguise` | Loki / Shapeshift | - | preset LokiShapeshift | - | (activation only) | Opens the player menu. You take the chosen player's look but keep your own name. |
| **Eldritch Shield** `eldritch_shield` | Strange / Combat | - | Sigil, Aura | - | Glass | A rune shield that blocks the next stun, bind, curse or kill used on you. |
| **Eldritch Whip** `eldritch_whip` | Strange / Combat | - | Lightning, Comic, Beam | - | Slash, Snap | Needs a target. A glowing whip drags them right next to you. |
| **Enchanted Dagger** `enchanted_dagger` | Loki / Sorcery | - | preset LokiDagger | - | (activation only) | Needs a target very close. Long stun + knockback. |
| **Enchanted Witness** `enchanted_witness` | Loki / Mischief | - | preset LokiMagic | - | (activation only) | Opens the player menu. That player alone sees a ghost of someone walking near them. |
| **End of Reality** `end_of_reality` | Team-ups | Chaos, Cosmic, Electric, Glitch | Lightning, Tendril, Levitate, Darken, Cracks, Clouds, Toast, Flash | Embers, Static | Crackle, Glass, Rise, Stab, Thunder, Warp | TEAM-UP ULTIMATE. Fractures spread, lightning and chaos converge into a rift, and the whole map gets stunned and slowed. No kills. |
| **Eternal Loop** `eternal_loop` | Thanos / Time | Time | Sigil, Toast | - | (activation only) | ULTIMATE. Needs a target. They keep getting snapped back to the same spot for several seconds. |
| **Eye of Agamotto** `eye_of_agamotto` | Strange / Time | Time | Sigil, Aura, ScreenTint | - | (activation only) | ULTIMATE. Needs a target. The Eye opens: they're frozen in a sphere of runes while their echoes wander. |
| **Fake Body** `fake_body` | Loki / Mischief | - | preset LokiSpawn | - | (activation only) | Drops a dead body that looks like you. It can't be reported. |
| **False Emergency** `false_emergency` | Loki / Illusions | - | Toast, ScreenTint, preset LokiMagic | - | Bell | Players near you see a fake red REACTOR MELTDOWN alarm on their screen. No real sabotage happens. |
| **False World** `false_world` | Thanos / Reality | Glitch | Wave, Toast, ScreenTint | - | (activation only) | Players near you see fake bodies and fake crewmates walking around (only they see it). |
| **Fractured Reality** `fractured_reality` | Strange / Mirror | Glitch | Cracks, ShockRing, ScreenTint | - | Reverse | Reality cracks around you: everyone nearby is slowed and their screen fractures. |
| **God of Thunder** `god_of_thunder` | Thor | Electric, Heavy | Lightning, StrikeFromSky, Darken, Cracks, Wave, Clouds, Flash, body pose (CharacterVisuals) | Static | Thunder | ULTIMATE. The sky goes dark, bolts rain around you, then a colossal strike and shockwave. Set Damage > 0 to make the core lethal. |
| **Gravity Well** `gravity_well` | Thanos / Space | Cosmic | Debris | - | Rumble | A black hole in front of you pulls everyone nearby toward it. |
| **Green Bolt** `green_bolt` | Loki / Sorcery | Magic | Projectile, preset LokiCast | - | Whoosh | Fires a bolt (aims at the closest player, otherwise where you're walking). Hit = stunned + knocked back. |
| **Hall of Loki** `hall_of_loki` | Loki / Illusions | - | Darken, Comic, preset LokiSpawn | - | (activation only) | ULTIMATE. Short wind-up, then copies of you appear all over the map (at vents). |
| **Hex of Deception** `hex_of_deception` | Scarlet Witch | Glitch | Darken, Toast | - | Reverse | Everyone near you sees a fake world on their own screen: red darkness, phantom crewmates that follow them and glitch away. Visual only. |
| **House of M** `house_of_m` | Scarlet Witch / Finishers | Cosmic, Glitch | Levitate, Wave, Fragments, WorldText, Toast, ScreenTint | - | Choir, Reverse | KILL FINISHER. The world warps into the House of M with a court of Wandas, then the illusion collapses onto the victim. |
| **Identity Swap** `identity_swap` | Loki / Shapeshift | - | Tendril, preset LokiShapeshift | - | (activation only) | Opens the player menu. You and the chosen player swap looks and names. |
| **Illusion Army** `illusion_army` | Thanos / Mind | - | Tendril, ShockRing | - | (activation only) | Several copies of you appear and wander around. |
| **Illusion Trap** `illusion_trap` | Loki / Illusions | - | Comic, Projectile, ScreenTint, preset LokiMagic | - | Stab | Needs a target. Fires a bolt that briefly stuns them; only they see Lokis surrounding them and their screen pulses green. |
| **The Infinite Deception** `infinite_deception` | Loki / Finishers | Glitch | Fragments, WorldText, ScreenTint | - | Bell, Chime, Choir, Hum, Reverse, Snap | KILL. Six Lokis circle the victim; only one is real. The victim dissolves into green fragments. |
| **Infinity Surge** `infinity_surge` | Thanos / Infinity | Cosmic | Wave | - | Chime | Six rainbow shockwaves: everyone around you is stunned and thrown back. |
| **King of Thunder** `king_of_thunder` | Thor / Finishers | Electric, Heavy | Lightning, Cracks, ShockRing, Clouds, Beam, Toast, ScreenTint, Flash | Static | Crackle, Rise, Rumble, Stab, Thunder | ULTIMATE KILL FINISHER. Thor rises inside rings of lightning, calls a gigantic column, then drops in a shockwave. |
| **The King's Last Dance** `kings_last_dance` | Loki / Finishers | Magic | Wave, Streaks, Sparkles, WorldText, ScreenTint | - | Bell, Chime, Clang, Slash | KILL. A dagger duel: you dodge, blink around them, afterimages finish it. They vanish in gold sparks. |
| **Kneel Before the Trickster** `kneel_before_trickster` | Loki / Finishers | Magic | Wave, Sparkles, WorldText, Toast, ScreenTint, Flash | - | Boom, Chime, Choir, Snap, Stab, Swell | ULTIMATE KILL. A throne room full of kneeling Lokis folds into a tiny cube you flick away. |
| **Lightning Strike** `lightning_strike` | Thor | - | Lightning, StrikeFromSky | Static | Thunder | A warning ring flickers on the closest player (or ahead), then a bolt hits everyone in the area. |
| **Loki's Contract** `loki_contract` | Loki / Mind | - | Beam, Sparkles, Toast, ScreenTint | - | Chime | Opens the player menu. That crewmate becomes an IMPOSTOR employee who can do whatever they like. They don't know you can end them. |
| **Master of Misdirection** `master_of_misdirection` | Loki / Trickster | Magic | Tendril, Comic, preset LokiSmoke, preset LokiTeleport | - | (activation only) | ULTIMATE. Copies of you appear at several spots and you teleport to the last one. |
| **Memory Erasure** `memory_erasure` | Thanos / Mind | Glitch | Sparkles, WorldText, ScreenTint | - | (activation only) | Needs a target. For a few seconds they can't see any other players. |
| **Mind Whisper** `mind_whisper` | Loki / Mischief | - | Toast, ScreenTint, preset LokiMagic | - | Reverse | Opens the player menu. A creepy message appears on that player's screen only. |
| **Mirror Clone** `mirror_clone` | Strange / Mirror | Glitch | Beam | - | (activation only) | A copy of you wanders around as a decoy. |
| **The Mirror Dimension** `mirror_dimension_finisher` | Loki / Finishers | Glitch | Cracks, Wave, Fragments, ScreenTint | - | Glass, Slash, Snap | KILL FINISHER. The world folds into mirrors, Stranges on every surface, the victim shatters. |
| **Mirror Image** `mirror_image` | Loki / Illusions | - | Trail, preset LokiSpawn | - | (activation only) | Spawns a copy of you that follows you around. Confuses anyone chasing you. |
| **Mirror Shard** `mirror_shard` | Strange / Mirror | Glitch | Fragments, Projectile | - | (activation only) | A mirror projectile that bursts into shards, stunning anyone near the impact. |
| **Mirror World Collapse** `mirror_world_collapse` | Strange / Mirror | Glitch | Sigil, Darken, Fragments | - | (activation only) | ULTIMATE. You and everyone near you fall into a folding mirror world full of Stranges, then return. |
| **Mjolnir Throw** `mjolnir_throw` | Thor | Electric, Heavy | Lightning, Trail, Scorch, Flare | - | Clang, Sizzle, Thud, Whoosh | Throw Mjolnir at the closest player (or ahead). Heavy hit + lightning, then it flies back to your hand. |
| **Multiversal Gateway** `multiversal_gateway` | Strange / Sling Ring | Cosmic | Sigil, Darken | - | Gong | ULTIMATE. Everyone near you is dragged through a chain of portals across the map, then returned. |
| **Mystic Blades** `mystic_blades` | Strange / Combat | - | Streaks, Projectile | - | Clang, Whoosh | Blades orbit you, then fly at the closest player (or where you're walking). Hit = stunned + knocked back. |
| **Mystic Blink** `mystic_blink` | Loki / Trickster | - | preset LokiTeleport | - | (activation only) | Short teleport in the direction you're walking. Stops at walls. |
| **Mystic Shield** `mystic_shield` | Loki / Sorcery | - | Aura, preset LokiShield | - | (activation only) | Bubble that blocks the next stun, bind, curse or finisher used on you. |
| **No More** `no_more` | Scarlet Witch / Finishers | Chaos, Cosmic | Sigil, Levitate, Cracks, WorldText, ScreenTint, Flash, body pose (CharacterVisuals) | Embers | Choir, Rise, Stab, Thunder, Tick, Warp | ULTIMATE KILL FINISHER. \"No more.\" Time freezes, red runes cover the screen, the world folds in and detonates. |
| **Perfect Impersonation** `perfect_impersonation` | Loki / Shapeshift | - | Beam, preset LokiShapeshift | - | (activation only) | Opens the player menu. You take the chosen player's look AND name. |
| **Phantom Squad** `phantom_squad` | Loki / Illusions | - | Tendril, ShockRing, preset LokiSpawn | - | Pop | Spawns several copies of you that wander around near you. |
| **Planetbreaker** `planetbreaker` | Thanos / Power | Heavy | Levitate, Cracks, Wave, ShockRing, body pose (CharacterVisuals) | Debris, Embers | (activation only) | ULTIMATE. Float up, then a giant purple explosion: players very close die, the rest are stunned and thrown. |
| **Portal Link** `portal_link` | Strange / Sling Ring | Magic | Tendril, Toast | - | (activation only) | Open the second portal here and link it to your waiting one (only lights up while one is waiting). |
| **The Portal Maze** `portal_maze` | Loki / Finishers | Cosmic | Aura, WorldText | - | Bell, Gong, Whoosh | KILL FINISHER. A portal opens beneath them; they fall through portal after portal; you close it into a ring. |
| **Portal Redirect** `portal_redirect` | Strange / Sling Ring | Cosmic | Comic | - | (activation only) | Needs a target in front of you: they are shoved through to your latest portal (or a random vent). |
| **Portal Snare** `portal_snare` | Strange / Sling Ring | - | Sigil | - | (activation only) | Leave a trap portal. The first other player who steps on it is flung to a random vent. |
| **Frame Kill** `possess_frame` | Loki / Possessing | - | - | - | (activation only) | (While possessing) The body you control kills the nearest crewmate. It's THEIR kill, not yours. |
| **Kill Host** `possess_kill` | Loki / Possessing | - | - | - | (activation only) | (While possessing) Kill the body you're possessing, then return to your own. |
| **Stop Possessing** `possess_stop` | Loki / Possessing | - | - | - | (activation only) | (While possessing) Leave the body and return to your own. |
| **Possession** `possession` | Loki / Mind | - | Toast | - | (activation only) | Opens the player menu. You jump into that crewmate: you see through their eyes and move their body (your real body stays behind as a statue). Nobody sees any effect. While possessing, the buttons become STOP / FRAME KILL / KILL HOST. Can't possess impostors or your employees. |
| **Power Blast** `power_blast` | Thanos / Power | Heavy | Projectile, Flare | Smoke | Whoosh | Purple energy orb (aims at the closest player, otherwise where you walk). Hit = stunned + knocked back. |
| **Power Overload** `power_overload` | Thanos / Power | Electric | Lightning, Rising, Toast, body pose (CharacterVisuals) | - | Stab | For a few seconds you move faster and your stuns last 50% longer. |
| **Psychic Pulse** `psychic_pulse` | Thanos / Mind | Magic | Wave, WorldText | - | (activation only) | A yellow wave: everyone nearby is briefly stunned and their controls are inverted. |
| **The God Who Pulls the Strings** `puppet_master` | Loki / Finishers | Comedy | Comic, Aura, Smoke, ScreenTint | - | Bell, Boing, Creak, Swell | KILL. Glowing threads lift them like a marionette; they turn into a wooden puppet you pocket. |
| **Puppet of Chaos** `puppet_of_chaos` | Scarlet Witch / Finishers | Chaos | Tendril, WorldText, Flash | Smoke | Boing, Creak, Pop | KILL FINISHER. Red threads hook the victim and Wanda makes them dance, then cuts the strings in a burst. |
| **Reality Collapse** `reality_collapse` | Thanos / Reality | Glitch | Darken, Cracks, Wave, ScreenTint | - | Reverse | ULTIMATE. A crimson wave spreads across the whole map, stunning everyone it reaches. |
| **Reality Fracture** `reality_fracture` | Scarlet Witch / Finishers | Cosmic, Glitch | Cracks, Fragments | - | Creak, Glass | KILL FINISHER. Reality cracks like glass, alternate versions of the victim flicker in the cracks, everything converges. |
| **Reality Rewrite** `reality_rewrite` | Scarlet Witch | Chaos, Cosmic, Glitch | Tendril, Levitate, Darken, Cracks, Toast, body pose (CharacterVisuals) | - | (activation only) | ULTIMATE. The area becomes a chaos dimension: debris floats, reality cracks, everyone inside is slowed and their controls are inverted. |
| **Reality Shatter** `reality_shatter` | Thanos / Reality | Glitch | Cracks, Fragments | - | Creak, Glass | Red cracks appear in front of you, then explode, stunning anyone standing there. |
| **Reality Thunderclap** `reality_thunderclap` | Team-ups | Chaos, Electric | Lightning, Sigil, Wave, ShockRing, Charge, Aura, Flash | - | Clang, Glass, Rise, Stab, Thunder | TEAM-UP. Thor strikes Wanda's barrier and it explodes outward as a huge red and blue wave that throws everyone back. |
| **Reality Warp** `reality_warp` | Thanos / Reality | Glitch | Wave, ScreenTint | - | (activation only) | Everyone nearby is slowed and their screen warps red. |
| **Rewind** `rewind` | Thanos / Time | Time | Flare | - | Reverse | Teleport back to where you were a few seconds ago. |
| **Shadow Step** `shadow_step` | Loki / Trickster | - | Rising, preset LokiSmoke | - | (activation only) | Turn invisible to everyone else for a few seconds (you see yourself faded). |
| **Shockwave** `shockwave` | Thanos / Power | Heavy | Wave, body pose (CharacterVisuals) | Debris | Whoosh | Slam the ground: everyone around you is stunned and thrown back. |
| **Sling Ring** `sling_ring` | Strange / Sling Ring | Magic | Toast | - | (activation only) | Open a portal where you stand. Walk somewhere else and use PORTAL B to link it. Anyone who steps in comes out the other side. |
| **The Sorcerer Supreme** `sorcerer_supreme` | Loki / Finishers | Magic | Fragments, Projectile, Aura, WorldText, ScreenTint, Flash | - | Bell, Choir, Swell, Whoosh | ULTIMATE KILL FINISHER. A cosmic chamber, a hundred portals, every construct merges into a golden sigil. |
| **Soul Chains** `soul_chains` | Thanos / Soul | - | Bind, Aura | - | Clang | Needs a target. Orange chains root them in place. |
| **Soul Drain** `soul_drain` | Thanos / Soul | - | Bind, Projectile, Toast | Embers | Heartbeat | Needs a target. Slows them and halves the cooldowns of your other non-ultimate abilities. |
| **Soul Sight** `soul_sight` | Thanos / Soul | Eerie | ScreenTint | - | Heartbeat | For a few seconds you see where every player is, through walls. |
| **Soul World** `soul_world` | Thanos / Soul | Eerie | Rising, Flash | - | (activation only) | ULTIMATE. You and everyone near you are pulled into the orange Soul World for a few seconds. |
| **Blink** `space_blink` | Thanos / Space | Cosmic | ShockRing, Beam, Sparkles | - | Pop | Short teleport in the direction you're walking (stops at walls). |
| **Space Rift** `space_rift` | Thanos / Space | Cosmic | - | - | (activation only) | Opens two linked portals: here and at a random vent. Anyone who steps into one comes out of the other. |
| **Spatial Prison** `spatial_prison` | Thanos / Space | Cosmic | Beam | - | Glass | Needs a target. Traps them inside a glowing cube; they can't move. |
| **Spirit Projection** `spirit_projection` | Thanos / Soul | - | Rising, Aura, Toast | - | Choir | Leave your body as a spirit and scout with WASD / arrow keys. Your body stays rooted. |
| **Stormbreaker** `stormbreaker` | Thor / Finishers | Electric | Lightning, Wave, Trail, Clouds, WorldText, ScreenTint, Flash | Static | Clang, Stab, Thunder, Whoosh | KILL FINISHER. Stormbreaker answers the call, a storm gathers over the victim, one swing of blue-white lightning. |
| **Stormflight** `stormflight` | Thor | Electric | Lightning, Trail, Scorch, body pose (CharacterVisuals) | - | Thud, Whoosh | Fly in the direction you're walking, leaving a lightning trail. Stops at walls. |
| **Temporal Echo** `strange_echo` | Strange / Time | Time | Trail | - | (activation only) | An echo of you replays the path you just walked. |
| **Swap Trick** `swap_trick` | Loki / Trickster | - | Tendril, WorldText, preset LokiTeleport | - | Boing | Needs a target nearby. You and the target swap places. |
| **Telekinetic Grip** `telekinetic_grip` | Scarlet Witch | Heavy | Bind, Streaks, Comic, WorldText, body pose (CharacterVisuals) | - | Reverse, Whoosh | Needs a target. Lift them helplessly into the air, then hurl them away (throw distance = Radius). |
| **Temporal Echo** `temporal_echo` | Thanos / Time | Time | ShockRing | - | Tick | A green echo of you replays the path you just walked (a moving decoy). |
| **Temporal Fracture** `temporal_fracture` | Loki / Finishers | Time | Wave, Aura, ScreenTint | - | Bell, Chime | KILL FINISHER. Time stops; three echoes of the victim converge into light and collapse. |
| **Temporal Rewind** `temporal_rewind` | Strange / Time | Time | Flare | - | (activation only) | Teleport back to where you were a few seconds ago. |
| **Mind Control** `thanos_mind_control` | Thanos / Mind | - | Tendril, Aura, WorldText | - | Shimmer | Needs a target. Their body walks toward you on its own for a few seconds. |
| **The Grand Deception** `the_grand_deception` | Loki / Mischief | Glitch | preset FakeKillFlash, preset LokiMapGreen | - | Choir | ULTIMATE. The screen turns green, fake crewmates appear around the map, and players near you see a fake kill. |
| **The Other You** `the_other_you` | Loki / Shapeshift | Glitch | preset LokiSpawn | - | (activation only) | ULTIMATE. Opens the player menu. You become that player while a fake copy of them walks around somewhere else. |
| **The Snap** `the_snap` | Thanos / Infinity | - | Fragments, Rising, Toast, ScreenTint | - | Chime, Choir, Reverse, Snap | ULTIMATE. Gauntlet close-up, the stones light up, SNAP. Outcome set by SnapMode in the config: visual (default, nobody hurt), practice_kill (freeplay only: half the dummies turn to dust), match_kill_half, match_stun. |
| **The Worthy** `the_worthy` | Thor / Finishers | Heavy | Lightning, Trail, Scorch, Flare, WorldText, Flash | Static | Clang, Crackle, Rise, Thud, Thunder, Whoosh | KILL FINISHER. Mjolnir flies into your hand, charges with lightning, and comes down in one huge strike. |
| **Thunderclap** `thunderclap` | Thor | Electric, Heavy | Lightning, Wave, ShockRing, body pose (CharacterVisuals) | Static | Sizzle, Thunder | Leap and slam: a thunder wave stuns everyone nearby and throws them back (push = Radius). |
| **Time Bubble** `time_bubble` | Strange / Time | Time | - | - | Tick | A green bubble ahead of you: everyone inside moves in slow motion. |
| **Time Freeze** `time_freeze` | Thanos / Time | Time | ScreenTint | Frost | (activation only) | Everyone near you is frozen in time for a few seconds. |
| **Time Loop** `time_loop` | Strange / Time | Time | Sigil, Beam | - | (activation only) | Needs a target. They keep snapping back to the same spot for a few seconds. |
| **Titan's Judgment** `titans_judgment` | Thanos / Infinity | Cosmic, Heavy | Lightning, StoneToGauntlet, Beam, body pose (CharacterVisuals) | - | (activation only) | ULTIMATE. A colossal six-coloured beam in your aim direction kills everyone it hits. |
| **Object Transmutation** `transmutation` | Thanos / Reality | Comedy | Comic, Smoke | - | (activation only) | Needs a target. They turn into a crate and can't move for a few seconds. |
| **The Ultimate Betrayal** `ultimate_betrayal` | Loki / Mind | - | Fragments, Toast | - | Snap | KILL. Only works while you have employees alive (the number is shown on the button). Pick one from the menu and they die wherever they are on the map. |
| **Wrath of Asgard** `wrath_of_asgard` | Thor / Finishers | Heavy | Lightning, StrikeFromSky, Clouds, Scorch, WorldText, ScreenTint, Flash | Smoke | Thunder | KILL FINISHER. Thunder rolls, strikes land in a tightening circle, the last one hits the victim. |

## Repetition audit

Abilities whose code uses exactly the same set of impact styles, shapes, residue and extra sounds. Before the
overhaul every ability shared one cast flash and each Thanos page shared one sound; now each one also has its own
activation sound (SoundBook) and family motif, so these pairs differ at least there. Silent possession actions
are identical on purpose (nobody may notice a possession).

- Frame Kill (Loki / Possessing), Kill Host (Loki / Possessing), Stop Possessing (Loki / Possessing)
