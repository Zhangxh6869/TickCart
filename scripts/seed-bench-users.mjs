#!/usr/bin/env node
/**
 * 压测账号准备：建 N 个账号 + 每人一条默认地址，并缓存登录 token。
 *
 * 为什么需要这么多账号：秒杀限购 1 件/人，且同一用户有 3 秒请求锁 ——
 * 想跑 N 并发就必须有 N 个不同用户。
 *
 * 用法：
 *   node scripts/seed-bench-users.mjs 500          # 建 500 个账号并缓存 token
 *   node scripts/seed-bench-users.mjs 500 --clean  # 删除这批账号与地址
 *
 * token 缓存在 `.workbuddy-ai/tmp/bench-tokens.json`（不进 git）：
 * 登录走 BCrypt，500 个账号约 30~60 秒，缓存后重复压测只需几秒。
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const REPO = path.resolve(HERE, '..')
const TOKEN_FILE = path.join(REPO, '.workbuddy-ai', 'tmp', 'bench-tokens.json')

const args = process.argv.slice(2)
const clean = args.includes('--clean')
const count = Number(args.find((a) => /^\d+$/.test(a)) ?? 500)

const ADMIN_USER = process.env.ADMIN_USER ?? 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS ?? 'admin123'
const USER_PASS = process.env.TICKCART_PASS ?? '123456'
const BASE = (process.env.BASE ?? 'http://localhost:8080').replace(/\/$/, '')

function mysql(sql) {
  execFileSync('docker', ['exec', '-i', 'tickcart-mysql', 'mysql', '-uroot', '-proot'], {
    input: sql,
    stdio: ['pipe', 'ignore', 'inherit'],
  })
}

function redisCli(...args) {
  return execFileSync('docker', ['exec', '-i', 'tickcart-redis', 'redis-cli', ...args], {
    encoding: 'utf8',
  })
}

function mysqlQuery(sql) {
  return execFileSync(
    'docker',
    ['exec', '-i', 'tickcart-mysql', 'mysql', '-uroot', '-proot', '-N', '-e', sql],
    { encoding: 'utf8' },
  ).trim()
}

const prefixFor = (i) => `bench${String(i).padStart(4, '0')}`

// ---------------- 清理 ----------------
if (clean) {
  console.log(`删除 bench 账号与地址…`)
  // 子表没有 user_id 列，只能按 order_no 关联清理
  mysql(`START TRANSACTION;
DELETE FROM tickcart.order_item        WHERE order_no IN (SELECT order_no FROM tickcart.t_order WHERE user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%'));
DELETE FROM tickcart.payment_record    WHERE order_no IN (SELECT order_no FROM tickcart.t_order WHERE user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%'));
DELETE FROM tickcart.t_order           WHERE user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%');
DELETE FROM tickcart.idempotent_record WHERE user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%');
DELETE FROM tickcart.user_address      WHERE user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%');
DELETE FROM tickcart.sys_user          WHERE username LIKE 'bench%';
COMMIT;`)
  if (fs.existsSync(TOKEN_FILE)) fs.rmSync(TOKEN_FILE)
  for (const key of redisCli('--scan', '--pattern', 'seckill:*').split('\n').filter(Boolean)) {
    redisCli('del', key)
  }
  console.log('已清理：账号、地址、订单、秒杀 Redis key、token 缓存')
  process.exit(0)
}

// ---------------- 建账号 ----------------
if (count <= 0) {
  console.error('账号数必须大于 0')
  process.exit(1)
}

console.log(`\n=== 准备 ${count} 个压测账号 ===\n`)

const hash = mysqlQuery(
  "SELECT password FROM tickcart.sys_user WHERE username='demo';",
).replace(/\r/g, '')

// 用户名/手机号必须唯一：用户名 bench0001..，手机号 1382000xxxx
const userRows = []
const addrRows = []
for (let i = 1; i <= count; i++) {
  const u = prefixFor(i)
  userRows.push(`('${u}','${hash}','1382000${String(i).padStart(4, '0')}','压测${i}',0,0,0)`)
  addrRows.push(
    `((SELECT id FROM tickcart.sys_user WHERE username='${u}'),'压测用户${i}',`
    + `'1382000${String(i).padStart(4, '0')}','广东省','深圳市','南山区','科技园路 ${i} 号',1,0)`,
  )
}

// 幂等：先清掉上一批 bench 账号与地址，再重建（重跑不会撞唯一键）
mysql(`START TRANSACTION;
DELETE FROM tickcart.user_address WHERE user_id IN (SELECT id FROM tickcart.sys_user WHERE username LIKE 'bench%');
DELETE FROM tickcart.sys_user     WHERE username LIKE 'bench%';
COMMIT;`)

const batch = 500
for (let start = 0; start < count; start += batch) {
  const users = userRows.slice(start, start + batch)
  const addrs = addrRows.slice(start, start + batch)
  mysql(`INSERT INTO tickcart.sys_user (username,password,phone,nickname,role,status,deleted) VALUES
${users.join(',\n')};
INSERT INTO tickcart.user_address (user_id,receiver,phone,province,city,district,detail,is_default,deleted) VALUES
${addrs.join(',\n')};`)
}
console.log(`  ✓ 已创建 ${count} 个账号与地址`)

// ---------------- 登录缓存 token ----------------
const cacheFile = TOKEN_FILE
if (fs.existsSync(cacheFile)) {
  console.log(`  · token 缓存已存在，跳过登录：${path.relative(REPO, cacheFile)}`)
  console.log('    想强制重新登录请先删掉该文件')
  process.exit(0)
}

/**
 * 登录（带 429 退避重试）。
 *
 * <p>登录频控是账号/IP 维度的，批量登录很容易撞上「操作过于频繁」——
 * 就算后端关了频控（`LOGIN_GUARD_ENABLED=false`），也保不齐有别的限制，
 * 所以这里统一退避重试，别让缓存半途而废。</p>
 */
async function login(username, password) {
  for (let attempt = 0; attempt < 12; attempt++) {
    const res = await fetch(`${BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    const json = await res.json()
    if (json.code === 0) return json.data.accessToken
    if (json.code === 42900) {
      await new Promise((r) => setTimeout(r, 300 + attempt * 200))
      continue
    }
    throw new Error(`登录失败 ${username}: ${JSON.stringify(json)}`)
  }
  throw new Error(`登录重试后仍失败：${username}`)
}

const entries = []
const concurrency = 20
let done = 0

for (let start = 0; start < count; start += concurrency) {
  const batchIndexes = []
  for (let i = start; i < Math.min(start + concurrency, count); i++) batchIndexes.push(i + 1)

  const tokens = await Promise.all(
    batchIndexes.map(async (i) => {
      const token = await login(prefixFor(i), USER_PASS)
      const res = await fetch(`${BASE}/api/addresses`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      const json = await res.json()
      const first = json.data?.[0]
      if (!first) throw new Error(`账号 ${prefixFor(i)} 没有地址`)
      return { username: prefixFor(i), token, addressId: first.id }
    }),
  )
  entries.push(...tokens)
  done += tokens.length
  if (done % 100 < concurrency) process.stdout.write(`  登录 ${done}/${count}\r`)
}

fs.mkdirSync(path.dirname(cacheFile), { recursive: true })
fs.writeFileSync(cacheFile, JSON.stringify({ count, entries }, null, 0))
console.log(`  ✓ token 已缓存：${path.relative(REPO, cacheFile)}（${entries.length} 个）`)
console.log('')
console.log('现在可以跑压测了：')
console.log('  STOCK=100 node scripts/bench-seckill.mjs')
