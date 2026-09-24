# LegendaryAdditions 3.0 (Folia 26.1.2)

This plugin contains:

- A persistent, End-style **Admin dimension**.
- Authenticated special **fishing-rod weapons and tools**.
- An **Admin Teleport Rod**.
- A chest-GUI **suggestion and voting system**.
- The original Legendary Additions items: Drill, Paxel, Riftcaster, Embershade and Tidefire Crossbow.

Nothing else needs to be installed. There is no external datapack to add.

## Build

```
cd legendary-additions
./gradlew build          # Windows: gradlew.bat build
```

- The jar is written to `build/libs/legendary_additions-3.0.0.jar`.
- It needs a Java 25 JDK, because Minecraft 26.1.2 runs on Java 25. Gradle downloads one automatically if it is missing.
- `./gradlew build` also runs the unit tests.

## Install

1. Stop the server.
2. Delete any older `legendary_additions-*.jar` from `plugins/`.
3. Copy the new jar into `plugins/`.
4. Start the server.

The plugin creates these files in `plugins/LegendaryAdditions/`:

- `config.yml`
- `rod-secret.key`: the signing key. Keep it private and back it up.
- `suggestions.db`: the SQLite database that stores suggestions and votes.

## Project structure

```
net.srv.legendaryadditions
├─ LegendaryAdditionsBootstrap     registers the Admin dimension at bootstrap
├─ LegendaryAdditionsMod           main plugin: wiring only
├─ admin
│  ├─ AdminSettings                configuration manager (typed snapshot of config.yml)
│  ├─ SelfTest                     opt-in in-server checks used by CI (-Dlegendaryadditions.selftest=true)
│  ├─ command                      CommandRegistrar, RodGiveCommand, AdminCommand, SuggestionsCommand
│  ├─ rod                          RodKind, RodRegistry (rod manager), RodAuthenticator (HMAC),
│  │                               RayTargeting (targeting manager), RodListener (cast handling), RodKeys
│  ├─ effect                       RodEffect interface + RodEffects registry, StrikeEffect (orbital + nuke),
│  │                               LawNukeEffect, WitherNukeEffect, WolfPackEffect, ArrowRainEffect,
│  │                               TeleportEffect + SafeLocations (teleport manager), ExplosionGuard, Fx
│  ├─ dimension                    AdminDimension (Admin dimension manager)
│  ├─ suggestion                   SuggestionService (DB thread), SuggestionAccess (permissions),
│  │  │                            ChatInputManager
│  │  ├─ data                      SuggestionRepository (SQLite persistence, voting records), models
│  │  └─ gui                       SuggestionGui (public), AdminSuggestionGui (admin), Menu, MenuListener,
│  │                               ClickGuard, Items, SuggestionFormat
│  └─ util                         Messages
└─ custom                          original Legendary Additions items
```

## Commands

| Command | What it gives or does | Permission |
|---|---|---|
| `/admin` | Teleport to the Admin dimension's central spawn | `admindimension.use` |
| `/admin return` | Go back to where you were before `/admin` | `admindimension.use` |
| `/stab [player]` | **Shulker of 27 single-use Orbital Strike Rods** | `admindimension.orbitalrod` |
| `/stabshot [player]` | One reusable Orbital Strike Rod | `admindimension.orbitalrod` |
| `/nuke [player]` | **Shulker of 27 single-use Nuke Shots** | `admindimension.nukerod` |
| `/nukeshot [player]` | One reusable Nuke Shot | `admindimension.nukerod` |
| `/lawnuke [player]` | Shulker of 27 single-use Law-Nuke Shots | `admindimension.lawnukerod` |
| `/lawnukeshot [player]` | One reusable Law-Nuke Shot | `admindimension.lawnukerod` |
| `/withernuke [player]` | Shulker of 27 single-use Wither Nuke Shots | `admindimension.withernukerod` |
| `/withernukeshot [player]` | One reusable Wither Nuke Shot | `admindimension.withernukerod` |
| `/wolfrod [player]` | Shulker of 27 single-use Wolf Rods | `admindimension.wolfrod` |
| `/wolfrod shot [player]` | One reusable Wolf Rod | `admindimension.wolfrod` |
| `/arrowrod [player]` | Shulker of 27 single-use Arrow Rods | `admindimension.arrowrod` |
| `/arrowrodshot [player]` | One reusable Arrow Rod | `admindimension.arrowrod` |
| `/teleportshot [player]` | One reusable Admin Teleport Rod | `admindimension.teleportrod` |
| `/suggestions` | Open the suggestions GUI | everyone, or `admindimension.suggestions` if `allow-all-players: false` |
| `/suggestions admin` | Open the admin suggestion backend | `admindimension.suggestions.admin` |
| `/legendary_additions give <player> <item>` | Original Legendary Additions items | `legendaryadditions.give` |

- The plugin does not add a `/teleport` command. Vanilla `/teleport`, the alias of `/tp`, still exists and is unrelated to this plugin.
- `[player]` is optional for players. From the console, it is required.
- Tab completion works for `/admin return`, `/wolfrod shot`, player names, and `/suggestions admin`.

## Permissions

| Permission | Default | Meaning |
|---|---|---|
| `admindimension.use` | op | `/admin`, `/admin return` |
| `admindimension.orbitalrod` | op | `/stab`, `/stabshot` |
| `admindimension.nukerod` | op | `/nuke`, `/nukeshot` |
| `admindimension.teleportrod` | op | `/teleportshot` |
| `admindimension.lawnukerod` / `withernukerod` / `wolfrod` / `arrowrod` | op | the matching commands |
| `admindimension.suggestions` | true | browse, vote and submit when `allow-all-players` is false |
| `admindimension.suggestions.admin` | op | the admin backend and every admin action |
| `admindimension.admin` | op | all of the above |
| `legendaryadditions.give` | op | `/legendary_additions give` |

Casting a genuine rod needs no permission. Only admins can obtain rods, and every cast is checked against the signature.

## Rods

**Activation.** Casting a special rod cancels the cast, so no hook is thrown.

1. The plugin ray-traces from the player's eye position along their exact look direction.
2. It finds the first block or entity hit, using the precise hit point.
3. It copies that location, so moving the camera during the charge-up cannot move the strike.
4. The effect starts.

**Range.** The range is set by `rod-targeting.max-distance`. The teleport rod is also limited by `teleport-rod.max-distance`.

**No cooldowns.** `warning-time-ticks` is only the visual charge-up.

**Single-use rods:**
- Exactly one item is removed from the casting hand (main hand or off-hand), and only after the effect starts successfully.
- A stack goes down by one; it is never removed whole.
- If the rod refuses to fire (no target, unsafe teleport, disabled world), nothing is consumed.
- Before consuming, the plugin checks that the hand still holds the same signed rod.

**Reusable rods** have Unbreaking III, Mending and Curse of Vanishing, and are never consumed.

**Normal fishing rods** are untouched. Only rods carrying the plugin's hidden data are intercepted.

### What each rod does

| Rod | Effect |
|---|---|
| Orbital Strike | The sequence below, then an impact: shockwave, damage and knockback. Block damage and fire are optional. |
| Nuke Shot | Larger radius, much more damage and stronger knockback. The optional visuals are a wide soul-fire beam, several explosion emitters, a double shockwave and a mushroom cloud. Block breaking and fire are optional and off by default. |
| Law-Nuke Shot | 140 TNT-strength blasts ripple across a 45-block radius (an optional fuse is set by `warning-time-ticks`). Each blast runs on the region that owns its own location. |
| Wither Nuke Shot | 160 charged wither skulls rain from 70 blocks above the target, released 20 per tick. |
| Wolf Rod | 53 wolves with wolf armor, Strength II, Regeneration, Speed II and Fire Resistance, tamed to the caster. They can be made temporary. |
| Arrow Rod | A 5×5 grid of arrows, two layers deep (damage 70 and 80), fired straight down at 10 blocks/tick. |
| Admin Teleport Rod | Moves you to the nearest safe spot at or near the target: solid ground, two blocks of headroom, inside the world border, no lava (configurable), no void. If there is none, the teleport is cancelled and you are told why. |

Orbital Strike sequence: a target marker and warning particles, warning sounds with rising pitch, a charging beam that descends from the sky, and a burst of sparks just before impact.

**Sounds and particles are off by default.** `rod-effects.sounds` and `rod-effects.particles` in `config.yml` control every extra sound and particle the rods make: warning markers, beams, charge-up sounds, smoke, shockwave rings, teleport sparkles, and the break sound of used-up rods. Set either to `true` to bring them back. With both off:
- Rods still do their real effect.
- Vanilla explosions, wither skulls and arrows still make their normal vanilla sounds.
- There is no target warning: `warning-time-ticks` defaults to `0`, so strikes land the moment you cast.
- The Nuke defaults to `destroy-blocks: false` and `create-fire: false`. Vanilla explosions always show explosion particles and play the boom sound, so with these off the Nuke makes no explosions and only deals damage and knockback. Turn them back on if you want terrain damage. The Law-Nuke is made of explosions, so it still shows them.

For every rod the counts, radius, damage and power are set in `config.yml`. `damage-owner: false` protects the caster from their own explosions and projectiles.

### Authentication

Every rod stores hidden persistent data:

| Key | Contents |
|---|---|
| `adminplugin:rod_type` | e.g. `orbital_single`, `nuke_reusable`, `teleport_reusable`, `lawnuke_*`, `withernuke_*`, `wolfrod_*`, `arrowrod_*` |
| `adminplugin:rod_version` | format version |
| `adminplugin:rod_nonce` | random, unique per item |
| `adminplugin:rod_auth` | HMAC-SHA256 over type, version and nonce |

How the check works:

- The HMAC key is in `rod-secret.key` and is never written onto an item.
- Every cast re-verifies the signature.
- Items made with `/give`, renamed rods, copied lore, and edited type or signature fields do nothing and show "failed authentication".
- **Limitation:** nothing can stop an operator from copying an entire item (for example, creative middle-click) or from changing the server or plugin itself. The goal is to stop normal players from making working rods.

## Admin dimension

**Separate from the vanilla End.**
- The dimension is `adminplugin:admin`, with its own dimension type `adminplugin:admin`.
- That type is a copy of the 26.1.2 End type (End sky, fog, lighting and music) with `has_ender_dragon_fight: false`.
- The vanilla End is never modified.

**Terrain.**
- It uses vanilla End noise: a central island, a void ring, and floating outer islands of end stone.
- The biome is set to End Highlands everywhere, so chorus plants and Endermen appear everywhere, including on the central island.
- End cities can also generate.

**How it is created.**
- Folia cannot create worlds while running. The dimension definition is packaged inside the plugin jar and registered at bootstrap through Paper's `DatapackRegistrar`.
- The server then generates it and saves it under `world/dimensions/adminplugin/admin` like any vanilla dimension. It persists and is not regenerated on restart.

**Central spawn.**
- The first `/admin` finds the top of the central island at 0,0 and checks it is safe.
- A 5×5 end-stone-brick platform is built only if nothing safe exists.
- The result is saved as the dimension's spawn.

**Return location.**
- The world key, X, Y, Z, yaw and pitch are saved in the player's persistent data after a successful arrival. They survive restarts.
- `/admin return` without a saved location explains that instead of throwing an error.

## Suggestions

**Opening it.** `/suggestions` opens a 6-row chest GUI:

| Slots | Contents |
|---|---|
| 0–44 | Suggestions for the current page |
| 45 | Legendary Suggestions tab |
| 46 | Server Suggestions tab |
| 47 | Sort order (highest votes → newest → oldest) |
| 48 / 50 | Previous / Next page |
| 49 | Submit Suggestion |
| 51 | Admin Panel (visible to admins only) |
| 52 | Back |
| 53 | Close |

**Browsing.** Each suggestion shows its ID, shortened text, category, vote count, author, date, and whether you have voted.
- Left-click a suggestion to open its details, with the full text and a vote button.
- Right-click to vote directly from the list.

**Submitting.**
1. Click Submit.
2. Type the suggestion in chat. It is not broadcast. Type `cancel` to stop; the input times out after `input-timeout-seconds`.
3. Pick Legendary Suggestion or Server Suggestion.

New suggestions are saved as **PENDING** and stay hidden from both public tabs until an admin approves them. `max-pending-per-player` limits spam.

**Voting.**
- Each vote is a database row keyed by (suggestion ID, player UUID) with a primary key, so a second vote is impossible.
- This holds across reconnects, reopened GUIs, re-running `/suggestions`, and server restarts.
- Displayed counts are always a `COUNT` over those rows.
- Only APPROVED suggestions accept votes.
- If `allow-vote-removal` is true, the vote button lets you remove your vote.

**Admin backend** (`/suggestions admin`, or the Admin Panel button):
- Tabs for Pending, Approved, Rejected and Archived.
- The review screen shows the full text, ID, submitter name and UUID, date, status, requested and assigned category, votes, approval info and last reviewer.
- Buttons: **Approve as Legendary**, **Approve as Server**, **Change Category**, **Reject**, **Archive**, **Delete permanently** (if `allow-delete`), **View voters**, Back and Close.
- Reject, Archive and Delete ask for confirmation first.
- Approval stores the admin's UUID and name and the timestamp.
- The author is notified if online.
- Online admins are told about new submissions.

### GUI security

- What a click does comes only from a server-side map built when the screen was drawn. The clicked item's name, lore or data is never trusted.
- Every click in a plugin GUI is cancelled, so items can't be taken out or put in.
- Only left and right clicks (including shift) on top-inventory slots with a registered action do anything. Drags, number keys, drops and clicks in your own inventory are ignored.
- Admin screens re-check `admindimension.suggestions.admin` on open, on every click, and again right before each action is queued. Removing someone's permission takes effect on their next click.
- Clicks are rate-limited to one every 200 ms. Only one database operation per player can run at a time, and confirm buttons work once.
- Every admin change only applies if the suggestion still has the status the admin was looking at. A stale screen, a double click, or two admins acting at once gets "changed by someone else", and the screen refreshes.
- Unknown or deleted suggestion IDs are handled with a message.

## Folia design

**Scheduling:**
- Rod effects run on the target location's `RegionScheduler`.
- Multi-point explosions are each scheduled on the region that owns that point.
- Wolf lifetimes run on each wolf's `EntityScheduler`.
- Item giving, GUI opening and chat input all go through the player's `EntityScheduler`, so work is dropped automatically if the player logs off.
- The Paxel haste check uses one `GlobalRegionScheduler` timer that hands each player's check to their own scheduler.
- Spawn-point and world data changes run on the `GlobalRegionScheduler`.

**Region safety:**
- Ray traces, safe-spot searches and area damage only read chunks that are loaded and owned by the current region.
- Repeating effects stop themselves when finished or when their world unloads.

**Teleports** always use `teleportAsync`.

**Database:**
- All database work runs on one dedicated thread, never on a region thread.
- SQLite allows only one writer, so a single ordered queue is safer than the shared `AsyncScheduler` pool.
- The SQLite driver comes bundled with Folia (sqlite-jdbc 3.49.1.0).

**No old scheduler.** There is no `BukkitScheduler` usage anywhere.

## Verification

Checked automatically:
- **Compilation** against `dev.folia:folia-api:26.1.2.build.8-stable`, on every push through GitHub Actions or locally with `./gradlew build`.
- **Unit tests** (JUnit, `src/test`):
  - The SQLite repository: new suggestions are PENDING and hidden; approval puts them in the right tab; category changes work.
  - Votes: duplicates are rejected, and votes persist when the database is reopened.
  - Rejected, archived and deleted suggestions can't be voted on or shown; stale admin actions are refused; the pending limit holds.
  - Paging and sorting work.
  - Text cleaning works.
  - HMAC signing: the signature verifies, is stable across reloads, and fails when any field is tampered with.
  - Every rod type ID resolves correctly.
- **Folia server test** (CI job `folia-server-test`, script `.github/scripts/folia-server-test.sh`):
  - Boots the official Folia 26.1.2 build twice with the plugin and `-Dlegendaryadditions.selftest=true`.
  - Every rod kind is genuine and reusable rods have Unbreaking III, Mending and Curse of Vanishing.
  - Every single-use command's shulker holds 27 genuine single-use rods.
  - A vanilla rod and a renamed rod with copied lore are treated as normal; edited or made-up signatures are rejected.
  - A stack of 3 becomes 2, and the last rod empties the hand.
  - The database keeps pending suggestions hidden and allows one vote per player.
  - The Admin dimension loads, is separate from the End, generates terrain at the centre, and has a safe spawn.
  - Every command is registered and responds.
  - `rod-secret.key` is unchanged after a restart, and the Admin dimension folder is saved.

Not tested automatically, because they need a real game client:
- Actually casting rods in-game and seeing the effects.
- Clicking through the GUIs as a player.
- Chat entry.
- Teleporting a player.

Test those on your server before relying on them.
