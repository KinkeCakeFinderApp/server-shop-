// CI-only in-game test: joins the offline-mode test server as a real player, gets every rod,
// casts it at a target and checks that something actually happened.
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const results = []
const chat = []
const spawned = []
const actionBars = []
const gone = new Set()
let bot

const sleep = ms => new Promise(r => setTimeout(r, ms))
const log = (...a) => console.log('[bot]', ...a)
function record (name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`RESULT ${ok ? 'PASS' : 'FAIL'} ${name}: ${detail}`)
}

async function cmd (text, wait = 800) {
  bot.chat('/' + text)
  await sleep(wait)
}

function heldRod () {
  const item = bot.heldItem
  return item && item.name === 'fishing_rod' ? item : null
}

function rodCount () {
  return bot.inventory.items().filter(i => i.name === 'fishing_rod').reduce((n, i) => n + i.count, 0)
}

async function equipRod () {
  const rod = bot.inventory.items().find(i => i.name === 'fishing_rod')
  if (!rod) return false
  await bot.equip(rod, 'hand')
  await sleep(300)
  return !!heldRod()
}

function nearest (name, from, radius) {
  return Object.values(bot.entities).filter(e => e.name === name && e.position.distanceTo(from) <= radius)
}

async function reset () {
  await cmd('kill @e[type=!player]', 600)
  await cmd('clear', 400)
  await cmd('effect clear @s', 200)
  // Put the flat ground back so craters from earlier casts don't change the next test.
  await cmd('fill -24 -60 -16 30 -50 16 air', 300)
  await cmd('fill -24 -63 -16 30 -62 16 dirt', 300)
  await cmd('fill -24 -61 -16 30 -61 16 grass_block', 300)
  await cmd('tp @s 0.5 -60 0.5 -90 0', 1500) // flat world surface, facing east (+x)
}

async function cast () {
  chat.length = 0
  spawned.length = 0
  actionBars.length = 0
  bot.activateItem()
  await sleep(150)
  bot.deactivateItem()
}

// Summons a husk (does not burn in daylight) 10 blocks east and aims at it.
async function aimAtZombie () {
  await cmd('summon husk 10.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Health:20f}', 1200)
  const z = nearest('husk', new Vec3(10.5, -60, 0.5), 3)[0]
  await bot.lookAt(new Vec3(10.5, -59, 0.5), true)
  await sleep(400)
  return z
}

async function aimAtGround (dx) {
  await bot.lookAt(new Vec3(bot.entity.position.x + dx, -60.2, bot.entity.position.z), true)
  await sleep(400)
}

async function shotRod (command, check) {
  await reset()
  await cmd(command, 1200)
  if (!(await equipRod())) {
    record(command, false, 'no fishing rod was given')
    return
  }
  const before = rodCount()
  try {
    await check(before)
  } catch (e) {
    record(command, false, 'exception ' + e.message)
  }
}

async function run () {
  await sleep(6000) // the server script ops us when it sees the join
  await cmd('gamemode survival', 500)
  await cmd('time set day', 200)

  // Stab: a column of primed TNT from the build limit (y=319) down to bedrock at the target.
  // The top goes off after 1 s and the blast runs down the column, so it must drill to bedrock.
  await shotRod('stabshot', async before => {
    const z = await aimAtZombie()
    if (!z) { record('stabshot', false, 'test husk did not spawn'); return }
    await cast()
    await sleep(700)
    const column = Object.values(bot.entities).filter(e => e.name === 'tnt' &&
      Math.abs(e.position.x - 10.5) < 0.6 && Math.abs(e.position.z - 0.5) < 0.6)
    record('stabshot TNT column', column.length >= 5,
      `primed TNT in the column near the bot: ${column.length}, ys=${column.map(e => Math.round(e.position.y)).sort((p, q) => p - q).slice(0, 12)}`)
    record('stabshot report', chat.some(m => /Stab launched: \d+ TNT from y=319 down to bedrock \(y=-64\)/.test(m)), JSON.stringify(chat))
    await sleep(4000)
    const dead = gone.has(z.id)
    record('stabshot', dead, `husk ${dead ? 'killed' : 'still alive'}`)
    record('stabshot reusable', rodCount() === before, `rods ${before}->${rodCount()}`)
    const shaft = [-61, -62, -63, -64].map(y => {
      const b = bot.blockAt(new Vec3(10, y, 0))
      return { y, block: b && b.name }
    })
    record('stabshot drills to bedrock', shaft.slice(0, 3).every(h => h.block === 'air') && shaft[3].block === 'bedrock',
      JSON.stringify(shaft))
  })

  // Nuke: Unstable SMP rings - 1169 TNT dropped 70 blocks above the target spread into 9 rings
  // (9.8..81 blocks) and explode together. Cast from 70 blocks away.
  await reset()
  await cmd('nukeshot', 1200)
  if (!(await equipRod())) {
    record('nukeshot', false, 'no fishing rod was given')
  } else {
    await cmd('tp @s -59.5 -60 0.5 -90 0', 2500)
    const before = rodCount()
    const z = await aimAtZombie()
    await cast()
    await sleep(1500)
    const tnt = spawned.filter(n => n === 'tnt').length
    // The TNT spawns 70 blocks up, mostly outside this bot's entity view, so only require that some was seen.
    record('nukeshot drops TNT', tnt >= 1, `primed TNT seen=${tnt}`)
    record('nukeshot report', chat.some(m => m.includes('1169 TNT in 9 rings')), JSON.stringify(chat))
    await sleep(6500)
    const dead = z && gone.has(z.id)
    record('nukeshot', !!dead, `husk at the centre ${dead ? 'killed' : 'still alive'}`)
    record('nukeshot reusable', rodCount() === before, `rods ${before}->${rodCount()}`)
    // One TNT of every ring sits on the +x axis from the target (angle 0), so each ring leaves a hole there.
    const holes = [0, 9.8, 19.7, 28.9].map(r => {
      const b = bot.blockAt(new Vec3(Math.floor(10.5 + r), -61, 0))
      return { r, block: b && b.name }
    })
    record('nukeshot rings', holes.every(h => h.block === 'air'), JSON.stringify(holes))
    const between = bot.blockAt(new Vec3(10 + 15, -61, 0))
    log('nuke surface sample between rings', between && between.name)
    await cmd('tp @s 0.5 -60 0.5 -90 0', 2500)
  }

  await legendaryCreator()
  await abilities()
  await shopAdmin()

  // A player without any permission can use a rod they were handed, but cannot get new ones.
  await reset()
  await cmd('teleportshot', 1200)
  await equipRod()
  await cmd('deop RodTester', 1500)
  chat.length = 0
  await cmd('stabshot', 1000)
  await cmd('stab', 1000)
  const denied = !chat.some(m => m.includes('You received'))
  record('non-op cannot obtain rods', denied && rodCount() === 1, `rods=${rodCount()} chat=${JSON.stringify(chat)}`)
  await aimAtGround(15)
  const from = bot.entity.position.clone()
  await cast()
  await sleep(2500)
  const moved = bot.entity.position.distanceTo(from)
  record('non-op can use a rod', moved > 8, `moved ${moved.toFixed(1)} blocks; chat=${JSON.stringify(chat)}`)

  finish()
}


// ------------------------------------------------------------------ GUI helpers

function nextWindow (timeout = 6000) {
  return new Promise(resolve => {
    const handler = w => { clearTimeout(timer); resolve(w) }
    const timer = setTimeout(() => { bot.removeListener('windowOpen', handler); resolve(null) }, timeout)
    bot.once('windowOpen', handler)
  })
}

function title (w) { return w ? JSON.stringify(w.title) : 'no window' }

// Clicks a slot of the open GUI; resolves with the next window the server opens (or null).
async function click (slot, button = 0, mode = 0, expectWindow = true) {
  const next = expectWindow ? nextWindow() : null
  await bot.clickWindow(slot, button, mode)
  const w = expectWindow ? await next : null
  await sleep(350) // the GUI ignores clicks closer than 200 ms apart
  return w
}

function findSlot (w, text) {
  if (!w) return -1
  for (let i = 0; i < w.inventoryStart; i++) {
    const item = w.slots[i]
    if (item && JSON.stringify(item).includes(text)) return i
  }
  return -1
}

// Types the answer to a chat question and waits for the GUI that opens afterwards.
async function answer (text) {
  const next = nextWindow()
  bot.chat(text)
  const w = await next
  await sleep(350)
  return w
}

function count (name) {
  return bot.inventory.items().filter(i => i.name === name).reduce((n, i) => n + i.count, 0)
}

// ------------------------------------------------------------------ Legendary Creator

async function legendaryCreator () {
  await reset()
  chat.length = 0
  try {
    const opening = nextWindow()
    bot.chat('/suggestionadmin')
    let w = await opening
    await sleep(400)
    w = await click(52)
    record('creator opens from /suggestionadmin', title(w).includes('Legendary Creator'), title(w))
    w = await click(49, 0, 0, false)
    await sleep(600)
    w = await answer('testpick')
    record('creator new draft', title(w).includes('Editing: testpick'), title(w) + ' chat=' + JSON.stringify(chat))
    w = await click(20, 1, 0, false) // right click: type a material in chat
    w = await answer('netherite_pickaxe')
    w = await click(23)
    record('creator ability screen', title(w).includes('Abilities'), title(w))
    for (const name of ['Vein Miner', 'Tree Capitator', 'Auto Smelt', 'Telekinesis']) {
      const slot = findSlot(w, name)
      if (slot < 0) { record('creator ability ' + name, false, 'not in GUI'); continue }
      w = await click(slot)
    }
    w = await click(45)
    w = await click(22)
    const eff = findSlot(w, 'Efficiency')
    if (eff >= 0) w = await click(eff, 0, 1) // shift + left = +10
    record('creator enchantment screen', eff >= 0, title(w))
    w = await click(45)
    chat.length = 0
    w = await click(49) // save
    await sleep(800)
    record('creator saves', chat.some(m => m.includes("Saved legendary 'testpick'")), JSON.stringify(chat))
    const before = count('netherite_pickaxe')
    await click(51, 0, 0, false) // give yourself one
    await sleep(600)
    record('creator gives the item', count('netherite_pickaxe') === before + 1, `pickaxes ${before}->${count('netherite_pickaxe')}`)
    bot.closeWindow(bot.currentWindow || w)
  } catch (e) {
    record('legendary creator', false, 'exception ' + e.stack)
  }
}

// ------------------------------------------------------------------ abilities of the new item

async function abilities () {
  try {
    await reset()
    await cmd('gamemode survival', 400)
    await cmd('legendary_additions give RodTester testpick', 1000)
    const pick = bot.inventory.items().find(i => i.name === 'netherite_pickaxe')
    if (!pick) { record('abilities', false, 'console give did not hand out testpick'); return }
    record('give command hands out custom legendary', true, 'netherite_pickaxe received')
    await bot.equip(pick, 'hand')
    // mineflayer cannot read 26.1 enchantment data when it works out dig time; the tool breaks these instantly anyway.
    // Iron ore is instant for this pickaxe; a log with a pickaxe takes about 3 seconds.
    bot.digTime = block => (block && block.name.endsWith('_log') ? 3500 : 150)

    // Vein Miner (level 1 = up to 16 extra blocks) + Auto Smelt + Telekinesis: a 12 block iron vein, mine one block.
    await cmd('fill 2 -60 -1 3 -59 1 iron_ore', 600)
    const ingotsBefore = count('iron_ingot')
    await bot.dig(bot.blockAt(new Vec3(2, -60, 0)), true)
    await sleep(1200)
    let left = 0
    for (let x = 2; x <= 3; x++) for (let y = -60; y <= -59; y++) for (let z = -1; z <= 1; z++) {
      const b = bot.blockAt(new Vec3(x, y, z))
      if (b && b.name === 'iron_ore') left++
    }
    record('vein miner', left === 0, `iron ore left=${left} of 12`)
    const ingots = count('iron_ingot') - ingotsBefore
    record('auto smelt + telekinesis', ingots >= 12 && count('raw_iron') === 0, `iron ingots in inventory=${ingots}, raw iron=${count('raw_iron')}`)

    // Tree Capitator: a 10 log trunk, chop the bottom log.
    await cmd('fill 2 -60 3 2 -51 3 oak_log', 600)
    const charcoalBefore = count('charcoal')
    await bot.dig(bot.blockAt(new Vec3(2, -60, 3)), true)
    await sleep(1500)
    let logs = 0
    for (let y = -60; y <= -51; y++) {
      const b = bot.blockAt(new Vec3(2, y, 3))
      if (b && b.name === 'oak_log') logs++
    }
    record('tree capitator', logs === 0, `logs left=${logs} of 10`)
    record('auto smelt logs to charcoal', count('charcoal') - charcoalBefore >= 10, `charcoal=${count('charcoal') - charcoalBefore}`)

    // A plain netherite pickaxe must not vein mine.
    await cmd('clear', 300)
    await cmd('give @s netherite_pickaxe', 600)
    await bot.equip(bot.inventory.items().find(i => i.name === 'netherite_pickaxe'), 'hand')
    await cmd('fill 2 -60 -1 3 -59 1 iron_ore', 600)
    await bot.dig(bot.blockAt(new Vec3(2, -60, 0)), true)
    await sleep(800)
    let plain = 0
    for (let x = 2; x <= 3; x++) for (let y = -60; y <= -59; y++) for (let z = -1; z <= 1; z++) {
      const b = bot.blockAt(new Vec3(x, y, z))
      if (b && b.name === 'iron_ore') plain++
    }
    record('plain pickaxe has no abilities', plain === 11, `iron ore left=${plain} (expected 11)`)
  } catch (e) {
    record('abilities', false, 'exception ' + e.stack)
  }
}

// ------------------------------------------------------------------ /shop admin with FoliaShop

async function shopAdmin () {
  try {
    await reset()
    chat.length = 0
    let opening = nextWindow()
    bot.chat('/shop admin')
    let w = await opening
    await sleep(400)
    record('/shop admin opens GUI', title(w).includes('Shop Admin'), title(w) + ' chat=' + JSON.stringify(chat))
    const legendary = findSlot(w, 'ID: legendary')
    if (legendary < 0) { record('shop admin categories', false, 'legendary category not listed'); return }
    w = await click(legendary)
    record('shop admin category', title(w).includes('Admin'), title(w))
    w = await click(49)
    w = await click(10)
    const pickSlot = findSlot(w, 'testpick')
    record('shop admin lists custom legendaries', pickSlot >= 0, title(w))
    w = await click(pickSlot < 0 ? 0 : pickSlot, 0, 0, false)
    await sleep(500)
    chat.length = 0
    w = await answer('50000 0')
    record('shop admin price screen', title(w).includes('Price: items + money'), title(w))
    w = await click(49) // Save Price (money only)
    await sleep(800)
    record('shop admin adds legendary', chat.some(m => m.includes('Added testpick to legendary')), JSON.stringify(chat))

    // Add a vanilla item through the item search.
    w = await click(49)
    w = await click(14, 0, 0, false)
    await sleep(500)
    w = await answer('diamond_block')
    const block = findSlot(w, 'diamond_block')
    record('shop admin vanilla search', block >= 0, title(w))
    w = await click(block < 0 ? 0 : block, 0, 0, false)
    await sleep(500)
    chat.length = 0
    w = await answer('900 300 1')
    w = await click(49)
    await sleep(800)
    record('shop admin adds vanilla item', chat.some(m => m.includes('Added diamond_block')), JSON.stringify(chat))

    // Item price: a Heart of the Sea that costs 2 diamonds and no money.
    await cmd('give @s diamond 5', 800)
    w = await click(49)
    w = await click(14, 0, 0, false)
    await sleep(500)
    w = await answer('heart_of_the_sea')
    const heart = findSlot(w, 'heart_of_the_sea')
    w = await click(heart < 0 ? 0 : heart, 0, 0, false)
    await sleep(500)
    w = await answer('0 0 1')
    let dia = -1
    for (let i = w ? w.inventoryStart : 0; w && i < w.slots.length; i++) if (w.slots[i] && w.slots[i].name === 'diamond') dia = i
    record('price screen shows own inventory', dia >= 0, title(w))
    w = await click(dia, 1) // right click = add one diamond to the price
    w = await click(dia, 1)
    const costSlot = findSlot(w, 'Costs 2x')
    record('price screen adds items', costSlot >= 0, JSON.stringify(w && w.slots[0]))
    chat.length = 0
    w = await click(49)
    await sleep(800)
    record('shop admin adds item-priced item', chat.some(m => m.includes('Added heart_of_the_sea')), JSON.stringify(chat))
    record('item price does not take the admin\'s items', count('diamond') === 5, `diamonds=${count('diamond')}`)
    bot.closeWindow(bot.currentWindow || w)
    await sleep(1500)

    // Buy it in FoliaShop itself (left click = buy): the 2 diamonds must be taken and the heart given.
    const buyHeart = async () => {
      const open = nextWindow()
      bot.chat('/shop search heart')
      const sw = await open
      await sleep(500)
      const slot = findSlot(sw, 'Also costs')
      if (slot < 0) return 'not found in ' + title(sw)
      chat.length = 0
      await click(slot, 0, 0, false)
      await sleep(1500)
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      await sleep(300)
      return 'ok'
    }
    let bought = await buyHeart()
    record('buy with item price', bought === 'ok' && count('heart_of_the_sea') === 1 && count('diamond') === 3,
      `${bought} hearts=${count('heart_of_the_sea')} diamonds=${count('diamond')} chat=${JSON.stringify(chat)}`)
    await cmd('clear @s diamond', 600)
    bought = await buyHeart()
    record('item price refused without the items', count('heart_of_the_sea') === 1 && chat.some(m => m.includes('You also need 2x Diamond')),
      `${bought} hearts=${count('heart_of_the_sea')} chat=${JSON.stringify(chat)}`)

    // Search across every shop category.
    opening = nextWindow()
    bot.chat('/shop admin')
    w = await opening
    await sleep(400)
    w = await click(47, 0, 0, false)
    await sleep(500)
    w = await answer('testpick')
    record('shop admin search', title(w).includes('Search: testpick (1)'), title(w))
    bot.closeWindow(bot.currentWindow || w)
    await sleep(1500)

    // FoliaShop itself must now show the new entry (it reloaded shops.yml).
    opening = nextWindow()
    bot.chat('/shop search testpick')
    w = await opening
    await sleep(500)
    const shown = w ? w.slots.slice(0, w.inventoryStart).filter(i => i && i.name === 'netherite_pickaxe').length : 0
    record('FoliaShop shows the added legendary', shown >= 1, title(w) + ` netherite pickaxes shown=${shown}`)
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  } catch (e) {
    record('shop admin', false, 'exception ' + e.stack)
  }
}

function finish () {
  const failed = results.filter(r => !r.ok)
  console.log(`BOT DONE: ${results.length - failed.length}/${results.length} passed`)
  bot.quit()
  setTimeout(() => process.exit(failed.length ? 1 : 0), 500)
}

bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'RodTester', auth: 'offline' })
bot.on('messagestr', (m, position) => {
  if (position === 'game_info') { if (!actionBars.includes(m)) actionBars.push(m); return }
  chat.push(m); log('chat:', m)
})
bot.on('actionBar', m => { const t = m.toString(); actionBars.push(t); log('action bar:', t) })
bot.on('entityDead', e => gone.add(e.id))
bot.on('entityGone', e => gone.add(e.id))
bot.on('entitySpawn', e => { if (e !== bot.entity) spawned.push(e.name) })
bot.on('kicked', r => { console.log('KICKED', JSON.stringify(r)); process.exit(2) })
bot.on('error', e => { console.log('ERROR', e); process.exit(2) })
bot.once('spawn', () => {
  log('spawned at', bot.entity.position, 'version', bot.version)
  run().catch(e => { console.log('RUN FAILED', e); process.exit(3) })
})
setTimeout(() => { console.log('TIMEOUT'); process.exit(4) }, 420000)
