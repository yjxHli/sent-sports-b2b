/// <reference types="vite/client" />

/** 客服工作台环境变量（与 .env.development 一一对应） */
interface ImportMetaEnv {
  readonly VITE_PORT?: string;
  readonly VITE_PUBLIC_PATH?: string;
  readonly VITE_ROUTER_HISTORY?: string;
  readonly VITE_API_BASE?: string;
  readonly VITE_PROXY_API_TARGET?: string;
  readonly VITE_PROXY_WS_TARGET?: string;
  readonly VITE_TOKEN_STORAGE_PREFIX?: string;
  readonly VITE_AUD?: string;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}
