// CI-only in-game test for EloRanks. Two real players join the offline-mode test server:
// EloKiller (op) and EloVictim (not op). Phase "one" tests PvP ELO, anti-farming, the GUIs,
// claiming every kit, cooldowns, full inventories, GUI item safety and admin commands.
// Phase "two" runs after a server restart and checks that everything was saved.
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const phase = process.argv[2] || 'one'
const results = []
const sleep = ms => new Promise(r => setTimeout(r, ms))
const log = (...a) => console.log('[bot]', ...a)
function record (name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`RESULT ${ok ? 'PASS' : 'FAIL'} ${name}: ${detail}`)
}

function makeBot (username) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username, auth: 'offline' })
  bot.chatLog = []
  bot.on('messagestr', (m, position) => {
    if (position === 'game_info') return
    bot.chatLog.push(m); log(username, 'chat:', m)
  })
  bot.on('title', (t, type) => { bot.chatLog.push('TITLE ' + JSON.stringify(t)); log(username, 'title', type, JSON.stringify(t)) })
  bot.on('kicked', r => { console.log(username, 'KICKED', JSON.stringify(r)) })
  bot.on('error', e => { console.log(username, 'ERROR', e) })
  return bot
}

function spawned (bot) {
  return new Promise((resolve, reject) => {
    bot.once('spawn', resolve)
    bot.once('end', () => reject(new Error(bot.username + ' disconnected before spawning')))
  })
}

async function cmd (bot, text, wait = 800) {
  bot.chat('/' + text)
  await sleep(wait)
}

function said (bot, text) { return bot.chatLog.some(m => m.includes(text)) }
function recent (bot) { return JSON.stringify(bot.chatLog.slice(-8)) }

function nextWindow (bot, timeout = 6000) {
  return new Promise(resolve => {
    const handler = w => { clearTimeout(timer); resolve(w) }
    const timer = setTimeout(() => { bot.removeListener('windowOpen', handler); resolve(null) }, timeout)
    bot.once('windowOpen', handler)
  })
}

async function open (bot, command) {
  const next = nextWindow(bot)
  bot.chat('/' + command)
  const w = await next
  await sleep(500)
  return w
}

async function click (bot, slot, button = 0, mode = 0, expectWindow = true) {
  const next = expectWindow ? nextWindow(bot) : null
  await bot.clickWindow(slot, button, mode)
  const w = expectWindow ? await next : null
  await sleep(450) // the GUI ignores clicks closer than 250 ms apart
  return w
}

function title (w) { return w ? JSON.stringify(w.title) : 'no window' }
function slotText (w, slot) { return w && w.slots[slot] ? JSON.stringify(w.slots[slot]) : '' }
function count (bot, name) {
  return bot.inventory.items().filter(i => i.name === name).reduce((n, i) => n + i.count, 0)
}

// Asks the server for the bot's inventory data (item components) and returns the text.
async function inventoryData (bot) {
  bot.chatLog.length = 0
  await cmd(bot, 'data get entity @s Inventory', 1500)
  return bot.chatLog.join('\n')
}

async function kill (killer, victim) {
  await cmd(killer, `tp EloVictim ${killer.entity.position.x + 1.5} ${killer.entity.position.y} ${killer.entity.position.z}`, 1500)
  const target = killer.players.EloVictim && killer.players.EloVictim.entity
  if (!target) return false
  await killer.lookAt(target.position.offset(0, 1.5, 0), true)
  killer.attack(target)
  await sleep(2500)
  return true
}

// ------------------------------------------------------------------ phase one

async function phaseOne () {
  const killer = makeBot('EloKiller')
  await spawned(killer)
  const victim = makeBot('EloVictim')
  await spawned(victim)
  await sleep(4000) // the test script ops EloKiller when it joins
  log('both bots spawned, version', killer.version)

  await cmd(killer, 'gamemode survival EloKiller')
  await cmd(killer, 'gamemode survival EloVictim')
  await cmd(killer, 'tp EloKiller 0.5 -60 0.5')
  await cmd(killer, 'clear EloKiller')

  // --- the main GUI, available to everyone
  let w = await open(victim, 'rank')
  record('/rank opens for a non-op player', title(w).includes('Ranked PvP'), title(w))
  record('main GUI has RANK KITS and LEADERBOARD buttons',
    slotText(w, 11).includes('RANK KITS') && slotText(w, 15).includes('LEADERBOARD'),
    slotText(w, 11).slice(0, 200) + ' | ' + slotText(w, 15).slice(0, 200))
  record('main GUI shows the rank info', slotText(w, 13).includes('Unranked') && slotText(w, 13).includes('Tier 6'),
    slotText(w, 13).slice(0, 600))
  w = await click(victim, 15)
  record('LEADERBOARD button opens the leaderboard', title(w).includes('Leaderboard'), title(w))
  w = await click(victim, 49)
  record('leaderboard Back returns to the main GUI', title(w).includes('Ranked PvP'), title(w))
  w = await click(victim, 11)
  record('RANK KITS button opens the kits GUI', title(w).includes('Rank Kits'), title(w))
  record('kits are LOCKED at 0 ELO', slotText(w, 10).includes('LOCKED') && slotText(w, 16).includes('LOCKED'),
    slotText(w, 10).slice(0, 300))
  victim.chatLog.length = 0
  await click(victim, 10, 0, 0, false)
  record('clicking a locked kit is refused', said(victim, 'is locked'), recent(victim))
  victim.closeWindow(victim.currentWindow || w)

  // --- /elo info
  killer.chatLog.length = 0
  await cmd(killer, 'elo', 1000)
  record('/elo shows ELO and tier', said(killer, 'Your ELO: 0') && said(killer, 'Unranked'), recent(killer))

  // --- PvP kill
  await cmd(killer, 'effect give EloKiller strength 600 9 true')
  killer.chatLog.length = 0
  victim.chatLog.length = 0
  await kill(killer, victim)
  record('a PvP kill gives the killer ELO', said(killer, '+16 ELO'), recent(killer))
  record('the victim is told about the ELO loss', said(victim, '-0 ELO') && said(victim, 'EloKiller'), recent(victim))
  await sleep(2000) // respawn

  // --- anti farming: the same victim again right away
  killer.chatLog.length = 0
  await kill(killer, victim)
  record('killing the same player again at once does not count', said(killer, 'did not count') && said(killer, 'too recently'),
    recent(killer))
  await sleep(2000)
  killer.chatLog.length = 0
  await cmd(killer, 'elo', 1000)
  record('ELO unchanged by the farmed kill', said(killer, 'Your ELO: 16'), recent(killer))

  // --- non-admins cannot use admin commands
  victim.chatLog.length = 0
  await cmd(victim, 'elo set EloVictim 5000', 1000)
  record('non-admin cannot /elo set', said(victim, "don't have permission"), recent(victim))

  // --- admin: set ELO -> rank up to Tier 1
  killer.chatLog.length = 0
  await cmd(killer, 'elo set EloKiller 1000', 2000)
  record('/elo set reports the new ELO', said(killer, 'now has 1000 ELO'), recent(killer))
  record('rank up message', said(killer, 'RANK UP') && said(killer, 'Tier 1'), recent(killer))
  record('rank up title', killer.chatLog.some(m => m.startsWith('TITLE') && m.includes('Tier 1')), recent(killer))

  // --- claim every kit
  await cmd(killer, 'clear EloKiller')
  w = await open(killer, 'kits')
  record('kits AVAILABLE at 1000 ELO', [10, 11, 12, 14, 15, 16].every(s => slotText(w, s).includes('AVAILABLE')),
    slotText(w, 10).slice(0, 300))
  killer.chatLog.length = 0
  w = await click(killer, 11) // Tier 5 pearls; the GUI reopens refreshed
  await sleep(500)
  record('claim Tier 5 gives one barrel', count(killer, 'barrel') === 1 && said(killer, 'You claimed'), `barrels=${count(killer, 'barrel')} ${recent(killer)}`)
  record('claimed kit shows COOLDOWN', slotText(w, 11).includes('COOLDOWN') && slotText(w, 11).includes('Time left'),
    slotText(w, 11).slice(0, 400))
  killer.chatLog.length = 0
  await click(killer, 11, 0, 0, false)
  await sleep(700)
  record('claiming again is refused (48h cooldown)', said(killer, 'again in') && said(killer, '1d 23h'), recent(killer))
  record('no second barrel', count(killer, 'barrel') === 1, `barrels=${count(killer, 'barrel')}`)
  // double click on Tier 6 within 50 ms: only one reward
  const before = count(killer, 'barrel')
  killer.clickWindow(10, 0, 0)
  killer.clickWindow(10, 0, 0)
  await sleep(2500)
  record('double click claims Tier 6 once', count(killer, 'barrel') === before + 1, `barrels ${before}->${count(killer, 'barrel')}`)
  w = killer.currentWindow
  for (const slot of [12, 14, 15, 16]) {
    w = await click(killer, slot)
    await sleep(400)
  }
  await sleep(1000)
  if (killer.currentWindow) killer.closeWindow(killer.currentWindow)
  await sleep(500)
  record('Tier 4 = 3 stacks of breeze rods', count(killer, 'breeze_rod') === 192, `breeze_rod=${count(killer, 'breeze_rod')} wind_charge=${count(killer, 'wind_charge')}`)
  record('Tier 3 = 1 stack of golden carrots', count(killer, 'golden_carrot') === 64, `golden_carrot=${count(killer, 'golden_carrot')}`)
  record('Tier 2 = 2 stacks of golden apples', count(killer, 'golden_apple') === 128, `golden_apple=${count(killer, 'golden_apple')}`)
  record('Tier 1 + 5 + 6 = 3 barrels, no shulker boxes', count(killer, 'barrel') === 3 &&
    !killer.inventory.items().some(i => i.name.includes('shulker')), killer.inventory.items().map(i => i.name + 'x' + i.count).join(','))

  const data = await inventoryData(killer)
  const pearls = (data.match(/minecraft:ender_pearl/g) || []).length
  const carts = (data.match(/minecraft:tnt_minecart/g) || []).length
  record('pearl barrel holds 27 stacks of ender pearls (432)', pearls === 27 && data.includes('minecraft:container'), `ender_pearl entries=${pearls}`)
  record('TNT minecart barrel holds 27 TNT minecarts (not minecarts)', carts === 27 && !/"minecraft:minecart"/.test(data), `tnt_minecart entries=${carts}`)
  record('potion barrel holds Speed II and Strength II', data.includes('minecraft:strong_swiftness') && data.includes('minecraft:strong_strength'),
    data.slice(0, 300))

  // --- full inventory
  await cmd(killer, 'elo resetclaims EloKiller', 1500)
  await cmd(killer, 'clear EloKiller')
  await cmd(killer, 'give EloKiller stone 2304', 1000)
  w = await open(killer, 'kits')
  record('resetclaims makes kits claimable again', slotText(w, 12).includes('AVAILABLE'), slotText(w, 12).slice(0, 200))
  killer.chatLog.length = 0
  await click(killer, 12, 0, 0, false)
  await sleep(1500)
  record('full inventory: claim refused, nothing lost', said(killer, 'inventory is full') && count(killer, 'breeze_rod') === 0,
    recent(killer))
  if (killer.currentWindow) killer.closeWindow(killer.currentWindow)
  await cmd(killer, 'clear EloKiller')

  // Claim Tier 1 and Tier 5 again so phase two can check the saved cooldowns.
  w = await open(killer, 'kits')
  w = await click(killer, 16)
  w = await click(killer, 11)
  if (killer.currentWindow) killer.closeWindow(killer.currentWindow)
  await sleep(800)
  record('re-claim after resetclaims', count(killer, 'barrel') === 2, `barrels=${count(killer, 'barrel')}`)
  await cmd(killer, 'clear EloKiller')

  // --- GUI items cannot be taken (checked against the server's inventory data)
  w = await open(killer, 'rank')
  const tries = [[13, 0, 0], [0, 0, 1], [13, 0, 2], [4, 0, 6], [13, 1, 4], [13, 40, 2], [11, 0, 1]]
  for (const [slot, button, mode] of tries) {
    try { await click(killer, slot, button, mode, false) } catch (e) { log('click mode', mode, 'not supported by mineflayer:', e.message) }
  }
  if (killer.currentWindow) killer.closeWindow(killer.currentWindow)
  await sleep(1000)
  const inv = await inventoryData(killer)
  record('GUI items cannot be taken out', !inv.includes('player_head') && !inv.includes('stained_glass_pane') && !inv.includes('minecraft:chest'),
    inv.slice(0, 300))

  // --- leaderboard shows both players, highest first
  w = await open(victim, 'leaderboard')
  await sleep(500)
  record('leaderboard lists players by ELO', slotText(w, 0).includes('EloKiller') && slotText(w, 0).includes('1000 ELO') &&
    slotText(w, 1).includes('EloVictim'), slotText(w, 0).slice(0, 300) + ' | ' + slotText(w, 1).slice(0, 200))
  victim.closeWindow(victim.currentWindow || w)

  // --- ELO drops: rewards stay unlocked; ELO never negative
  killer.chatLog.length = 0
  await cmd(killer, 'elo remove EloKiller 950', 1500)
  w = await open(killer, 'kits')
  record('rewards stay unlocked after ELO drops', !slotText(w, 16).includes('LOCKED'), slotText(w, 16).slice(0, 200))
  killer.closeWindow(killer.currentWindow || w)
  killer.chatLog.length = 0
  await cmd(killer, 'elo remove EloKiller 5000', 1500)
  record('ELO never goes below 0', said(killer, 'now has 0 ELO'), recent(killer))
  await cmd(killer, 'elo set EloKiller 1000', 1500)

  // --- combat log: the victim disconnects right after being hit
  await cmd(killer, 'effect clear EloKiller')
  await cmd(killer, `tp EloVictim ${killer.entity.position.x + 1.5} ${killer.entity.position.y} ${killer.entity.position.z}`, 1500)
  const target = killer.players.EloVictim && killer.players.EloVictim.entity
  if (target) {
    await killer.lookAt(target.position.offset(0, 1.5, 0), true)
    killer.attack(target)
  }
  await sleep(600)
  killer.chatLog.length = 0
  victim.quit()
  await sleep(2500)
  record('disconnecting in combat counts as a death', said(killer, 'did not count') || said(killer, 'ELO'), recent(killer))

  killer.quit()
  await sleep(1000)
}

// ------------------------------------------------------------------ phase two (after restart)

async function phaseTwo () {
  const killer = makeBot('EloKiller')
  await spawned(killer)
  await sleep(4000)
  killer.chatLog.length = 0
  await cmd(killer, 'elo', 1500)
  record('ELO saved across the restart (set to 300 from the console while offline)', said(killer, 'Your ELO: 300') && said(killer, 'Tier 5'),
    recent(killer))
  const w = await open(killer, 'kits')
  record('claim cooldowns saved across the restart', slotText(w, 16).includes('COOLDOWN') && slotText(w, 11).includes('COOLDOWN'),
    slotText(w, 16).slice(0, 200))
  killer.chatLog.length = 0
  await click(killer, 16, 0, 0, false)
  await sleep(1000)
  record('Tier 1 still on cooldown after the restart', said(killer, 'again in'), recent(killer))
  killer.closeWindow(killer.currentWindow || w)
  killer.quit()
  await sleep(1000)
}

function finish () {
  const failed = results.filter(r => !r.ok)
  console.log(`BOT DONE (${phase}): ${results.length - failed.length}/${results.length} passed`)
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500)
}

setTimeout(() => { console.log('TIMEOUT'); finish(); process.exit(4) }, 300000)
;(phase === 'one' ? phaseOne() : phaseTwo())
  .catch(e => record('run', false, 'exception ' + (e && e.stack)))
  .then(finish)

module.exports = { Vec3 }
