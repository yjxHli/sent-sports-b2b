/**
 * 通用表格列定义。行类型由各页面在插槽中自行断言，列表基座不绑定具体 VO，
 * 避免把商品/库存/订单领域类型耦合进 components。
 */
export interface TableColumn {
  prop: string;
  label: string;
  width?: number | string;
  minWidth?: number | string;
  align?: 'left' | 'center' | 'right';
  /** 无具名插槽时的默认文本格式化 */
  formatter?: (row: any, value: any) => string;
}
