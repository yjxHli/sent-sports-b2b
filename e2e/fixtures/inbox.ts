/**
 * 站内信测试夹具（2026-11-17 用户第 5 点复盘）。
 *
 * 用户反馈：「商城端的站内信似乎每次重启以后都会重新变成未读状态」。
 * 根因不在产品代码——`markRead` / `markAllRead` 都正确落库（`user_message.read_flag`），
 * 而是**用例自己**在 `beforeAll` 里 `DELETE FROM user_message WHERE customer_id = 9002`
 * 再灌 3 条 read_flag = 0 的演示消息：9002 就是演示账号本身，于是每跑一次测试，
 * 用户收件箱就被整体重置成「3 条未读」。
 *
 * 所以这里改成「快照 → 播种 → 还原」：
 *   1. 用例开跑前把该客户的收件箱整表备份到 `_e2e_user_message_bak`；
 *   2. 灌入用例需要的确定初始状态（保证可重复执行）；
 *   3. 用例结束后原样还原，读过的还是读过的，用户看到的东西不变。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const DB_NAME = process.env.E2E_DB_NAME ?? 'sent_dev';
const BACKUP_TABLE = '_e2e_user_message_bak';

function mysqlBin(): string {
  const fromEnv = process.env.E2E_MYSQL_BIN;
  if (fromEnv) return fromEnv;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function mysqlArgs(): string[] {
  return [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    DB_NAME, '-N', '-B',
  ];
}

/** 执行写 SQL（可多语句），不回显结果 */
export function runSql(statement: string): void {
  execFileSync(mysqlBin(), [...mysqlArgs(), '-e', statement], { stdio: 'ignore' });
}

/** 执行查询 SQL，返回首行首列（去掉首尾空白） */
export function querySql(statement: string): string {
  return execFileSync(mysqlBin(), [...mysqlArgs(), '-e', statement], { encoding: 'utf8' }).trim();
}

/**
 * 备份当前收件箱并灌入用例初始状态。
 * @param customerId 客户 ID（演示账号 9002）
 * @param seedSql    播种语句（DELETE + INSERT，用例各自定义，保证断言有确定基准）
 */
export function snapshotInbox(customerId: number, seedSql: string): void {
  runSql(`DROP TABLE IF EXISTS ${BACKUP_TABLE};
CREATE TABLE ${BACKUP_TABLE} AS SELECT * FROM user_message WHERE customer_id = ${customerId};
${seedSql}`);
}

/**
 * 还原到用例开始前的收件箱（含每条自己的 read_flag）。
 * 备份表不存在时（用例没走到 snapshotInbox / 上一次异常退出已清掉）什么也不做，
 * 绝不能先 DELETE 再 INSERT——那会把用户真实收件箱清空。
 */
export function restoreInbox(customerId: number): void {
  const exists = querySql(
    `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${DB_NAME}'`
    + ` AND table_name = '${BACKUP_TABLE}';`,
  );
  if (exists !== '1') return;
  runSql(`DELETE FROM user_message WHERE customer_id = ${customerId};
INSERT INTO user_message SELECT * FROM ${BACKUP_TABLE};
DROP TABLE IF EXISTS ${BACKUP_TABLE};`);
}
