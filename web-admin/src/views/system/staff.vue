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
      <p class="sent-hint">
        新增员工初始口令为 123456，请提醒本人首次登录后修改；强制全端下线端点后端 P4 待补。
      </p>
    </div>

    <el-dialog v-model="formVisible" :title="editing ? '编辑员工' : '新增员工'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="登录名">
          <el-input v-model="staffForm.username" :disabled="editing" placeholder="必填" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="staffForm.name" placeholder="必填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="staffForm.phone" placeholder="必填，登录与找回凭据" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="staffForm.roleCode"
            filterable
            allow-create
            default-first-option
            placeholder="选择角色，留空默认 SALES"
            style="width: 100%"
          >
            <el-option
              v-for="opt in roleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
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
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi } from '@/api';
import { openConfirm } from '@/utils/confirm';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const columns: TableColumn[] = [
  { prop: 'staffId', label: '员工 ID', width: 130 },
  { prop: 'username', label: '登录名', minWidth: 150 },
  { prop: 'name', label: '姓名', minWidth: 120 },
  { prop: 'phone', label: '手机号', width: 140 },
  { prop: 'roleCode', label: '角色', width: 180 },
  { prop: 'enabled', label: '状态', width: 110, align: 'center' },
];

const { rows, total, loading, page, size, load, onPage, onSize } = useTable<any>({
  fetch: (params) => unwrap(systemApi.staffList(params)),
  errorMessage: '员工列表加载失败',
});
const submitting = ref(false);
/** 角色下拉：编码与中文名同源 sys_role，避免运营手敲出库里不存在的角色编码 */
const roleOptions = ref<{ value: string; label: string }[]>([]);

const formVisible = ref(false);
const editing = ref<number | null>(null);
const staffForm = reactive<{ username: string; name: string; phone: string; roleCode: string }>({
  username: '', name: '', phone: '', roleCode: '',
});

async function loadRoles(): Promise<void> {
  try {
    const list = await unwrap(systemApi.roles());
    roleOptions.value = (list ?? []).map((role) => ({
      value: String(role.roleCode),
      label: `${role.roleCode} · ${role.roleName}`,
    }));
  } catch {
    roleOptions.value = [];
  }
}

function openCreate(): void {
  editing.value = null;
  staffForm.username = '';
  staffForm.name = '';
  staffForm.phone = '';
  staffForm.roleCode = '';
  void loadRoles();
  formVisible.value = true;
}

function openEdit(row: any): void {
  editing.value = Number(row.staffId);
  staffForm.username = String(row.username ?? '');
  staffForm.name = String(row.name ?? '');
  staffForm.phone = String(row.phone ?? '');
  staffForm.roleCode = String(row.roleCode ?? '');
  void loadRoles();
  formVisible.value = true;
}

async function submitForm(): Promise<void> {
  if (!staffForm.username.trim() || !staffForm.name.trim()) {
    ElMessage.warning('请填写登录名与姓名');
    return;
  }
  if (!staffForm.phone.trim()) {
    ElMessage.warning('请填写手机号（登录与找回凭据，不能为空）');
    return;
  }
  const payload = {
    username: staffForm.username.trim(),
    name: staffForm.name.trim(),
    phone: staffForm.phone.trim(),
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


</script>
