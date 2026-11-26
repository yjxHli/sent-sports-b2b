<template>
  <div>
    <h2 class="sent-page-title">违禁词库</h2>

    <div class="sent-panel sw-stats">
      <div class="sw-stat">
        <b>{{ stats.total }}</b>
        <span>词条总数</span>
      </div>
      <div class="sw-stat sw-stat--on">
        <b>{{ stats.enabled }}</b>
        <span>启用中</span>
      </div>
      <div class="sw-stat sw-stat--off">
        <b>{{ stats.disabled }}</b>
        <span>已停用</span>
      </div>
      <p class="sent-hint sw-stats__hint">
        词库实时作用于<b>聊天消息</b>（用户↔客服双向）与<b>昵称 / 登录名</b>（注册、改资料、建客服账号）。
        保存后<strong>立即生效，无需重启</strong>；停用只是暂时不拦，词条仍保留。
      </p>
    </div>

    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="违禁词库"
        :operation-width="200"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="query.keyword"
            placeholder="搜词或备注"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-select
            v-model="query.enabled"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @clear="query.enabled = undefined"
          >
            <el-option label="启用中" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button data-testid="sw-create" @click="openCreate">新增违禁词</el-button>
          <span class="sent-hint">「测试违禁词 / 傻X / 加V」供 E2E 断言，请勿删除</span>
        </template>

        <template #col-word="{ row }">
          <b class="sw-word">{{ row.word }}</b>
        </template>
        <template #col-categoryLabel="{ row }">
          <span class="sw-category" :class="`sw-category--${row.category.toLowerCase()}`">
            {{ row.categoryLabel }}
          </span>
        </template>
        <template #col-enabled="{ row }">
          <StatusTag :label="row.enabled ? '启用中' : '已停用'" :tone="row.enabled ? 'success' : 'info'" />
        </template>
        <template #col-updatedAtMillis="{ row }">
          {{ fmtDateTime(Number(row.updatedAtMillis ?? 0)) }}
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.enabled ? 'warning' : 'success'"
            data-testid="sw-toggle"
            @click="toggle(row)"
          >{{ row.enabled ? '停用' : '启用' }}</el-button>
          <el-popconfirm title="确认删除该违禁词？删除后立即不再拦截。" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </SentTable>

      <p class="sent-hint">
        写法建议：只录<b>完整词组</b>（如「加微信」）。录单字（如「操」）会把「操作」「操场」一起拦掉，
        这类误杀比漏拦更伤业务。
      </p>
    </div>

    <el-dialog v-model="formVisible" :title="editing === null ? '新增违禁词' : '编辑违禁词'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="违禁词" required>
          <el-input
            v-model="form.word"
            data-testid="sw-word"
            maxlength="64"
            show-word-limit
            placeholder="如：加微信"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" placeholder="命中了怎么处理 / 词从哪来" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="sw-save" @click="submit">
          保存并立即生效
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 违禁词库（V46）：把原先写死在 classpath 的词表搬到管理端。
 *
 * - 列表走后端分页 + 筛选（词/备注、分类、启停），不再前端切片；
 * - 增删改 / 启停后端都会重建前缀树，页面只需重新拉一次列表 + 统计；
 * - 分类中文名由后端下发（`categoryLabel`），前端只维护下拉选项，避免两处口径漂移。
 */
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { systemApi, type SensitiveWordForm, type SensitiveWordVO } from '@/api';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const CATEGORY_OPTIONS = [
  { value: 'PROFANITY', label: '辱骂脏话' },
  { value: 'AD', label: '广告导流' },
  { value: 'ILLEGAL', label: '违法违禁' },
  { value: 'OTHER', label: '其他' },
];

const columns: TableColumn[] = [
  // 2026-11-26 用户要求：取消序号列；列表顺序由后端按分类+首字拼音给出
  { prop: 'word', label: '违禁词', minWidth: 160 },
  { prop: 'categoryLabel', label: '分类', width: 120 },
  { prop: 'enabled', label: '状态', width: 110, align: 'center' },
  { prop: 'remark', label: '备注', minWidth: 220 },
  { prop: 'updatedAtMillis', label: '更新时间', width: 170 },
];

const submitting = ref(false);
const stats = reactive({ total: 0, enabled: 0, disabled: 0 });
const query = reactive<{ keyword: string; category: string; enabled: number | undefined }>({
  keyword: '', category: '', enabled: undefined,
});
const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<
  SensitiveWordVO
>({
  fetch: (params) => unwrap(systemApi.sensitiveWords({
    ...params,
    keyword: query.keyword.trim() || undefined,
    category: query.category || undefined,
    // el-select 清空时可能把值置成 ''，Number 型查询参数必须归一成 undefined，
    // 否则后端 @RequestParam Integer 绑定会 400
    enabled: typeof query.enabled === 'number' ? query.enabled : undefined,
  })),
  errorMessage: '违禁词库加载失败',
});

const formVisible = ref(false);
const editing = ref<number | null>(null);
const form = reactive<Required<SensitiveWordForm>>({
  word: '', category: 'AD', enabled: true, remark: '',
});


async function loadStats(): Promise<void> {
  try {
    const data = await unwrap(systemApi.sensitiveWordStats());
    stats.total = Number(data?.total ?? 0);
    stats.enabled = Number(data?.enabled ?? 0);
    stats.disabled = Number(data?.disabled ?? 0);
  } catch {
    // 统计失败不影响列表使用
  }
}


function openCreate(): void {
  editing.value = null;
  form.word = '';
  form.category = 'AD';
  form.enabled = true;
  form.remark = '';
  formVisible.value = true;
}

function openEdit(row: SensitiveWordVO): void {
  editing.value = Number(row.id);
  form.word = row.word;
  form.category = row.category;
  form.enabled = row.enabled;
  form.remark = row.remark ?? '';
  formVisible.value = true;
}

async function submit(): Promise<void> {
  const word = form.word.trim();
  if (!word) {
    ElMessage.warning('请填写违禁词');
    return;
  }
  submitting.value = true;
  try {
    const payload: SensitiveWordForm = {
      word,
      category: form.category,
      enabled: form.enabled,
      remark: form.remark.trim() || undefined,
    };
    if (editing.value === null) {
      await unwrap(systemApi.createSensitiveWord(payload));
      ElMessage.success(`「${word}」已加入词库，立即生效`);
    } else {
      await unwrap(systemApi.updateSensitiveWord(editing.value, payload));
      ElMessage.success(`「${word}」已更新，立即生效`);
    }
    formVisible.value = false;
    await Promise.all([load(), loadStats()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function toggle(row: SensitiveWordVO): Promise<void> {
  try {
    await unwrap(systemApi.toggleSensitiveWord(Number(row.id), !row.enabled));
    ElMessage.success(row.enabled ? `「${row.word}」已停用，不再拦截` : `「${row.word}」已启用`);
    await Promise.all([load(), loadStats()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态切换失败');
  }
}

async function remove(row: SensitiveWordVO): Promise<void> {
  try {
    await unwrap(systemApi.deleteSensitiveWord(Number(row.id)));
    ElMessage.success(`「${row.word}」已删除`);
    await Promise.all([load(), loadStats()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '删除失败');
  }
}

/** 列表由 useTable 自动加载，这里只补统计卡 */
onMounted(() => {
  void loadStats();
});
</script>

<style scoped>
/* 三个统计数字 + 一段口径说明：一条横带，不用三张卡 */
.sw-stats {
  display: flex;
  align-items: baseline;
  gap: 32px;
  flex-wrap: wrap;
}
.sw-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 72px;
}
.sw-stat b {
  font-size: 20px;
  font-weight: 500;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
}
.sw-stat span {
  font-size: 12px;
  color: var(--sent-ink-3);
}
.sw-stat--off b {
  color: var(--sent-ink-3);
}
.sw-stats__hint {
  flex: 1 1 320px;
  margin: 0;
}

.sw-word {
  font-size: 13px;
  font-weight: 500;
  color: var(--sent-ink);
}

/* 分类：6px 色点 + 文字。原来四种淡底药丸里有一个是薰衣草紫。 */
.sw-category {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sent-ink-2);
  white-space: nowrap;
}
.sw-category::before {
  content: '';
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sent-ink-3);
}
.sw-category--profanity::before {
  background: var(--sent-danger);
}
.sw-category--ad::before {
  background: var(--sent-warn);
}
.sw-category--illegal::before {
  background: var(--sent-primary);
}
</style>
