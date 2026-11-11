/**
 * 管理端 API 聚合出口：页面统一从 `@/api` 引入，避免散落的 http 直接调用。
 * 各模块按后端业务域拆分，互不依赖；公共契约（VO / PageQuery / 错误码）来自 @sent/shared。
 */
export * from './auth';
export * from './product';
export * from './stock';
export * from './order';
export * from './custom';
export * from './customer';
export * from './finance';
export * from './chat';
export * from './system';
