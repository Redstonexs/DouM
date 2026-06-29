import mineflayer from 'mineflayer'
import minecraftData from 'minecraft-data'
import { Vec3 } from 'vec3'

const config = {
  host: process.env.QA_HOST ?? '127.0.0.1',
  port: Number.parseInt(process.env.QA_PORT ?? '25585', 10),
  playerName: process.env.QA_PLAYER ?? 'DeathGateQA',
  playerUuid: process.env.QA_PLAYER_UUID ?? '',
  adminName: process.env.QA_ADMIN ?? 'DeathGateAdmin',
  mode: process.env.QA_MODE ?? 'surface',
  timeoutMs: Number.parseInt(process.env.QA_TIMEOUT_MS ?? '180000', 10)
}

const positions = {
  spawn: new Vec3(0.5, 80, 0.5),
  floor: new Vec3(0, 79, 0),
  breakTarget: new Vec3(1, 80, 0),
  placeBase: new Vec3(0, 79, 1),
  placeTarget: new Vec3(0, 80, 1)
}

class QaFailure extends Error {
  constructor (message) {
    super(message)
    this.name = 'QaFailure'
  }
}

const wait = (ms) => new Promise(resolve => setTimeout(resolve, ms))

function pass (name) {
  console.log(`PASS ${name}`)
}

function fail (error) {
  const message = error instanceof Error ? error.stack ?? error.message : String(error)
  console.error(`FAIL mineflayer-surface-qa ${message}`)
}

function createBot (username) {
  return mineflayer.createBot({
    host: config.host,
    port: config.port,
    username,
    auth: 'offline',
    checkTimeoutInterval: 60_000
  })
}

function onceWithTimeout (emitter, eventName, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new QaFailure(`Timed out waiting for ${eventName}`))
    }, timeoutMs)

    const onEvent = (...args) => {
      cleanup()
      resolve(args)
    }

    const onError = (error) => {
      cleanup()
      reject(error instanceof Error ? error : new QaFailure(String(error)))
    }

    const cleanup = () => {
      clearTimeout(timer)
      emitter.removeListener(eventName, onEvent)
      emitter.removeListener('error', onError)
      emitter.removeListener('kicked', onError)
    }

    emitter.once(eventName, onEvent)
    emitter.once('error', onError)
    emitter.once('kicked', onError)
  })
}

function onceAnyWithTimeout (emitter, eventNames, timeoutMs, description) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new QaFailure(`Timed out waiting for ${description}`))
    }, timeoutMs)

    const onEvent = (...args) => {
      cleanup()
      resolve(args)
    }

    const onError = (error) => {
      cleanup()
      reject(error instanceof Error ? error : new QaFailure(String(error)))
    }

    const cleanup = () => {
      clearTimeout(timer)
      for (const eventName of eventNames) {
        emitter.removeListener(eventName, onEvent)
      }
      emitter.removeListener('error', onError)
      emitter.removeListener('kicked', onError)
    }

    for (const eventName of eventNames) {
      emitter.once(eventName, onEvent)
    }
    emitter.once('error', onError)
    emitter.once('kicked', onError)
  })
}

async function connectBot (username) {
  const bot = createBot(username)
  await onceWithTimeout(bot, 'spawn', 60_000)
  await bot.waitForChunksToLoad()
  return bot
}

async function closeBot (bot) {
  if (bot.ended) return
  bot.end()
  try {
    await onceWithTimeout(bot, 'end', 10_000)
  } catch (_error) {
  }
}

async function command (admin, rawCommand, expectedPattern = null) {
  const seen = []
  let listener = null
  const waiter = expectedPattern
    ? new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
          admin.removeListener('message', listener)
          reject(new QaFailure(`Command did not report expected output: ${rawCommand}; seen=${seen.join(' | ')}`))
        }, 15_000)
        listener = (message) => {
          const text = message.toString()
          seen.push(text)
          if (expectedPattern.test(text)) {
            clearTimeout(timer)
            admin.removeListener('message', listener)
            resolve(text)
          }
        }
        admin.on('message', listener)
      })
    : wait(350)

  admin.chat(rawCommand)
  return waiter
}

async function waitForCondition (description, probe, timeoutMs = 10_000) {
  const deadline = Date.now() + timeoutMs
  let lastValue = ''
  while (Date.now() < deadline) {
    const result = await probe()
    if (result.ok) return result.value
    lastValue = result.value ?? ''
    await wait(100)
  }
  throw new QaFailure(`Timed out waiting for ${description}; last=${lastValue}`)
}

function normalizeUuid (uuid) {
  return uuid.replaceAll('-', '').toLowerCase()
}

function playerUuid (bot) {
  return bot.player?.uuid ?? bot.players[bot.username]?.uuid ?? bot._client?.uuid ?? ''
}

async function waitForPlayerUuid (bot) {
  const uuid = await waitForCondition(`${bot.username} UUID`, async () => {
    const uuid = playerUuid(bot)
    return { ok: uuid.length > 0, value: uuid || 'missing' }
  }, 30_000)
  if (config.playerUuid && normalizeUuid(uuid) !== normalizeUuid(config.playerUuid)) {
    throw new QaFailure(`Expected ${bot.username} UUID ${config.playerUuid}, got ${uuid}`)
  }
  return uuid
}

function botState (bot) {
  const position = bot.entity?.position
  if (!position) return 'position=missing'
  const below = bot.blockAt(position.offset(0, -1, 0))?.name ?? 'missing'
  return `position=${position.toString()} below=${below}`
}

async function blockNameAt (bot, position) {
  const block = bot.blockAt(position)
  return block?.name ?? 'missing'
}

async function waitBlockName (bot, position, expectedName) {
  return waitForCondition(`block ${position} to become ${expectedName}`, async () => {
    const name = await blockNameAt(bot, position)
    return { ok: name === expectedName, value: name }
  })
}

async function waitInventoryCount (bot, itemName, expectedCount) {
  return waitForCondition(`inventory ${itemName} count ${expectedCount}`, async () => {
    const count = inventoryCount(bot, itemName)
    return { ok: count >= expectedCount, value: String(count) }
  })
}

function inventoryCount (bot, itemName) {
  return bot.inventory.items()
    .filter(item => item.name === itemName)
    .reduce((total, item) => total + item.count, 0)
}

async function baseSetup (admin, qa) {
  await command(admin, '/gamerule doDaylightCycle false')
  await command(admin, '/gamerule keepInventory true')
  await command(admin, '/time set noon')
  await command(admin, '/fill -2 79 -2 2 79 2 minecraft:stone')
  await command(admin, '/fill -2 80 -2 2 82 2 minecraft:air')
  await command(admin, `/gamemode survival ${qa.username}`)
  const movement = onceAnyWithTimeout(qa, ['forcedMove', 'move'], 20_000, 'QA teleport movement').catch(() => null)
  await command(admin, `/tp ${qa.username} 0.5 80 0.5 0 0`, new RegExp(`Teleported ${qa.username} to`))
  await Promise.race([movement, wait(1_000)])
  await command(admin, `/clear ${qa.username}`)
  await qa.waitForChunksToLoad()
  await waitForCondition('QA teleport', async () => ({
    ok: qa.entity.position.distanceTo(positions.spawn) < 2,
    value: botState(qa)
  }), 20_000)
}

async function setDeaths (admin, count) {
  await command(
    admin,
    `/doum setdeaths ${config.playerName} ${count}`,
    new RegExp(`Set ${config.playerName} deaths to ${count}\\.`)
  )
}

async function assertDeaths (admin, count) {
  await command(
    admin,
    `/doum deaths ${config.playerName}`,
    new RegExp(`${config.playerName} has ${count} deaths\\.`)
  )
}

async function digTarget (qa, position) {
  const block = qa.blockAt(position)
  if (!block) throw new QaFailure(`Missing block at ${position}`)
  await qa.lookAt(position.offset(0.5, 0.5, 0.5), true)
  try {
    await qa.dig(block)
  } catch (_error) {
  }
}

async function verifyBreakGate (admin, qa) {
  await baseSetup(admin, qa)
  await setDeaths(admin, 0)
  await command(admin, `/setblock ${positions.breakTarget.x} ${positions.breakTarget.y} ${positions.breakTarget.z} minecraft:dirt`)
  await waitBlockName(qa, positions.breakTarget, 'dirt')
  await digTarget(qa, positions.breakTarget)
  await waitBlockName(qa, positions.breakTarget, 'dirt')

  await setDeaths(admin, 1)
  await digTarget(qa, positions.breakTarget)
  await waitBlockName(qa, positions.breakTarget, 'air')
  pass('break-gate')
}

async function placeGoldBlock (qa) {
  const reference = qa.blockAt(positions.placeBase)
  if (!reference) throw new QaFailure(`Missing placement reference at ${positions.placeBase}`)
  await qa.lookAt(positions.placeTarget.offset(0.5, 0.5, 0.5), true)
  try {
    await qa.placeBlock(reference, new Vec3(0, 1, 0))
  } catch (_error) {
  }
}

async function verifyPlaceGate (admin, qa) {
  await baseSetup(admin, qa)
  await setDeaths(admin, 0)
  await command(admin, `/give ${config.playerName} minecraft:gold_block 1`)
  await waitInventoryCount(qa, 'gold_block', 1)
  await placeGoldBlock(qa)
  await waitBlockName(qa, positions.placeTarget, 'air')

  await setDeaths(admin, 1)
  await command(admin, `/give ${config.playerName} minecraft:gold_block 1`)
  await waitInventoryCount(qa, 'gold_block', 1)
  await placeGoldBlock(qa)
  await waitBlockName(qa, positions.placeTarget, 'gold_block')
  pass('place-gate')
}

async function craftOakPlanks (qa) {
  const data = minecraftData(qa.version)
  const planks = data.itemsByName.oak_planks
  const recipe = qa.recipesFor(planks.id, null, 1, null)[0]
  if (!recipe) throw new QaFailure('No oak_planks recipe available')
  try {
    await qa.craft(recipe, 1, null)
  } catch (_error) {
  }
}

async function verifyCraftGate (admin, qa) {
  await baseSetup(admin, qa)
  await setDeaths(admin, 0)
  await command(admin, `/give ${config.playerName} minecraft:oak_log 1`)
  await waitInventoryCount(qa, 'oak_log', 1)
  await craftOakPlanks(qa)
  await waitForCondition('oak planks denied', async () => ({
    ok: inventoryCount(qa, 'oak_planks') === 0,
    value: String(inventoryCount(qa, 'oak_planks'))
  }))

  await setDeaths(admin, 1)
  await command(admin, `/clear ${config.playerName}`)
  await command(admin, `/give ${config.playerName} minecraft:oak_log 1`)
  await waitInventoryCount(qa, 'oak_log', 1)
  await craftOakPlanks(qa)
  await waitInventoryCount(qa, 'oak_planks', 4)
  pass('craft-gate')
}

async function verifyDeathIncrement (admin, qa) {
  await baseSetup(admin, qa)
  await setDeaths(admin, 1)
  const death = onceWithTimeout(qa, 'death', 20_000)
  await command(admin, `/kill ${config.playerName}`)
  await death
  console.log('QA_DEATH_EVENT observed')
  await wait(1_000)
  await assertDeaths(admin, 2)
  pass('death-increment')
}

async function runSurface () {
  const admin = await connectBot(config.adminName)
  const qa = await connectBot(config.playerName)
  console.log(`QA_PLAYER_UUID ${await waitForPlayerUuid(qa)}`)
  try {
    await verifyBreakGate(admin, qa)
    await verifyPlaceGate(admin, qa)
    await verifyCraftGate(admin, qa)
    await verifyDeathIncrement(admin, qa)
    pass('setdeaths-command')
  } finally {
    await closeBot(qa)
    await closeBot(admin)
  }
}

async function runPersistence () {
  const admin = await connectBot(config.adminName)
  const qa = await connectBot(config.playerName)
  console.log(`QA_PLAYER_UUID ${await waitForPlayerUuid(qa)}`)
  try {
    await baseSetup(admin, qa)
    await assertDeaths(admin, 2)
    pass('persistence-restart')
  } finally {
    await closeBot(qa)
    await closeBot(admin)
  }
}

async function main () {
  if (!Number.isInteger(config.port) || config.port <= 0) {
    throw new QaFailure(`Invalid QA_PORT: ${process.env.QA_PORT}`)
  }
  if (config.mode === 'surface') {
    await runSurface()
    return
  }
  if (config.mode === 'persistence') {
    await runPersistence()
    return
  }
  throw new QaFailure(`Unknown QA_MODE: ${config.mode}`)
}

const timeout = setTimeout(() => {
  console.error(`FAIL mineflayer-surface-qa timed out after ${config.timeoutMs}ms`)
  process.exit(1)
}, config.timeoutMs)

main()
  .then(() => {
    clearTimeout(timeout)
  })
  .catch(error => {
    clearTimeout(timeout)
    fail(error)
    process.exitCode = 1
  })
