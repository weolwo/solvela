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
    /*
     * 🔴 关掉 Node 自带的 localStorage，让 jsdom 的那份生效。
     *
     * Node 25 起默认带一个全局 localStorage，没给 --localstorage-file 时它的值是 undefined。
     * vitest 的 jsdom 环境看到全局上已经有这个名字，就不再拿 jsdom 的覆盖它 ——
     * 于是 localStorage.clear() 报「读取 undefined 的属性」，而报错位置在用例里，
     * 看上去像用例写错了。Node 24 及以下没有这个全局，所以只在升 Node 之后才冒出来。
     *
     * 用带 experimental 的旧名字是刻意的：Node 22 也认它，短名 --no-webstorage 只有 25+ 认。
     * 管理端的 vitest.config.js 有同一条，改一个记得看另一个。
     */
    execArgv: ['--no-experimental-webstorage'],
  },
})
