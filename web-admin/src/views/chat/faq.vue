<template>
  <div>
    <h2 class="sent-page-title">话术配置</h2>

    <div class="sent-panel">
      <SentTable
        :select-all-rows="fetchAll"
        data-testid="faq-list"
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="id"
        exportable
        export-name="智能客服话术"
        :operation-width="150"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <!-- 删除口径与发票管理一致：常态不显示勾选列，点「删除」进入删除态 → 勾选 → 输入「删除」换令牌 -->
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.id))"
            :summary="`已选择 ${selected.length} 条话术：${selected
              .map((r: any) => splitKeywords(r.keywords).slice(0, 2).join('/') || `#${r.id}`)
              .join('、')}`"
            :delete-fn="(ids, config) => unwrap(faqAdminApi.batchDelete(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input
            v-model="query.keyword"
            placeholder="搜关键词或回复"
            clearable
            style="width: 200px"
            data-testid="faq-search"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            data-testid="faq-status-filter"
            @change="onSearch"
            @clear="query.status = ''"
          >
            <el-option label="启用中" value="ENABLED" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button data-testid="faq-create-btn" @click="openCreate">新增话术</el-button>
        </template>

        <!-- 关键词：一条 Tag 一个词，超过三个折叠成 +N（列宽有限，别把表格撑成两行） -->
        <template #col-keywords="{ row }">
          <div class="faq-tags">
            <el-tag
              v-for="word in splitKeywords(row.keywords).slice(0, 3)"
              :key="word"
              class="faq-tag"
              size="small"
              effect="plain"
            >{{ word }}</el-tag>
            <span v-if="splitKeywords(row.keywords).length > 3" class="faq-tags__more">
              +{{ splitKeywords(row.keywords).length - 3 }}
            </span>
          </div>
        </template>

        <!-- 回复内容：单行截断，悬停看全文（整段回复塞进表格会把行高撑爆） -->
        <template #col-replyContent="{ row }">
          <span class="faq-reply" :title="row.replyContent">{{ row.replyContent }}</span>
        </template>

        <template #col-hitCount="{ row }">
          <span class="faq-hit" :class="{ 'faq-hit--zero': Number(row.hitCount) === 0 }">
            {{ Number(row.hitCount ?? 0) }}
          </span>
        </template>

        <template #col-status="{ row }">
          <StatusTag
            :label="row.status === 'ENABLED' ? '启用中' : '已停用'"
            :tone="row.status === 'ENABLED' ? 'success' : 'info'"
          />
        </template>

        <template #col-updatedAtMillis="{ row }">
          {{ fmtDateTime(Number(row.updatedAtMillis ?? 0)) }}
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" data-testid="faq-edit-btn" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm
            :title="row.status === 'ENABLED'
              ? '停用后机器人不再回复这条话术，确认停用？'
              : '启用后机器人立刻开始使用这条话术，确认启用？'"
            width="240"
            @confirm="toggle(row)"
          >
            <template #reference>
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'warning' : 'success'"
                data-testid="faq-toggle"
              >{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </SentTable>

      <p class="sent-hint">
        保存后<b>立即生效</b>，客户下一条消息就会用新话术，不需要重启也不需要等缓存。
        停用只是让机器人暂时不回复，条目与命中次数都会保留；
        <b>删除</b>是软删（条目从列表与机器人候选集里一起消失，命中次数留在库里可追溯），不可撤销。
      </p>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editing === null ? '新增话术' : '编辑话术'"
      width="560px"
      data-testid="faq-dialog"
    >
      <el-form label-width="88px">
        <el-form-item label="关键词" required>
          <el-input
            v-model="form.keywords"
            data-testid="faq-keyword-input"
            maxlength="255"
            placeholder="多个关键词用逗号分隔，如：退换货,退货,换货"
          />
          <p class="faq-form-hint">客户消息里出现任意一个词就算命中；词越多覆盖越广，但别把单个字当关键词。</p>
        </el-form-item>
        <el-form-item label="回复内容" required>
          <el-input
            v-model="form.replyContent"
            data-testid="faq-reply-input"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="机器人替你说的那句话，如：支持7天无理由退换，在订单详情点「申请售后」就行"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.enabled"
            data-testid="faq-status-switch"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="faq-save-btn" @click="submit">
          保存并立即生效
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 智能客服话术配置（V60）：管理端维护机器人知识库。
 *
 * - 列表走后端分页 + 筛选（关键词 / 状态），不在前端切片；
 * - 增改 / 启停后端都会刷新知识库快照，页面只需重新拉一次列表；
 * - 关键词在表格里拆成 Tag（一条话术常配 3~5 个同义词，一整串逗号读不下去）；
 * - 停用走二次确认：话术直接影响客户看到的回复，误点一下就是一次内容事故。
 */
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { faqAdminApi, type FaqForm, type FaqVO } from '@/api';
import { useTable } from '@/composables/useTable';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'keywords', label: '关键词', minWidth: 220 },
  { prop: 'replyContent', label: '回复内容', minWidth: 320 },
  { prop: 'hitCount', label: '命中次数', width: 96, align: 'center' },
  { prop: 'status', label: '状态', width: 100, align: 'center' },
  { prop: 'updatedAtMillis', label: '更新时间', width: 170 },
];

const query = reactive<{ keyword: string; status: string }>({ keyword: '', status: '' });
const { rows, total, loading, page, size, load, onPage, onSize, onSearch, fetchAll } = useTable<FaqVO>({
  fetch: (params) => unwrap(faqAdminApi.list({
    ...params,
    keyword: query.keyword.trim() || undefined,
    // el-select 清空会置成 ''，后端按 String 接，空白必须归一成 undefined 才不会被当成筛选条件
    status: query.status || undefined,
  })),
  errorMessage: '话术列表加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);

const formVisible = ref(false);
const submitting = ref(false);
const editing = ref<number | null>(null);
const form = reactive<{ keywords: string; replyContent: string; enabled: boolean }>({
  keywords: '', replyContent: '', enabled: true,
});

/** 拆词规则要与后端一致：英文逗号与中文逗号都算分隔符 */
function splitKeywords(raw: string | null | undefined): string[] {
  return String(raw ?? '').split(/[,，]/).map((s) => s.trim()).filter(Boolean);
}

function openCreate(): void {
  editing.value = null;
  form.keywords = '';
  form.replyContent = '';
  form.enabled = true;
  formVisible.value = true;
}

function openEdit(row: FaqVO): void {
  editing.value = Number(row.id);
  form.keywords = row.keywords;
  form.replyContent = row.replyContent;
  form.enabled = row.status === 'ENABLED';
  formVisible.value = true;
}

async function submit(): Promise<void> {
  const keywords = splitKeywords(form.keywords).join(',');
  const replyContent = form.replyContent.trim();
  if (!keywords) {
    ElMessage.warning('请填写关键词');
    return;
  }
  if (!replyContent) {
    ElMessage.warning('请填写回复内容');
    return;
  }
  submitting.value = true;
  try {
    const payload: FaqForm = {
      keywords,
      replyContent,
      status: form.enabled ? 'ENABLED' : 'DISABLED',
    };
    if (editing.value === null) {
      await unwrap(faqAdminApi.create(payload));
      ElMessage.success('话术已新增，客户下一条消息即生效');
    } else {
      await unwrap(faqAdminApi.update(editing.value, payload));
      ElMessage.success('话术已更新，客户下一条消息即生效');
    }
    formVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function toggle(row: FaqVO): Promise<void> {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  try {
    await unwrap(faqAdminApi.updateStatus(Number(row.id), next));
    ElMessage.success(next === 'ENABLED' ? '已启用，机器人立刻开始使用' : '已停用，机器人不再回复这条话术');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态切换失败');
  }
}

/** 列表由 useTable 自动加载（挂载即拉第一页），无需额外初始化 */
</script>

<style scoped>
/* 关键词 Tag：浅描边 + 12px 字，不抢回复内容的视觉重量 */
.faq-tags {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
  overflow: hidden;
}
.faq-tag {
  flex: none;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.faq-tags__more {
  flex: none;
  font-size: 12px;
  color: var(--sent-ink-3);
}

.faq-reply {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--sent-ink-2);
}

.faq-hit {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--sent-ink);
}
/* 从没被命中过的话术要能一眼看出来：运营据此决定改词还是删掉 */
.faq-hit--zero {
  color: var(--sent-ink-3);
}

.faq-form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--sent-ink-3);
}
</style>
