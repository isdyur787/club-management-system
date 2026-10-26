import { beforeEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '../src/store/chat.js'

class MemoryStorage {
  values = new Map()
  get length() { return this.values.size }
  key(index) { return [...this.values.keys()][index] }
  getItem(key) { return this.values.get(key) ?? null }
  setItem(key, value) { this.values.set(key, String(value)) }
  removeItem(key) { this.values.delete(key) }
}
const response = (answer, sessionId = 'session-1') => ({ data: { answer, sessionId, sources: [{ title: '社团资料' }] } })

beforeEach(() => {
  globalThis.sessionStorage = new MemoryStorage()
  setActivePinia(createPinia())
})

test('restores the selected club, messages and server session after a page refresh', async () => {
  const store = useChatStore()
  store.initialize(2)
  store.selectClub(6)
  await store.sendMessage(6, '街舞社怎么报名？', async () => response('报名说明'))
  setActivePinia(createPinia())
  const restored = useChatStore()
  restored.initialize(2)
  assert.equal(restored.currentClubId, 6)
  assert.equal(restored.getConversation(6).messages[1].content, '报名说明')
  assert.equal(restored.getConversation(6).sessionId, 'session-1')
})

test('keeps each club conversation and reuses its server session', async () => {
  const store = useChatStore()
  store.initialize(2)
  await store.sendMessage(6, '街舞社问题', async () => response('街舞答复', 'dance-session'))
  await store.sendMessage(7, '英语角问题', async () => response('英语答复', 'english-session'))
  let sent
  await store.sendMessage(6, '继续上一个问题', async data => { sent = data; return response('继续答复', 'dance-session') })
  assert.equal(sent.sessionId, 'dance-session')
  assert.equal(store.getConversation(7).messages.length, 2)
  assert.equal(store.getConversation(6).messages.length, 4)
})

test('a reply arriving after navigation belongs to the original club', async () => {
  const store = useChatStore()
  store.initialize(2)
  let resolve
  const pending = store.sendMessage(6, '原社团问题', () => new Promise(done => { resolve = done }))
  store.selectClub(7)
  assert.equal(store.getConversation(6).loading, true)
  resolve(response('原社团答复'))
  await pending
  assert.equal(store.getConversation(6).messages[1].content, '原社团答复')
  assert.equal(store.getConversation(7).messages.length, 0)
})

test('logout removes stored history and discards a late reply', async () => {
  const store = useChatStore()
  store.initialize(2)
  let resolve
  const pending = store.sendMessage(6, '旧账号问题', () => new Promise(done => { resolve = done }))
  store.clear()
  store.initialize(3)
  resolve(response('旧账号答复'))
  await pending
  assert.equal(store.getConversation(6).messages.length, 0)
  assert.equal(sessionStorage.getItem('club-ai-chat:2'), null)
  assert.ok(![...sessionStorage.values.values()].some(value => value.includes('旧账号')))
})

test('logging out and logging in as the same user starts with empty history', async () => {
  const store = useChatStore()
  store.initialize(2)
  await store.sendMessage(6, '问题', async () => response('答复'))
  store.clear()
  store.initialize(2)
  assert.equal(store.getConversation(6).sessionId, null)
  assert.deepEqual(store.getConversation(6).messages, [])
})

test('damaged session storage does not prevent entering chat', () => {
  sessionStorage.setItem('club-ai-chat:2', '{broken')
  const store = useChatStore()
  assert.doesNotThrow(() => store.initialize(2))
  assert.deepEqual(store.getConversation(6).messages, [])
})
