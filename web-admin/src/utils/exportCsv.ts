/**
 * 管理端 CSV 导出工具（与 SentTable.exportCsv 同口径）：
 * BOM + RFC4180 双引号转义，Excel 直接打开不乱码。
 * 供仍使用原生 el-table 的页面（回款记录 / 核销明细）复用。
 */
export interface CsvColumn<T> {
  label: string;
  value: (row: T) => unknown;
}

function cell(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}

export function downloadCsv<T>(fileName: string, columns: CsvColumn<T>[], rows: T[]): void {
  const header = columns.map((c) => cell(c.label)).join(',');
  const lines = rows.map((row) => columns.map((c) => cell(c.value(row))).join(','));
  const blob = new Blob(['\uFEFF' + [header, ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${fileName}.csv`;
  a.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 2000);
}
