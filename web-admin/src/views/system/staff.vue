<template>
  <div>
    <h2 class="sent-page-title">员工管理</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="员工列表"
        :operation-width="230"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-button type="primary" @click="openCreate">新增员工</el-button>
          <el-button @click="router.push('/system/role')">角色权限矩阵</el-button>
        </template>
        <template #col-enabled="{ row }">
          <StatusTag :label="row.enabled === false ? '已停用' : '启用中'" :tone="row.enabled === false ? 'info' : 'success'" />
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="forceLogout(row)">强制下线</el-button>
          <el-popconfirm title="确认删除该员工？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </SentTable>
      <p class="sent-hint">强制全端下线端点后端 P4 待补；操作前需输入确认短语换取一次性令牌。</p>
    </div>

    <el-dialog v-model="formVisible" :title="editing ? '编辑员工' : '新增员工'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="登录名">
          <el-input v-model="staffForm.username" :disabled="editing" placeholder="必填" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="staffForm.name" placeholder="必填" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="staffForm.roleCode" placeholder="如 ADMIN / FINANCE" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/** 员工管理（P6-48）：增删改 + 强制全端下线（确认令牌，端点待后端补齐）。 */
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi } from '@/api';
import { openConfirm } from '@/utils/confirm';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const columns: TableColumn[] = [
  { prop: 'staffId', label: '员工 ID', width: 130 },
  { prop: 'username', label: '登录名', minWidth: 150 },
  { prop: 'name', label: '姓名', minWidth: 150 },
  { prop: 'roleCode', label: '角色', width: 150 },
  { prop: 'enabled', label: '状态', width: 120, align: 'center' },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const submitting = ref(false);

const formVisible = ref(false);
const editing = ref<number | null>(null);
const staffForm = reactive<{ username: string; name: string; roleCode: string }>({
  username: '', name: '', roleCode: '',
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(systemApi.staffList({ page: page.value, size: size.value }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '员工列表加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editing.value = null;
  staffForm.username = '';
  staffForm.name = '';
  staffForm.roleCode = '';
  formVisible.value = true;
}

function openEdit(row: any): void {
  editing.value = Number(row.staffId);
  staffForm.username = String(row.username ?? '');
  staffForm.name = String(row.name ?? '');
  staffForm.roleCode = String(row.roleCode ?? '');
  formVisible.value = true;
}

async function submitForm(): Promise<void> {
  if (!staffForm.username.trim() || !staffForm.name.trim()) {
    ElMessage.warning('请填写登录名与姓名');
    return;
  }
  const payload = {
    username: staffForm.username.trim(),
    name: staffForm.name.trim(),
    roleCode: staffForm.roleCode.trim() || undefined,
  };
  submitting.value = true;
  try {
    if (editing.value !== null) {
      await unwrap(systemApi.updateStaff(editing.value, payload));
    } else {
      await unwrap(systemApi.createStaff(payload));
    }
    ElMessage.success('已保存');
    formVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function remove(row: any): Promise<void> {
  try {
    await unwrap(systemApi.deleteStaff(Number(row.staffId)));
    ElMessage.success('已删除');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '删除失败');
  }
}

async function forceLogout(row: any): Promise<void> {
  try {
    const grant = await openConfirm({
      action: 'STAFF_LOGOUT_ALL',
      targetId: String(row.staffId),
      summary: `员工 ${row.name}（${row.username}）· 将撤销全部刷新令牌并强制重新登录`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    await unwrap(systemApi.logoutAll(Number(row.staffId), grant.token));
    ElMessage.success('已强制全端下线');
  } catch (error) {
    ElMessage.error((error as Error)?.message || '强制下线端点暂不可用（后端 P4 待补）');
  }
}

function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

onMounted(() => { void load(); });
</script>
