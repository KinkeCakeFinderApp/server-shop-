# LegendaryAdditions 2.0 (Folia / Paper 26.1.2)

This plugin combines three things:

- The original Legendary Additions items: Drill, Paxel, Riftcaster, Embershade and Tidefire Crossbow.
- An authenticated admin-rod system that includes every rod from the *Orbital Strike Cannon v11.0* datapack.
- A persistent Admin dimension.

## Build (no GitHub needed)

```
cd legendary-additions
./gradlew build          # Windows: gradlew.bat build
```

The jar is written to `build/libs/legendary_additions-2.0.0.jar`.

- A Java 25 JDK is needed. Gradle downloads one automatically if none is installed.
- GitHub Actions also builds the jar and uploads it as the `legendary_additions` artifact.

## Install

1. Remove any older `legendary_additions-*.jar` from `plugins/`.
2. Remove the *Orbital Strike Cannon* datapack from `world/datapacks/`. The plugin replaces it, and leaving both installed would run two copies of each effect on old datapack rods.
3. Drop the new jar into `plugins/` and start the server.
   - The plugin registers a built-in datapack that creates the `adminplugin:admin` dimension.
   - If the log says the Admin dimension is not loaded yet, restart the server once.

## Commands

There are no cooldowns, and there is no `/teleport` command.

| Command | Gives | Permission |
|---|---|---|
| `/stab [player]` | Shulker of 27 single-use **Orbital Strike Rods** | `admindimension.orbitalrod` |
| `/stabshot [player]` | Reusable Orbital Strike Rod | `admindimension.orbitalrod` |
| `/nuke [player]` | Shulker of 27 single-use **Nuke Shots** | `admindimension.nukerod` |
| `/nukeshot [player]` | Reusable Nuke Shot | `admindimension.nukerod` |
| `/teleportshot [player]` | Reusable **Admin Teleport Rod** | `admindimension.teleportrod` |
| `/lawnuke [player]` / `/lawnukeshot [player]` | Law-Nuke Shot: shulker / reusable | `admindimension.lawnukerod` |
| `/withernuke [player]` / `/withernukeshot [player]` | Wither Nuke Shot: shulker / reusable | `admindimension.withernukerod` |
| `/wolf [player]` / `/wolfshot [player]` | Wolf Rod: shulker / reusable | `admindimension.wolfrod` |
| `/arrow [player]` / `/arrowshot [player]` | Arrow Shot: shulker / reusable | `admindimension.arrowrod` |
| `/admin` | Teleport to the Admin dimension | `admindimension.admin` |
| `/admin return` | Go back to where you were before `/admin` | `admindimension.admin` |
| `/legendary_additions give <player> <item>` | Original Legendary Additions items | `legendaryadditions.give` |

- `admindimension.use` (default: everyone) lets a player cast genuine rods.
- `admindimension.*` grants every permission above.
- Reusable rods have Unbreaking III, Mending and Curse of Vanishing.
- Single-use rods are consumed one at a time when cast.

## How rods fire

**Cast → ray trace from the eyes → lock the exact crosshair hit (block or entity) → effect.**

- The cast is cancelled, so no hook is ever thrown and the hook is never used for targeting.
- The range is set by `rod-targeting.max-distance`. The teleport rod also caps it at `teleport.max-distance`.
- On Folia the ray only reads chunks that are loaded and owned by the caster's region, so it never touches another thread's data. Very long shots into unloaded terrain report "No valid target".
- Normal fishing rods, and the Riftcaster, work exactly as before.

## Authentication

Every genuine rod carries hidden persistent data:

- `adminplugin:rod_type`, for example `orbital_single` or `nuke_reusable`.
- `adminplugin:rod_version`
- `adminplugin:rod_nonce`: random, different on every item.
- `adminplugin:rod_origin`: `plugin` or `datapack`.
- `adminplugin:rod_source`: the original datapack function, for converted rods.
- `adminplugin:rod_auth`: an HMAC-SHA256 signature over the fields above.

How the signature works:

- The HMAC key is in `plugins/LegendaryAdditions/rod-secret.key`, is generated on first start, and is never written onto an item.
- Items made with `/give`, renamed rods, copied lore, and hand-built NBT all fail the check. They do nothing and show a "failed authentication" message.
- Keep that file private. Deleting it invalidates every rod that already exists.
- An operator who can copy a whole item, or who edits the server itself, can still duplicate rods. Nothing can prevent that.

Internal ids:

| Rod | Single-use id | Reusable id |
|---|---|---|
| Orbital Strike | `orbital_single` | `orbital_reusable` |
| Nuke | `nuke_single` | `nuke_reusable` |
| Admin Teleport | — | `teleport_reusable` |
| Law-Nuke | `lawnuke_single` | `lawnuke_reusable` |
| Wither Nuke | `withernuke_single` | `withernuke_reusable` |
| Wolf | `wolf_single` | `wolf_reusable` |
| Arrow | `arrow_single` | `arrow_reusable` |

## Every rod found in the Orbital Strike Cannon datapack

The datapack has 43 files, all inspected. Here is how it works:

- It is namespace `orbital_strike_cannon` with pack format 101.
- `tick.json` runs `orbital_strike_cannon:tick` every tick. That function runs `setup` on every fishing bobber not yet tagged `spawned`.
- `setup` checks the bobber owner's held rod. A rod counts when it is `fishing_rod[damage=60, custom_data={Orbital_Cannon:1010} or {wolf_cannon:1010}, custom_name=...]`.
- Each rod then:
  1. Ray-marches from the player's eyes in 0.5-block steps until it reaches a non-air block (`raycast/*/find_block`).
  2. Deletes the rod with `item replace ... weapon with air`, so every datapack rod was single-use.
  3. Plays `item.shield.break`, kills the bobber, and runs the payload.
- The `get_*` functions give one rod, and the `get_*_shulker` functions give a red shulker holding 27 rods.
- Nothing else in the pack is rod-related: no recipes, loot tables, tags, advancements or scoreboards.

### 1. Stab Shot
- **Original item:** `fishing_rod[damage=60, custom_data={Orbital_Cannon:1010}, custom_name="Stab Shot"]`
- **Original function:** A 3-stage orbital strike, overworld only. It summons 709 primed TNT at Y=319 above the hit block, falling at -10 blocks/tick:
  - Stage 1 "Auxiliary Charge": 9 TNT with fuses 1–9.
  - 300 TNT with fuse 10.
  - Stage 3 "Penetrator Warhead": 400 TNT with fuse 11.
  - Together they bore a deep shaft.
- **Datapack functions:** `get_stab`, `get_stab_shulker`, `setup`, `raycast/stab_raycast_power_1/*`, `stab`
- **Conversion:** Merged with the built-in Orbital Strike.
  - It uses the configured warning marker, charging beam, sparks, smoke, impact, shockwave, damage and knockback.
  - Optional block damage and fire are set in `orbital-strike`.
  - Direct effects replace the 709 TNT entities.
- **New id:** `orbital_single` / `orbital_reusable` (origin `plugin`, source `orbital_strike_cannon:stab`)
- **Commands:** `/stab`, `/stabshot`. The datapack's `get_stab` and `get_stab_shulker` functions map to these.

### 2. Nuke Shot
- **Original item:** `fishing_rod[damage=60, custom_data={Orbital_Cannon:1010}, custom_name="Nuke Shot"]`
- **Original function:** 1169 primed TNT (fuse 80) summoned 70 blocks above the hit, flung outward in 9 rings at up to 2 blocks/tick. Overworld only.
- **Datapack functions:** `get_nuke`, `get_nuke_shulker`, `setup`, `raycast/nuke_raycast_power_1/*`, `nuke`
- **Conversion:** Merged with the built-in Nuke.
  - It has a large marker, a wide soul-fire beam, and a Wither-spawn warning sound.
  - The impact includes a multi-emitter blast, sparks, flames, a double shockwave, and a rising mushroom cloud.
  - Heavy damage and knockback apply.
  - Block damage comes from a central blast plus a ring of 8 secondary blasts. Fire and block damage are both configurable in `nuke`.
- **New id:** `nuke_single` / `nuke_reusable` (origin `plugin`, source `orbital_strike_cannon:nuke`)
- **Commands:** `/nuke`, `/nukeshot`

### 3. Law-Nuke Shot
- **Original item:** `fishing_rod[damage=60, custom_data={Orbital_Cannon:1010}, custom_name="Law-Nuke Shot"]`
- **Original function:** The biggest nuke. 1836 primed TNT (fuse 90) summoned 86 blocks above the hit, flung outward at up to 3 blocks/tick, carpet-bombing a very wide area. Overworld only.
- **Datapack functions:** `get_lawnuke`, `get_lawnuke_shulker`, `setup`, `raycast/lawnuke_raycast/*`, `lawnuke`
- **Conversion:** `LawNukeEffect`.
  - A marker and a descending payload cloud appear during a 90-tick fuse.
  - Then 140 TNT-strength explosions (configurable) fire across a 45-block radius over 30 ticks.
  - Each explosion runs on the Folia region that owns its location.
  - Explosions replace the 1836 TNT entities.
- **New id:** `lawnuke_single` / `lawnuke_reusable` (origin `datapack`)
- **Commands:** `/lawnuke`, `/lawnukeshot`. The datapack only had `get_lawnuke`, renamed to this format.

### 4. Wither Nuke Shot
- **Original item:** `fishing_rod[damage=60, custom_data={Orbital_Cannon:1010}, custom_name="Wither Nuke Shot"]`
- **Original function:** 1296 charged (`dangerous:1b`) wither skulls summoned 70 blocks above the hit. They rain down with outward motion and acceleration 0.3. This one works in every dimension.
- **Datapack functions:** `get_wither_nuke`, `get_wither_cannon_rod_shulker`, `setup`, `raycast/wither_nuke_raycast/*`, `wither_nuke`
- **Conversion:** `WitherNukeEffect`.
  - 160 charged skulls (configurable) with the same spread, released 20 per tick.
  - Skulls are tagged with their owner, so `damage-owner: false` protects the caster.
  - `destroy-blocks: false` stops them breaking terrain.
- **New id:** `withernuke_single` / `withernuke_reusable` (origin `datapack`)
- **Commands:** `/withernuke`, `/withernukeshot`

### 5. Wolf Rod
- **Original item:** `fishing_rod[damage=60, custom_data={wolf_cannon:1010}, custom_name="Wolf Rod"]`. This is the only rod using the `wolf_cannon` tag.
- **Original function:** Summons 53 wolves, each with wolf armor, Strength II, Regeneration I, Speed II (1800 ticks) and Fire Resistance (9600 ticks), and sets their owner to the caster. They appeared 10 blocks in front of the player (no raycast). Overworld only.
- **Datapack functions:** `get_wolf_rod`, `get_wolf_rod_shulker`, `setup`, `spawn_wolf`
- **Conversion:** `WolfPackEffect`.
  - The same 53 armoured, buffed, tamed wolves, now spawned at the crosshair target.
  - `lifetime-seconds` can make them temporary.
- **New id:** `wolf_single` / `wolf_reusable` (origin `datapack`)
- **Commands:** `/wolf`, `/wolfshot`

### 6. Arrow Shot
- **Original item:** `fishing_rod[damage=60, custom_data={Orbital_Cannon:1010}, custom_name="Arrow Shot"]`
- **Original function:** 50 arrows (a 5×5 grid, two layers with damage 70 and 80) summoned 86 blocks above the hit, moving straight down at 10 blocks/tick. Overworld only.
- **Datapack functions:** `get_arrow_rod`, `get_arrow_shot_shulker`, `setup`, `raycast/arrow_raycast/*`, `arrow_shot`
- **Conversion:** `ArrowRainEffect`.
  - The identical grid, height, speed and damage.
  - Arrows can't be picked up and disappear shortly after landing.
- **New id:** `arrow_single` / `arrow_reusable` (origin `datapack`)
- **Commands:** `/arrow`, `/arrowshot`

### Datapack functions that are not rods
- `load` is listed in `load.json`, but the function does not exist in the pack.
- `tick` and `setup` are the detection loop that the plugin's cast listener replaces.

### Differences from the datapack
- Rods fire immediately at the crosshair when cast. The fishing hook is not used.
- Every datapack rod was single-use. Each now also has a reusable `…shot` version.
- The datapack only let most rods work in the overworld. The plugin allows every world; use `rod-targeting.disabled-worlds` to block some.
- Converted single-use rods keep the datapack's name and its `damage=60` durability look.
- Rods are recognised by the signed hidden data, not by name. The original datapack id is kept in `adminplugin:rod_source`.
- Old rods made by the datapack carry no signature, so they behave as normal fishing rods. Hand out new ones with the commands above.

## Folia notes

- Effects run on the `RegionScheduler` of the target location.
- Delayed and multi-point explosions are each scheduled on the region that owns that point.
- Give commands and Paxel haste use each player's `EntityScheduler`.
- Global timers use the `GlobalRegionScheduler`.
- Teleports use `teleportAsync`.
- Every repeating effect task cancels itself when it finishes, and no entities are spawned beyond the ones the effect itself needs.
- The Admin dimension is created by a datapack at bootstrap, because Folia cannot create worlds at runtime.
