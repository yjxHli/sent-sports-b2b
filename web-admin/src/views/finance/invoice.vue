<template>
  <div>
    <h2 class="sent-page-title">发票管理</h2>
    <div class="sent-panel">
      <el-alert
        v-if="!loading && !rows.length"
        title="暂无开票申请，点击右上角「开具发票」按订单号新建并开具"
        type="info"
        :closable="false"
        show-icon
        class="list-alert"
      />
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="发票列表"
        :operation-width="240"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-button type="primary" @click="openCreate">开具发票</el-button>
        </template>
        <template #col-status="{ row }">
          <StatusTag :status="row.status" />
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" @click="openPreview(row)">预览 / 下载</el-button>
          <el-button link type="primary" @click="openIssue(row)">开票 / 换开</el-button>
        </template>
      </SentTable>
    </div>

    <el-dialog
      v-model="previewVisible"
      :title="`发票预览 · ${previewTitle}`"
      width="920px"
      top="6vh"
      destroy-on-close
      @closed="releasePreview"
    >
      <div v-loading="previewLoading" class="pdf-frame-wrap">
        <iframe v-if="previewUrl" :src="previewUrl" class="pdf-frame" title="发票 PDF 预览" />
        <el-empty v-else description="正在生成发票 PDF…" :image-size="60" />
      </div>
      <p class="sent-hint preview-hint">
        预览由浏览器内置 PDF 阅读器渲染；若此处空白（浏览器禁用了 PDF 插件），
        可直接点「下载 PDF」用本地阅读器打开。
      </p>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" :loading="downloading" @click="downloadPdf(previewRow)">
          下载 PDF
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="visible"
      :title="mode === 'create' ? '开具发票（按订单号新建）' : '开票 / 换开'"
      width="520px"
    >
      <el-form label-width="100px">
        <template v-if="mode === 'create'">
          <el-form-item label="订单号" required>
            <el-select
              v-model="form.orderNo"
              filterable
              allow-create
              default-first-option
              placeholder="选择最近订单，或直接输入订单号"
              style="width: 100%"
            >
              <el-option
                v-for="opt in orderOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="发票抬头" required>
            <el-input v-model="form.title" placeholder="如：森特体育用品有限公司" clearable />
          </el-form-item>
          <el-form-item label="税号">
            <el-input v-model="form.taxNo" placeholder="统一社会信用代码（选填）" clearable />
          </el-form-item>
          <el-form-item label="接收邮箱">
            <el-input v-model="form.email" placeholder="电子发票接收邮箱（选填）" clearable />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="发票申请">
            <el-input
              :model-value="`${form.invoiceNo || '待开票'} · ID ${form.invoiceId}`"
              disabled
            />
          </el-form-item>
        </template>
        <el-form-item label="PDF 地址">
          <el-input
            v-model="form.pdfUrl"
            placeholder="留空由系统按发票 ID 生成（推荐）；如需外部文件可填地址"
            clearable
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认开票</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 发票管理（P6-46，2026-11-08 收口）。
 *
 * 原缺陷：系统没有创建开票申请的入口，invoice_request 恒为 0 行；工具栏「开具发票」
 * 打开弹窗时把「发票 ID」写死成 1，提交 POST /admin/invoices/1/issue → 后端 markIssued
 * 返回 0 行 → 40401 / HTTP 404。
 *
 * 现在：工具栏开票 = 按订单号一次完成「新建申请 + 开具」（后端 POST /admin/invoices），
 * 订单号可从最近订单下拉选、也可手填；行内「开票 / 换开」仍走 /invoices/{id}/issue。
 * 两条路径都不会再提交一个不存在的 invoiceId。
 *
 * 2026-11-10 增补：发票 PDF 由后端现场渲染（增值税电子普通发票版式），
 * 行内「预览 / 下载」走 GET /admin/invoices/{id}/pdf —— 之前 pdfUrl 指向的
 * /files/invoice/*.pdf 从来不存在，点下载必然 404，只能靠人手填地址。
 */
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { financeApi, orderApi } from '@/api';
import { fmtDateTime, fmtMoney } from '@sent/shared';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

type Mode = 'create' | 'issue';
interface OrderOption { value: string; label: string }

const columns: TableColumn[] = [
  { prop: 'invoiceId', label: '发票 ID', width: 130 },
  { prop: 'invoiceNo', label: '发票号', minWidth: 180 },
  { prop: 'customerId', label: '客户 ID', width: 130 },
  { prop: 'amountCent', label: '票面金额', width: 140, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
  { prop: 'createdAt', label: '创建时间', width: 160, formatter: (_r, v) => fmtDateTime(v) },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const submitting = ref(false);

const visible = ref(false);
const mode = ref<Mode>('create');
const orderOptions = ref<OrderOption[]>([]);
const form = reactive({
  invoiceId: 0,
  invoiceNo: '',
  orderNo: '',
  title: '',
  taxNo: '',
  email: '',
  pdfUrl: '',
  remark: '',
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(financeApi.invoices({ page: page.value, size: size.value }));
    rows.value = data?.list ?? [];
    total.value = Number(data?.total ?? 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '发票列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadOrderOptions(): Promise<void> {
  try {
    const data = await unwrap(orderApi.list({ page: 1, size: 20 }));
    orderOptions.value = (data?.list ?? []).map((o: any) => ({
      value: String(o.orderNo),
      label: `${o.orderNo} · ${fmtMoney(o.payableCent)}`,
    }));
  } catch {
    orderOptions.value = [];
  }
}

function resetForm(): void {
  form.invoiceId = 0;
  form.invoiceNo = '';
  form.orderNo = '';
  form.title = '';
  form.taxNo = '';
  form.email = '';
  form.pdfUrl = '';
  form.remark = '';
}

function openCreate(): void {
  mode.value = 'create';
  resetForm();
  void loadOrderOptions();
  visible.value = true;
}

function openIssue(row: any): void {
  mode.value = 'issue';
  resetForm();
  form.invoiceId = Number(row?.invoiceId ?? 0);
  form.invoiceNo = String(row?.invoiceNo ?? '');
  form.pdfUrl = String(row?.pdfUrl ?? '');
  visible.value = true;
}

/* ---------------- 发票 PDF 预览 / 下载 ---------------- */

const previewVisible = ref(false);
const previewLoading = ref(false);
const previewUrl = ref('');
const previewTitle = ref('');
const previewRow = ref<any>(null);
const downloading = ref(false);

/** iframe 直接指向 blob: URL，浏览器自带 PDF 阅读器即可预览，不需要额外 PDF 组件 */
async function openPreview(row: any): Promise<void> {
  const invoiceId = Number(row?.invoiceId ?? 0);
  if (!(invoiceId > 0)) {
    ElMessage.warning('发票申请无效，请刷新列表后重试');
    return;
  }
  previewRow.value = row;
  previewTitle.value = String(row?.invoiceNo ?? invoiceId);
  previewLoading.value = true;
  releasePreview();
  previewVisible.value = true;
  try {
    const blob = await financeApi.invoicePdfBlob(invoiceId);
    previewUrl.value = URL.createObjectURL(blob);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '发票 PDF 生成失败');
    previewVisible.value = false;
  } finally {
    previewLoading.value = false;
  }
}

/** blob URL 不释放会一直占内存；destroy-on-close + 关闭回调各释放一次 */
function releasePreview(): void {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
  }
}

async function downloadPdf(row: any): Promise<void> {
  const invoiceId = Number(row?.invoiceId ?? 0);
  if (!(invoiceId > 0)) {
    ElMessage.warning('发票申请无效，请刷新列表后重试');
    return;
  }
  downloading.value = true;
  try {
    await financeApi.downloadInvoicePdf(invoiceId, String(row?.invoiceNo ?? ''));
    ElMessage.success('发票 PDF 已开始下载');
  } catch (error) {
    ElMessage.error((error as Error)?.message || '发票 PDF 下载失败');
  } finally {
    downloading.value = false;
  }
}

async function submit(): Promise<void> {
  const pdfUrl = form.pdfUrl.trim();
  if (mode.value === 'create' && (!form.orderNo.trim() || !form.title.trim())) {
    ElMessage.warning('请填写订单号与发票抬头');
    return;
  }
  if (mode.value === 'issue' && !(form.invoiceId > 0)) {
    ElMessage.warning('发票申请无效，请从列表重新选择');
    return;
  }

  submitting.value = true;
  try {
    if (mode.value === 'create') {
      const vo = await unwrap(financeApi.applyInvoice({
        orderNo: form.orderNo.trim(),
        title: form.title.trim(),
        taxNo: form.taxNo.trim() || undefined,
        email: form.email.trim() || undefined,
        pdfUrl: pdfUrl || undefined,
        remark: form.remark.trim() || undefined,
      }));
      ElMessage.success(`开票成功：${vo?.invoiceNo ?? ''}`);
    } else {
      await unwrap(financeApi.issueInvoice(
        form.invoiceId, pdfUrl || undefined, form.remark.trim() || undefined,
      ));
      ElMessage.success('开票 / 换开成功');
    }
    visible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '开票失败');
  } finally {
    submitting.value = false;
  }
}

function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

onMounted(() => { void load(); });
</script>

<style scoped>
.list-alert { margin-bottom: 14px; }
.pdf-frame-wrap {
  height: 62vh;
  min-height: 420px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f5f7fa;
  overflow: hidden;
}
.pdf-frame { width: 100%; height: 100%; border: 0; display: block; }
.preview-hint { margin: 8px 0 0; line-height: 1.6; }
</style>
