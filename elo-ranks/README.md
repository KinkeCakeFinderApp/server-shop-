# EloRanks

PvP ELO ranking for **Paper / Folia 26.1.2** (Java 25). Players earn ELO by killing other
players and climb from **Tier 6** to **Tier 1**. Each tier has a reward that the player claims
in a GUI, and each reward has its own 48-hour cooldown. There is a public leaderboard, and the
player's tier is shown next to their name through **LuckPerms**.

## Install

1. Put `EloRanks-<version>.jar` in `plugins/`. LuckPerms is optional but recommended.
2. Start the server. This writes `plugins/EloRanks/config.yml` (fully commented).
3. Edit the config if you want, then run `/elo reload`.

**Build:** `cd elo-ranks && ./gradlew build`. The jar is written to `build/libs/`. You can also
download it from the **EloRanks** artifact of the "Build EloRanks" GitHub Actions run.

## Default tiers

| Tier   | ELO  | Reward |
|--------|------|--------|
| Tier 6 | 100  | 1 barrel of potions: 14× Speed II, 13× Strength II |
| Tier 5 | 280  | 1 full barrel of Ender Pearls (432) |
| Tier 4 | 460  | 3 stacks of Breeze Rods (192) |
| Tier 3 | 640  | 1 stack of Golden Carrots |
| Tier 2 | 820  | 2 stacks of Golden Apples |
| Tier 1 | 1000 | 1 full barrel of TNT Minecarts (27) |

- **Below 100 ELO:** the player is *Unranked*.
- **New players:** start at `elo.starting-elo` (default 0).
- **Config:** the thresholds, names, colours, rewards (items, amounts, potion types and levels),
  the cooldown and every GUI item, slot, text and message can be changed in `config.yml`.

### Barrels

Barrel rewards are real barrels. Their contents are stored in the `minecraft:container` item
data component, the modern form of the old BlockEntityTag NBT, so placing the barrel gives the
items. Shulker boxes are never used. If a reward needs more than 27 stacks, more barrels are
created.

## How it works

### ELO

EloRanks uses standard ELO. The killer gains `k-factor × (1 − expected win chance)`, and the
victim loses the same amount multiplied by `loss-multiplier`. Two equal players trade 16 ELO,
using the default K-factor of 32.

- ELO never goes below 0.
- You can set a minimum ELO, a maximum ELO, and whether players can drop below the starting ELO.

### Which deaths count

- **Direct kills:** a player killed by another player, in melee, with projectiles, or anything
  else the game credits to that player.
- **Credited deaths (`pvp.credit-last-attacker`, default 15s):** a player who dies of anything
  (fall, lava, void…) shortly after being hit by another player counts as killed by that player.
- **Combat logging (`pvp.combat-log`, default 15s):** a player who disconnects shortly after
  being hit counts as killed. Kicks and server shutdowns do not count.
- **Never counted:** suicides, and deaths to mobs or the environment without a recent player hit.

### Anti-farming

When a kill breaks one of these rules, nobody's ELO changes. Each rule can be turned off.

- The same victim can't be killed for ELO again within 10 minutes.
- A killer can count at most 3 kills of the same victim per 24 hours.
- A player can gain at most 400 ELO per 24 hours.
- Kills between two players on the same IP address never count.

### Claiming

- Rewards are never given automatically. The player opens `/kits` and clicks an **AVAILABLE**
  tier to claim it.
- Each tier has its own cooldown per player (`claim-cooldown: 48h`). This cooldown survives
  restarts, reloads and reconnects.
- A tier stays unlocked once reached, even if the player's ELO drops later. It stays unlocked
  until an admin runs `/elo resetprogress`.
- **Kit states in the GUI:**
  - **LOCKED:** shows the required ELO, the player's ELO, and the ELO still needed.
  - **AVAILABLE:** shows *click to claim*.
  - **COOLDOWN:** shows the last claim time, the time left, and when it is next available.
- **Full inventory (`full-inventory`):**
  - `refuse` (default): the claim is refused and the player is told how many slots to free.
  - `pending`: the reward waits under *Pending deliveries* in `/kits`.
  - `drop`: whatever doesn't fit is dropped at the player's feet.

### Duplicate and loss protection

- Only one claim per player can run at a time, and fast repeated clicks are ignored.
- The database re-checks the unlock and the cooldown, saves the claim time, and queues the reward
  in a single transaction. A double click or a race can never give a reward twice.
- The queued reward is deleted only after the items are in the inventory. If the player logs out
  or the server stops mid-claim, the reward is delivered on the player's next join.

### GUIs

- **Screens:** `/rank` opens the main menu. It shows your rank, ELO, next tier, ELO needed, a
  progress bar, and buttons for 🎁 **Rank Kits** and 🏆 **Leaderboard**.
- **Read-only:** no GUI item can be taken out. Shift-clicks, number keys, double clicks,
  off-hand swaps, drops and drags are all blocked.
- **Leaderboard:** sorted by ELO and paged. It is cached and refreshed every
  `leaderboard.refresh-interval` (default 60s) and after admin changes, so opening it never
  queries the database.

### LuckPerms

LuckPerms is changed only through its API, never with console commands.

- **`mode: prefix` (default):** gives the player one prefix, such as `&6[T1] `, at `priority`.
  It only ever removes the prefix it added itself. That prefix is remembered in the meta key
  `eloranks-prefix`, so it is found even after you change the configured prefixes. Other
  prefixes, groups, permissions and meta are never touched.
- **`mode: group`:** keeps the player in the LuckPerms group of their tier (`luckperms.groups`).
  It only adds or removes those groups. Create the groups first.
- **Meta keys for chat and tab plugins:** the tier number is stored in the `eloranks-tier` meta
  key, and the prefix in `eloranks-prefix`. For example, `%luckperms_meta_eloranks-prefix%`.
- **If the prefix doesn't show:** LuckPerms shows the highest-priority prefix. Raise
  `luckperms.priority`, or use LuckPerms' meta stacking to show several prefixes.
- **If LuckPerms is missing:** everything else still works, and a warning is logged.

## Commands

**Player commands:** no permission needed.

| Command | What it does |
|---------|--------------|
| `/rank` | Main ranking GUI |
| `/kits` | Kits GUI |
| `/leaderboard` | Leaderboard GUI |
| `/elo` | Your ELO, tier, next unlock, rewards ready, stats |

**Admin commands:** these work on offline players who have joined before, and from the console.

| Command | Permission |
|---------|------------|
| `/elo set <player> <amount>` | `elo.admin.set` |
| `/elo add <player> <amount>` | `elo.admin.set` |
| `/elo remove <player> <amount>` | `elo.admin.set` |
| `/elo reset <player>`: ELO back to the starting ELO | `elo.admin.reset` |
| `/elo resetprogress <player>`: re-locks rewards above the current ELO | `elo.admin.reset` |
| `/elo wipe <player>`: deletes all of the player's ranking data | `elo.admin.reset` |
| `/elo resetclaims <player>`: clears kit cooldowns only, ELO unchanged | `elo.admin.claimreset` |
| `/elo forcetier <player> <tier>`: sets ELO to that tier's requirement | `elo.admin.forcetier` |
| `/elo reload` | `elo.admin.reload` |

`elo.admin` grants all of them. The permission node names can be changed under
`permissions:` in the config.

## Technical notes

- **Folia:** there is no global scheduler.
  - Player work runs on the player's entity scheduler.
  - The leaderboard refresh runs on the async scheduler.
  - Broadcasts run on the global region scheduler.
- **Database:** SQLite (`plugins/EloRanks/eloranks.db`). All queries run on one dedicated
  database thread, so no player or region thread ever waits on the disk. Every
  check-then-write is serialized.
- **Stored data:**
  - UUID (the key; name changes are harmless), name, ELO, kills, deaths, ELO gained and lost.
  - Unlocked tiers, last claim time per tier, and pending deliveries.
  - Kill history. Only what the anti-farming windows need is kept; it is pruned on start.
- **Config errors:** every invalid config value is logged with its path, what is wrong and how
  to fix it, and a safe default is used instead.
- **Invalid YAML:** on start, EloRanks disables itself with a clear message. On `/elo reload`,
  the old config stays in use.
- **Database failure:** if the database can't be opened, EloRanks disables itself, because
  rankings could not be saved.
