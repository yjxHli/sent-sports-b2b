import type { RouteLocationRaw, Router } from 'vue-router';

/**
 * 新标签页跳转的统一口径（2026-11-25）。
 *
 * <p>为什么不用路由字符串手拼：站点有 `base`（`VITE_PUBLIC_PATH`），
 * 手拼 `/product/1` 在子路径部署时会 404；`router.resolve()` 会带出正确的 href。</p>
 *
 * <p>两个入口：</p>
 * <ul>
 *   <li>链接类（导航、卡片）→ 直接用 `<a :href="resolveHref(...)" target="_blank" rel="noopener">`，
 *       或 `<router-link :to target="_blank">`。这样鼠标中键、右键「在新窗口打开」都生效。</li>
 *   <li>按钮类（`<button>` 不好换标签）→ {@link openInNewTab}，由点击事件里 `window.open`。</li>
 * </ul>
 */
export function resolveHref(router: Router, to: RouteLocationRaw): string {
  return router.resolve(to).href;
}

/** 商品详情页 href（列表卡专用，禁止手拼） */
export function productHref(router: Router, productId: string | number): string {
  return resolveHref(router, { path: `/product/${productId}` });
}

/** 按钮类入口：点击即新开标签页（同步调用，不会被弹窗拦截） */
export function openInNewTab(router: Router, to: RouteLocationRaw): void {
  window.open(resolveHref(router, to), '_blank', 'noopener');
}
