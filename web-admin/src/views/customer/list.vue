<template>
  <div>
    <h2 class="sent-page-title">客户列表</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="客户列表"
        :operation-width="160"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="query.keyword"
            placeholder="客户名称 / 手机号"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="query.level" placeholder="客户等级" clearable style="width: 150px">
            <el-option
              v-for="l in CUSTOMER_LEVELS"
              :key="l.value"
              :label="`${l.label}（${l.value}）`"
              :value="l.value"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/customer/enterprise')">企业审核与授信</el-button>
          <span class="sent-hint">数据范围（ALL/TEAM/SELF）由后端 SQL 改写决定，前端不做二次过滤</span>
        </template>

        <template #col-type="{ row }"><StatusTag :status="row.type" /></template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="router.push(`/customer/detail/${row.customerId}`)">
            详情
          </el-button>
          <el-button link type="warning" @click="openTransfer(row)">改派</el-button>
        </template>
      </SentTable>
    </div>

    <el-dialog v-model="transferVisible" title="客户改派" width="440px">
      <el-form label-width="100px">
        <el-form-item label="客户">
          <span>{{ transferTarget?.name }}（{{ transferTarget?.customerId }}）</span>
        </el-form-item>
        <el-form-item label="目标员工">
          <el-select v-model="transferForm.toStaffId" filterable placeholder="选择归属员工" style="width: 100%">
            <el-option
              v-for="s in staffOptions"
              :key="String(s.staffId)"
              :label="`${s.name}（${s.staffId}）`"
              :value="Number(s.staffId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="改派原因">
          <el-input v-model="transferForm.reason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTransfer">确认改派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 客户列表（P6-38）。列表数据范围由后端 DataScope 拦截器控制，前端只做展示，
 * 不在页面里按 staffId 过滤，避免与后端口径不一致造成越权或漏数据。
 */
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customerApi, systemApi, CUSTOMER_LEVELS } from '@/api';
import { fmtDateTime, fmtMoney, maskPhone } from '@sent/shared';
import type { StaffVO } from '@sent/shared';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const columns: TableColumn[] = [
  { prop: 'customerId', label: '客户 ID', width: 130 },
  { prop: 'name', label: '客户名称', minWidth: 160 },
  { prop: 'phone', label: '手机号', width: 150, formatter: (_r, v) => maskPhone(v) },
  { prop: 'type', label: '类型', width: 100, align: 'center' },
  { prop: 'level', label: '等级', width: 120, align: 'center' },
  { prop: 'ownerStaffId', label: '归属员工', width: 110, align: 'center' },
  { prop: 'creditLimitCent', label: '授信额度', width: 140, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'createdAt', label: '创建时间', width: 160, formatter: (_r, v) => fmtDateTime(v) },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const submitting = ref(false);
const query = reactive<{ keyword: string; level?: string }>({ keyword: '' });

const transferVisible = ref(false);
const transferTarget = ref<any>(null);
/** 默认取真库存在的员工 9004（销售经理），避免旧默认值 1 落到不存在的员工。 */
const transferForm = reactive<{ toStaffId: number; reason: string }>({ toStaffId: 9004, reason: '' });
const staffOptions = ref<StaffVO[]>([]);

async function loadStaff(): Promise<void> {
  try {
    const data = await unwrap(systemApi.staffList({ page: 1, size: 100 }));
    staffOptions.value = data?.list ?? [];
  } catch {
    staffOptions.value = [];
  }
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(customerApi.list({
      page: page.value,
      size: size.value,
      keyword: query.keyword || undefined,
      level: query.level || undefined,
    }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '客户列表加载失败');
  } finally {
    loading.value = false;
  }
}

function onSearch(): void {
  page.value = 1;
  void load();
}
function onPage(value: number): void {
  page.value = value;
  void load();
}
function onSize(value: number): void {
  size.value = value;
  page.value = 1;
  void load();
}

function openTransfer(row: any): void {
  transferTarget.value = row;
  transferForm.toStaffId = 9004;
  transferForm.reason = '';
  transferVisible.value = true;
}

async function submitTransfer(): Promise<void> {
  if (!transferTarget.value) return;
  if (!transferForm.reason.trim()) {
    ElMessage.warning('请填写改派原因');
    return;
  }
  submitting.value = true;
  try {
    await unwrap(customerApi.transfer(
      Number(transferTarget.value.customerId),
      Number(transferForm.toStaffId),
      transferForm.reason.trim(),
    ));
    ElMessage.success('改派成功');
    transferVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '改派失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => { void loadStaff(); void load(); });
</script>
