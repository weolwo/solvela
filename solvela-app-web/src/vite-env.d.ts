/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口前缀。**只能是相对路径**，理由见 .env.production 的注释 */
  readonly VITE_API_BASE_URL: string
  /** 仅开发环境使用：vite proxy 的后端地址 */
  readonly VITE_PROXY_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
