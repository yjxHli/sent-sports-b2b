/**
 * shared 包独立类型检查（tsc -p，types: []）时看不到各端的 vite-env.d.ts，
 * 因此这里补齐最小 ImportMeta 环境声明；两端 Vite 构建时会被 vite/client 的类型合并覆盖。
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
