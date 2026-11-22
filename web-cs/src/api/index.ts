/**
 * 客服端 API 聚合出口：页面统一从 `@/api` 引入。
 *
 * 剥离原则：只搬运工作台真实用到的域（auth / chat / order），
 * 商品、库存、财务、优惠券等管理端域一律不进来——客服没有这些权限，
 * 带过来只会让打包体积和权限面一起变大。
 */
export * from './auth';
export * from './chat';
export * from './order';
