/**
 * 列表批量删除的「删除态」状态机（2026-11-17 用户二次要求）。
 *
 * 用户口径：正常进页面**不显示勾选框**，首位就是业务主键（发票 ID / 退款单号…）；
 * 点「删除」进入删除态后，勾选列才出现，取消或删完自动退出并清空勾选。
 *
 * 六个列表（退款 / 发票 / 合同 / 定制 / 订单 / 库存）行为完全一致，抽到这里，
 * 避免每个页面各写一份 `watch + 清空 + 重载`。
 */
import { ref, watch } from 'vue';

export function useBatchDelete(reload: () => void | Promise<void>) {
  /** 是否处于删除态：同时驱动表格的 selectable 复选框列 */
  const deleteMode = ref(false);
  /** 当前勾选行（el-table 原始行对象） */
  const selected = ref<any[]>([]);

  // 退出删除态必须清空勾选：复选框列被移除后 el-table 不再发 selection-change，
  // 残留的 selected 会让下一次进入删除态时「未勾选却显示已选 N 条」
  watch(deleteMode, (on) => {
    if (!on) selected.value = [];
  });

  /** 删除成功：退出删除态 → 清空勾选 → 重新拉列表 */
  function onDeleted(): void {
    deleteMode.value = false;
    selected.value = [];
    void reload();
  }

  return { deleteMode, selected, onDeleted };
}
