<template>
  <div>
    <h2 class="sent-page-title">系统配置中心</h2>

    <div class="sent-panel">
      <SentTable
        data-testid="config-list"
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="系统配置"
        :operation-width="96"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="搜配置项名称 / 键名 / 说明"
            clearable
            style="width: 240px"
            data-testid="config-search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select
            v-model="group"
            placeholder="全部分组"
            clearable
            style="width: 150px"
            data-testid="config-group-filter"
            @change="onSearch"
            @clear="group = ''"
          >
            <el-option v-for="item in groups" :key="item" :label="item" :value="item" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </template>

        <!-- 序号按当前页连续编号，翻页后从 (page-1)*size+1 重新开始 -->
        <template #col-__index="{ index }">
          {{ (page - 1) * size + Number(index) + 1 }}
        </template>

        <!-- 配置项：中文名当主标题，原始键名降级成小字灰底（运营不用懂 order.pay.timeout.minutes） -->
        <template #col-configKey="{ row }">
          <div class="cfg-item">
            <span class="cfg-item__name" :title="row.configName">{{ row.configName }}</span>
            <span class="cfg-item__meta">
              <span class="cfg-item__key" :title="row.configKey">{{ row.configKey }}</span>
              <span v-if="row.groupName" class="cfg-item__group">{{ row.groupName }}</span>
            </span>
          </div>
        </template>

        <template #col-configValue="{ row }">
          <span
            class="cfg-value"
            :class="{ 'cfg-value--empty': !row.configValue }"
            :title="rawTitle(row)"
          >{{ row.displayValue }}</span>
        </template>

        <template #col-description="{ row }">
          <span class="cfg-desc" :title="row.description">{{ row.description }}</span>
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" data-testid="config-edit-btn" @click="openEdit(row)">
            修改
          </el-button>
        </template>
      </SentTable>

      <p class="sent-hint">
        这里的每一项都是系统运行时真正读取的参数：改完<b>下一次判断就生效</b>，不需要重启。
        金额类一律以「分」为单位存储（1 元 = 100 分），输入时请填整数分。
      </p>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editing ? `修改配置 · ${editing.configName}` : '修改配置'"
      width="540px"
      @closed="reset"
    >
      <!-- data-testid 挂在真正的对话框内容上：el-dialog 根节点会吞掉透传属性，E2E 找不到 -->
      <div v-if="editing" class="cfg-body" data-testid="config-dialog">
        <!-- 中文名 + 键名 + 说明：填之前先让人看清「这一项是干什么的」，避免把超时时间填成秒 -->
        <div class="cfg-head">
          <span class="cfg-head__name">{{ editing.configName }}</span>
          <span v-if="editing.groupName" class="cfg-item__group">{{ editing.groupName }}</span>
          <code class="cfg-head__key">{{ editing.configKey }}</code>
        </div>
        <p class="cfg-head__desc">{{ editing.description || '该项没有登记说明，修改前请先确认它被哪些功能读取。' }}</p>

        <el-form label-width="86px">
          <el-form-item label="当前值" required>
            <el-switch
              v-if="editing.valueType === 'BOOLEAN'"
              v-model="boolValue"
              data-testid="config-value-switch"
              active-text="开启"
              inactive-text="关闭"
            />
            <template v-else>
              <el-input
                v-model="value"
                data-testid="config-value-input"
                :placeholder="valuePlaceholder"
                clearable
              >
                <template v-if="editing.unit" #append>{{ editing.unit }}</template>
              </el-input>
              <p v-if="editing.valueType === 'MONEY_CENT' && moneyHint" class="cfg-form-hint">
                折合 {{ moneyHint }}
              </p>
              <p v-else-if="unitHint" class="cfg-form-hint">{{ unitHint }}</p>
            </template>
          </el-form-item>
        </el-form>

        <p class="cfg-preview">
          保存后：<b>{{ preview }}</b>
        </p>
      </div>

      <template #footer>
        <el-button data-testid="config-cancel-btn" @click="formVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          data-testid="config-save-btn"
          @click="submit"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统配置中心（2026-11-25，原「业务参数」页）。
 *
 * 改造动机：原页面只有 `order.pay.timeout.minutes | 30` 两列，运营看不出这一项是什么、
 * 填错了会怎样；更要命的是「值」没有类型约束，把「30 分钟」写成「半小时」也能存进库，
 * 读取方 parseInt 在定时任务里静默抛错，表现为「订单再也不自动关闭」。
 *
 * 三条口径：
 * 1. **中文名 / 说明 / 单位全部由后端字典下发**，前端不再维护一份映射（两份必然漂移）；
 * 2. 键名保留但降级为小字灰底——排障时仍需要它，但不该是页面的主角；
 * 3. 值按类型给控件：布尔给开关、其余给带单位后缀的输入框，提交前先做一次前端校验，
 *    后端再按同一份字典校验一次（前端只是少一次往返，不是安全边界）。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi, type ConfigVO } from '@/api';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: '__index', label: '序号', width: 72, align: 'center' },
  { prop: 'configKey', label: '配置项', minWidth: 300 },
  { prop: 'configValue', label: '当前值', width: 148, align: 'right' },
  { prop: 'description', label: '说明', minWidth: 300 },
];

const keyword = ref('');
const group = ref('');
/** 分组下拉的候选值：配置项一共十几条，一次性拉全后本地推导，不为它加接口 */
const groupOptions = ref<string[]>([]);

const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<ConfigVO>({
  mode: 'client',
  fetch: () => unwrap(systemApi.configs()),
  transform: ({ list }) => {
    groupOptions.value = [...new Set(list.map((item) => item.groupName).filter(Boolean))];
    const kw = keyword.value.trim().toLowerCase();
    const filtered = list.filter((item) => {
      if (group.value && item.groupName !== group.value) return false;
      if (!kw) return true;
      return [item.configName, item.configKey, item.description]
        .filter(Boolean)
        .some((text) => text.toLowerCase().includes(kw));
    });
    return { list: filtered, total: filtered.length };
  },
  errorMessage: '配置加载失败',
});

const groups = computed(() => groupOptions.value);

const formVisible = ref(false);
const submitting = ref(false);
const editing = ref<ConfigVO | null>(null);
const value = ref('');
const boolValue = ref(false);

const valuePlaceholder = computed(() => {
  if (!editing.value) return '';
  return editing.value.valueType === 'NUMBER' && editing.value.unit
    ? `请输入整数，单位：${editing.value.unit}`
    : '请输入配置值';
});

/** 单位提示：讲清「这个数字代表什么」，比在输入框旁边只挂一个「天」有用 */
const unitHint = computed(() => {
  if (!editing.value) return '';
  if (editing.value.valueType === 'PERCENT') return '填 0~100 的整数，13 表示 13%';
  if (editing.value.unit === '分') return '以「分」为单位：200000 分 = 2000.00 元（企业授信上限）';
  return '';
});

/** 金额换算：让运营在填「分」的同时看到「元」，避免多写两个零 */
const moneyHint = computed(() => {
  const raw = value.value.trim();
  if (!/^\d+$/.test(raw)) return '';
  const cents = Number(raw);
  if (!Number.isFinite(cents)) return '';
  return `${(cents / 100).toFixed(2)} 元`;
});

/** 保存后的展示值预览：与列表里那一列同口径，所见即所得 */
const preview = computed(() => {
  if (!editing.value) return '';
  if (boolValue.value && editing.value.valueType === 'BOOLEAN') return '开启';
  if (editing.value.valueType === 'BOOLEAN' && !boolValue.value) return '关闭';
  const raw = value.value.trim();
  if (!raw) return '未设置';
  if (editing.value.valueType === 'MONEY_CENT' && /^\d+$/.test(raw)) {
    return `¥${(Number(raw) / 100).toLocaleString('zh-CN', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    })}`;
  }
  return editing.value.unit ? `${raw} ${editing.value.unit}` : raw;
});

/** 悬停提示：列表显示的是加工后的值，排障时仍要能一眼看到库里真正存的是什么 */
function rawTitle(row: ConfigVO): string {
  const unit = row.unit ? ` ${row.unit}` : '';
  return `原始值：${row.configValue || '空'}${unit}`;
}

function openEdit(row: ConfigVO): void {  editing.value = row;
  value.value = row.configValue ?? '';
  boolValue.value = String(row.configValue).toLowerCase() === 'true';
  formVisible.value = true;
}

function reset(): void {
  editing.value = null;
  value.value = '';
  boolValue.value = false;
}

async function submit(): Promise<void> {
  const row = editing.value;
  if (!row) return;
  const next = row.valueType === 'BOOLEAN'
    ? (boolValue.value ? 'true' : 'false')
    : value.value.trim();
  if (!next) {
    ElMessage.warning('请填写配置值');
    return;
  }
  // 前端只挡「明显填错」：数字类里出现字母、百分比超过 100，省一次往返
  const numeric = ['NUMBER', 'MONEY_CENT', 'PERCENT'].includes(row.valueType);
  if (numeric && !/^\d+$/.test(next)) {
    ElMessage.warning(`${row.configName}只能填非负整数`);
    return;
  }
  if (row.valueType === 'PERCENT' && Number(next) > 100) {
    ElMessage.warning(`${row.configName}不能大于 100`);
    return;
  }
  submitting.value = true;
  try {
    await unwrap(systemApi.updateConfigValue(row.configKey, next));
    ElMessage.success(`「${row.configName}」已更新，后续判断立刻按新值执行`);
    formVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
/* 两行单元格：中文名 13px 正文色 + （键名 12px 弱色 + 分组小签）一行。
   全局 .el-table td 高 44px，这里两行共约 33px，不能再加第三行。 */
.cfg-item {
  line-height: 1.3;
}
.cfg-item__name {
  display: block;
  overflow: hidden;
  font-size: var(--fs-label);
  font-weight: var(--fw-medium);
  color: var(--sent-ink);
  white-space: nowrap;
  text-overflow: ellipsis;
}
/* 第二行：键名可压缩省略，分组小签不可压缩（签被切一半比不显示更难看） */
.cfg-item__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 1px;
}
/* 分组标签：11px 描边小签，解释「这项属于哪块业务」，不抢名字的视觉重量 */
.cfg-item__group {
  flex: none;
  padding: 0 5px;
  font-size: 11px;
  line-height: 15px;
  color: var(--sent-ink-3);
  background: var(--sent-fill);
  border-radius: var(--r-tag);
}
.cfg-item__key {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink-3);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cfg-value {
  font-size: var(--fs-meta);
  font-weight: var(--fw-medium);
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
  white-space: nowrap;
}
/* 空值不该看起来像「0」：降一档色阶，一眼能看出这项还没配 */
.cfg-value--empty {
  color: var(--sent-ink-3);
  font-weight: var(--fw-normal);
}

.cfg-desc {
  display: block;
  overflow: hidden;
  font-size: var(--fs-meta);
  color: var(--sent-ink-2);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cfg-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}
.cfg-head__name {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  color: var(--sent-ink);
}
.cfg-head__key {
  font-size: 12px;
  color: var(--sent-ink-3);
  background: var(--sent-fill);
  border-radius: var(--r-tag);
  padding: 1px 6px;
}
/* 说明放在输入框上方：先看「改了会发生什么」，再动手填 */
.cfg-head__desc {
  margin: 0 0 var(--sp-4);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-meta);
  line-height: 1.6;
  color: var(--sent-ink-2);
  background: var(--sent-bg);
  border-left: 2px solid var(--sent-primary);
  border-radius: 0 var(--r-tag) var(--r-tag) 0;
}
.cfg-form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--sent-ink-3);
}
.cfg-preview {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-meta);
  color: var(--sent-ink-3);
}
.cfg-preview b {
  font-weight: var(--fw-medium);
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
}
</style>
