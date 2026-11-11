<template>
  <div>
    <h2 class="sent-page-title">会员与积分</h2>

    <div class="sent-panel account-bar">
      <span class="account-label">当前查看账号</span>
      <el-select
        v-model="targetCustomerId"
        filterable
        remote
        clearable
        placeholder="输入昵称 / 手机号搜索客户"
        :remote-method="searchCustomers"
        :loading="searching"
        style="width: 300px"
        @change="onCustomerChange"
      >
        <el-option
          v-for="c in customerOptions"
          :key="Number(c.customerId)"
          :label="customerLabel(c)"
          :value="Number(c.customerId)"
        />
      </el-select>
      <template v-if="targetCustomer">
        <span class="account-meta">
          客户 ID {{ targetCustomer.customerId }}
          · {{ targetCustomer.type === 'ENTERPRISE' ? '企业客户' : '个人客户' }}
          · {{ targetCustomer.type === 'ENTERPRISE' ? unlockState(credit?.creditLimitCent) : '非企业授信对象' }}
        </span>
      </template>
      <el-button :loading="loading" @click="loadOverview">刷新</el-button>
    </div>
    <p class="sent-hint">
      顶部三张卡与下方运营动作都作用于「当前查看账号」，不再是写死的占位客户；
      切换账号后会自动重新取数。
    </p>

    <el-row :gutter="16" class="overview">
      <el-col :xs="24" :sm="8">
        <div class="sent-panel metric">
          <span class="metric-label">当前账号等级</span>
          <strong class="metric-value">{{ levelLabel }}</strong>
          <span class="metric-sub">
            成长值 {{ level?.growthValue ?? '--' }} / 下一档 {{ level?.nextThreshold ?? '--' }}
          </span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="sent-panel metric">
          <span class="metric-label">可用积分</span>
          <strong class="metric-value">{{ points?.availablePoints ?? '--' }}</strong>
          <span class="metric-sub">累计成长值 {{ level?.growthValue ?? '--' }}</span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="sent-panel metric">
          <span class="metric-label">授信可用额度</span>
          <strong class="metric-value">{{ fmtMoney(credit?.availableCent) }}</strong>
          <span class="metric-sub">
            总额度 {{ fmtMoney(credit?.creditLimitCent) }} · 已用 {{ fmtMoney(credit?.creditUsedCent) }}
          </span>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <div class="sent-panel">
          <h3 class="section-title">积分调整</h3>
          <p class="target-line">目标客户：{{ targetText }}</p>
          <el-form label-width="100px">
            <el-form-item label="增减积分">
              <el-input-number v-model="pointForm.delta" :min="-100000" :max="100000" style="width: 100%" />
            </el-form-item>
            <el-form-item label="原因">
              <el-input v-model="pointForm.reason" type="textarea" :rows="2" placeholder="必填" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="submitting" @click="adjustPoints">提交调整</el-button>
        </div>

        <div class="sent-panel">
          <h3 class="section-title">授予等级</h3>
          <p class="target-line">目标客户：{{ targetText }}</p>
          <el-form label-width="100px">
            <el-form-item label="目标等级">
              <el-select v-model="grantForm.level" style="width: 100%">
                <el-option
                  v-for="l in CUSTOMER_LEVELS"
                  :key="l.value"
                  :label="`${l.label}（${l.value}）`"
                  :value="l.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="有效期至">
              <el-date-picker v-model="grantForm.validTo" type="datetime" style="width: 100%" />
            </el-form-item>
            <el-form-item label="原因">
              <el-input v-model="grantForm.reason" type="textarea" :rows="2" placeholder="必填" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="submitting" @click="grantLevel">确认授级</el-button>
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="sent-panel">
          <h3 class="section-title">等级规则（展示用）</h3>
          <el-table :data="LEVEL_RULES" border size="small">
            <el-table-column prop="level" label="等级" width="120" />
            <el-table-column prop="growth" label="成长值门槛" width="130" align="right" />
            <el-table-column prop="discount" label="折扣说明" min-width="150" />
          </el-table>
          <p class="sent-hint">实际等级计算与折扣以后端 LevelCalculator / 取价引擎为准，此处仅作运营说明。</p>
        </div>

        <div class="sent-panel">
          <h3 class="section-title">会员专属商品</h3>
          <el-table v-loading="loading" :data="exclusive" border size="small">
            <el-table-column prop="productId" label="商品 ID" width="150" />
            <el-table-column prop="productName" label="商品名称" min-width="160" />
            <el-table-column label="专属价" width="130" align="right">
              <template #default="scope">{{ fmtMoney(scope.row.priceCent) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无专属商品" :image-size="60" />
            </template>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 会员与积分（P6-41，收口重做）：
 * - 首屏先确定「当前查看账号」，顶部等级 / 积分 / 授信三张卡都读该账号的真库数据；
 * - 下方运营动作（积分调整 / 授予等级）直接作用于同一账号，不再让运营手填客户 ID；
 * - 概览数据来自 `/member/*?customerId=`，管理端显式传客户号，用户端不传时取当前登录者。
 *
 * 此前的问题：三个 `/member/*` 读接口恒抛 40401，顶部三卡永远 `--`，
 * 且页面没有任何「当前是谁」的标识，运营无法确认自己在看哪一个账号。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import {
  customerApi,
  CUSTOMER_LEVELS,
  type MemberCreditVO,
  type ExclusiveProductVO,
} from '@/api';
import type { CustomerVO, MemberLevelVO, PointVO } from '@sent/shared';
import { fmtMoney } from '@sent/shared';

/** 等级码与真库 customer.level_code 值域一致；门槛为运营说明，实际取价以后端为准。 */
const LEVEL_RULES = [
  { level: 'NORMAL', growth: 0, discount: '标准零售价' },
  { level: 'LV1', growth: 1000, discount: '阶梯价优先' },
  { level: 'LV2', growth: 5000, discount: '协议价优先' },
  { level: 'LV3', growth: 20000, discount: '一客一价 / 专属商品价' },
];

const level = ref<MemberLevelVO | null>(null);
const points = ref<PointVO | null>(null);
const credit = ref<MemberCreditVO | null>(null);
const exclusive = ref<ExclusiveProductVO[]>([]);
const loading = ref(false);
const searching = ref(false);
const submitting = ref(false);

/** 当前查看账号：所有卡片与运营动作的唯一作用对象。 */
const targetCustomerId = ref<number | undefined>(undefined);
const targetCustomer = ref<CustomerVO | null>(null);
const customerOptions = ref<CustomerVO[]>([]);

const pointForm = reactive<{ delta: number; reason: string }>({ delta: 0, reason: '' });
const grantForm = reactive<{ level: string; validTo: Date | null; reason: string }>({
  level: 'LV2', validTo: null, reason: '',
});

const levelLabel = computed(() => {
  const code = level.value?.level;
  if (!code) return '--';
  return CUSTOMER_LEVELS.find((l) => l.value === code)?.label ?? code;
});

const targetText = computed(() => {
  if (!targetCustomer.value) return '未选择';
  const c = targetCustomer.value;
  return `${c.name}（${c.phone || '无手机号'}）· 客户 ID ${c.customerId}`;
});

function customerLabel(c: CustomerVO): string {
  return `${c.name}（${c.phone || '无手机号'}）`;
}

/** 企业客户的授信状态，与「企业审核与授信」页口径一致：额度 > 0 即已授信。 */
function unlockState(creditLimitCent?: number): string {
  return Number(creditLimitCent ?? 0) > 0 ? '已授信' : '未授信';
}

/** 远程搜索客户：列表最多 20 条，避免一次拉全量。 */
async function searchCustomers(keyword: string): Promise<void> {
  searching.value = true;
  try {
    const data = await unwrap(customerApi.list({
      page: 1, size: 20, keyword: keyword?.trim() || undefined,
    }));
    customerOptions.value = data?.list ?? [];
  } catch {
    customerOptions.value = [];
  } finally {
    searching.value = false;
  }
}

function onCustomerChange(value: number | undefined): void {
  if (!value) {
    targetCustomer.value = null;
    level.value = null;
    points.value = null;
    credit.value = null;
    return;
  }
  const hit = customerOptions.value.find((c) => Number(c.customerId) === Number(value));
  if (hit) targetCustomer.value = hit;
  void loadOverview();
}

/**
 * 概览取数：三个接口各自独立，用 allSettled 保证单点失败不拖垮整页
 * （例如个人客户没有企业授信档案时，等级与积分仍应正常显示）。
 */
async function loadOverview(): Promise<void> {
  const id = targetCustomerId.value;
  if (!id) return;
  loading.value = true;
  const [lv, pt, cr, ex] = await Promise.allSettled([
    unwrap(customerApi.memberLevel(id)),
    unwrap(customerApi.memberPoints(id)),
    unwrap(customerApi.memberCredit(id)),
    unwrap(customerApi.exclusiveProducts(id)),
  ]);
  level.value = lv.status === 'fulfilled' ? lv.value : null;
  points.value = pt.status === 'fulfilled' ? pt.value : null;
  credit.value = cr.status === 'fulfilled' ? cr.value : null;
  exclusive.value = ex.status === 'fulfilled' ? (ex.value ?? []) : [];
  loading.value = false;
}

/** 首屏默认账号：取客户列表第一条真实客户，避免写死 1 号导致 40401。 */
async function initDefaultCustomer(): Promise<void> {
  await searchCustomers('');
  const first = customerOptions.value[0];
  if (!first) return;
  const id = Number(first.customerId);
  if (!Number.isFinite(id) || id <= 0) return;
  targetCustomerId.value = id;
  targetCustomer.value = first;
  await loadOverview();
}

async function run(task: Promise<unknown>, text: string): Promise<void> {
  submitting.value = true;
  try {
    await task;
    ElMessage.success(text);
    // 运营动作会改动等级 / 积分，回读一次让顶卡与列表立刻对齐
    await loadOverview();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '操作失败');
  } finally {
    submitting.value = false;
  }
}

function adjustPoints(): void {
  if (!targetCustomerId.value) {
    ElMessage.warning('请先选择要调整的客户');
    return;
  }
  if (!pointForm.reason.trim() || pointForm.delta === 0) {
    ElMessage.warning('请填写非零积分与调整原因');
    return;
  }
  void run(
    unwrap(customerApi.adjustPoints(
      Number(targetCustomerId.value), Number(pointForm.delta), pointForm.reason.trim(), 'MANUAL',
    )),
    '积分已调整',
  );
}

function grantLevel(): void {
  if (!targetCustomerId.value) {
    ElMessage.warning('请先选择要授级的客户');
    return;
  }
  if (!grantForm.reason.trim() || !grantForm.validTo) {
    ElMessage.warning('请选择有效期并填写原因');
    return;
  }
  void run(
    unwrap(customerApi.grantLevel(
      Number(targetCustomerId.value), grantForm.level,
      grantForm.validTo.getTime(), grantForm.reason.trim(),
    )),
    '等级已授予',
  );
}

onMounted(() => { void initDefaultCustomer(); });
</script>

<style scoped>
.account-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.account-label { color: #6b7c75; font-size: 13px; }
.account-name { font-size: 15px; font-weight: 600; color: #12664f; }
.account-meta { color: #93a29c; font-size: 12px; }
.target-line { margin: 0 0 12px; color: #33463f; font-size: 13px; }
.overview { margin: 12px 0 6px; }
.metric { display: flex; flex-direction: column; gap: 6px; }
.metric-label { color: #6b7c75; font-size: 13px; }
.metric-value { font-size: 26px; color: #12664f; }
.metric-sub { color: #93a29c; font-size: 12px; }
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
</style>
