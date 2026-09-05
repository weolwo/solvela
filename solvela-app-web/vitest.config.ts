import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  /*
   * 组件自动注册要和 vite.config 保持一致，否则测试里 <Button> / <Section>
   * 全是未注册组件 —— 挂载出来的 DOM 和真实页面不是一回事，测了也不算数。
   * dts 指到构建产物同一个文件，跑测试不会把它改出一份差异来。
   */
  plugins: [vue(), Components({ dirs: ['src/ui'], extensions: ['vue'], dts: 'src/components.d.ts' })],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/__tests__/**/*.spec.ts'],
    globals: false,
    restoreMocks: true,
  },
})
