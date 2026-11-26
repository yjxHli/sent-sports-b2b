/**
 * E2E 共用：直接读写演示库（默认 `sent_dev`）。
 *
 * <p><b>为什么需要它</b>：整套 E2E 共用一个演示库，用例跑久了库一定会漂移——
 * 券被领空、评价留残留、库存被买空、待发货被发完。这些都不是功能坏了，却会把
 * 「绝对数断言」（`0 条` / `2 条` / `> 0`）打成假红。新写的用例请按
 * <b>「开始前自建 / 结束后还原」</b>的纪律来：需要什么前提就现场造，别假设种子还在。</p>
 *
 * <p>只放最底层的 SQL 通道，业务化的造数/清理请写在各 spec 或 demo-state.ts 里。</p>
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const DB_NAME = process.env.E2E_DB_NAME ?? 'sent_dev';

export function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

/** 执行一段 SQL（可多语句），返回制表符分隔的裸结果，已 trim。 */
export function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    DB_NAME, '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

/** 单值查询：查不到（空结果）返回 null，省掉调用方的 split 判空。 */
export function sqlScalar(statement: string): string | null {
  const value = sql(statement);
  return value === '' ? null : value.split(/\r?\n/)[0].trim();
}

export function sqlNumber(statement: string, fallback = 0): number {
  const value = sqlScalar(statement);
  const n = value == null ? Number.NaN : Number(value);
  return Number.isFinite(n) ? n : fallback;
}

/** 安全拼接字符串字面量（反斜杠与单引号）。 */
export function sqlLit(value: string): string {
  return `'${value.replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}
