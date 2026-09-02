import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')

  return {
    plugins: [
      vue(),
      // src/ui 下的基础组件自动注册，页面里不用逐个 import。
      // 这套自动导入本来是给 Varlet 用的，去掉 Varlet 之后它照样有用 ——
      // 只是解析目标从「组件库」换成了「我们自己的 ui 目录」
      Components({
        dirs: ['src/ui'],
        extensions: ['vue'],
        dts: 'src/components.d.ts',
      }),
    ],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    server: {
      host: true,
      /*
       * ⚠️ 别改回 5175。Windows 会把一段端口保留给 Hyper-V/WSL，本机上
       * 5141-5240 正好盖住 5175，vite 撞上会直接 EACCES 起不来（它不会自动换端口）。
       * 查当前保留段：netsh interface ipv4 show excludedportrange protocol=tcp
       * 这个段在重启后可能漂移，真撞上了照上面的命令查一个段外的端口即可。
       */
      port: 5273,
      // ⚠️ 必须走同源代理，不要改成直接请求 http://host:1025。
      //    pre / prod 的 CorsFilter 挂着 @Conditional(SystemEnvironmentConfig)，
      //    只在 dev / test 环境注册 —— 也就是说线上没有 CORS。
      //    本地一旦习惯了跨域直连，上 pre 才会发现全线报错。
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:1025',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },

    build: {
      target: 'es2022',
      sourcemap: mode !== 'production',
      // 首屏体积是 C 端的主要成本，超了要有人知道，而不是悄悄变慢
      chunkSizeWarningLimit: 500,
      rollupOptions: {
        output: {
          // Vite 8 的 manualChunks 只接受函数形式，对象形式的类型已被移除。
          // 用 includes 而不是正则：rollup 的 id 一律是正斜杠，正则只会引入转义噪音。
          manualChunks(id: string) {
            if (id.includes('node_modules/@varlet/')) {
              return 'varlet'
            }
            if (
              id.includes('node_modules/vue/') ||
              id.includes('node_modules/vue-router/') ||
              id.includes('node_modules/pinia/') ||
              id.includes('node_modules/@vue/')
            ) {
              return 'vue'
            }
            return undefined
          },
        },
      },
    },
  }
})
