/**
 * 兼容层（2026-11-28 P2）：实现已上提到 `@sent/shared/api`，由本端 api/index.ts
 * 注入本端 http 实例后导出。保留本文件是为了不改动十几处 `from '@/api/file'` 的调用点。
 */
export { authApi, orderApi, fileApi } from '@/api';
export * from '@sent/shared/api';
