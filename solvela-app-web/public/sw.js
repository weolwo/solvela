/*
 * C 端 service worker：让「添加到主屏幕」之后的 H5 打开得快、断网时不白屏。
 *
 * 只管三类请求，其余（/api、图片、跨域）一律不碰，原样走网络：
 *
 *   ① 页面导航（打开 / 刷新 / 地址栏进入任何 SPA 路由）—— 网络优先。
 *      联网时永远拿服务端最新的 index.html，发版即生效，和没有 SW 时一样；
 *      断网、网关 5xx、或 4 秒还没回来，才退回上一次存下的那份。
 *   ② /assets/ 下带 hash 的构建产物 —— 缓存优先。文件名里有 hash，内容永不变，存下就不用再问。
 *   ③ public/ 里几个固定文件（主题脚本、图标）—— 网络优先，断网时让外壳也能起来。
 *
 * 🔴 为什么不做「预缓存全部产物 + 弹窗提示有新版本」那一套（vite-plugin-pwa / workbox 的默认形状）：
 *    那一套里 index.html 也是缓存优先，用户打开的永远是上一版，要等 SW 更新、再点一下「刷新」才是新版。
 *    处理不好就是「发了版用户一直看不到」，而且没有任何报错。这里 index.html 走网络，
 *    版本永远跟着服务端走，SW 只是一层加速，删掉它行为不变。
 *
 * 🔴 /api 绝不缓存：响应因人而异（余额、券、登录态），缓存一份就可能给 A 看 B 的数据。
 *
 * 不经过 Vite 打包（public/ 原样拷贝），所以不能 import。能跑 service worker 的浏览器都支持
 * async/await，这里不必像 theme-init.js 那样守 ES5。
 * 改了这个文件就会触发浏览器更新 SW（它逐字节比对）；改缓存结构时把下面的版本号加一，旧缓存在 activate 时清掉。
 */

const SHELL_CACHE = 'solvela-shell-v1'
const ASSET_CACHE = 'solvela-assets-v1'

/** 所有 SPA 路由回的都是同一份 index.html，存一份就够，用固定的键 */
const SHELL_KEY = '/index.html'

/** 弱网下等多久就先用缓存顶上（网络那一份回来后照样写进缓存，下次打开就是新的） */
const NAVIGATION_TIMEOUT_MS = 4000

/**
 * /assets/ 最多留多少个文件。一次构建约 100 个，留够三个版本；超了按写入先后删最旧的。
 * Cache 的 keys() 按写入顺序返回，所以「最旧」就是排在最前面的。
 */
const MAX_ASSETS = 300

/** 网络优先、断网兜底的几个 public/ 文件 */
const STATIC_FILES = ['/theme-init.js']
const STATIC_PREFIXES = ['/icons/']

/** 这些前缀即使是导航也不当成 SPA 路由（直接在地址栏打开一张图片、一个接口） */
const NON_SPA_PREFIXES = ['/api/', '/support/']

self.addEventListener('install', () => {
  // 新 SW 装好就接管，不等旧页面全部关掉。安全的前提是 index.html 不在缓存优先里 ——
  // 新旧 SW 对同一个请求给出的都是「服务端此刻的版本」
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keep = [SHELL_CACHE, ASSET_CACHE]
      for (const key of await caches.keys()) {
        if (!keep.includes(key)) {
          await caches.delete(key)
        }
      }
      await self.clients.claim()
    })(),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') {
    return
  }
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) {
    return
  }
  const path = url.pathname

  if (request.mode === 'navigate') {
    if (!NON_SPA_PREFIXES.some((prefix) => path.startsWith(prefix))) {
      event.respondWith(networkFirst(event, request, SHELL_CACHE, SHELL_KEY, isHtml))
    }
    return
  }
  if (path.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request))
    return
  }
  if (STATIC_FILES.includes(path) || STATIC_PREFIXES.some((prefix) => path.startsWith(prefix))) {
    event.respondWith(networkFirst(event, request, SHELL_CACHE, path, () => true))
  }
})

function isHtml(response) {
  return (response.headers.get('content-type') || '').includes('text/html')
}

/**
 * 网络优先。网络回来了就用网络的（顺手存一份），否则用缓存；两边都没有才把网络的错误交出去。
 *
 * <p>只有 5xx 算「网络不可用」：Cloudflare 隧道断了、nginx 挂了回的是 502 / 530 的错误页，
 * 这时给用户上一份能用的外壳，比给一张 Cloudflare 错误页好。其余状态码（含跳转）原样返回。
 */
async function networkFirst(event, request, cacheName, cacheKey, cacheable) {
  const cache = await caches.open(cacheName)
  const network = fetch(request).then(async (response) => {
    if (response.ok && cacheable(response)) {
      await cache.put(cacheKey, response.clone())
    }
    return response
  })
  // 超时先返回缓存时，网络那一份还在路上 —— 让 SW 活到它回来、写进缓存
  event.waitUntil(network.catch(() => undefined))

  const usable = network.then((response) => {
    if (response.status >= 500) {
      throw new Error('upstream ' + response.status)
    }
    return response
  })

  try {
    return await Promise.race([usable, timeout(NAVIGATION_TIMEOUT_MS)])
  } catch {
    const cached = await cache.match(cacheKey)
    if (cached) {
      return cached
    }
    // 没有缓存可退：只能接着等网络，网络的错误（或 5xx 页面）原样交给浏览器
    return network
  }
}

function timeout(ms) {
  return new Promise((_, reject) => {
    setTimeout(() => reject(new Error('timeout')), ms)
  })
}

/**
 * 缓存优先。只存 200：404 的产物（发版后旧 chunk 没了）不能存，否则页面的自愈重载会一直撞上同一个 404。
 */
async function cacheFirst(request) {
  const cache = await caches.open(ASSET_CACHE)
  const cached = await cache.match(request)
  if (cached) {
    return cached
  }
  const response = await fetch(request)
  if (response.status === 200) {
    await cache.put(request, response.clone())
    await trim(cache)
  }
  return response
}

async function trim(cache) {
  const keys = await cache.keys()
  for (const key of keys.slice(0, Math.max(0, keys.length - MAX_ASSETS))) {
    await cache.delete(key)
  }
}
