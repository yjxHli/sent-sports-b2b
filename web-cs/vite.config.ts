import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

/**
 * 客服工作台（web-cs）：端口 5175 / base / / 代理目标均由 .env.[mode] 驱动。
 *
 * 与 web-admin 的差异只有三处：端口 5175、Token 前缀 cs-token、无侧边菜单。
 * base 默认 '/'：开发态直接访问 http://localhost:5175/ 即可进登录页；
 * 若生产要和管理端共用同一域名子路径，构建时把 VITE_PUBLIC_PATH 改成 /cs/ 即可，
 * createTokenStorage 的登录回跳地址会跟着变，不需要改代码。
 *
 * 代理必须保留 /api 与 /ws 两条：前者是业务接口，后者是客服长连接
 * （ws: true 让 Vite 把 Upgrade 头透传给后端 ChatWebSocketHandler）。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  const apiTarget = env.VITE_PROXY_API_TARGET || 'http://127.0.0.1:8088';
  const wsTarget = env.VITE_PROXY_WS_TARGET || 'ws://127.0.0.1:8088';

  return {
    base: env.VITE_PUBLIC_PATH || '/',
    // 与商城端/管理端共用根目录 assets/images 的图片真源
    publicDir: fileURLToPath(new URL('../assets/images', import.meta.url)),
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT || 5175),
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
