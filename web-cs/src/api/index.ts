/**
 * 客服端 API 聚合出口：页面统一从 `@/api` 引入。
 *
 * 剥离原则：只搬运工作台真实用到的域（auth / chat / order / tag / gift），
 * 商品、库存、财务、券模板等管理端域一律不进来——客服没有这些权限，
 * 带过来只会让打包体积和权限面一起变大。
 *
 * 例外说明（V64）：`gift` 是「客服发券」专用通道（面额 ≤ 20 元、只发当前客户），
 * 与管理端 `/admin/coupons`（全量发券、任意面额）是两套接口，不是同一个权限面。
 */
export * from './auth';
export * from './chat';
export * from './order';
export * from './tag';
export * from './gift';
