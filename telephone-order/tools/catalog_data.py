"""Single source of truth for the TELEPHONE ORDER catalog.

`gen_assets.py` turns this into:
  * src/main/resources/telephoneorder_catalog/*.json  (read by the Java mod at start-up)
  * all item / block / sound assets
"""

FAMS = ["pistol", "smg", "rifle", "sniper", "shotgun", "lmg", "energy", "heavy", "launcher"]

SLOTS_BY_FAM = {
    "pistol":   ["optic", "muzzle", "mag", "under", "finish"],
    "smg":      ["optic", "muzzle", "mag", "grip", "under", "stock", "finish"],
    "rifle":    ["optic", "muzzle", "mag", "grip", "under", "stock", "finish"],
    "sniper":   ["optic", "muzzle", "stock", "under", "finish"],
    "shotgun":  ["optic", "muzzle", "grip", "under", "stock", "finish"],
    "lmg":      ["optic", "muzzle", "grip", "mag", "under", "finish"],
    "energy":   ["optic", "muzzle", "module", "finish"],
    "heavy":    ["module", "finish"],
    "launcher": ["optic", "grip", "finish"],
}

# ----------------------------------------------------------------------------------------------
# Ammunition.  per = rounds supplied by ONE item.
# ----------------------------------------------------------------------------------------------
AMMO = [
    dict(id="ammo_9mm",   name="9mm Rounds",        per=30, price=6,  color=(214, 170, 70)),
    dict(id="ammo_357",   name=".357 Magnum Rounds", per=18, price=8,  color=(226, 140, 60)),
    dict(id="ammo_556",   name="5.56 Rounds",       per=40, price=9,  color=(120, 150, 90)),
    dict(id="ammo_762",   name="7.62 Rounds",       per=30, price=10, color=(150, 120, 80)),
    dict(id="ammo_12g",   name="12 Gauge Shells",   per=16, price=9,  color=(200, 60, 50)),
    dict(id="ammo_50bmg", name=".50 BMG Rounds",    per=10, price=16, color=(190, 190, 200)),
    dict(id="ammo_rocket", name="Rocket Pack",      per=2,  price=40, color=(220, 90, 40)),
    dict(id="ammo_cell",  name="Energy Cell",       per=60, price=14, color=(80, 230, 255)),
    dict(id="ammo_core",  name="Fusion Core",       per=6,  price=60, color=(255, 120, 230)),
]
# Special rounds: loaded from the OFF-HAND during a reload; they replace the standard load.
SPECIAL_AMMO = [
    dict(id="rounds_incendiary", name="Incendiary Rounds",       per=15, price=30, mod="incendiary", color=(255, 120, 30),
         desc="Sets targets ablaze. Slightly reduced damage."),
    dict(id="rounds_ap",         name="Armor-Piercing Rounds",   per=15, price=36, mod="ap",         color=(190, 200, 215),
         desc="Ignores 55% of armor. Lower stopping power."),
    dict(id="rounds_explosive",  name="Explosive Rounds",        per=10, price=48, mod="explosive",  color=(255, 70, 50),
         desc="Tiny detonation on impact. Does not break blocks."),
    dict(id="rounds_frost",      name="Frost Rounds",            per=15, price=34, mod="frost",      color=(150, 220, 255),
         desc="Chills targets: heavy slowness and freezing."),
]

# ----------------------------------------------------------------------------------------------
# Attachments.  mods are multipliers unless noted.
# ----------------------------------------------------------------------------------------------
ATT = [
    # optics
    dict(id="red_dot",      name="Red Dot Sight",     slot="optic", fits=["pistol","smg","rifle","shotgun","lmg","energy"], price=40, tier="standard",
         mods=dict(spread=0.9, zoom=0.9), desc="Clean sight picture. Slightly tighter spread, mild zoom."),
    dict(id="holo_sight",   name="Holographic Sight", slot="optic", fits=["pistol","smg","rifle","shotgun","lmg","energy"], price=90, tier="advanced",
         mods=dict(spread=0.82, zoom=0.8), desc="Wide, bright reticle. Better close-quarters aim."),
    dict(id="scope_4x",     name="4x Combat Scope",   slot="optic", fits=["rifle","sniper","lmg","energy","launcher"], price=120, tier="advanced",
         mods=dict(spread=0.78, zoom=0.4, range=1.2), desc="4x magnification and extended effective range."),
    dict(id="scope_8x",     name="8x Sniper Scope",   slot="optic", fits=["sniper","rifle"], price=220, tier="experimental",
         mods=dict(spread=0.65, zoom=0.17, range=1.45), desc="Extreme magnification for long range work."),
    # muzzle
    dict(id="suppressor",   name="Suppressor",        slot="muzzle", fits=["pistol","smg","rifle","sniper","shotgun"], price=110, tier="advanced",
         mods=dict(dmg=0.92, noise=0.12, range=0.94, flash=0), desc="Cuts the report and kills the muzzle flash."),
    dict(id="muzzle_brake", name="Muzzle Brake",      slot="muzzle", fits=["smg","rifle","sniper","lmg","shotgun"], price=70, tier="standard",
         mods=dict(recoil=0.68, spread=1.06), desc="Vents gas to soak up recoil."),
    dict(id="focus_lens",   name="Focusing Lens",     slot="muzzle", fits=["energy"], price=140, tier="experimental",
         mods=dict(range=1.3, spread=0.8, dmg=1.05, heat=1.12), desc="Collimates the emitter. Tighter, longer, a bit hotter."),
    # magazines
    dict(id="extended_mag", name="Extended Magazine", slot="mag", fits=["pistol","smg","rifle","lmg"], price=60, tier="standard",
         mods=dict(mag=1.5, reload=1.12), desc="+50% capacity, slightly slower reload."),
    dict(id="drum_mag",     name="Drum Magazine",     slot="mag", fits=["smg","rifle","lmg"], price=130, tier="advanced",
         mods=dict(mag=2.2, reload=1.4, spread=1.08), desc="More than double the capacity. Heavy and slow to seat."),
    dict(id="quickdraw_mag", name="Quickdraw Magazine", slot="mag", fits=["pistol","smg","rifle"], price=75, tier="standard",
         mods=dict(mag=0.9, reload=0.65), desc="Flared well and tuned springs for a fast reload."),
    # grips
    dict(id="foregrip",     name="Vertical Foregrip", slot="grip", fits=["smg","rifle","lmg","shotgun"], price=55, tier="standard",
         mods=dict(recoil=0.75, spread=0.92), desc="Steadies the weapon under sustained fire."),
    dict(id="stabilizer",   name="Gyro Stabilizer",   slot="grip", fits=["lmg","launcher"], price=150, tier="experimental",
         mods=dict(recoil=0.5, spread=0.8, reload=1.1), desc="Gyroscopic grip for heavy weapons."),
    # stocks
    dict(id="heavy_stock",  name="Heavy Stock",       slot="stock", fits=["smg","rifle","sniper","shotgun"], price=65, tier="standard",
         mods=dict(recoil=0.8, spread=0.85), desc="Solid shoulder weld. Less kick, tighter groups."),
    dict(id="skeleton_stock", name="Skeleton Stock",  slot="stock", fits=["smg","rifle","shotgun"], price=60, tier="standard",
         mods=dict(reload=0.88, spread=1.1), desc="Light and nimble, a bit twitchy."),
    # underbarrel
    dict(id="laser_sight",  name="Laser Sight",       slot="under", fits=["pistol","smg","rifle","shotgun","lmg","sniper"], price=80, tier="advanced",
         mods=dict(spread=0.88), laser=1, desc="Projects a visible dot. Tighter hip-fire."),
    dict(id="flashlight",   name="Tactical Flashlight", slot="under", fits=["pistol","smg","rifle","shotgun","lmg","sniper"], price=45, tier="standard",
         mods=dict(), light=1, desc="Illuminates what you hit: targets glow briefly."),
    # energy modules
    dict(id="amp_module",   name="Energy Amplifier",  slot="module", fits=["energy","heavy"], price=170, tier="experimental",
         mods=dict(dmg=1.2, heat=1.3), desc="+20% output at the cost of more heat."),
    dict(id="overclock",    name="Overclock Capacitor", slot="module", fits=["energy","heavy"], price=190, tier="experimental",
         mods=dict(delay=0.8, heat=1.4), desc="Faster cycling. Runs hot."),
    dict(id="heatsink",     name="Heatsink Fins",     slot="module", fits=["energy","heavy"], price=120, tier="advanced",
         mods=dict(heat=0.6, cool=1.6), desc="Dumps heat much faster."),
    dict(id="cell_extender", name="Cell Extender",    slot="module", fits=["energy","heavy"], price=140, tier="advanced",
         mods=dict(mag=1.6), desc="+60% energy capacity."),
    # finishes (cosmetic)
    dict(id="finish_gold",    name="Gilded Finish",   slot="finish", fits=FAMS, price=150, tier="standard", mods=dict(), finish="gold",
         desc="Cosmetic. 24-karat, entirely unnecessary."),
    dict(id="finish_arctic",  name="Arctic Finish",   slot="finish", fits=FAMS, price=60, tier="standard", mods=dict(), finish="arctic",
         desc="Cosmetic. Pale frost camouflage."),
    dict(id="finish_crimson", name="Crimson Finish",  slot="finish", fits=FAMS, price=60, tier="standard", mods=dict(), finish="crimson",
         desc="Cosmetic. Corporate red."),
]

# ----------------------------------------------------------------------------------------------
# Weapons
# ----------------------------------------------------------------------------------------------
GUNS = []

def G(**k):
    d = dict(pellets=1, spread=1.0, range=64, delay=4, reload=40, perRound=False, rp=1.5, ry=0.4, hs=1.5, ap=0.0,
             pierce=0, modes=["semi"], proj=0.0, p={}, tier="standard", rarity="common", delivery="crate", eta=30,
             secret=None, heat=0.0, cool=0.0, sound="pistol", rsound="mag", desc="", lore="", fam="rifle")
    d.update(k)
    d["slots"] = SLOTS_BY_FAM[d["fam"]] if "slots" not in k else k["slots"]
    GUNS.append(d)

# ============ A. CONVENTIONAL ============
G(id="vesper_p9", name="Vesper P9", cat="conventional", fam="pistol", beh="bullet", ammo="ammo_9mm",
  price=60, dmg=5.0, spread=1.3, range=60, delay=5, mag=15, reload=36, rp=2.4, ry=0.5, hs=1.7,
  sound="pistol", rsound="mag", eta=20, delivery="crate",
  desc="Reliable semi-automatic sidearm. Snappy, accurate, forgiving.",
  vis=dict(style="pistol", body=(52,54,62), accent=(130,138,150), len=0))
G(id="hawk_357", name="Hawk .357", cat="conventional", fam="pistol", beh="bullet", ammo="ammo_357",
  price=120, dmg=11.0, spread=0.7, range=70, delay=13, mag=6, reload=62, rp=5.5, ry=0.9, hs=1.9, ap=0.15,
  sound="revolver", rsound="revolver", eta=25, delivery="crate",
  desc="Six-shot revolver. Every round counts. Speedloader reload.",
  vis=dict(style="revolver", body=(110,116,128), accent=(120,78,45)))
G(id="rattler_smg", name="Rattler SMG", cat="conventional", fam="smg", beh="bullet", ammo="ammo_9mm",
  price=150, dmg=3.4, spread=2.6, range=45, delay=2, mag=30, reload=44, rp=0.9, ry=0.6, hs=1.35, modes=["auto","semi"],
  sound="smg", rsound="mag", eta=25,
  desc="High rate of fire submachine gun. Controllable but thirsty.",
  vis=dict(style="smg", body=(46,48,54), accent=(150,150,160)))
G(id="warden_ar4", name="Warden AR-4", cat="conventional", fam="rifle", beh="bullet", ammo="ammo_556",
  price=220, dmg=5.0, spread=1.6, range=90, delay=3, mag=30, reload=52, rp=1.5, ry=0.55, hs=1.5, modes=["auto","semi"],
  sound="rifle", rsound="mag", eta=30,
  desc="The standard issue assault rifle. Does everything well.",
  vis=dict(style="rifle", body=(60,64,60), accent=(140,150,130)))
G(id="anvil_br7", name="Anvil BR-7", cat="conventional", fam="rifle", beh="bullet", ammo="ammo_762",
  price=330, dmg=9.0, spread=0.9, range=110, delay=6, mag=20, reload=58, rp=3.6, ry=0.7, hs=1.7, ap=0.2,
  tier="advanced", sound="battle", rsound="mag", eta=35, delivery="drone",
  desc="Semi-automatic battle rifle. Hits like an anvil, kicks like one too.",
  vis=dict(style="battle", body=(70,52,36), accent=(60,60,66)))
G(id="mesa_dmr", name="Mesa DMR", cat="conventional", fam="rifle", beh="bullet", ammo="ammo_762",
  price=390, dmg=12.0, spread=0.35, range=140, delay=10, mag=10, reload=60, rp=3.0, ry=0.4, hs=2.0, ap=0.25,
  tier="advanced", sound="dmr", rsound="mag", eta=40, delivery="drone",
  desc="Designated marksman rifle. Precise out to extreme range.",
  vis=dict(style="dmr", body=(88,92,98), accent=(52,56,60)))
G(id="longshot_l96", name="Longshot L96", cat="conventional", fam="sniper", beh="bullet", ammo="ammo_762",
  price=520, dmg=24.0, spread=0.12, range=220, delay=30, mag=5, reload=70, rp=7.5, ry=0.5, hs=2.6, ap=0.35, pierce=1,
  tier="advanced", sound="sniper", rsound="bolt", eta=45, delivery="drone", p=dict(zoom=0.22, scoped=1),
  desc="Bolt-action sniper. Built-in 4.5x scope. One shot, one problem solved.",
  vis=dict(style="sniper", body=(50,58,48), accent=(30,30,34)))
G(id="pumpjack_12", name="Pumpjack 12", cat="conventional", fam="shotgun", beh="shotgun", ammo="ammo_12g",
  price=180, dmg=2.3, pellets=9, spread=6.0, range=26, delay=24, mag=6, reload=10, perRound=True, rp=6.0, ry=1.0, hs=1.2,
  sound="shotgun", rsound="pump", eta=25,
  desc="Pump-action shotgun. Loads shells one at a time; cycles with authority.",
  vis=dict(style="pump", body=(60,40,28), accent=(120,124,132)))
G(id="autotwelve", name="Auto-Twelve", cat="conventional", fam="shotgun", beh="shotgun", ammo="ammo_12g",
  price=340, dmg=1.9, pellets=8, spread=5.5, range=24, delay=9, mag=8, reload=64, rp=4.2, ry=0.9, hs=1.2,
  tier="advanced", sound="shotgun2", rsound="mag", eta=35, delivery="drone",
  desc="Semi-automatic combat shotgun with a detachable box magazine.",
  vis=dict(style="autoshot", body=(46,50,58), accent=(214,120,50)))
G(id="brickhouse_lmg", name="Brickhouse LMG", cat="conventional", fam="lmg", beh="bullet", ammo="ammo_556",
  price=480, dmg=4.6, spread=3.0, range=85, delay=3, mag=100, reload=118, rp=1.2, ry=0.7, hs=1.3, modes=["auto"],
  tier="advanced", sound="lmg", rsound="belt", eta=45, delivery="pod", p=dict(slow=1),
  desc="Light machine gun with a 100-round box. Plant it and hold the line.",
  vis=dict(style="lmg", body=(58,64,52), accent=(180,150,70)))
G(id="pocket_pdw", name="Pocket PDW", cat="conventional", fam="smg", beh="bullet", ammo="ammo_9mm",
  price=160, dmg=3.1, spread=2.3, range=40, delay=2, mag=40, reload=40, rp=0.7, ry=0.5, hs=1.4, modes=["auto","semi"],
  sound="pdw", rsound="mag", eta=22,
  desc="Compact personal-defense weapon. Bullpup layout, big magazine, small footprint.",
  vis=dict(style="pdw", body=(40,44,50), accent=(80,200,170)))

# ============ B. TACTICAL ============
G(id="whisper_9", name="Whisper-9", cat="tactical", fam="smg", beh="bullet", ammo="ammo_9mm",
  price=300, dmg=4.1, spread=1.8, range=55, delay=3, mag=32, reload=44, rp=0.8, ry=0.4, hs=1.5, modes=["auto","semi"],
  tier="advanced", sound="suppressed", rsound="mag", eta=35, delivery="drone", p=dict(silent=1),
  desc="Integrally suppressed SMG. Near-silent, no muzzle flash, mobs rarely notice.",
  vis=dict(style="suppsmg", body=(38,42,46), accent=(90,100,110)))
G(id="triad_burst", name="Triad Burst Rifle", cat="tactical", fam="rifle", beh="bullet", ammo="ammo_556",
  price=340, dmg=6.2, spread=0.9, range=95, delay=11, mag=30, reload=54, rp=1.8, ry=0.4, hs=1.7, modes=["burst"],
  tier="advanced", sound="burst", rsound="mag", eta=35, delivery="drone", p=dict(burst=3, burst_gap=2),
  desc="Three-round burst rifle. Controlled and brutally efficient at range.",
  vis=dict(style="burst", body=(64,66,72), accent=(220,200,60)))
G(id="goliath_50", name="Goliath .50", cat="tactical", fam="sniper", beh="bullet", ammo="ammo_50bmg",
  price=950, dmg=38.0, spread=0.2, range=240, delay=42, mag=5, reload=96, rp=11.0, ry=1.2, hs=2.2, ap=0.7, pierce=2,
  tier="experimental", rarity="rare", sound="anti_material", rsound="bolt", eta=60, delivery="pod", p=dict(zoom=0.2, scoped=1),
  desc="Heavy precision rifle. Punches through armor, mobs and thin cover.",
  vis=dict(style="antimat", body=(78,78,66), accent=(30,30,34)))
G(id="hornet_mp", name="Hornet MP", cat="tactical", fam="pistol", beh="bullet", ammo="ammo_9mm",
  price=140, dmg=2.6, spread=4.2, range=32, delay=1, mag=24, reload=34, rp=0.55, ry=0.9, hs=1.2, modes=["auto","semi"],
  sound="machinepistol", rsound="mag", eta=22,
  desc="Compact fully-automatic machine pistol. Cyclic rate is frankly alarming.",
  vis=dict(style="machinepistol", body=(60,50,50), accent=(220,70,60)))
G(id="hammerhead_saw", name="Hammerhead SAW", cat="tactical", fam="lmg", beh="bullet", ammo="ammo_762",
  price=720, dmg=6.0, spread=3.4, range=95, delay=2, mag=200, reload=170, rp=1.4, ry=0.9, hs=1.3, ap=0.2, modes=["auto"],
  tier="experimental", rarity="rare", sound="saw", rsound="belt", eta=60, delivery="pod", p=dict(slow=2),
  desc="Belt-fed support weapon. 200 rounds. Slows you down while firing.",
  vis=dict(style="saw", body=(62,60,56), accent=(200,150,50)))
G(id="chameleon_mod", name="Chameleon Carbine", cat="tactical", fam="rifle", beh="bullet", ammo="ammo_556",
  price=460, dmg=5.3, spread=1.5, range=90, delay=3, mag=30, reload=50, rp=1.4, ry=0.5, hs=1.55, modes=["auto","semi","burst"],
  tier="advanced", rarity="uncommon", sound="rifle2", rsound="mag", eta=40, delivery="drone", p=dict(burst=3, burst_gap=2),
  slots=["optic","muzzle","mag","grip","under","stock","module","finish"],
  desc="Fully modular carbine with every rail slot. Auto, semi and burst fire.",
  vis=dict(style="modular", body=(68,70,76), accent=(60,200,230)))

# ============ C. SCI-FI ============
G(id="sunder_pr", name="Sunder PR-1", cat="scifi", fam="energy", beh="plasma", ammo="ammo_cell",
  price=720, dmg=6.5, spread=1.0, range=70, delay=5, mag=36, reload=48, rp=1.0, ry=0.3, hs=1.2, modes=["auto","semi"],
  tier="experimental", rarity="uncommon", sound="plasma", rsound="cell", heat=5.5, cool=0.9, eta=40, delivery="case",
  p=dict(speed=1.7, splash=2.0, splash_dmg=2.5, gravity=0.0, glow=(80,255,200)),
  desc="Plasma rifle. Fast bright bolts that splash on impact.",
  vis=dict(style="plasma", body=(60,70,90), glow=(80,255,200)))
G(id="lance_l1", name="Lance L-1", cat="scifi", fam="energy", beh="laser", ammo="ammo_cell",
  price=640, dmg=3.8, spread=0.25, range=110, delay=3, mag=60, reload=48, rp=0.5, ry=0.15, hs=1.6, modes=["auto","semi"],
  tier="experimental", rarity="uncommon", sound="laser", rsound="cell", heat=3.6, cool=1.0, eta=40, delivery="case",
  p=dict(glow=(255,70,70)),
  desc="Pulsed laser. Instant, dead-straight beam. Watch the heat.",
  vis=dict(style="laser", body=(200,205,215), glow=(255,70,70)))
G(id="meridian_rail", name="Meridian Railgun", cat="scifi", fam="energy", beh="rail", ammo="ammo_cell",
  price=1800, dmg=32.0, spread=0.0, range=160, delay=52, mag=20, reload=70, rp=6.0, ry=0.2, hs=1.8, ap=0.9, pierce=6,
  tier="prototype", rarity="epic", sound="rail", rsound="cell", heat=40.0, cool=0.7, eta=75, delivery="case",
  p=dict(charge=14, blocks=2, cost=5, glow=(150,200,255), zoom=0.45),
  desc="Electromagnetic accelerator. Charges, then punches through everything.",
  vis=dict(style="rail", body=(40,44,60), glow=(150,200,255)))
G(id="pulse_carbine", name="Pulse Carbine", cat="scifi", fam="energy", beh="pulse", ammo="ammo_cell",
  price=700, dmg=4.6, spread=0.9, range=75, delay=12, mag=48, reload=44, rp=1.3, ry=0.2, hs=1.4, modes=["burst"],
  tier="experimental", rarity="uncommon", sound="pulse", rsound="cell", heat=7.0, cool=1.1, eta=40, delivery="case",
  p=dict(burst=4, burst_gap=2, glow=(255,170,50)),
  desc="Four-pulse burst of concussive energy rings. Stagger and shred.",
  vis=dict(style="pulse", body=(88,70,50), glow=(255,170,50)))
G(id="tesla_arc", name="Tesla Arc", cat="scifi", fam="energy", beh="arc", ammo="ammo_cell",
  price=1500, dmg=6.0, spread=0.0, range=22, delay=9, mag=40, reload=60, rp=0.8, ry=0.4, hs=1.0, ap=0.5,
  tier="prototype", rarity="rare", sound="arc", rsound="cell", heat=9.0, cool=0.9, eta=60, delivery="case",
  p=dict(chains=5, chain_range=6.0, falloff=0.82, glow=(160,200,255)),
  desc="Lightning projector. Arcs jump from target to target.",
  vis=dict(style="arc", body=(70,60,86), glow=(160,200,255)))
G(id="frostbite", name="Frostbite Projector", cat="scifi", fam="energy", beh="cryo", ammo="ammo_cell",
  price=800, dmg=0.9, spread=0.0, range=9, delay=1, mag=120, reload=52, rp=0.0, ry=0.0, hs=1.0, modes=["auto"],
  tier="experimental", rarity="uncommon", sound="cryo", rsound="cell", heat=0.0, cool=0.0, eta=45, delivery="case",
  p=dict(cone=22.0, slow=3, freeze=60, cost_every=2, glow=(170,230,255)),
  desc="Cryogenic cone projector. Slows, chills and eventually freezes.",
  vis=dict(style="cryo", body=(120,160,190), glow=(170,230,255)))
G(id="graviton", name="Graviton Driver", cat="scifi", fam="energy", beh="gravity", ammo="ammo_cell",
  price=1400, dmg=0.0, spread=0.0, range=16, delay=1, mag=200, reload=60, rp=0.0, ry=0.0, hs=1.0, modes=["auto"],
  tier="prototype", rarity="rare", sound="gravity", rsound="cell", heat=0.0, cool=0.0, eta=60, delivery="case",
  p=dict(force=0.17, radius=16.0, cone=30.0, cost_every=3, glow=(190,120,255)),
  desc="Hold to pull entities toward you. Crouch to push them away.",
  vis=dict(style="gravity", body=(60,50,80), glow=(190,120,255)))
G(id="seeker_sw", name="Seeker SW", cat="scifi", fam="energy", beh="smart", ammo="ammo_cell",
  price=1700, dmg=6.0, spread=0.0, range=80, delay=26, mag=30, reload=58, rp=1.6, ry=0.2, hs=1.0,
  tier="prototype", rarity="rare", sound="smart", rsound="cell", heat=14.0, cool=0.8, eta=65, delivery="case",
  p=dict(lock_ticks=14, cone=11.0, missiles=3, speed=1.0, splash=1.8, splash_dmg=2.0, glow=(255,90,200)),
  desc="Smart launcher. Aim at a target to lock on; microrockets track it.",
  vis=dict(style="smart", body=(54,56,66), glow=(255,90,200)))
G(id="scatterbolt", name="Scatterbolt", cat="scifi", fam="energy", beh="eshotgun", ammo="ammo_cell",
  price=900, dmg=2.6, pellets=7, spread=7.0, range=30, delay=22, mag=42, reload=56, rp=4.0, ry=0.7, hs=1.1,
  tier="experimental", rarity="uncommon", sound="eshotgun", rsound="cell", heat=14.0, cool=0.9, eta=45, delivery="case",
  p=dict(speed=1.35, bounces=1, cost=6, glow=(255,150,60)),
  desc="Fan of plasma shards that ricochet once off walls.",
  vis=dict(style="eshotgun", body=(96,60,44), glow=(255,150,60)))
G(id="kiloton_charger", name="Kiloton Charger", cat="scifi", fam="energy", beh="charge", ammo="ammo_cell",
  price=2400, dmg=40.0, spread=0.0, range=100, delay=40, mag=60, reload=80, rp=7.0, ry=0.2, hs=1.0,
  tier="prototype", rarity="epic", sound="charge", rsound="cell", heat=35.0, cool=0.6, eta=80, delivery="case",
  p=dict(charge=44, min_charge=10, splash=4.5, speed=1.5, cost=20, glow=(255,255,120)),
  desc="Chargeable particle cannon. Hold to build up; release to fire. Size scales with charge.",
  vis=dict(style="charger", body=(70,70,76), glow=(255,255,120)))
G(id="aegis_emitter", name="Aegis Emitter", cat="scifi", fam="energy", beh="shield", ammo="ammo_cell",
  price=1600, dmg=0.0, spread=0.0, range=0, delay=20, mag=40, reload=60, rp=0.0, ry=0.0, hs=1.0,
  tier="prototype", rarity="rare", sound="shield", rsound="cell", heat=0.0, cool=0.0, eta=60, delivery="case",
  p=dict(duration=140, radius=3.4, cost=20, absorb=6, glow=(90,160,255)),
  desc="Projects a temporary energy bubble that deflects projectiles and grants absorption.",
  vis=dict(style="shield", body=(70,90,120), glow=(90,160,255)))
G(id="rift_gun", name="Rift Gun", cat="scifi", fam="energy", beh="rift", ammo="ammo_cell",
  price=2600, dmg=4.0, spread=0.0, range=90, delay=34, mag=45, reload=70, rp=2.4, ry=0.2, hs=1.0,
  tier="prototype", rarity="epic", sound="rift", rsound="cell", heat=24.0, cool=0.7, eta=80, delivery="case",
  p=dict(speed=1.1, duration=90, radius=5.0, cost=15, pull=0.2, glow=(190,90,255)),
  desc="Fires a seed that tears space on impact: entities are dragged in and scrambled.",
  vis=dict(style="rift", body=(48,36,70), glow=(190,90,255)))

# ============ D. HEAVY & EXPERIMENTAL ============
G(id="stormcaller_rl", name="Stormcaller RL", cat="heavy", fam="launcher", beh="rocket", ammo="ammo_rocket",
  price=1100, dmg=14.0, spread=0.3, range=100, delay=28, mag=1, reload=64, rp=5.5, ry=0.5, hs=1.0,
  tier="experimental", rarity="rare", sound="rocket", rsound="rocket_reload", eta=60, delivery="pod",
  p=dict(speed=1.25, power=2.4, splash=4.0),
  desc="Shoulder-fired rocket launcher. Dangerous backblast; explosions don't wreck terrain.",
  vis=dict(style="launcher", body=(88,92,64), accent=(210,80,40)))
G(id="obliterator", name="Obliterator", cat="heavy", fam="heavy", beh="heavycannon", ammo="ammo_core",
  price=3200, dmg=24.0, spread=0.0, range=90, delay=70, mag=6, reload=90, rp=9.0, ry=0.4, hs=1.0,
  tier="prototype", rarity="epic", sound="cannon", rsound="core", heat=45.0, cool=0.5, eta=90, delivery="pod",
  p=dict(speed=1.2, splash=5.5, splash_dmg=14.0, cost=1, glow=(255,110,60)),
  desc="Heavy energy cannon. One enormous, slow, devastating shot. Long cooldown.",
  vis=dict(style="cannon", body=(74,64,60), glow=(255,110,60)))
G(id="gauss_mortar", name="Gauss Mortar", cat="heavy", fam="heavy", beh="emp", ammo="ammo_core",
  price=2800, dmg=10.0, spread=0.5, range=70, delay=60, mag=8, reload=80, rp=6.0, ry=0.3, hs=1.0,
  tier="prototype", rarity="epic", sound="mortar", rsound="core", heat=30.0, cool=0.6, eta=90, delivery="pod",
  p=dict(speed=1.15, gravity=0.045, splash=6.0, emp=120, cost=1, glow=(110,210,255)),
  desc="Portable electromagnetic mortar. Lobbed shell detonates in an EMP: mobs are slowed, weakened and lit up.",
  vis=dict(style="mortar", body=(56,62,70), glow=(110,210,255)))
G(id="prism_lance", name="Prism Lance", cat="heavy", fam="heavy", beh="beam", ammo="ammo_core",
  price=3000, dmg=1.6, spread=0.0, range=48, delay=1, mag=60, reload=90, rp=0.0, ry=0.0, hs=1.4, modes=["auto"],
  tier="prototype", rarity="epic", sound="beam", rsound="core", heat=1.6, cool=1.1, eta=90, delivery="case",
  p=dict(sustained=1, cost_every=4, glow=(255,255,255)),
  desc="Sustained refracted beam. Damage and brightness ramp the longer you hold it on target.",
  vis=dict(style="prism", body=(200,200,210), glow=(255,255,255)))
G(id="sentinel_kit", name="Sentinel Turret Kit", cat="heavy", fam="heavy", beh="turret", ammo="ammo_cell",
  price=1900, dmg=3.5, spread=1.0, range=22, delay=4, mag=3, reload=40, rp=0.0, ry=0.0, hs=1.0,
  tier="experimental", rarity="rare", sound="turret", rsound="core", eta=70, delivery="pod",
  p=dict(life=2400, turret_range=18.0, turret_dmg=3.5, turret_delay=4, turret_ammo=200),
  desc="Deployable sentry. Right-click a block to place it; it engages hostile mobs automatically.",
  vis=dict(style="turret", body=(70,76,60), glow=(255,80,60)))

# ============ CLASSIFIED ============
G(id="unstable_x13", name="Prototype X-13", cat="classified", fam="energy", beh="unstable", ammo="ammo_cell",
  price=1300, dmg=9.0, spread=1.5, range=70, delay=8, mag=40, reload=50, rp=2.0, ry=0.8, hs=1.3,
  tier="classified", rarity="epic", sound="unstable", rsound="cell", heat=8.0, cool=0.8, eta=70, delivery="case",
  secret="proto_lab", p=dict(instability=0.14, glow=(120,255,90)),
  desc="Experimental weapon with an unstable core. Each shot is a different idea. Some are bad ideas.",
  vis=dict(style="unstable", body=(40,56,44), glow=(120,255,90)))
G(id="paradox_revolver", name="Paradox Revolver", cat="classified", fam="pistol", beh="bullet", ammo="ammo_357",
  price=1500, dmg=12.0, spread=0.6, range=80, delay=14, mag=6, reload=62, rp=4.5, ry=0.6, hs=2.0, ap=0.3,
  tier="classified", rarity="epic", sound="paradox", rsound="revolver", eta=60, delivery="case", secret="operator",
  p=dict(echo=10, glow=(255,60,220)),
  desc="Every shot echoes: a second bullet follows half a second later along the same line.",
  vis=dict(style="revolver", body=(60,30,70), accent=(255,60,220)))
G(id="void_whisper", name="Void Whisper", cat="classified", fam="rifle", beh="bullet", ammo="ammo_762",
  price=2000, dmg=10.0, spread=0.3, range=120, delay=12, mag=8, reload=62, rp=2.4, ry=0.3, hs=2.0, ap=0.5,
  tier="classified", rarity="epic", sound="void", rsound="mag", eta=80, delivery="case", secret="line_end",
  p=dict(phase=1, glow=(180,100,255), zoom=0.45),
  desc="Rounds phase through blocks: the target behind the wall is still a target.",
  vis=dict(style="dmr", body=(28,20,44), accent=(180,100,255)))
G(id="ember_judgment", name="Ember Judgment", cat="classified", fam="shotgun", beh="shotgun", ammo="ammo_12g",
  price=1800, dmg=2.4, pellets=10, spread=7.0, range=22, delay=18, mag=6, reload=60, rp=5.0, ry=0.8, hs=1.1,
  tier="classified", rarity="epic", sound="ember", rsound="pump", eta=75, delivery="pod", secret="line_nether",
  p=dict(burn=6, glow=(255,120,30)),
  desc="Forged in the Nether. Hellfire pellets ignite everything they touch.",
  vis=dict(style="autoshot", body=(60,22,16), accent=(255,140,30)))
G(id="daybreak_lance", name="Daybreak Lance", cat="classified", fam="energy", beh="beam", ammo="ammo_cell",
  price=1700, dmg=11.0, spread=0.0, range=120, delay=24, mag=24, reload=60, rp=2.0, ry=0.0, hs=1.5, ap=0.4,
  tier="classified", rarity="epic", sound="beam", rsound="cell", heat=26.0, cool=0.9, eta=75, delivery="case",
  secret="line_overworld", p=dict(sun=1, cost=2, glow=(255,240,150)),
  desc="Solar lance. Hits harder in daylight and sets the undead on fire.",
  vis=dict(style="laser", body=(230,200,100), glow=(255,240,150)))
G(id="black_ledger", name="The Black Ledger", cat="classified", fam="sniper", beh="bullet", ammo="ammo_50bmg",
  price=2600, dmg=22.0, spread=0.1, range=260, delay=36, mag=4, reload=84, rp=8.0, ry=0.5, hs=2.5, ap=0.5, pierce=1,
  tier="classified", rarity="epic", sound="ledger", rsound="bolt", eta=90, delivery="case", secret="archive",
  p=dict(mark=1, zoom=0.18, scoped=1, glow=(255,200,60)),
  desc="Marks every target it hits. Marked targets take +50% damage from all your shots.",
  vis=dict(style="antimat", body=(24,22,24), accent=(255,200,60)))

SUPPLY_LISTING_DELIVERY = "crate"

TIERS = ["standard", "advanced", "experimental", "prototype", "classified"]
TIER_THRESHOLDS = [0, 400, 2000, 6000]   # total credits spent to unlock tiers 0..3 (classified is by secrets only)
# teleport = instant rift: fastest (fixed 5 s) and ruinously expensive
DELIVERY_FEE = dict(crate=10, drone=25, pod=45, case=90, teleport=750)
