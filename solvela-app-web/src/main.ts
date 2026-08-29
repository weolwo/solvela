import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './styles/index.css'

// @typescript-eslint/parser 不解析 SFC，从 .vue 导入的值在 lint 视角下是 any。
// .vue 的真实类型检查由 vue-tsc 负责（npm run typecheck），这里只关这一行的噪音。
// eslint-disable-next-line @typescript-eslint/no-unsafe-argument
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
