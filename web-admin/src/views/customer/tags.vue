<template>
  <div>
    <h2 class="sent-page-title">客户标签</h2>

    <div class="sent-panel">
      <SentTable
        data-testid="tag-list"
        :columns="columns"
        :rows="filteredRows"
        :loading="loading"
        row-key="tagId"
        :operation-width="190"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="搜标签名"
            clearable
            style="width: 180px"
            data-testid="tag-search"
          />
          <el-select
            v-model="categoryFilter"
            placeholder="全部分类"
            clearable
            style="width: 140px"
            data-testid="tag-category-filter"
            @clear="categoryFilter = ''"
          >
            <el-option
              v-for="opt in TAG_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-select
            v-model="statusFilter"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            data-testid="tag-status-filter"
            @clear="statusFilter = ''"
          >
            <el-option label="启用中" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" data-testid="tag-create-btn" @click="openCreate">新增标签</el-button>
        </template>

        <!-- 标签名：带分类配色的 chip，和客服端会话头部看到的是同一个样式 -->
        <template #col-name="{ row }">
          <span class="tag-chip" :class="`tag-chip--${row.colorKey}`">{{ row.name }}</span>
        </template>

        <template #col-category="{ row }">
          <span class="tag-cat" :class="`tag-cat--${row.colorKey}`">{{ row.categoryLabel }}</span>
        </template>

        <template #col-status="{ row }">
          <StatusTag
            :label="row.status === 'ACTIVE' ? '启用中' : '已停用'"
            :tone="row.status === 'ACTIVE' ? 'success' : 'info'"
          />
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" data-testid="tag-edit-btn" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm
            :title="row.status === 'ACTIVE'
              ? '停用后客服弹窗里不再出现这个标签（已打过的仍会显示），确认停用？'
              : '启用后客服可以重新选这个标签，确认启用？'"
            width="260"
            @confirm="toggle(row)"
          >
            <template #reference>
              <el-button
                link
                :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
                data-testid="tag-toggle"
              >{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm
            title="删除后不可恢复。若还有客户在用，系统会拒绝并告诉你还有几个客户。"
            width="260"
            @confirm="remove(row)"
          >
            <template #reference>
              <el-button link type="danger" data-testid="tag-delete-btn">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </SentTable>

      <p class="sent-hint">
        标签由后台统一维护，客服<b>只能选不能造</b>——一人一套说法，「犹豫观望」和「还在考虑」
        会同时出现在不同客服的屏幕上，标签就失去检索意义。
        分类决定颜色：<b>优质 / 正面</b>（绿）、<b>中性普通</b>（灰）、<b>风险留意</b>（琥珀）、
        <b>恶意</b>（红）。<b>停用</b>只是不再出现在客服弹窗里，已经打上的标签仍会显示；
        <b>删除</b>会被引用拦下（先停用即可）。
      </p>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editing === null ? '新增标签' : '编辑标签'"
      width="460px"
    >
      <!-- data-testid 挂在弹窗内部：el-dialog 不透传 attrs 到根节点 -->
      <div class="tag-form" data-testid="tag-dialog">
        <el-form label-width="72px">
          <el-form-item label="标签名" required>
            <el-input
              v-model="form.name"
              data-testid="tag-name-input"
              maxlength="32"
              show-word-limit
              placeholder="如：羊毛党"
            />
          </el-form-item>
          <el-form-item label="分类" required>
            <el-select v-model="form.category" data-testid="tag-category-input" style="width: 100%">
              <el-option
                v-for="opt in TAG_CATEGORY_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <p class="tag-form__hint">
          分类只影响客服端看到的颜色与分组顺序，不影响任何业务规则；
          标签不会被客户看到，也不会出现在用户端任何页面。
        </p>
      </div>
      <template #footer>
        <el-button data-testid="tag-cancel-btn" @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="tag-save-btn" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 客户标签管理（V63）：决定「客服弹窗里能选到哪些标签」。
 *
 * - 目录一次全量拉取（几十条，不分页），筛选在前端做，响应更快也不会因为
 *   停用项被分页藏在第 2 页而找不到；
 * - 分类改名只影响展示，颜色键与分组顺序由后端下发，页面不写死映射；
 * - 删除会被后端按引用拦下：还有客户在用时提示「还有 N 个客户在用」，
 *   所以这里不做「先删关联」这类猜测性操作。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { customerTagAdminApi, TAG_CATEGORY_OPTIONS, type CustomerTagForm, type CustomerTagVO } from '@/api';
import { unwrap } from '@/utils/http';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  // 2026-11-26 用户要求：取消序号列，列表直接从标签名开始
  { prop: 'name', label: '标签名', minWidth: 180 },
  { prop: 'category', label: '分类', width: 130 },
  { prop: 'status', label: '状态', width: 100, align: 'center' },
];

const rows = ref<CustomerTagVO[]>([]);
const loading = ref(false);
const keyword = ref('');
const categoryFilter = ref('');
const statusFilter = ref('');

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return rows.value.filter((row) => {
    if (kw && !String(row.name).toLowerCase().includes(kw)) return false;
    if (categoryFilter.value && row.category !== categoryFilter.value) return false;
    if (statusFilter.value && row.status !== statusFilter.value) return false;
    return true;
  });
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = (await unwrap(customerTagAdminApi.catalog(true))) ?? [];
  } catch (error) {
    ElMessage.error((error as Error)?.message || '标签列表加载失败');
  } finally {
    loading.value = false;
  }
}

const formVisible = ref(false);
const submitting = ref(false);
const editing = ref<number | null>(null);
const form = reactive<CustomerTagForm>({ name: '', category: 'NEUTRAL' });

function openCreate(): void {
  editing.value = null;
  form.name = '';
  form.category = 'NEUTRAL';
  formVisible.value = true;
}

function openEdit(row: CustomerTagVO): void {
  editing.value = Number(row.tagId);
  form.name = row.name;
  form.category = row.category;
  formVisible.value = true;
}

async function submit(): Promise<void> {
  const name = form.name.trim();
  if (!name) {
    ElMessage.warning('请填写标签名');
    return;
  }
  submitting.value = true;
  try {
    const payload: CustomerTagForm = { name, category: form.category };
    if (editing.value === null) {
      await unwrap(customerTagAdminApi.create(payload));
      ElMessage.success('标签已新增，客服弹窗立即可选');
    } else {
      await unwrap(customerTagAdminApi.update(editing.value, payload));
      ElMessage.success('标签已更新');
    }
    formVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function toggle(row: CustomerTagVO): Promise<void> {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  try {
    await unwrap(customerTagAdminApi.updateStatus(row.tagId, next));
    ElMessage.success(next === 'ACTIVE' ? '已启用' : '已停用，客服弹窗里不再出现');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态切换失败');
  }
}

async function remove(row: CustomerTagVO): Promise<void> {
  try {
    await unwrap(customerTagAdminApi.remove(row.tagId));
    ElMessage.success('标签已删除');
    await load();
  } catch (error) {
    // 后端文案里带「还有 N 个客户在用」，直接用，不要再包一层「删除失败」
    ElMessage.error((error as Error)?.message || '删除失败');
  }
}

onMounted(load);
</script>

<style scoped>
/* 标签 chip：与客服端会话头部同款（四档配色来自 theme.css 令牌） */
.tag-chip {
  display: inline-flex;
  align-items: center;
  max-width: 160px;
  padding: 1px 8px;
  border: 1px solid var(--tag-bd, var(--sent-line-2));
  border-radius: 999px;
  background: var(--tag-bg, var(--sent-fill));
  color: var(--tag-fg, var(--sent-ink-2));
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tag-chip--excellent {
  --tag-fg: var(--sent-tag-excellent-fg);
  --tag-bg: var(--sent-tag-excellent-bg);
  --tag-bd: var(--sent-tag-excellent-bd);
}
.tag-chip--neutral {
  --tag-fg: var(--sent-tag-neutral-fg);
  --tag-bg: var(--sent-tag-neutral-bg);
  --tag-bd: var(--sent-tag-neutral-bd);
}
.tag-chip--risk {
  --tag-fg: var(--sent-tag-risk-fg);
  --tag-bg: var(--sent-tag-risk-bg);
  --tag-bd: var(--sent-tag-risk-bd);
}
.tag-chip--malicious {
  --tag-fg: var(--sent-tag-malicious-fg);
  --tag-bg: var(--sent-tag-malicious-bg);
  --tag-bd: var(--sent-tag-malicious-bd);
}

/* 分类列：小圆点 + 中文名，颜色比 chip 更弱一档，避免一屏两处抢眼 */
.tag-cat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-meta);
  color: var(--sent-ink-2);
}
.tag-cat::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tag-fg, var(--sent-ink-3));
}
.tag-cat--excellent { --tag-fg: var(--sent-tag-excellent-fg); }
.tag-cat--neutral { --tag-fg: var(--sent-tag-neutral-fg); }
.tag-cat--risk { --tag-fg: var(--sent-tag-risk-fg); }
.tag-cat--malicious { --tag-fg: var(--sent-tag-malicious-fg); }

.tag-sort {
  font-size: var(--fs-meta);
  color: var(--sent-ink-3);
  font-variant-numeric: tabular-nums;
}

.tag-form__hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sent-ink-3);
}
</style>
