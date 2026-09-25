# LegendaryAdditions 3.2 (Folia 26.1.2)

This plugin contains:

- A persistent, End-style **Admin dimension**.
- Authenticated special **fishing-rod weapons and tools**.
- An **Admin Teleport Rod**.
- A chest-GUI **suggestion and voting system**.
- A **Legendary Creator** (inside `/suggestionadmin`) to make new legendary items with abilities such as Vein Miner and Tree Capitator, and any enchantments, saved in `legendaries.db`.
- **`/shop admin`**: an in-game editor for the FoliaShop `/shop` that adds custom legendaries, built-in legendaries, any vanilla item or the item in your hand, with search.
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
│  ├─ effect                       RodEffect interface + RodEffects registry, StabColumnEffect, NukeRingsEffect, StrikeEffect (crater style),
│  │                               LawNukeEffect, WitherNukeEffect, WolfPackEffect, ArrowRainEffect,
│  │                               TeleportEffect + SafeLocations (teleport manager), ExplosionGuard, Fx
│  ├─ dimension                    AdminDimension (Admin dimension manager)
│  ├─ suggestion                   SuggestionService (DB thread), SuggestionAccess (permissions),
│  │  │                            ChatInputManager
│  │  ├─ data                      SuggestionRepository (SQLite persistence, voting records), models
│  │  └─ gui                       SuggestionGui (public), AdminSuggestionGui (admin), Menu, MenuListener,
│  │                               ClickGuard, Items, SuggestionFormat
│  └─ util                         Messages
├─ custom                          original Legendary Additions items
├─ forge                           Legendary Creator: Ability (every ability), LegendaryItems (build/recognise),
│  │                               LegendaryService (cache + DB thread), AbilityListener (runs the abilities)
│  ├─ data                         LegendaryDef, LegendaryRepository (SQLite, legendaries.db)
│  └─ gui                          LegendaryCreatorGui
└─ shopadmin                       /shop admin: ShopAdminCommand, ShopAdminGui, FoliaShopStore (shops.yml), ShopBuyCommand (item prices)
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
| `/suggestionadmin` | Open the admin suggestion backend (the only way in) | `admindimension.suggestions.admin` |
| `/legendary_additions give <player> <item>` | Original Legendary Additions items and every legendary made in the Legendary Creator | `legendaryadditions.give` |
| `/shop admin` or `/shopadmin` | Open the shop editor (needs FoliaShop) | `legendaryadditions.shopadmin` |

- The plugin does not add a `/teleport` command. Vanilla `/teleport`, the alias of `/tp`, still exists and is unrelated to this plugin.
- `[player]` is optional for players. From the console, it is required.
- Tab completion works for `/admin return`, `/wolfrod shot` and player names.

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
| `legendaryadditions.shopadmin` | op | `/shop admin`, `/shopadmin` |

**Using a rod needs no permission.** Anyone holding a genuine rod can use it, including players with no permissions at all. That means an admin can give or drop rods to normal players. The permissions above only control *getting* rods through the commands. Every use is still checked against the rod's signature, so players can't make their own rods.

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
| Orbital Strike (stab) | A column of TNT-strength explosions at the target from the build limit (y=319) all the way down to bedrock (y=-64), one every 2 blocks (192 in the Overworld), drilling a shaft to bedrock. Like the Law-Nuke the explosions are created directly (no TNT entities), so the whole column goes off the instant the rod is used. Spacing, explosions per layer, power, an optional delay (`fuse-ticks`) and a top-down ripple (`blocks-per-tick`) are in `config.yml` (`orbital-strike`); `orbital-strike.style: crater` brings back the old crater strike. |
| Nuke Shot | The Unstable SMP nuke, copied from the Unstable SMP orbital strike datapack. 1169 primed TNT (48, 96, 118, 132, 142, 150, 154, 162 and 166 per ring, plus one in the centre) appear 70 blocks above the target with an 80-tick fuse and are pushed outwards so that, while they fall, air drag spreads them into 9 rings from 9.8 to 81 blocks. They land about 4 seconds later and all explode together. The ring radii, TNT per ring, height, fuse and power are in `config.yml`; `nuke.style: crater` brings back the old instant crater nuke. |
| Law-Nuke Shot | 140 TNT are summoned 70 blocks above the target, like the Nuke, and pushed outwards so that while they fall they spread evenly over a 45-block radius; they explode on the ground about 4 seconds later. Count, radius, power, height, fuse and fire are in `config.yml` (`law-nuke`). |
| Wither Nuke Shot | 160 charged wither skulls rain from 70 blocks above the target, released 20 per tick. |
| Wolf Rod | 53 wolves with wolf armor, Strength II, Regeneration, Speed II and Fire Resistance, tamed to the caster. They can be made temporary. |
| Arrow Rod | A sphere of arrows (radius 12) around the target, every arrow flying straight at its centre, in 3 waves half a second apart (400 arrows per wave spread evenly over the whole sphere; damage 70 for the first wave and 80 after). Each wave is turned slightly to fill the gaps of the one before. The player who used the rod is the owner of every arrow, so hits and kills are theirs, and the arrows never hurt them (`damage-owner: false`). Parts of the sphere inside solid blocks (under the ground) are skipped. |
| Admin Teleport Rod | Moves you to the nearest safe spot at or near the target: solid ground, two blocks of headroom, inside the world border, no lava (configurable), no void. If there is none, the teleport is cancelled and you are told why. |

**Sounds and particles are off by default.** `rod-effects.sounds` and `rod-effects.particles` in `config.yml` control every extra sound and particle the rods make: warning markers, beams, charge-up sounds, smoke, shockwave rings, teleport sparkles, and the break sound of used-up rods. Set either to `true` to bring them back. With both off:
- Rods still do their real effect.
- Vanilla explosions, wither skulls and arrows still make their normal vanilla sounds.
- There is no target warning: `warning-time-ticks` defaults to `0`, so strikes land the moment you cast.
- The Stab and the Law-Nuke explosions and the Nuke/Law-Nuke TNT are vanilla explosions, so they make the normal explosion sound and particles. With `orbital-strike.style: crater`, the old stab removes blocks silently instead.
- Old config files are upgraded automatically on startup (`config-version: 5`): the Stab goes off instantly, the Law-Nuke drops TNT from the sky, the Arrow Rod fires the arrow sphere, and the Nuke uses the Unstable SMP rings.
- **TNT limit:** Paper and Folia only let `max-tnt-per-tick` primed TNT (in `spigot.yml`, default 100) move and count down each tick; the rest freeze, which would leave most of a big nuke hanging in the sky. So when a strike needs more TNT than that limit, it spawns falling TNT blocks instead. They fall with exactly the same gravity and air drag as primed TNT, are not limited, and explode like TNT when they land or when their fuse runs out. Set `max-tnt-per-tick` to 2000 or more if you want real primed TNT for the Nuke and Law-Nuke.

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

**Admin backend** (`/suggestionadmin` only):
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

## Legendary Creator

Open `/suggestionadmin` and click **Legendary Creator** (the nether star, slot 52). You need `admindimension.suggestions.admin`.

- **List**: every custom legendary. Left click to edit, right click to give yourself one, shift + right click to delete. **Create New Legendary** asks for an id in chat (for example `storm_blade`). The new item starts as the material in your hand.
- **Editor**:
  - **Name**: type it in chat. Colour codes such as `&6&l` work.
  - **Material**: left click uses the item in your hand; right click lets you type one (for example `netherite_axe`).
  - **Lore**: add lines, remove the last line, or clear.
  - **Enchantments**: every enchantment in the game, at any level up to 255. Left +1, right -1, shift + left +10, shift + right removes it.
  - **Abilities**: the list below. Left +1, right -1, shift + left sets the max level, shift + right removes it.
  - Toggles for **Unbreakable** and **Enchant Glow**, and **Custom Model Data** for resource packs.
  - **Save** writes the item to `plugins/LegendaryAdditions/legendaries.db` (SQLite). **Give Yourself One** and **Discard Changes** are next to it.
- Items only carry their id. Their abilities are always read from the saved definition, so editing a legendary changes every copy players already have, and deleting one turns its abilities off.
- Give one to anyone with `/legendary_additions give <player> <id>`. That command also works from the console and from shop purchases.

| Ability | Max | What it does |
|---|---|---|
| Vein Miner | 5 | Mining an ore also mines every connected ore of the same type (16 per level). |
| Tree Capitator | 5 | Chopping a log fells the whole trunk of that log type (64 logs per level). |
| Area Miner | 3 | Mines 3×3, 5×5 or 7×7 facing where you mine (only blocks the tool is right for). |
| Auto Smelt | 1 | Drops come out smelted, using the server's furnace recipes (raw iron to iron ingot, sand to glass, logs to charcoal...). |
| Telekinesis | 1 | Block drops, mob drops and mob XP go straight into your inventory. |
| Replant | 1 | Harvesting a fully grown wheat, carrot, potato, beetroot or nether wart replants it. |
| Wisdom | 5 | +50% XP per level from blocks and mobs. |
| Magnet | 5 | Pulls dropped items to you (4 blocks per level). |
| Lifesteal | 5 | Heals you for 5% of the damage you deal per level. |
| Critical Strike | 5 | 10% chance per level to deal double damage. |
| Thunderlord | 5 | 10% chance per level for a lightning strike that adds extra damage. |
| Venom / Withering / Frost / Ignite | 5 | Poison, wither, slowness + freezing, or fire on what you hit. |
| Beheading | 5 | 10% chance per level that a kill drops the victim's head. |
| Explosive Arrows | 5 | Arrows and bolts from this bow or crossbow explode where they land, without breaking blocks. |
| Dash | 5 | Right click to dash forward. |
| Featherweight | 1 | No fall damage while held or worn. |
| Soulbound | 1 | Kept in your inventory when you die. |
| Speed, Haste, Strength, Resistance, Jump Boost, Regeneration, Night Vision, Water Breathing, Fire Resistance, Saturation, Dolphin's Grace | 1–5 | That effect while the item is held in either hand or worn. |

Blocks broken by Vein Miner, Tree Capitator and Area Miner go through the normal block-break event one by one, so claim and protection plugins still apply to them.

## Shop admin (`/shop admin`)

Needs [FoliaShop](https://modrinth.com/plugin/foliashop) (the `/shop` plugin) and `legendaryadditions.shopadmin`. `/shop admin` is caught before FoliaShop sees it and opens the same GUI as `/shopadmin`.

- **Main screen**: every FoliaShop category, **Search Shop Items** (type a name, id or material; it searches every category), and **Reload FoliaShop**.
- **Category screen**: every item with its buy and sell price, amount and slot. Left click an item to change its prices; shift + right click removes it (after a confirmation). **Add Item** offers:
  - **Custom Legendaries** from the Legendary Creator.
  - **Built-in Legendaries**: Drill, Paxel, Embershade and Tidefire Crossbow.
  - **Search Vanilla Items**: type part of a name (`diamond`, `oak log`) and pick from the results.
  - **Item In Your Hand**: adds exactly the held item (through FoliaShop's own `addheld` command when the price is money only).
- **Prices: money, items, or both.** First type the money price in chat as `<buy> <sell> [amount]`, for example `100 25 16` (buy `0` = the item costs only items; sell `0` = players can't sell it back). Then the **Price** screen opens: click items in your own inventory to add them to the price (left click = the whole stack, right click = one; your items are not taken). On a price item, left click = +1, right click = -1, shift + right click = remove. **Save Price** saves it. Left clicking an item in a category opens the same two steps, so you can add or change item prices on existing items too.
- **How item prices work:** FoliaShop itself can only charge money or XP. An item-priced entry keeps its money price in FoliaShop (or a free 0 XP price when it costs only items) and, instead of FoliaShop giving the item, runs `[console] la_shopbuy %player% <category> <id>`. That command takes the items and gives the product; if the buyer doesn't have them, nothing is taken and the money part is refunded through Vault. The item price is listed in the item's lore in `/shop` ("Also costs: 2x Diamond"). Items that are FoliaShop saved-items, BuffedItems or spawners can't have an item price.
- Changes are written to `plugins/FoliaShop/shops.yml` and FoliaShop is reloaded (`foliashop reload`), so they show in `/shop` straight away.
- Legendaries are sold as FoliaShop command products (`give-item: false` + `[console] legendary_additions give %player% <id>`). FoliaShop only uses `name` and `lore` for the menu, so this is what gives buyers the real item with its abilities. Saving a legendary in the creator also updates its shop entries (name, lore, material, enchantments).

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

- **In-game bot test** (CI job `ingame-rod-test`, script `.github/scripts/ingame-rod-test.sh`): a real Folia 26.1.2 server with FoliaShop and this repository's `shops.yml`, and a mineflayer bot that joins as a player and:
  - casts every rod: the Stab kills the target within 1 second without spawning TNT and drills to bedrock; the Nuke drops 1169 TNT whose blasts kill the target and leave a hole on the first rings; the Law-Nuke drops 140 TNT from the sky that kill the target; the Arrow Rod fires 300+ arrows whose owner is the caster (the server logs "ArrowTarget was shot by RodTester") and that do not hurt the caster; the other rods do their effect;
  - builds a legendary in the Legendary Creator by clicking the GUI and typing in chat (netherite pickaxe, Vein Miner, Tree Capitator, Auto Smelt, Telekinesis, Efficiency 10), saves it, and the script checks the row in `legendaries.db`;
  - mines a 12-block iron vein with it (all of it breaks and 12 iron ingots land in the inventory) and chops a 10-log trunk (all of it falls, as charcoal); a plain netherite pickaxe breaks only one block;
  - adds that legendary and a diamond block to the Legendary shop through `/shop admin`, searches for it, checks `shops.yml`, and sees it in FoliaShop's own `/shop search`;
  - adds a Heart of the Sea priced at 2 diamonds (no money) using the Price screen, buys it in FoliaShop's `/shop search` (the 2 diamonds are taken and the heart is given), then tries again without diamonds (refused, nothing given);
  - checks the permission rules (a non-op can use a rod but can't get one).

Not tested automatically:
- Buying with **money** in `/shop`, and the money refund when a buyer lacks the items: the test server has no economy (Vault) plugin.
- The combat, movement and passive abilities (everything in the table after Magnet).
- Other plugins' protection of blocks broken by the mining abilities.

Test those on your server before relying on them.
