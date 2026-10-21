// @vitest-environment node
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * service worker（`public/sw.js`）。它坏掉的方式全是静默的：
 * 缓存了 /api 就是给 A 看 B 的余额；index.html 走了缓存就是「发了版用户看不到」；
 * 断网兜底失效就是白屏 —— 哪一种都不会有报错。
 *
 * <p>sw.js 不经打包、不能 import，这里把源码原样跑在一个假的 SW 全局环境里（self / caches / fetch）。
 * 用 node 环境而不是 jsdom：要的是原生的 Request / Response。
 */

const ORIGIN = 'https://app.example.com'
const SOURCE = readFileSync(
  fileURLToPath(new URL('../../../public/sw.js', import.meta.url)),
  'utf8',
)

type Listener = (event: unknown) => void

interface FakeCache {
  match(request: Request | string): Promise<Response | undefined>
  put(request: Request | string, response: Response): Promise<void>
  keys(): Promise<Request[]>
  delete(request: Request | string): Promise<boolean>
}

let listeners: Map<string, Listener>
let stores: Map<string, Map<string, Response>>
let fetchMock: ReturnType<typeof vi.fn>

function keyOf(request: Request | string): string {
  return typeof request === 'string' ? new URL(request, ORIGIN).href : request.url
}

function openStore(name: string): FakeCache {
  let store = stores.get(name)
  if (store === undefined) {
    store = new Map()
    stores.set(name, store)
  }
  const s = store
  return {
    match: (request) => Promise.resolve(s.get(keyOf(request))?.clone()),
    put: (request, response) => {
      s.set(keyOf(request), response)
      return Promise.resolve()
    },
    keys: () => Promise.resolve([...s.keys()].map((url) => new Request(url))),
    delete: (request) => Promise.resolve(s.delete(keyOf(request))),
  }
}

beforeEach(() => {
  listeners = new Map()
  stores = new Map()
  fetchMock = vi.fn()
  const self = {
    location: { origin: ORIGIN },
    addEventListener: (type: string, listener: Listener) => listeners.set(type, listener),
    skipWaiting: vi.fn(),
    clients: { claim: vi.fn(() => Promise.resolve()) },
  }
  const caches = {
    open: (name: string) => Promise.resolve(openStore(name)),
    keys: () => Promise.resolve([...stores.keys()]),
    delete: (name: string) => Promise.resolve(stores.delete(name)),
  }
  // eslint-disable-next-line @typescript-eslint/no-implied-eval
  const run = new Function('self', 'caches', 'fetch', SOURCE) as (...globals: unknown[]) => void
  run(self, caches, fetchMock)
})

afterEach(() => {
  vi.useRealTimers()
})

interface Dispatched {
  /** SW 没接手（没调 respondWith）时为 undefined —— 浏览器照常走网络 */
  response: Promise<Response> | undefined
  /** waitUntil 收下的后台任务全部结束 */
  settled: () => Promise<unknown>
}

function dispatch(path: string, init: { mode?: string; method?: string } = {}): Dispatched {
  // Request 构造函数不允许 mode: 'navigate'，导航请求只能用一个形状相同的对象冒充
  const request = {
    url: new URL(path, ORIGIN).href,
    method: init.method ?? 'GET',
    mode: init.mode ?? 'cors',
  }
  const waits: Promise<unknown>[] = []
  let response: Promise<Response> | undefined
  listeners.get('fetch')!({
    request,
    respondWith: (value: Promise<Response>) => {
      response = value
    },
    waitUntil: (value: Promise<unknown>) => {
      waits.push(value)
    },
  })
  return { response, settled: () => Promise.all(waits) }
}

function navigate(path = '/'): Dispatched {
  return dispatch(path, { mode: 'navigate' })
}

function html(body: string, status = 200): Response {
  return new Response(body, { status, headers: { 'content-type': 'text/html; charset=utf-8' } })
}

function cached(cacheName: string, path: string): Response | undefined {
  return stores.get(cacheName)?.get(new URL(path, ORIGIN).href)
}

async function primeShell(body = 'old shell'): Promise<void> {
  fetchMock.mockResolvedValueOnce(html(body))
  const first = navigate()
  await first.response
  await first.settled()
}

describe('页面导航：网络优先', () => {
  it('🔴 联网时用服务端最新的 index.html，并存一份 —— 发版即生效，不是「下次打开才是新版」', async () => {
    await primeShell('v1')
    fetchMock.mockResolvedValueOnce(html('v2'))

    const { response, settled } = navigate('/mall')
    await settled()

    expect(await (await response)!.text()).toBe('v2')
    expect(await cached('solvela-shell-v1', '/index.html')!.text()).toBe('v2')
  })

  it('断网 → 用上一次存下的外壳，不白屏', async () => {
    await primeShell()
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    expect(await (await navigate('/me').response)!.text()).toBe('old shell')
  })

  it('网关 5xx（隧道断了、nginx 挂了）→ 也用外壳，不给用户看 Cloudflare 错误页', async () => {
    await primeShell()
    fetchMock.mockResolvedValueOnce(html('cloudflare 502', 502))

    expect(await (await navigate().response)!.text()).toBe('old shell')
  })

  it('5xx 且没有外壳可退 → 原样交出错误页，不吞掉', async () => {
    fetchMock.mockResolvedValueOnce(html('cloudflare 502', 502))

    expect((await navigate().response)!.status).toBe(502)
  })

  it('弱网 4 秒没回来 → 先用外壳；网络那一份回来后照样写进缓存', async () => {
    await primeShell()
    vi.useFakeTimers()
    let arrive: (response: Response) => void = () => undefined
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => (arrive = resolve)))

    const { response, settled } = navigate()
    await vi.advanceTimersByTimeAsync(4000)
    expect(await (await response)!.text()).toBe('old shell')

    arrive(html('late but fresh'))
    await settled()
    expect(await cached('solvela-shell-v1', '/index.html')!.text()).toBe('late but fresh')
  })

  it('不是 HTML 的导航响应不存成外壳（直接在地址栏打开一个文件）', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('{}', { headers: { 'content-type': 'application/json' } }),
    )

    const { response, settled } = navigate('/some-file')
    await response
    await settled()

    expect(cached('solvela-shell-v1', '/index.html')).toBeUndefined()
  })
})

describe('🔴 不碰的请求', () => {
  it.each([
    ['接口 GET', '/api/auth/me', {}],
    ['接口 POST', '/api/lottery/draw', { method: 'POST' }],
    ['图片', '/support/file/public/abc.png', {}],
    ['在地址栏直接打开接口', '/api/auth/me', { mode: 'navigate' }],
    ['跨域', 'https://cdn.example.net/x.js', {}],
  ])('%s：不接手，原样走网络', (_, path, init) => {
    expect(dispatch(path, init).response).toBeUndefined()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

describe('/assets/：缓存优先', () => {
  it('第二次直接从缓存出，不再发请求', async () => {
    fetchMock.mockResolvedValueOnce(new Response('chunk', { status: 200 }))

    await dispatch('/assets/index-abc.js').response
    const second = await dispatch('/assets/index-abc.js').response

    expect(await second!.text()).toBe('chunk')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('🔴 404 不存 —— 发版后旧 chunk 没了，存下 404 会让页面的自愈重载一直撞同一个 404', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 404 }))

    await dispatch('/assets/gone-123.js').response
    await dispatch('/assets/gone-123.js').response

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(cached('solvela-assets-v1', '/assets/gone-123.js')).toBeUndefined()
  })

  it('超过 300 个按写入先后删最旧的', async () => {
    const store = new Map<string, Response>()
    for (let i = 0; i < 300; i++) {
      store.set(`${ORIGIN}/assets/old-${i}.js`, new Response('x'))
    }
    stores.set('solvela-assets-v1', store)
    fetchMock.mockResolvedValueOnce(new Response('new', { status: 200 }))

    await dispatch('/assets/new.js').response

    expect(store.size).toBe(300)
    expect(store.has(`${ORIGIN}/assets/old-0.js`)).toBe(false)
    expect(store.has(`${ORIGIN}/assets/new.js`)).toBe(true)
  })
})

describe('升级', () => {
  it('activate 时删掉版本号不认识的旧缓存，留下自己的', async () => {
    stores.set('solvela-shell-v0', new Map())
    stores.set('solvela-shell-v1', new Map())
    let done: Promise<unknown> = Promise.resolve()

    listeners.get('activate')!({ waitUntil: (value: Promise<unknown>) => (done = value) })
    await done

    expect([...stores.keys()]).toEqual(['solvela-shell-v1'])
  })
})
