import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './styles/index.css'

// 2026-09-26 之前的版本把会话令牌、设备令牌存在这几个键里，现在它们都在服务端下发的 HttpOnly cookie 里。
// 迁移接口已经删了，残留的旧令牌不会再交给服务端；但留在本地就是一份脚本读得到的凭证，启动时直接扔掉。
// 隐私模式下访问 storage 会直接抛，不能让它拦住应用启动。
for (const key of [
  'solvela.app.token',
  'solvela.app.token.expiresAt',
  'solvela.app.device.token',
  'solvela.app.device.id',
]) {
  try {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  } catch {
    break
  }
}

// @typescript-eslint/parser 不解析 SFC，从 .vue 导入的值在 lint 视角下是 any。
// .vue 的真实类型检查由 vue-tsc 负责（npm run typecheck），这里只关这一行的噪音。
// eslint-disable-next-line @typescript-eslint/no-unsafe-argument
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
