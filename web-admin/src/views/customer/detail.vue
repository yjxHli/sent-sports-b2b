<template>
  <div>
    <h2 class="sent-page-title">客户详情</h2>
    <div class="sent-panel">
      <div class="sent-form-row">
        <el-button link type="primary" @click="router.push('/customer/list')">返回列表</el-button>
        <el-button link @click="router.push('/customer/enterprise')">企业审核与授信</el-button>
        <span class="customer-id">{{ customerId }}</span>
      </div>

      <el-descriptions :column="3" border v-loading="loading">
        <el-descriptions-item label="客户 ID">{{ detail?.customerId ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail?.name ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="客户类型"><StatusTag :status="detail?.type" /></el-descriptions-item>
        <el-descriptions-item label="手机号">{{ maskPhone(detail?.phone) }}</el-descriptions-item>
        <el-descriptions-item label="客户等级">{{ detail?.level ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="归属员工">{{ detail?.ownerStaffId ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="授信额度">{{ fmtMoney(detail?.creditLimitCent) }}</el-descriptions-item>
        <el-descriptions-item label="积分余额">{{ detail?.pointsBalance ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="成长值">{{ detail?.growthValue ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtDateTime(detail?.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">客户运营</h3>
      <div class="sent-form-row">
        <el-button type="primary" @click="levelVisible = true">授予等级</el-button>
        <el-button @click="pointVisible = true">积分调整</el-button>
        <el-button @click="transferVisible = true">改派归属</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-dialog v-model="levelVisible" title="授予客户等级" width="460px">
      <el-form label-width="110px">
        <el-form-item label="目标等级">
          <el-select v-model="levelForm.level" style="width: 100%">
            <el-option
              v-for="l in CUSTOMER_LEVELS"
              :key="l.value"
              :label="`${l.label}（${l.value}）`"
              :value="l.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker
            v-model="levelForm.validTo"
            type="datetime"
            placeholder="选择到期时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="levelForm.reason" type="textarea" :rows="2" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitLevel">确认授级</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pointVisible" title="积分调整" width="460px">
      <el-form label-width="110px">
        <el-form-item label="增减积分">
          <el-input-number v-model="pointForm.delta" :min="-100000" :max="100000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="pointForm.bizType" placeholder="选填，如 ORDER / MANUAL" />
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="pointForm.bizNo" placeholder="选填" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="pointForm.reason" type="textarea" :rows="2" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPoints">确认调整</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="改派归属" width="440px">
      <el-form label-width="110px">
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
          <el-input v-model="transferForm.reason" type="textarea" :rows="2" placeholder="必填" />
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
 * 客户详情（P6-39）：基础档案 + 授信概览 + 授级/积分调整/改派三个运营动作。
 * 会员侧 /member/* 接口返回的是当前登录者信息，不能拿来当客户档案，故这里只调客户域接口。
 */
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customerApi, systemApi, CUSTOMER_LEVELS } from '@/api';
import { fmtDateTime, fmtMoney, maskPhone } from '@sent/shared';
import type { StaffVO } from '@sent/shared';
import StatusTag from '@/components/StatusTag.vue';

const route = useRoute();
const router = useRouter();
const customerId = Number(route.params.id ?? 0);

const detail = ref<any>(null);
const loading = ref(false);
const submitting = ref(false);

const levelVisible = ref(false);
const levelForm = reactive<{ level: string; validTo: Date | null; reason: string }>({
  level: 'LV2', validTo: null, reason: '',
});

const pointVisible = ref(false);
const pointForm = reactive<{ delta: number; reason: string; bizType: string; bizNo: string }>({
  delta: 0, reason: '', bizType: '', bizNo: '',
});

const transferVisible = ref(false);
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
  if (!customerId) return;
  loading.value = true;
  try {
    detail.value = await unwrap(customerApi.detail(customerId));
  } catch (error) {
    detail.value = null;
    ElMessage.error((error as Error)?.message || '客户详情加载失败');
  } finally {
    loading.value = false;
  }
}

async function run(task: Promise<unknown>, text: string, close: () => void): Promise<void> {
  submitting.value = true;
  try {
    await task;
    ElMessage.success(text);
    close();
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '操作失败');
  } finally {
    submitting.value = false;
  }
}

function submitLevel(): void {
  if (!levelForm.reason.trim() || !levelForm.validTo) {
    ElMessage.warning('请选择有效期并填写原因');
    return;
  }
  void run(
    unwrap(customerApi.grantLevel(
      customerId, levelForm.level, levelForm.validTo.getTime(), levelForm.reason.trim(),
    )),
    '等级已授予',
    () => { levelVisible.value = false; },
  );
}

function submitPoints(): void {
  if (!pointForm.reason.trim() || pointForm.delta === 0) {
    ElMessage.warning('请填写非零积分与调整原因');
    return;
  }
  void run(
    unwrap(customerApi.adjustPoints(
      customerId, Number(pointForm.delta), pointForm.reason.trim(),
      pointForm.bizType || undefined, pointForm.bizNo || undefined,
    )),
    '积分已调整',
    () => { pointVisible.value = false; },
  );
}

function submitTransfer(): void {
  if (!transferForm.reason.trim()) {
    ElMessage.warning('请填写改派原因');
    return;
  }
  void run(
    unwrap(customerApi.transfer(customerId, Number(transferForm.toStaffId), transferForm.reason.trim())),
    '改派成功',
    () => { transferVisible.value = false; },
  );
}

onMounted(() => { void loadStaff(); void load(); });
</script>

<style scoped>
.customer-id { margin-left: auto; font-weight: 600; color: #12664f; }
.section-title { margin: 22px 0 12px; font-size: 15px; color: #33463f; }
</style>
