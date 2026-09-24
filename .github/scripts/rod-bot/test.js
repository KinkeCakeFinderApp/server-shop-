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

  // Orbital strike and nuke: must damage a zombie in the target zone.
  for (const command of ['stabshot', 'nukeshot']) {
    await shotRod(command, async before => {
      const z = await aimAtZombie()
      if (!z) { record(command, false, 'test zombie did not spawn'); return }
      await cast()
      await sleep(2500)
      const dead = gone.has(z.id)
      record(command, dead, `husk ${dead ? 'killed' : 'still alive'}; chat=${JSON.stringify(chat)}`)
      record(command + ' reusable', rodCount() === before, `rods ${before}->${rodCount()}`)
      const ground = bot.blockAt(new Vec3(10, -61, 0))
      record(command + ' crater', ground && ground.name === 'air', `block under target is ${ground && ground.name}`)
      record(command + ' action bar', actionBars.some(m => m.includes('hit')), JSON.stringify(actionBars))
    })
  }

  await shotRod('lawnukeshot', async before => {
    const z = await aimAtZombie()
    await cast()
    await sleep(4000)
    const dead = z && gone.has(z.id)
    record('lawnukeshot', !!dead, `husk ${dead ? 'killed' : 'still alive'}; chat=${JSON.stringify(chat)}`)
  })

  await shotRod('withernukeshot', async before => {
    await aimAtGround(12)
    await cast()
    await sleep(1500)
    const skulls = spawned.filter(n => n === 'wither_skull').length
    record('withernukeshot', skulls > 0, `wither skulls seen=${skulls}; chat=${JSON.stringify(chat)}`)
  })

  await shotRod('wolfrod shot', async before => {
    await aimAtGround(6)
    await cast()
    await sleep(1500)
    const wolves = spawned.filter(n => n === 'wolf').length
    record('wolfrod shot', wolves > 0, `wolves spawned=${wolves}; chat=${JSON.stringify(chat)}`)
  })

  await shotRod('arrowrodshot', async before => {
    await aimAtGround(10)
    await cast()
    await sleep(1500)
    const arrows = spawned.filter(n => n === 'arrow').length
    record('arrowrodshot', arrows > 0, `arrows spawned=${arrows}; chat=${JSON.stringify(chat)}`)
  })

  await shotRod('teleportshot', async before => {
    await aimAtGround(15)
    const from = bot.entity.position.clone()
    await cast()
    await sleep(2500)
    const moved = bot.entity.position.distanceTo(from)
    record('teleportshot', moved > 8, `moved ${moved.toFixed(1)} blocks to ${bot.entity.position}; chat=${JSON.stringify(chat)}`)
  })

  // Single-use: the shulker from /wolfrod must contain rods that work and are used up one at a time.
  await reset()
  await cmd('wolfrod', 1200)
  const box = bot.inventory.items().find(i => i.name.endsWith('shulker_box'))
  if (!box) {
    record('wolfrod single-use', false, 'no shulker box was given')
  } else {
    try {
      await bot.equip(box, 'hand')
      await bot.lookAt(new Vec3(-2.5, -60.5, 0.5), true)
      const floor = bot.blockAt(new Vec3(-3, -61, 0))
      await bot.placeBlock(floor, new Vec3(0, 1, 0))
      await sleep(800)
      const placed = bot.blockAt(new Vec3(-3, -60, 0))
      const win = await bot.openContainer(placed)
      const inside = win.containerItems()
      const rodsInside = inside.filter(i => i.name === 'fishing_rod').reduce((n, i) => n + i.count, 0)
      record('shulker contents', rodsInside === 27, `rods in box=${rodsInside}`)
      await win.withdraw(inside[0].type, null, 2)
      win.close()
      await sleep(500)
      await equipRod()
      const before = rodCount()
      await aimAtGround(6)
      await cast()
      await sleep(1500)
      const wolves = spawned.filter(n => n === 'wolf').length
      record('wolfrod single-use', wolves > 0, `wolves spawned=${wolves}; chat=${JSON.stringify(chat)}`)
      record('wolfrod single-use consumed one', rodCount() === before - 1, `rods ${before}->${rodCount()}`)
    } catch (e) {
      record('wolfrod single-use', false, 'exception ' + e.message)
    }
  }

  // Plain fishing rods must still fish normally.
  await reset()
  await cmd('give @s fishing_rod', 800)
  await equipRod()
  await aimAtGround(6)
  await cast()
  await sleep(800)
  record('plain rod casts a hook', spawned.includes('fishing_bobber'), `spawned=${JSON.stringify(spawned)}`)

  // /suggestionadmin exists for admins; /suggestions no longer takes "admin".
  chat.length = 0
  await cmd('suggestions admin', 1000)
  record('/suggestions admin removed', chat.some(m => m.includes('Usage: /suggestions')), JSON.stringify(chat))
  chat.length = 0
  let opened = false
  bot.once('windowOpen', w => { opened = true; log('admin window title', JSON.stringify(w.title)) })
  await cmd('suggestionadmin', 2000)
  record('/suggestionadmin opens GUI', opened, JSON.stringify(chat))

  finish()
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
setTimeout(() => { console.log('TIMEOUT'); process.exit(4) }, 240000)
