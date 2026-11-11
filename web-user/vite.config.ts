import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

/** 商城端：端口 / 基路径 / 代理目标统一由 .env.[mode] 驱动 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());

  return {
    base: env.VITE_PUBLIC_PATH || '/',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT || 5173),
      proxy: {
        '/api': { target: env.VITE_PROXY_API_TARGET || 'http://127.0.0.1:8088', changeOrigin: true },
        '/ws': { target: env.VITE_PROXY_WS_TARGET || 'ws://127.0.0.1:8088', ws: true },
      },
    },
    build: {
      outDir: 'dist',
    },
  };
});
