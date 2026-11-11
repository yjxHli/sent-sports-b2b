import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

/**
 * 管理端（P6-05）：端口 / 部署路径 / 代理目标统一由 .env.[mode] 驱动。
 * 不再硬编码端口，后端换端口只改 .env.development 即可（默认 8088，避开 Steam 占用的 8080）。
 * 走同源代理是为了让浏览器能读到 X-New-Token 等自定义响应头（WebMvcConfig 已 expose）。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  const apiTarget = env.VITE_PROXY_API_TARGET || 'http://127.0.0.1:8088';
  const wsTarget = env.VITE_PROXY_WS_TARGET || 'ws://127.0.0.1:8088';

  return {
    base: env.VITE_PUBLIC_PATH || '/admin/',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT || 5174),
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true },
        '/ws': { target: wsTarget, ws: true },
      },
    },
    build: {
      outDir: 'dist',
    },
  };
});
