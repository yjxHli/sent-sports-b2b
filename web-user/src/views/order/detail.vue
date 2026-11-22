<template>
  <div class="order-detail">
    <!-- 返回条 -->
    <div class="od-bar">
      <button type="button" class="od-bar__back" @click="goBack">‹ 返回我的订单</button>
      <span class="od-bar__crumb">我的订单 / 订单详情</span>
    </div>

    <div v-if="loading" class="od-hint">加载中…</div>
    <el-empty v-else-if="!order" description="订单不存在" />

    <div v-else class="od-grid">
      <!-- ================= 左列：进度 / 状态 / 地址 / 操作 / 店铺商品 ================= -->
      <div class="od-col-main">
        <!-- ① 交易进度（淘宝四步） -->
        <section class="od-steps">
          <div
            v-for="(s, i) in steps"
            :key="s.label"
            class="od-step"
            :class="{ 'od-step--done': s.done, 'od-step--in': inDone(i), 'od-step--last': i === steps.length - 1 }"
          >
            <span class="od-step__dot">
              <svg v-if="s.done" viewBox="0 0 12 12" aria-hidden="true">
                <path d="M2.6 6.1 4.9 8.4 9.4 3.7" fill="none" stroke="currentColor" stroke-width="1.9"
                      stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
            <span class="od-step__label">{{ s.label }}</span>
            <span class="od-step__time">{{ stepText(s) }}</span>
          </div>
        </section>

        <!-- ② 状态大字 + 收货地址 + 操作行 -->
        <section class="od-hero" :class="`od-hero--${tone(order.status)}`">
          <h1 class="od-hero__title">{{ statusTitle }}</h1>
          <p class="od-hero__tip">{{ statusTip }}</p>

          <div v-if="address" class="od-addr">
            <span class="od-addr__pin" aria-hidden="true">
              <svg viewBox="0 0 16 16">
                <path d="M8 1.8c-2.5 0-4.5 2-4.5 4.5 0 3.2 4.5 7.9 4.5 7.9s4.5-4.7 4.5-7.9c0-2.5-2-4.5-4.5-4.5Z"
                      fill="none" stroke="currentColor" stroke-width="1.3" />
                <circle cx="8" cy="6.3" r="1.6" fill="none" stroke="currentColor" stroke-width="1.3" />
              </svg>
            </span>
            <span class="od-addr__region">{{ address.region || '收货地址' }}</span>
            <span class="od-addr__text">{{ address.detail }}</span>
            <b class="od-addr__who">{{ address.receiver }}</b>
            <span class="od-addr__phone">{{ maskPhone(address.phone) }}</span>
          </div>
          <div v-else class="od-addr od-addr--empty">
            该订单未记录收货地址（历史订单或虚拟商品）
          </div>

          <!-- 待付款 5 分钟倒计时：时间源是后端 orders.expire_at，与超时取消任务同一口径 -->
          <div
            v-if="order.status === 'WAIT_PAY' && hasPayDeadline"
            class="od-countdown"
            :class="{ 'od-countdown--over': payExpired }"
            data-testid="pay-countdown"
          >
            <template v-if="!payExpired">
              请在 <b class="od-countdown__time">{{ payCountdownText }}</b> 内完成支付，超时订单将自动取消
            </template>
            <template v-else>
              支付已超时，订单正在自动取消…
            </template>
          </div>

          <!-- 操作按钮：按状态给不同组合，与淘宝「状态决定按钮」一致 -->
          <div class="od-actions">
            <template v-if="order.status === 'WAIT_PAY'">
              <el-button type="primary" class="od-btn od-btn--main" :loading="acting" @click="pay">去支付</el-button>
              <el-button class="od-btn" :loading="acting" @click="cancel">取消订单</el-button>
            </template>

            <el-button v-if="canRebuy" type="primary" class="od-btn od-btn--main" :loading="cartBusy" @click="buyAgain">
              再买一单
            </el-button>
            <el-button v-if="canRebuy" class="od-btn" :loading="cartBusy" @click="addAllToCart">加入购物车</el-button>
            <el-button v-if="canTrace" class="od-btn" @click="scrollToLogistics">查看物流</el-button>
            <el-button v-if="canConfirm" type="primary" class="od-btn od-btn--main" :loading="acting" @click="confirm">
              确认收货
            </el-button>
            <el-button
              v-if="canRefund"
              class="od-btn"
              data-testid="order-refund-apply"
              :class="{ 'od-btn--blocked': customRefundBlocked }"
              :title="customRefundBlocked ? CUSTOM_REFUND_BLOCK_TEXT : ''"
              @click="goRefundApply"
            >申请退款</el-button>

            <el-button v-if="canInvoice" class="od-btn" @click="invoiceVisible = true">
              {{ invoice ? '查看发票' : '申请开票' }}
            </el-button>
            <el-button v-if="canDelete" class="od-btn" :loading="acting" @click="removeOrder">删除订单</el-button>
            <el-button class="od-btn" @click="printOrder">打印</el-button>
            <el-button v-if="order.status !== 'COMPLETED'" class="od-btn" @click="goChat">联系客服</el-button>
          </div>

          <!-- 定制退款口径（2026-11-21）：按钮仍在，但点下去只会得到一句解释——
               按钮直接置灰会让用户不知道「为什么别人能退我不能退」 -->
          <p v-if="canRefund && customRefundBlocked" class="od-refund-tip od-refund-tip--block"
             data-testid="order-refund-blocked">
            定制商品已开始生产，无法申请退款。如有质量问题请联系客服。
          </p>
          <p v-else-if="canRefund && customRefundManual" class="od-refund-tip"
             data-testid="order-refund-manual">
            定制商品审核通过但尚未排产：可以提交退款申请，客服会先与生产确认是否已备料。
          </p>
        </section>

        <!-- ③ UV 定制图稿：商品页/结算页都提示「下单后上传」，
             入口必须在这里，不能让用户自己跑去「我的定制」里找单 -->
        <section v-if="hasCustom" class="od-custom" data-testid="order-custom">
          <div class="od-custom__head">
            <h3 class="od-custom__title">UV 定制图稿</h3>
            <span class="od-custom__sub">上传图稿后客服审图，通过即安排生产</span>
          </div>

          <p v-if="customOrders.length === 0" class="od-custom__empty">
            定制单正在生成中，稍后刷新本页即可上传；
            也可在
            <button type="button" class="od-custom__link" @click="goMyCustom">我的定制</button>
            中查看。
          </p>

          <ul v-else class="od-custom__list">
            <li v-for="c in customOrders" :key="c.customOrderId" class="od-custom__row">
              <div class="od-custom__meta">
                <b class="od-custom__no">{{ c.customOrderId }}</b>
                <span class="od-custom__status" :class="`od-custom__status--${tone(c.status)}`">
                  {{ label(c.status) }}
                </span>
                <span class="od-custom__info">
                  图稿 v{{ c.version }} · {{ c.qty }} 件 · 定制费 ¥{{ fmtCent(Number(c.feeCent ?? 0)) }}
                </span>
              </div>
              <div class="od-custom__ops">
                <button
                  v-if="canUploadDraft(c)"
                  type="button"
                  class="od-mini od-mini--primary"
                  :disabled="uploadingCustomId === c.customOrderId"
                  data-testid="order-custom-upload"
                  @click="pickDraft(c)"
                >
                  {{ uploadingCustomId === c.customOrderId
                    ? '上传中…'
                    : (Number(c.version) > 0 ? '重新上传图稿' : '上传图稿') }}
                </button>
                <span v-else class="od-custom__hint">{{ customHint(c) }}</span>
                <button type="button" class="od-mini" @click="goCustomDetail(c.customOrderId)">
                  查看定制详情
                </button>
              </div>
            </li>
          </ul>
          <input
            ref="draftInput"
            type="file"
            accept="image/*,.ai,.pdf"
            class="od-custom__input"
            @change="onDraftPicked"
          />
        </section>

        <!-- ④ 店铺 + 商品表格 + 物流 -->
        <section class="od-shop">
          <div class="od-shop__head">
            <span class="od-shop__mark">森特</span>
            <span class="od-shop__name">森特体育自营店</span>
            <span class="od-shop__badge">官方</span>
            <button type="button" class="od-shop__snap" @click="snapshotVisible = true">
              查看交易快照 ›
            </button>
          </div>

          <table class="od-table">
            <thead>
              <tr>
                <th class="od-table__goods">商品信息</th>
                <th class="od-table__price">单价</th>
                <th class="od-table__qty">数量</th>
                <th class="od-table__sub">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(it, idx) in items" :key="`${it.skuId ?? idx}-${idx}`">
                <td class="od-table__goods">
                  <div class="od-goods">
                    <ProductThumb class="od-goods__thumb" :name="it.title || ''" :seed="it.skuId || `${order.orderNo}-${idx}`" />
                    <div class="od-goods__info">
                      <button type="button" class="od-goods__title" @click="openProduct(it)">
                        {{ it.title || '商品' }}
                      </button>
                      <div class="od-goods__spec">{{ it.spec || '默认规格' }}</div>
                      <div class="od-goods__serv">
                        <span>退货宝</span><span>7天价保</span><span>假一赔四</span><span>极速退款</span>
                      </div>
                      <div class="od-goods__ops">
                        <button type="button" class="od-mini" :disabled="cartBusy" @click="addOneToCart(it)">
                          加入购物车
                        </button>
                        <button v-if="canRefund" type="button" class="od-mini" @click="goRefundApply">申请售后</button>
                      </div>
                    </div>
                  </div>
                </td>
                <td class="od-table__price">
                  <div class="od-price">¥{{ fmtCent(unitPaidCent(it)) }}</div>
                  <div v-if="unitPaidCent(it) < Number(it.unitPriceCent ?? 0)" class="od-price__old">
                    ¥{{ it.unitPriceYuan || '0.00' }}
                  </div>
                </td>
                <td class="od-table__qty">×{{ it.qty ?? 1 }}</td>
                <td class="od-table__sub">¥{{ fmtCent(lineCent(it)) }}</td>
              </tr>
              <tr v-if="items.length === 0">
                <td colspan="4" class="od-table__empty">该订单暂无商品行</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="3" class="od-table__sum">
                  共 {{ totalQty }} 件商品，商品总额 <b>¥{{ order.goodsAmountYuan || '0.00' }}</b>
                  <span v-if="savedCent > 0" class="od-table__saved">已优惠 ¥{{ fmtCent(savedCent) }}</span>
                </td>
                <td class="od-table__total">合计 <b>¥{{ order.payableYuan || '0.00' }}</b></td>
              </tr>
            </tfoot>
          </table>

          <!-- 物流：已发货才有单号，未发货按状态给「人话」空态 -->
          <div id="od-logistics" class="od-logi" :class="{ 'od-logi--empty': !order.carrier && !order.trackingNo }">
            <template v-if="order.carrier || order.trackingNo">
              <span class="od-logi__node" />
              <div class="od-logi__body">
                <div class="od-logi__row">
                  <b>{{ order.carrier || '承运商待补充' }}</b>
                  <span class="od-logi__no">{{ order.trackingNo || '暂无单号' }}</span>
                  <button v-if="order.trackingNo" type="button" class="od-mini"
                          @click="copyText(String(order.trackingNo), '运单号')">
                    复制单号
                  </button>
                </div>
                <div class="od-logi__time">发货时间 {{ timeText(order.shippedAtMillis) }}</div>
              </div>
            </template>
            <template v-else>{{ shipHint }}</template>
          </div>
        </section>
      </div>

      <!-- ================= 右列：付款详情 / 订单信息 / 订单服务 ================= -->
      <aside class="od-col-side">
        <section class="od-side-card">
          <h3 class="od-side-card__title">付款详情</h3>
          <div class="od-row"><span>商品总价</span><b>¥{{ order.goodsAmountYuan || '0.00' }}</b></div>
          <div class="od-row"><span>运费</span><b>¥{{ order.freightYuan || '0.00' }}</b></div>
          <div v-if="customFeeCent > 0" class="od-row"><span>定制费</span><b>¥{{ fmtCent(customFeeCent) }}</b></div>
          <div v-if="discountCent > 0" class="od-row">
            <span>店铺优惠</span><b class="od-row__minus">-¥{{ fmtCent(discountCent) }}</b>
          </div>
          <div v-if="pointDeductCent > 0" class="od-row">
            <span>积分抵扣</span><b class="od-row__minus">-¥{{ fmtCent(pointDeductCent) }}</b>
          </div>
          <div class="od-row od-row--total">
            <span>实付款</span><b class="od-row__pay">¥{{ order.payableYuan || '0.00' }}</b>
          </div>
        </section>

        <section class="od-side-card">
          <h3 class="od-side-card__title">订单信息</h3>
          <div class="od-row od-row--wide">
            <span>订单编号</span>
            <b class="od-row__mono">{{ order.orderNo }}</b>
            <button type="button" class="od-row__copy" @click="copyText(order.orderNo, '订单号')">
              {{ copied ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="od-row"><span>支付方式</span><b>{{ payChannelText }}</b></div>
          <div class="od-row od-row--wide">
            <span>收货信息</span>
            <b class="od-row__wrap">{{ addressText || '未填写' }}</b>
          </div>
          <div class="od-row"><span>创建时间</span><b>{{ timeText(order.createdAt) }}</b></div>
          <div class="od-row"><span>付款时间</span><b>{{ timeText(order.paidAt) }}</b></div>

          <template v-if="moreInfo">
            <div class="od-row"><span>发货时间</span><b>{{ timeText(order.shippedAtMillis) }}</b></div>
            <div class="od-row"><span>成交时间</span><b>{{ timeText(order.completedAt) }}</b></div>
            <div class="od-row">
              <span>物流单号</span><b class="od-row__wrap">{{ order.trackingNo || '—' }}</b>
            </div>
            <div class="od-row od-row--wide">
              <span>交易号</span>
              <b class="od-row__mono">{{ order.tradeNo || order.payTxnNo || '—' }}</b>
            </div>
            <div v-if="order.refundStatus && order.refundStatus !== 'NONE'" class="od-row">
              <span>售后状态</span><b>{{ label(order.refundStatus) }}</b>
            </div>
            <button type="button" class="od-snap-link" @click="snapshotVisible = true">
              交易快照（发生交易争议时，可作为判断依据）›
            </button>
          </template>

          <button type="button" class="od-more" @click="moreInfo = !moreInfo">
            {{ moreInfo ? '收起更多订单信息 ˄' : '展开更多订单信息 ˅' }}
          </button>
        </section>

        <!-- 订单服务：类名保留 od-invoice，发票状态与开票口径都收敛在这张卡里 -->
        <section class="od-side-card od-invoice">
          <h3 class="od-side-card__title">订单服务</h3>
          <!-- 售后走「这一单专属的申请页」，不是整个售后列表（列表里还得再找一遍这单） -->
          <button type="button" class="od-serv" @click="goRefundApply">
            <span>退款 / 售后</span><i>›</i>
          </button>
          <!-- 在途订单不给入口：货还没到手、随时可能退换，票面金额与成交金额会对不上 -->
          <div v-if="!canInvoice" class="od-serv od-serv--static">
            <span>电子发票</span><i>订单完成后可申请开票</i>
          </div>
          <button v-else type="button" class="od-serv" @click="invoiceVisible = true">
            <span>电子发票</span>
            <i>{{ invoice ? (invoice.status === 'ISSUED' ? '已开具 ›' : '已提交，待开具 ›') : '去申请 ›' }}</i>
          </button>
          <button type="button" class="od-serv" @click="goChat">
            <span>联系客服</span><i>›</i>
          </button>
          <div v-if="invoice && invoice.pdfUrl" class="od-serv__pdf">
            <a :href="invoice.pdfUrl" target="_blank" rel="noopener">下载发票 PDF</a>
          </div>
        </section>
      </aside>
    </div>

    <!-- ================= 交易快照 ================= -->
    <el-dialog v-model="snapshotVisible" title="交易快照" width="760px">
      <p class="od-snap__tip">以下是该订单成交时的快照，商品改价或下架不影响此记录。</p>
      <div class="od-snap__meta">
        <span>订单编号：{{ order?.orderNo }}</span>
        <span>成交时间：{{ timeText(order?.completedAt || order?.createdAt) }}</span>
      </div>
      <table class="od-snap__table">
        <thead>
          <tr><th>商品</th><th>规格</th><th>单价</th><th>数量</th><th>小计</th></tr>
        </thead>
        <tbody>
          <tr v-for="(it, idx) in items" :key="`snap-${idx}`">
            <td>{{ it.title || '商品' }}</td>
            <td>{{ it.spec || '默认规格' }}</td>
            <td>¥{{ it.unitPriceYuan || '0.00' }}</td>
            <td>{{ it.qty ?? 1 }}</td>
            <td>¥{{ fmtCent(lineCent(it)) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="od-snap__sum">
        <span>商品总额 ¥{{ order?.goodsAmountYuan || '0.00' }}</span>
        <span>运费 ¥{{ order?.freightYuan || '0.00' }}</span>
        <span>实付 <b>¥{{ order?.payableYuan || '0.00' }}</b></span>
      </div>
      <div v-if="addressText" class="od-snap__addr">收货信息：{{ addressText }}</div>
      <template #footer>
        <el-button @click="snapshotVisible = false">关闭</el-button>
        <el-button type="primary" @click="printOrder">打印快照</el-button>
      </template>
    </el-dialog>

    <!-- 申请开票 -->
    <InvoiceApplyDialog
      v-model="invoiceVisible"
      :order-no="String(route.params.orderNo ?? '')"
      :payable-cent="Number(order?.payableCent ?? 0)"
      @updated="onInvoiceUpdated"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 订单详情（仿淘宝「订单详情」，2026-11-15 二次改版）。
 *
 * 版式对齐淘宝截图：
 *   ① 顶部四步交易进度（拍下 → 付款 → 发货 → 成交），节点带勾、已走完的线段整段点亮；
 *   ② 状态大字 + 一句话引导 + 收货地址一行 + 操作按钮行（再买一单 / 加入购物车 / 申请开票 / 删除订单 / 打印）；
 *   ③ 店铺行（官方标 + 查看交易快照）+ 商品表格（商品信息 / 单价 / 数量 / 小计，行内还有加入购物车、申请售后）；
 *   ④ 右列：付款详情 / 订单信息（订单编号+复制、支付方式、收货信息、创建·付款·发货·成交时间、交易号）/ 订单服务。
 *
 * 按钮实现口径：
 *   - 再买一单 / 加入购物车 → 逐行 `POST /api/cart/items`（下架行跳过并提示，不整单失败）；
 *   - 申请开票 → `canInvoice`（订单已完成且付过款），在途单不给入口；
 *   - 删除订单 → `DELETE /api/orders/{no}` 软删，只在终态出现（在途后端回 40901）；
 *   - 打印 → `window.print()`，配套 `@media print` 只留订单主体；
 *   - 查看交易快照 → 弹窗展示下单时刻的商品行与金额拆分。
 *
 * 数据仍是一次 `GET /api/orders/{orderNo}`：时间戳、物流、支付摘要都由后端补齐，
 * 前端不再拼装多个接口。`JacksonConfig` 把 Long 序列化成字符串，所有数字先 `Number()` 再判断。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  CUSTOM_REFUND_BLOCK_TEXT,
  fmtCent,
  fmtDateTime,
  isCustomRefundBlocked,
  isCustomRefundManual,
  statusLabel,
  statusTone,
  type StatusTone,
} from '@sent/shared';

import { unwrap } from '@/utils/http';
import { orderApi, type OrderItemLine, type OrderVO } from '@/api/order';
import { cartApi } from '@/api/cart';
import { invoiceApi, type InvoiceVO } from '@/api/invoice';
import { customApi, type OrderCustomVO } from '@/api/custom';
import { fileApi } from '@/api/file';
import ProductThumb from '@/components/ProductThumb.vue';
import InvoiceApplyDialog from '@/components/InvoiceApplyDialog.vue';

const route = useRoute();
const router = useRouter();

const order = ref<OrderVO | null>(null);
const loading = ref(false);
const acting = ref(false);
const cartBusy = ref(false);
const copied = ref(false);
/** 右栏「展开更多订单信息」：默认收起，展开后才出现发货/成交/交易号等 */
const moreInfo = ref(false);
const snapshotVisible = ref(false);

const invoice = ref<InvoiceVO | null>(null);
const invoiceVisible = ref(false);

function label(s: string | null | undefined): string { return statusLabel(s); }
function tone(s: string | null | undefined): StatusTone { return statusTone(s); }
/**
 * 返回我的订单：**固定落到订单列表**，不再用 `router.back()`。
 *
 * 历史栈经常是「确认订单 → 支付成功 → 订单详情」，回退必然回到结算页；
 * 而结算页的购物车行已在下单时清空，退回去只剩一个「去选购」空态
 * （用户 2026-11-21 反馈：点「返回我的订单」回到了空白确认订单页）。
 * 上一页本来就是列表时仍用回退，保留状态 Tab / 搜索词的现场。
 */
function goBack(): void {
  const prev = (window.history.state as { back?: string } | null)?.back ?? '';
  if (prev.startsWith('/orders')) router.back();
  else void router.push('/orders');
}
function goChat(): void { router.push('/chat'); }
/**
 * 申请退款 / 售后：跳「这一单专属」的申请页（`/refund/apply/:orderNo`）。
 * 以前点了跳「我的售后」列表，用户还得在列表里再找一遍自己刚看的单；
 * 详情页里那套弹窗也一并撤掉——申请要选商品行、传凭证，一页放得下比弹窗清楚。
 *
 * 2026-11-21 增补：定制单一旦排产（IN_PRODUCTION / FINISHED / SHIPPED）后端会直接拒单，
 * 前端在点按钮这一刻就拦住并说清原因，别让用户填完凭证才吃一个 409。
 */
function goRefundApply(): void {
  if (customRefundBlocked.value) {
    void ElMessageBox.alert(CUSTOM_REFUND_BLOCK_TEXT, '无法申请退款', {
      confirmButtonText: '我知道了',
      type: 'warning',
    });
    return;
  }
  router.push(`/refund/apply/${String(route.params.orderNo ?? '')}`);
}

/** 后端把 Long 序列化成字符串：一律 Number() 归一后再判断，避免 `"0"` 被当成真值 */
const paidMillis = computed(() => Number(order.value?.paidAt ?? 0));
const shippedMillis = computed(() => Number(order.value?.shippedAtMillis ?? 0));
const completedMillis = computed(() => Number(order.value?.completedAt ?? 0));
const discountCent = computed(() => Number(order.value?.discountCent ?? 0));
const customFeeCent = computed(() => Number(order.value?.customFeeCent ?? 0));
const pointDeductCent = computed(() => Number(order.value?.pointDeductCent ?? 0));
/** 「已优惠」= 优惠 + 积分抵扣，商品表格底部给一行小字，用户不必去右栏加减 */
const savedCent = computed(() => discountCent.value + pointDeductCent.value);
const items = computed<OrderItemLine[]>(() => order.value?.items ?? []);
const totalQty = computed(() => items.value.reduce((sum, it) => sum + Number(it.qty ?? 0), 0));

/* ---------------- UV 定制图稿 ---------------- */
/**
 * 商品页勾选 UV 定制、结算页也写着「下单后需上传图稿」，但这个入口原来只存在于
 * 「我的定制」列表里 —— 用户在订单详情根本传不了图（2026-11-21 反馈）。
 * 这里按订单号把定制单带出来，直接给上传入口。
 */
const customOrders = ref<OrderCustomVO[]>([]);
const uploadingCustomId = ref('');
const draftTarget = ref('');
const draftInput = ref<HTMLInputElement | null>(null);

/** 含定制件就渲染卡片：定制费 > 0，或后端已能查到定制单 */
const hasCustom = computed(
  () => Number(order.value?.customFeeCent ?? 0) > 0 || customOrders.value.length > 0,
);

/** 与后端状态机同口径：只有「待提交图稿 / 审核驳回」能传新稿 */
function canUploadDraft(c: OrderCustomVO): boolean {
  return c.status === 'WAIT_DRAFT' || c.status === 'REJECTED';
}

function customHint(c: OrderCustomVO): string {
  switch (c.status) {
    case 'WAIT_REVIEW': return '图稿审核中，请留意站内信';
    case 'APPROVED': return '审图通过，等待排产';
    case 'IN_PRODUCTION': return '已排产，生产中';
    case 'FINISHED': return '定制已完工';
    case 'SHIPPED': return '已随订单发货';
    default: return '当前状态无需上传图稿';
  }
}

function goMyCustom(): void { router.push('/custom'); }
function goCustomDetail(id: string): void { router.push(`/custom/${id}`); }

function pickDraft(c: OrderCustomVO): void {
  if (!canUploadDraft(c)) {
    ElMessage.warning('当前状态不可上传图稿');
    return;
  }
  draftTarget.value = c.customOrderId;
  draftInput.value?.click();
}

/**
 * 选图即上传：先 `POST /api/files` 拿真实 fileId，再提交为图稿版本。
 * 只提交文件名会让管理端审图台拿到一个访问不到的假 ID（历史缺陷）。
 */
async function onDraftPicked(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  const target = draftTarget.value;
  if (!file || !target) return;
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('单个图稿不超过 20MB');
    return;
  }
  uploadingCustomId.value = target;
  try {
    const stored = await unwrap(fileApi.upload(file));
    if (!stored?.fileId) throw new Error('图稿上传失败');
    await unwrap(customApi.uploadDraft(target, {
      fileId: stored.fileId,
      fileName: stored.fileName || file.name,
      fileSize: Number(stored.size ?? file.size),
    }));
    ElMessage.success('图稿已上传，等待客服审核');
    await loadCustomOrders();
  } catch (err) {
    ElMessage.error((err as Error).message || '上传失败');
  } finally {
    uploadingCustomId.value = '';
  }
}

/** 订单没有定制件时不发这次请求，避免每看一单都多打一个接口 */
async function loadCustomOrders(): Promise<void> {
  if (Number(order.value?.customFeeCent ?? 0) <= 0 && customOrders.value.length === 0) {
    customOrders.value = [];
    return;
  }
  try {
    customOrders.value = (await unwrap(customApi.byOrder(String(route.params.orderNo ?? '')))) ?? [];
  } catch {
    customOrders.value = [];
  }
}

/** 状态大字：淘宝写「交易成功」这类句子，比「已完成」三个字更有结论感 */
const statusTitle = computed(() => {
  switch (order.value?.status) {
    case 'WAIT_PAY': return '等待付款';
    case 'WAIT_SHIP': return '买家已付款，等待卖家发货';
    case 'PART_SHIPPED': return '部分商品已发货';
    case 'WAIT_RECEIVE': return '卖家已发货，等待买家收货';
    case 'COMPLETED': return '交易成功';
    case 'CANCELLED': return '交易已取消';
    case 'CLOSED': return '交易已关闭';
    default: return label(order.value?.status);
  }
});

const statusTip = computed(() => {
  switch (order.value?.status) {
    case 'WAIT_PAY': return '请尽快完成付款，超时订单将自动关闭。';
    case 'WAIT_SHIP': return '商家正在备货，发货后可在这里查看物流。';
    case 'PART_SHIPPED': return '部分商品已发出，剩余商品正在打包。';
    case 'WAIT_RECEIVE': return '商品在路上，收到后请及时确认收货。';
    case 'COMPLETED': return '交易已完成，可申请开票、再次购买或删除订单。';
    case 'CANCELLED': return '订单已取消，如已付款将原路退回。';
    case 'CLOSED': return '订单已关闭，凭证仍保留在交易快照中。';
    default: return '订单状态以实际物流与付款情况为准。';
  }
});

/**
 * 开票入口口径（用户 2026-11-15 收紧）：订单必须**已完成**。
 * 在途订单货还没到手、可能退换，票面金额与实际成交不一致，财务无法核销。
 */
const canInvoice = computed(() => {
  if (order.value?.status !== 'COMPLETED') return false;
  const p = order.value?.payStatus;
  return p === 'PAID' || p === 'PART_REFUNDED';
});

const canRefund = computed(() => {
  const s = order.value?.status;
  return s === 'WAIT_SHIP' || s === 'WAIT_RECEIVE' || s === 'PART_SHIPPED' || s === 'PAID';
});

/**
 * 定制行退款三档：口径与提示语统一在 `@sent/shared`（`isCustomRefundBlocked` / `CUSTOM_REFUND_BLOCK_TEXT`），
 * 定制单详情页也消费同一份，避免两个页面各写一套判断。
 * 判定真源仍在后端（申请退款时逐行联查定制单状态）。
 */
const customStatusList = computed(() => customOrders.value.map((c) => String(c.status ?? '')));
const customRefundBlocked = computed(
  () => customStatusList.value.some((s) => isCustomRefundBlocked(s)),
);
const customRefundManual = computed(
  () => !customRefundBlocked.value && customStatusList.value.some((s) => isCustomRefundManual(s)),
);

const canConfirm = computed(() => {
  const s = order.value?.status;
  return s === 'WAIT_RECEIVE' || s === 'PART_SHIPPED';
});

/** 已发货才给「查看物流」，否则点了只能看到一个空态 */
const canTrace = computed(() => Boolean(order.value?.trackingNo) || shippedMillis.value > 0);

/** 终态才允许「再买一单 / 加入购物车」：在途订单直接加购容易重复下单 */
const canRebuy = computed(() => {
  const s = order.value?.status;
  return s === 'COMPLETED' || s === 'CANCELLED' || s === 'CLOSED';
});

/** 删除订单只在终态出现，与后端 DeleteOrderUseCase 的白名单一致 */
const canDelete = computed(() => {
  const s = order.value?.status;
  return s === 'COMPLETED' || s === 'CANCELLED' || s === 'CLOSED';
});

/** 物流空态的提示按状态说人话，不写死「暂无物流信息」 */
const shipHint = computed(() => {
  switch (order.value?.status) {
    case 'WAIT_PAY': return '付款后商家开始备货';
    case 'WAIT_SHIP': return '商家正在备货，发货后可查看物流';
    case 'CANCELLED':
    case 'CLOSED': return '该订单未发货';
    default: return '该订单暂无物流信息';
  }
});

/**
 * 进度条四步。
 * `done` 与 `time` 分开：发货可能只回写了物流单没改状态，成交也可能只有 status=COMPLETED
 * 而没有 completed_at（历史单）——只看时间会漏点亮，只看状态会漏掉物流已发的情况。
 */
const steps = computed(() => {
  const o = order.value;
  const paidDone = paidMillis.value > 0
    || ['PAID', 'PART_REFUNDED', 'FULL_REFUNDED'].includes(o?.payStatus ?? '');
  const shippedDone = shippedMillis.value > 0 || Boolean(o?.trackingNo);
  const dealDone = completedMillis.value > 0 || o?.status === 'COMPLETED';
  return [
    { label: '拍下商品', time: Number(o?.createdAt ?? 0) || null, done: true, hint: '待下单' },
    { label: '买家付款', time: paidMillis.value || null, done: paidDone, hint: '等待付款' },
    { label: '卖家发货', time: shippedMillis.value || null, done: shippedDone, hint: '等待发货' },
    { label: '交易成功', time: completedMillis.value || null, done: dealDone, hint: '等待收货' },
  ];
});

/** 连接线按「半段」上色：本步与上一步都点亮才把这一段染绿，避免线绿了点还灰 */
function inDone(index: number): boolean {
  const list = steps.value;
  return index > 0 && Boolean(list[index].done) && Boolean(list[index - 1].done);
}

/**
 * 节点下方那行小字：有时间显示时间；没时间但已点亮就写「已完成」
 * （历史单常常只有 status 没有 completed_at，光显示「等待收货」会自相矛盾）。
 */
function stepText(s: { time: number | null; done: boolean; hint: string }): string {
  if (s.time) return fmtDateTime(s.time);
  return s.done ? '已完成' : s.hint;
}

interface AddressView {
  receiver: string;
  phone: string;
  region: string;
  detail: string;
}

/** 收货地址快照是原始 JSON 串（历史单可能为空或结构不全），解析失败按空态处理 */
const address = computed<AddressView | null>(() => {
  const raw = order.value?.addressSnapshotJson;
  if (!raw) return null;
  try {
    const a = JSON.parse(raw) as Record<string, string>;
    const receiver = a.receiver || a.name || '';
    const detail = a.detail || a.address || '';
    const region = [a.province, a.city, a.district].filter(Boolean).join(' ');
    if (!receiver && !detail && !region) return null;
    return { receiver: receiver || '未填写', phone: a.phone || '', region, detail };
  } catch {
    return null;
  }
});

/**
 * 手机号中间脱敏（淘宝同款：138****8000）。
 * 订单详情页常被截图分享给客服，完整号码没必要露出；不足 7 位的老脏数据按首末各留一位处理。
 */
function maskPhone(phone: string | null | undefined): string {
  const p = (phone ?? '').trim();
  if (p.length <= 4) return p;
  if (p.length === 11) return `${p.slice(0, 3)}****${p.slice(7)}`;
  if (p.length > 7) return `${p.slice(0, 3)}****${p.slice(-2)}`;
  return `${p.slice(0, 1)}****${p.slice(-1)}`;
}

/** 右栏「收货信息」一行文本：地址 + 收件人 + 电话（同样脱敏） */
const addressText = computed(() => {
  const a = address.value;
  if (!a) return '';
  const where = [a.region, a.detail].filter(Boolean).join(' ');
  const who = [a.receiver, maskPhone(a.phone)].filter(Boolean).join(' ');
  return [where, who].filter(Boolean).join('，');
});

const CHANNEL_LABEL: Record<string, string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
  WXPAY: '微信支付',
  BALANCE: '余额支付',
  UNIONPAY: '银联',
  MOCK: '模拟支付',
};

const payChannelText = computed(() => {
  const c = order.value?.payChannel;
  if (!c) return '未支付';
  return CHANNEL_LABEL[c] || c;
});

function timeText(ms: number | string | null | undefined): string {
  const n = Number(ms ?? 0);
  return n > 0 ? fmtDateTime(n) : '—';
}

/** 行小计：优先后端给的行实付，缺失时退回「单价 × 数量」 */
function lineCent(it: OrderItemLine): number {
  const paid = Number(it.linePaidCent ?? 0);
  if (paid > 0) return paid;
  return Number(it.unitPriceCent ?? 0) * Number(it.qty ?? 0);
}

/** 实付单价：行实付均摊到件（有优惠时与标价不同，淘宝用划线原价对比展示） */
function unitPaidCent(it: OrderItemLine): number {
  const qty = Number(it.qty ?? 0);
  if (qty <= 0) return Number(it.unitPriceCent ?? 0);
  return Math.round(lineCent(it) / qty);
}

/** 点商品名进商品详情：按 productId（SPU），不是 skuId */
function openProduct(it: OrderItemLine): void {
  const pid = Number(it.productId ?? 0);
  if (!pid) {
    ElMessage.warning('该商品已下架，无法查看详情');
    return;
  }
  router.push(`/product/${pid}`);
}

function scrollToLogistics(): void {
  document.getElementById('od-logistics')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/** 复制任意文本：客服沟通时要报单号/运单号，省得用户手抄 */
async function copyText(text: string, what: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    copied.value = true;
    setTimeout(() => { copied.value = false; }, 1500);
    ElMessage.success(`${what}已复制`);
  } catch {
    ElMessage.warning(`请手动复制${what}：${text}`);
  }
}

/**
 * 逐行加购。
 * 商品可能已下架 / 库存为 0，`POST /api/cart/items` 会逐行拒绝——这里逐行兜住，
 * 成功几行就加几行，最后统一提示失败项，而不是一个失败整单回滚。
 */
async function addLinesToCart(lines: OrderItemLine[], jumpToCart: boolean): Promise<void> {
  const valid = lines.filter((l) => Number(l.skuId ?? 0) > 0);
  if (valid.length === 0) {
    ElMessage.warning('该订单没有可加购的商品行');
    return;
  }
  cartBusy.value = true;
  let ok = 0;
  const failed: string[] = [];
  try {
    for (const line of valid) {
      try {
        await unwrap(cartApi.add(Number(line.skuId), Number(line.qty ?? 1)));
        ok += 1;
      } catch {
        failed.push(line.title || '商品');
      }
    }
    if (ok > 0 && jumpToCart) {
      ElMessage.success(`已加入购物车 ${ok} 件商品`);
      router.push('/cart');
      return;
    }
    if (ok > 0) {
      ElMessage.success(`已加入购物车 ${ok} 件商品`);
    }
    if (failed.length > 0) {
      ElMessage.warning(`${failed.join('、')} 可能已下架或库存不足，未能加入购物车`);
    }
  } finally {
    cartBusy.value = false;
  }
}

/** 再买一单：加购成功后直接去购物车结算 */
function buyAgain(): void {
  void addLinesToCart(items.value, true);
}

/** 订单级「加入购物车」：留在详情页，只提示结果 */
function addAllToCart(): void {
  void addLinesToCart(items.value, false);
}

/** 商品行「加入购物车」 */
function addOneToCart(it: OrderItemLine): void {
  void addLinesToCart([it], false);
}

/** 打印：靠 `@media print` 把导航/按钮藏掉，只留订单正文 */
function printOrder(): void {
  snapshotVisible.value = false;
  window.print();
}

function onInvoiceUpdated(vo: InvoiceVO | null): void {
  invoice.value = vo;
}

async function loadInvoice(): Promise<void> {
  if (!order.value || !canInvoice.value) {
    invoice.value = null;
    return;
  }
  try {
    // 从未申请过返回 null：正常空态，不提示错误
    invoice.value = (await unwrap(invoiceApi.latest(order.value.orderNo))) ?? null;
  } catch {
    invoice.value = null;
  }
}

/** 与订单列表同口径：先跳独立支付页，由用户确认后再真正发起支付 */
function pay(): void {
  if (!order.value) return;
  void router.push(`/pay/${order.value.orderNo}`);
}

async function cancel(): Promise<void> {
  if (!order.value) return;
  acting.value = true;
  try {
    await unwrap(orderApi.cancel(order.value.orderNo));
    ElMessage.success('已取消');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '取消失败');
  } finally {
    acting.value = false;
  }
}

/**
 * 删除订单（软删）：二次确认后再调后端。
 * 删完直接回列表——详情页留在一个「已不在我的订单里」的单子上没有意义。
 */
async function removeOrder(): Promise<void> {
  if (!order.value) return;
  try {
    await ElMessageBox.confirm(
      '删除后该订单不再显示在「我的订单」中，交易凭证仍会保留，可联系客服调取。',
      '删除订单',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  acting.value = true;
  try {
    await unwrap(orderApi.remove(order.value.orderNo));
    ElMessage.success('订单已删除');
    router.replace('/orders');
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败');
  } finally {
    acting.value = false;
  }
}

async function confirm(): Promise<void> {
  if (!order.value) return;
  acting.value = true;
  try {
    await unwrap(orderApi.confirm(order.value.orderNo, []));
    ElMessage.success('已确认收货');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败');
  } finally {
    acting.value = false;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    order.value = await unwrap(orderApi.detail(String(route.params.orderNo)));
  } catch {
    order.value = null;
  } finally {
    loading.value = false;
  }
  await loadCustomOrders();
  startPayCountdown();
  await loadInvoice();
}

/* ---------------- 待付款 5 分钟倒计时 ---------------- */
/**
 * 用户要求（第 2 轮第 4 点）：商城端可以「暂不支付」，5 分钟内未付款订单自动取消，
 * 并且在页面上给出 5 分钟实时倒计时提醒。
 *
 * 时间源只有一个：后端 {@code orders.expire_at}（详情接口回 {@code expireAtMillis}），
 * 与 {@code OrderTimeoutJob} 判超时用的是同一列。前端自己算「创建时间 + 5 分钟」会在
 * 服务端时钟 / 时区不一致时和实际关单时间对不上，出现「倒计时结束但订单还在」。
 */
const payRemainMs = ref(0);
/** 没有截止时间的历史单不显示倒计时（否则会一直停在「支付已超时」） */
const hasPayDeadline = computed(() => Number(order.value?.expireAtMillis ?? 0) > 0);
const payExpired = computed(() => order.value?.status === 'WAIT_PAY' && payRemainMs.value <= 0);
/** 超时后只走一次「等任务关单」的轮询，避免 load() → startPayCountdown() → 超时 的无限循环 */
let payTimeoutHandled = false;
let payTimer: ReturnType<typeof setInterval> | undefined;

function stopPayTimer(): void {
  if (payTimer) clearInterval(payTimer);
  payTimer = undefined;
}

const payCountdownText = computed(() => {
  const total = Math.max(0, Math.ceil(payRemainMs.value / 1000));
  const mm = Math.floor(total / 60).toString().padStart(2, '0');
  const ss = (total % 60).toString().padStart(2, '0');
  return `${mm}:${ss}`;
});

function tickPayCountdown(): void {
  const expireAt = Number(order.value?.expireAtMillis ?? 0);
  if (expireAt <= 0) {
    // 没有截止时间：不渲染倒计时，也不触发「超时等关单」轮询
    payRemainMs.value = 0;
    stopPayTimer();
    return;
  }
  payRemainMs.value = Math.max(0, expireAt - Date.now());
  if (payRemainMs.value <= 0) {
    stopPayTimer();
    void onPayTimeout();
  }
}

function startPayCountdown(): void {
  stopPayTimer();
  if (order.value?.status !== 'WAIT_PAY' || !hasPayDeadline.value) {
    payRemainMs.value = 0;
    return;
  }
  tickPayCountdown();
  if (payRemainMs.value > 0) payTimer = setInterval(tickPayCountdown, 1000);
}

/**
 * 倒计时归零：后端超时任务是每分钟扫一轮，所以这里不等一次就断言失败，
 * 而是最多轮询约 30 秒，等订单真的被关掉再提示（用户看到的是「自动取消」而不是「还是待付款」）。
 */
async function onPayTimeout(): Promise<void> {
  if (payTimeoutHandled) return;
  payTimeoutHandled = true;
  for (let i = 0; i < 12; i += 1) {
    await load();
    if (order.value?.status !== 'WAIT_PAY') {
      ElMessage.warning('支付超时，订单已自动取消');
      return;
    }
    await new Promise((resolve) => { setTimeout(resolve, 2500); });
  }
}

onUnmounted(stopPayTimer);

onMounted(load);
</script>

<style scoped>
.order-detail {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 12px 0 40px;
}

/* ---------- 返回条 ---------- */
.od-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 12px;
}
.od-bar__back {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  font-family: inherit;
  color: var(--sent-text-2);
  cursor: pointer;
}
.od-bar__back:hover {
  color: var(--sent-primary);
}
.od-bar__crumb {
  font-size: 12px;
  color: var(--sent-muted);
}

.od-hint {
  text-align: center;
  padding: 40px 0;
  color: #b5b9c0;
  font-size: 13px;
}

/* ---------- 两列骨架（淘宝：左内容 + 右信息栏） ---------- */
.od-grid {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}
.od-col-main {
  flex: 1;
  min-width: 0;
}
.od-col-side {
  width: 288px;
  flex: none;
  position: sticky;
  top: 12px;
}

/* ---------- ① 四步进度 ---------- */
.od-steps {
  display: flex;
  padding: 6px 0 2px;
}
.od-step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}
/* 连接线：从左邻节点中心拉到自己中心，只画上半段颜色 */
.od-step:not(:first-child)::before {
  content: '';
  position: absolute;
  top: 11px;
  right: 50%;
  left: -50%;
  height: 2px;
  background: #e6e8eb;
}
.od-step--in::before {
  background: var(--sent-primary);
}
.od-step__dot {
  position: relative;
  z-index: 1;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 2px solid #dcdfe6;
  color: #fff;
}
.od-step--done .od-step__dot {
  background: var(--sent-primary);
  border-color: var(--sent-primary);
}
.od-step__dot svg {
  width: 12px;
  height: 12px;
}
.od-step__label {
  font-size: 13px;
  color: var(--sent-muted);
}
.od-step--done .od-step__label {
  color: var(--sent-text);
  font-weight: 600;
}
.od-step__time {
  font-size: 11px;
  color: #b5b9c0;
}

/* ---------- ② 状态 + 地址 + 操作 ---------- */
.od-hero {
  position: relative;
  margin-top: 14px;
  padding: 18px 20px 16px;
  border-radius: var(--sent-radius);
  background: linear-gradient(120deg, #f3faf6 0%, #ffffff 62%);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.od-hero--warning { background: linear-gradient(120deg, #fff8ec 0%, #ffffff 62%); }
.od-hero--danger { background: linear-gradient(120deg, #fff4f4 0%, #ffffff 62%); }
.od-hero--info { background: linear-gradient(120deg, #f6f7f9 0%, #ffffff 62%); }
.od-hero__title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--sent-text);
}
.od-hero--warning .od-hero__title { color: #d9891a; }
.od-hero--danger .od-hero__title { color: #d94a4f; }
.od-hero--info .od-hero__title { color: #8b9099; }
.od-hero__tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--sent-muted);
}

.od-addr {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--sent-text-2);
}
.od-addr__pin {
  display: inline-flex;
  color: var(--sent-primary);
}
.od-addr__pin svg {
  width: 14px;
  height: 14px;
}
.od-addr__region { font-weight: 600; color: var(--sent-text); }
.od-addr__who { color: var(--sent-text); }
.od-addr__phone { color: var(--sent-muted); }
.od-addr--empty { color: #b5b9c0; font-size: 12px; }

.od-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
/* 待付款倒计时：橙红提示条，数字等宽避免每秒跳动时抖动 */
.od-countdown {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  color: #b8741a;
  font-size: 13px;
}
.od-countdown--over {
  background: #fff1f0;
  border-color: #ffa39e;
  color: #cf1322;
}
.od-countdown__time {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  color: #d4380d;
}
/* 操作按钮做成淘宝那种等宽胶囊，主操作实心 */
.od-actions :deep(.od-btn) {
  height: 30px;
  padding: 0 16px;
  font-size: 13px;
  border-radius: 15px;
  margin-left: 0;
}
.od-actions :deep(.od-btn--main) {
  font-weight: 600;
}
/* 定制已排产：按钮留可点（点了给解释），但视觉上先降权 */
.od-actions :deep(.od-btn--blocked) {
  color: var(--sent-muted);
  border-style: dashed;
}
/* 定制退款口径提示：常规=灰字说明，拦截=琥珀警示条 */
.od-refund-tip {
  margin: 10px 0 0;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #8a5a00;
  background: #fdf6ec;
  border-left: 3px solid #e6a23c;
}
.od-refund-tip--block {
  color: #a8071a;
  background: #fff1f0;
  border-left-color: #cf1322;
}

/* ---------- ③ UV 定制图稿 ---------- */
.od-custom {
  margin-top: 14px;
  background: #fff;
  border-radius: var(--sent-radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 14px 16px 16px;
  border-left: 3px solid var(--sent-accent);
}
.od-custom__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.od-custom__title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--sent-text);
}
.od-custom__sub {
  font-size: 12px;
  color: var(--sent-muted);
}
.od-custom__empty {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--sent-muted);
}
.od-custom__link {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-primary);
  cursor: pointer;
}
.od-custom__list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.od-custom__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border: 1px solid #f0f2f4;
  border-radius: 8px;
  background: #fafbfc;
}
.od-custom__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}
.od-custom__no {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--sent-text);
}
.od-custom__status {
  font-size: 11px;
  border-radius: 3px;
  padding: 1px 6px;
  background: #eef2f5;
  color: var(--sent-text-2);
}
.od-custom__status--warning {
  background: #fff7e6;
  color: #b8741a;
}
.od-custom__status--danger {
  background: #fdeaec;
  color: #c93644;
}
.od-custom__status--success {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}
.od-custom__info {
  font-size: 12px;
  color: var(--sent-muted);
}
.od-custom__ops {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.od-custom__hint {
  font-size: 12px;
  color: var(--sent-muted);
}
.od-custom__input {
  display: none;
}
.od-mini--primary {
  border-color: var(--sent-accent);
  background: var(--sent-accent);
  color: #fff;
}
.od-mini--primary:hover:not(:disabled) {
  border-color: var(--sent-accent-dark);
  background: var(--sent-accent-dark);
  color: #fff;
}

/* ---------- ④ 店铺 + 商品表格 ---------- */
.od-shop {
  margin-top: 14px;
  background: #fff;
  border-radius: var(--sent-radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}
.od-shop__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f4;
}
.od-shop__mark {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: var(--sent-primary);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.od-shop__name {
  font-size: 13px;
  font-weight: 700;
  color: var(--sent-text);
}
.od-shop__badge {
  font-size: 10px;
  color: var(--sent-primary);
  border: 1px solid var(--sent-primary);
  border-radius: 3px;
  padding: 0 4px;
  line-height: 15px;
}
.od-shop__snap {
  margin-left: auto;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-muted);
  cursor: pointer;
}
.od-shop__snap:hover { color: var(--sent-primary); }

.od-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.od-table th {
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 400;
  color: var(--sent-muted);
  text-align: left;
  background: #fafbfc;
}
.od-table th.od-table__price,
.od-table th.od-table__qty,
.od-table th.od-table__sub { text-align: right; }
.od-table__goods { width: auto; }
.od-table__price { width: 96px; }
.od-table__qty { width: 70px; }
.od-table__sub { width: 96px; }

.od-table td {
  padding: 12px;
  border-top: 1px solid #f4f6f8;
  vertical-align: top;
}
.od-table td.od-table__price,
.od-table td.od-table__qty,
.od-table td.od-table__sub {
  text-align: right;
  font-size: 13px;
  color: var(--sent-text);
  padding-top: 14px;
}
.od-table td.od-table__qty { color: var(--sent-muted); }
.od-table td.od-table__sub { font-weight: 600; }

.od-goods {
  display: flex;
  gap: 10px;
  min-width: 0;
}
.od-goods__thumb {
  width: 68px;
  height: 68px;
  border-radius: 6px;
  flex: none;
}
.od-goods__info {
  min-width: 0;
  flex: 1;
}
.od-goods__title {
  display: block;
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  color: var(--sent-text);
  text-align: left;
  cursor: pointer;
}
.od-goods__title:hover { color: var(--sent-primary); }
.od-goods__spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-muted);
}
.od-goods__serv {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.od-goods__serv span {
  font-size: 11px;
  color: var(--sent-muted);
  background: #f5f7f9;
  border-radius: 3px;
  padding: 1px 5px;
  white-space: nowrap;
}
.od-goods__ops {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.od-mini {
  border: 1px solid #e2e5e9;
  background: #fff;
  border-radius: 13px;
  padding: 2px 12px;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-text-2);
  cursor: pointer;
}
.od-mini:hover:not(:disabled) {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.od-mini:disabled { opacity: 0.6; cursor: not-allowed; }

.od-price { font-weight: 600; }
.od-price__old {
  margin-top: 2px;
  font-size: 12px;
  font-weight: 400;
  color: #b5b9c0;
  text-decoration: line-through;
}

.od-table__empty {
  text-align: center;
  color: #b5b9c0;
  font-size: 13px;
  padding: 24px 0 !important;
}
.od-table tfoot td {
  border-top: 1px solid #f0f2f4;
  background: #fafbfc;
  font-size: 13px;
  padding: 12px;
}
.od-table__sum {
  text-align: right;
  color: var(--sent-text-2);
}
.od-table__saved {
  margin-left: 8px;
  font-size: 12px;
  color: #d9891a;
}
.od-table__total {
  text-align: right;
  color: var(--sent-text-2);
}
.od-table__total b {
  font-size: 17px;
  color: var(--sent-primary);
  margin-left: 4px;
}

/* 物流 */
.od-logi {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid #f0f2f4;
  background: #fafbfc;
}
.od-logi--empty {
  font-size: 12px;
  color: #b5b9c0;
}
.od-logi__node {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sent-primary);
  margin-top: 5px;
  flex: none;
}
.od-logi__row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--sent-text);
}
.od-logi__no { color: var(--sent-text-2); }
.od-logi__time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- ④ 右栏 ---------- */
.od-side-card {
  background: #fff;
  border-radius: var(--sent-radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 14px 16px 16px;
  margin-bottom: 12px;
}
.od-side-card__title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--sent-text);
}
.od-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--sent-muted);
}
.od-row b {
  font-weight: 500;
  color: var(--sent-text);
  text-align: right;
}
.od-row--wide {
  justify-content: flex-start;
}
.od-row--wide > span { flex: none; }
.od-row--wide > b { flex: 1; text-align: left; }
.od-row__mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 11px;
  word-break: break-all;
}
.od-row__wrap {
  line-height: 1.5;
  word-break: break-all;
}
.od-row__copy {
  flex: none;
  border: none;
  background: none;
  padding: 0;
  font-size: 11px;
  font-family: inherit;
  color: var(--sent-primary);
  cursor: pointer;
}
.od-row__minus { color: #d9891a; }
.od-row--total {
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px dashed #eceff2;
  align-items: center;
}
.od-row__pay {
  font-size: 17px !important;
  font-weight: 700 !important;
  color: var(--sent-primary) !important;
}

.od-snap-link {
  border: none;
  background: none;
  padding: 6px 0 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-muted);
  text-align: left;
  cursor: pointer;
  line-height: 1.5;
}
.od-snap-link:hover { color: var(--sent-primary); }
.od-more {
  margin-top: 8px;
  width: 100%;
  border: none;
  border-top: 1px solid #f0f2f4;
  background: none;
  padding: 8px 0 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-muted);
  cursor: pointer;
}
.od-more:hover { color: var(--sent-primary); }

.od-serv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: none;
  background: none;
  padding: 7px 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-text-2);
  cursor: pointer;
}
.od-serv:hover span { color: var(--sent-primary); }
.od-serv i {
  font-style: normal;
  color: #b5b9c0;
}
.od-serv--static {
  cursor: default;
}
.od-serv--static:hover span { color: var(--sent-text-2); }
.od-serv--static i { color: #b5b9c0; font-size: 11px; }
.od-serv__pdf {
  margin-top: 6px;
  font-size: 12px;
}
.od-serv__pdf a { color: var(--sent-primary); }

/* ---------- 交易快照弹窗 ---------- */
.od-snap__tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--sent-muted);
}
.od-snap__meta {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: var(--sent-text-2);
  margin-bottom: 10px;
}
.od-snap__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.od-snap__table th,
.od-snap__table td {
  border: 1px solid #eceff2;
  padding: 8px 10px;
  text-align: left;
}
.od-snap__table th {
  background: #fafbfc;
  color: var(--sent-muted);
  font-weight: 400;
}
.od-snap__sum {
  display: flex;
  gap: 24px;
  justify-content: flex-end;
  margin-top: 10px;
  font-size: 12px;
  color: var(--sent-text-2);
}
.od-snap__sum b { color: var(--sent-primary); font-size: 15px; }
.od-snap__addr {
  margin-top: 8px;
  font-size: 12px;
  color: var(--sent-muted);
}
</style>

<style>
/**
 * 打印样式不加 scoped：要把布局层的导航（.topbar / .searchbar / footer）一起藏掉，
 * scoped 选择器加不到父级组件里去。
 */
@media print {
  .topbar,
  .searchbar,
  .footer,
  .site-footer,
  .od-bar,
  .od-actions,
  .od-more,
  .od-shop__snap,
  .od-goods__ops,
  .od-serv,
  .od-custom__ops,
  .el-dialog__wrapper,
  .el-overlay {
    display: none !important;
  }
  .order-detail {
    width: 100% !important;
    max-width: 100% !important;
    padding: 0 !important;
  }
  .od-grid {
    display: block !important;
  }
  .od-col-side {
    width: 100% !important;
    position: static !important;
    margin-top: 12px;
  }
  .od-hero,
  .od-shop,
  .od-side-card {
    box-shadow: none !important;
    border: 1px solid #e6e8eb;
  }
}
</style>
