<template>
  <div class="wb">
    <!-- 顶部条：状态与工具收成一行，把空间让给会话 -->
    <header class="wb__bar">
      <div class="wb__bar-main">
        <h2 class="wb__title">客服工作台</h2>
        <el-tag :type="connected ? 'success' : 'info'" effect="dark" size="small">
          {{ connected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}
        </el-tag>
        <span class="wb__chip">在线客服 <b>{{ onlineCount }}</b></span>
        <span class="wb__chip">我的并发 <b>{{ activeRows.length }}/{{ maxSessions }}</b></span>
        <!-- 与后端 app.chat.idle-minutes（默认 10）同口径：客户 10 分钟没发消息，服务端定时任务自动结束会话 -->
        <span class="wb__chip wb__chip--tip">超 10 分钟无消息自动结束</span>
      </div>
      <div class="wb__bar-tools">
        <!-- 今日满意率 = 满意评价量 ÷ 有效评价总量；紧挨「今日完成接待」左侧，客户打分后实时刷新 -->
        <div class="today-stat" data-testid="today-satisfaction-rate"
             :title="`今日有效评价 ${Number(todayStats.ratedCount ?? 0)} 条，其中满意 ${Number(todayStats.satisfiedCount ?? 0)} 条`">
          <span class="today-stat__label">今日满意率</span>
          <!-- 数值单用 today-rate__num：共用 today-stat__num 会让既有「今日完成接待」的 E2E 撞 strict mode -->
          <b class="today-rate__num">{{ satisfactionRateText }}</b>
          <span v-if="Number(todayStats.ratePercent ?? -1) >= 0" class="today-stat__unit">%</span>
        </div>
        <!-- 今日完成接待数：按本地自然日统计，跨零点自动归零（每天刷新） -->
        <div class="today-stat" :title="`统计日期 ${todayStats.date || '--'}`">
          <span class="today-stat__label">今日完成接待</span>
          <b class="today-stat__num">{{ todayStats.closedCount }}</b>
          <span class="today-stat__unit">次</span>
        </div>
        <el-button text @click="reconnect">重连</el-button>
        <el-button text @click="loadOpen">刷新列表</el-button>
        <el-button text @click="loadWorkload">刷新工作量</el-button>
      </div>
    </header>

    <div class="wb__grid">
      <!-- 左：坐席身份 + 会话列表 -->
      <aside class="wb__side">
        <div class="seat">
          <div class="seat__avatar"><span>{{ staffInitial }}</span></div>
          <div class="seat__body">
            <div class="seat__name">客服 {{ staffId }}</div>
            <div class="seat__sub">
              <span class="seat__dot" :class="`seat__dot--${presence.toLowerCase()}`" />
              {{ presenceText }} · 自动接入
            </div>
          </div>
          <el-select v-model="presence" size="small" class="seat__presence" @change="changePresence">
            <el-option label="在线" :value="PresenceStatus.ONLINE" />
            <el-option label="忙碌" :value="PresenceStatus.BUSY" />
            <el-option label="离线" :value="PresenceStatus.OFFLINE" />
          </el-select>
        </div>

        <div class="side-search">
          <el-input v-model="keyword" size="small" clearable placeholder="搜索客户 ID / 会话号" />
        </div>

        <div class="side-list">
          <section class="list-panel">
            <h3 class="side-head">进行中 <span class="count">{{ activeRows.length }}</span></h3>
            <p v-if="!activeRowsView.length" class="side-empty">暂无进行中会话</p>
            <div
              v-for="row in activeRowsView"
              :key="row.sessionId"
              class="session-item"
              :class="{ current: isOpen(row.sessionId) }"
              @click="openSession(row)"
            >
              <div class="s-avatar">{{ String(row.customerId).slice(-2) }}</div>
              <div class="s-body">
                <div class="line1">
                  <strong>会话 #{{ row.sessionId }}</strong>
                  <StatusTag :status="row.status" />
                </div>
                <div class="line2">
                  客户 {{ row.customerId }} · 已等待 {{ waitText(row.waitingSinceMillis) }}
                </div>
              </div>
            </div>
          </section>

          <section class="list-panel">
            <h3 class="side-head">排队中 <span class="count">{{ waitingRows.length }}</span></h3>
            <p v-if="!waitingRowsView.length" class="side-empty">暂无排队客户</p>
            <div
              v-for="row in waitingRowsView"
              :key="row.sessionId"
              class="session-item queue-item"
              :class="{ current: isOpen(row.sessionId) }"
              @click="openSession(row)"
            >
              <div class="s-avatar s-avatar--queue">{{ String(row.customerId).slice(-2) }}</div>
              <div class="s-body">
                <div class="line1">
                  <strong>会话 #{{ row.sessionId }}</strong>
                  <el-tag size="small" type="warning" effect="plain">第 {{ row.queuePosition }} 位</el-tag>
                </div>
                <div class="line2">
                  客户 {{ row.customerId }} · 已排队 {{ waitText(row.waitingSinceMillis) }}
                </div>
              </div>
            </div>
          </section>
        </div>
      </aside>

      <!-- 右：多 Tab 聊天区（最多 3 个）+ 顾客订单 -->
      <main class="wb__main">
        <div v-if="!tabs.length" class="chat-empty">
          <div class="chat-empty__icon">💬</div>
          <p class="chat-empty__title">还没有打开任何会话</p>
          <p class="chat-empty__hint">
            客户进线会自动弹出聊天框；也可以从左侧点会话打开（最多同时 3 个）
          </p>
        </div>

        <template v-else>
          <el-tabs v-model="activeTab" class="chat-tabs" type="card" closable @tab-remove="closeTab">
            <el-tab-pane
              v-for="tab in tabs"
              :key="String(tab.sessionId)"
              :name="String(tab.sessionId)"
            >
              <template #label>
                <span class="tab-label">
                  #{{ tab.sessionId }}
                  <el-tag v-if="tab.status === ChatSessionStatus.WAITING" size="small" type="warning" effect="plain">
                    排队中
                  </el-tag>
                  <el-tag v-else-if="tab.status === ChatSessionStatus.CLOSED" size="small" type="info" effect="plain">
                    已结束
                  </el-tag>
                </span>
              </template>

              <div class="tab-body">
                <!-- 会话头：客户是谁、状态、可做的动作 -->
                <header class="chat-head">
                  <div class="chat-head__who">
                    <div class="chat-head__avatar">{{ String(tab.customerId).slice(-2) }}</div>
                    <div class="chat-head__info">
                      <div class="chat-head__name">
                        客户 {{ tab.customerId }}
                        <el-tag v-if="tab.status === ChatSessionStatus.WAITING" size="small" type="warning">
                          排队第 {{ positionOf(tab.sessionId) }} 位
                        </el-tag>
                        <el-tag v-else-if="tab.status === ChatSessionStatus.CLOSED" size="small" type="info" effect="plain">
                          已结束
                        </el-tag>
                        <el-tag v-else size="small" type="success" effect="plain">进行中</el-tag>
                      </div>
                      <div class="chat-head__sub">会话 #{{ tab.sessionId }}</div>
                    </div>
                  </div>
                  <div class="chat-head__actions">
                    <el-button
                      v-if="tab.status === ChatSessionStatus.WAITING"
                      type="primary"
                      size="small"
                      @click="accept(tab)"
                    >
                      接待
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      plain
                      :disabled="tab.status === ChatSessionStatus.CLOSED"
                      @click="closeSession(tab)"
                    >结束会话</el-button>
                  </div>
                </header>

                <div class="message-box">
                  <template v-for="(m, mi) in messagesOf(tab.sessionId)" :key="m.key">
                  <!-- 微信式时间提示：相邻消息间隔满 5 分钟重新显示当前时间（首条必显） -->
                  <div
                    v-if="shouldShowTimeDivider(messagesOf(tab.sessionId)[mi - 1]?.createdAt, m.createdAt)"
                    class="msg-time"
                    data-testid="chat-time-divider"
                  >{{ fmtChatTime(m.createdAt) }}</div>
                  <div
                    class="message-line"
                    :class="{ mine: m.senderType === SenderType.STAFF }"
                  >
                    <span v-if="m.msgType === 'IMAGE'" class="bubble bubble--img">
                      <img
                        class="bubble-img"
                        :src="fileApi.url(m.content)"
                        alt="客户图片"
                        @click="previewImage(m.content)"
                      />
                      <small v-if="m.pending">· 发送中</small>
                    </span>
                    <!-- 顾客发来的订单卡片：点「查看订单详情」在当前页弹窗，不离开工作台 -->
                    <ChatOrderCard
                      v-else-if="m.msgType === 'ORDER'"
                      :content="m.content"
                      :mine="m.senderType === SenderType.STAFF"
                      @open-detail="openOrderDetailByNo"
                    />
                    <!-- 服务评价卡：客户打分前显示「等待评价」，打分后就地显示档位 -->
                    <span v-else-if="m.msgType === 'SATISFY'" class="satisfy-card"
                          data-testid="satisfaction-card">
                      <template v-if="satisfactionOf(m).status !== 'RATED'">
                        <b>⭐ 请评价本次服务</b>
                        <small>满意 / 一般 / 不满意</small>
                      </template>
                      <template v-else>
                        <b>本次服务评价：{{ scoreLabel(satisfactionOf(m).score) }}</b>
                      </template>
                    </span>
                    <span v-else class="bubble">
                      {{ m.content }}
                      <small v-if="m.pending">· 发送中</small>
                    </span>
                  </div>
                  </template>
                  <p v-if="!messagesOf(tab.sessionId).length" class="msg-empty">还没有消息，先打个招呼吧</p>
                  <!-- 客户正在输入：收到 TYPING 帧亮 3s，无新帧自动熄灭 -->
                  <div v-if="typingSessions.has(tab.sessionId)" class="typing-hint">
                    <span class="typing-dot" /><span class="typing-dot" /><span class="typing-dot" />
                    客户正在输入…
                  </div>
                </div>

                <div class="send-row">
                  <!-- 图片按钮上方：主动发送服务评价卡（客服可随时发起，同会话幂等） -->
                  <div class="send-row__tools">
                    <el-button
                      class="send-row__rate"
                      size="small"
                      :disabled="tab.status === ChatSessionStatus.WAITING || tab.status === ChatSessionStatus.CLOSED"
                      title="发送服务评价卡，客户可打满意 / 一般 / 不满意"
                      data-testid="send-satisfaction"
                      @click="sendSatisfaction(tab)"
                    >
                      ⭐ 请评价
                    </el-button>
                    <el-button
                      class="send-row__img"
                      :disabled="!connected || uploadingImg || tab.status === ChatSessionStatus.CLOSED"
                      :loading="uploadingImg"
                      title="发送图片"
                      @click="pickImage(tab.sessionId)"
                    >
                      🖼 图片
                    </el-button>
                  </div>
                  <el-input
                    v-model="drafts[tab.sessionId]"
                    type="textarea"
                    :rows="1"
                    resize="none"
                    :disabled="tab.status === ChatSessionStatus.CLOSED"
                    :placeholder="tab.status === ChatSessionStatus.CLOSED ? '本次会话已结束' : '输入消息后回车发送'"
                    @input="notifyTyping(tab.sessionId)"
                    @keydown.enter.exact.prevent="sendMessage(tab)"
                  />
                  <el-button type="primary" :disabled="!connected || tab.status === ChatSessionStatus.CLOSED" @click="sendMessage(tab)">发送</el-button>
                </div>
                <p class="send-tip">Enter 发送 · 支持图片与订单卡片</p>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>

        <!-- 右侧：本次情况小结 + 历史服务 + 顾客历史订单（订单默认收起） -->
        <!-- 2026-11-18：无会话时也常驻显示，客服打开工作台即可看到表单与默认选项，
             不必先手动点开一个会话；无会话时保存按钮禁用、历史/订单区显示空态。 -->
        <aside class="cust-orders">
            <header class="cust-orders__head">
              <b>服务记录</b>
              <span class="cust-orders__cid">客户 {{ activeCustomerId || '--' }}</span>
            </header>

            <section class="side-section">
              <button type="button" class="side-section__toggle" data-testid="note-toggle"
                      @click="noteOpen = !noteOpen">
                <span>本次情况</span><span>{{ noteOpen ? '−' : '+' }}</span>
              </button>
              <div v-show="noteOpen" class="side-section__body note-form" data-testid="session-note-panel">
                <label>用户的问题</label>
                <el-input v-model="noteForm.summary" size="small" maxlength="255"
                          placeholder="客服手动输入问题概述" data-testid="note-summary" />
                <label>问题分类</label>
                <el-select v-model="noteForm.category" size="small" data-testid="note-category">
                  <el-option label="商品品质问题" value="QUALITY" />
                  <el-option label="商品咨询" value="INQUIRY" />
                  <el-option label="未明确" value="UNCLEAR" />
                </el-select>
                <label>明确信息</label>
                <el-select v-model="noteForm.info" size="small" data-testid="note-info">
                  <el-option label="明确信息" value="CLEAR" />
                  <el-option label="用户未回复" value="NO_REPLY" />
                </el-select>
                <label>处理结果</label>
                <el-select v-model="noteForm.result" size="small" data-testid="note-result">
                  <el-option label="已完成" value="DONE" />
                  <el-option label="未解决" value="UNRESOLVED" />
                </el-select>
                <label>解决办法</label>
                <el-input v-model="noteForm.solution" type="textarea" :rows="3" maxlength="1024"
                          placeholder="填写本次解决办法" data-testid="note-solution" />
                <el-button type="primary" size="small" :loading="noteSaving"
                           :disabled="!activeCustomerId" data-testid="note-save"
                           @click="saveNote">保存本次情况</el-button>
              </div>
            </section>

            <section class="side-section">
              <button type="button" class="side-section__toggle" data-testid="history-toggle"
                      @click="historyOpen = !historyOpen">
                <span>历史服务（最近 {{ historyRows.length }} 次）</span><span>{{ historyOpen ? '−' : '+' }}</span>
              </button>
              <div v-show="historyOpen" class="side-section__body">
                <p v-if="historyLoading" class="cust-orders__hint">加载中…</p>
                <p v-else-if="!historyRows.length" class="cust-orders__hint">暂无历史服务记录</p>
                <div v-for="h in historyRows" :key="h.sessionId" class="history-row"
                     data-testid="history-row" @click="openHistoryDetail(h.sessionId)">
                  <b>会话 #{{ h.sessionId }} · {{ h.staffName || `客服 ${h.staffId}` }}</b>
                  <span>{{ fmtDateTime(Number(h.startedAtMillis ?? 0)) }} · {{ h.messageCount }} 条消息</span>
                  <StatusTag :label="h.result === 'DONE' ? '已完成' : (h.result === 'UNRESOLVED' ? '未解决' : '未填写')"
                             :tone="h.result === 'DONE' ? 'success' : (h.result === 'UNRESOLVED' ? 'warning' : 'info')" />
                </div>
              </div>
            </section>

            <section class="side-section">
              <button type="button" class="side-section__toggle" data-testid="orders-toggle"
                      @click="ordersOpen = !ordersOpen">
                <span>顾客历史订单</span><span>{{ ordersOpen ? '−' : '+' }}</span>
              </button>
              <div v-show="ordersOpen" class="side-section__body">
                <el-input v-model="orderKeyword" size="small" clearable placeholder="搜索订单号"
                          data-testid="order-search" class="order-search" />
                <div v-if="custOrdersLoading" class="cust-orders__hint">加载中…</div>
                <div v-else-if="!filteredOrders.length" class="cust-orders__hint">没有匹配的订单</div>
                <div v-else class="cust-orders__list">
                  <div
                    v-for="o in filteredOrders"
                    :key="o.orderNo"
                    class="cust-order"
                    :class="{ 'cust-order--active': activeOrderNo === o.orderNo }"
                    @click="openOrderDetail(o)"
                  >
                    <div class="cust-order__top">
                      <span class="cust-order__no">{{ o.orderNo }}</span>
                      <StatusTag :status="o.status" />
                    </div>
                    <div class="cust-order__foot">
                      <span class="cust-order__time">{{ fmtDateTime(Number(o.createdAt ?? 0)) }}</span>
                      <b class="cust-order__amount">
                        ¥{{ o.payableYuan || fmtCent(Number(o.payableCent ?? 0)) }}
                      </b>
                    </div>
                  </div>
                </div>
              </div>
            </section>
        </aside>
      </main>
    </div>

    <input ref="imageInput" class="hidden-input" type="file" accept="image/*" @change="onImagePicked" />

    <!-- 点图片看大图 -->
    <el-image-viewer v-if="previewSrc" :url-list="[previewSrc]" @close="previewSrc = ''" />

    <!-- 顾客历史订单 / 聊天订单卡片：点订单在当前页弹详情，不离开工作台 -->
    <el-dialog
      v-model="orderDialogVisible"
      class="order-detail-dialog"
      width="640px"
      append-to-body
      :show-close="false"
    >
      <template #header>
        <div class="order-dlg-header">
          <button type="button" class="order-dlg-close" aria-label="关闭订单详情" @click="orderDialogVisible = false">×</button>
          <span class="order-dlg-title">订单详情</span>
        </div>
      </template>
      <div v-if="orderDialogLoading" class="order-dlg-hint">加载中…</div>
      <template v-else-if="orderDetail">
        <div class="order-dlg-meta">
          <div><span>订单号</span><b>{{ orderDetail.orderNo }}</b></div>
          <div><span>订单状态</span><StatusTag :status="orderDetail.status" /></div>
          <div><span>支付状态</span><StatusTag :status="orderDetail.payStatus" /></div>
          <div><span>下单时间</span><b>{{ fmtDateTime(Number(orderDetail.createdAt ?? 0)) }}</b></div>
        </div>
        <el-table :data="orderDetail.lines ?? []" border size="small" class="order-dlg-lines">
          <el-table-column prop="skuName" label="商品" min-width="160" />
          <el-table-column prop="spec" label="规格" min-width="90" />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ fmtCent(Number(row.unitPriceCent ?? 0)) }}</template>
          </el-table-column>
        </el-table>
        <div class="order-dlg-amount">应付金额：<b class="order-dlg-pay">¥{{ fmtCent(Number(orderDetail.payableCent ?? 0)) }}</b></div>
      </template>
      <div v-else class="order-dlg-hint">订单详情加载失败</div>
    </el-dialog>

    <!-- 历史服务回放：与客服聊天同款大弹窗 + 气泡式消息，不再用紧凑表格行 -->
    <el-dialog
      v-model="historyDetailVisible"
      width="1100px"
      top="4vh"
      class="history-replay-dialog"
      append-to-body
      destroy-on-close
      title="历史服务详情"
    >
      <div v-if="historyDetailLoading" class="sent-hint">加载中…</div>
      <template v-else-if="historyTranscript">
        <p class="history-meta">
          会话 #{{ historyTranscript.session.sessionId }} · 客服 {{ historyTranscript.session.staffId ?? '--' }}
          · {{ fmtDateTime(Number(historyTranscript.session.startedAtMillis ?? 0)) }}
        </p>
        <div class="message-box history-message-box" data-testid="history-transcript">
          <template v-for="(m, mi) in historyTranscript.messages" :key="m.seq">
            <div
              v-if="shouldShowTimeDivider(
                historyTranscript.messages[mi - 1]?.createdAtMillis ?? 0,
                Number(m.createdAtMillis ?? 0))"
              class="msg-time"
            >{{ fmtChatTime(Number(m.createdAtMillis ?? 0)) }}</div>
            <div class="message-line" :class="{ mine: m.senderType === SenderType.STAFF }">
              <span v-if="m.msgType === 'SATISFY'" class="satisfy-card">
                <template v-if="satisfactionOf(m).status !== 'RATED'">
                  <b>⭐ 请评价本次服务</b><small>满意 / 一般 / 不满意</small>
                </template>
                <b v-else>本次服务评价：{{ scoreLabel(satisfactionOf(m).score) }}</b>
              </span>
              <span v-else-if="m.senderType === 'SYSTEM'" class="history-system-card">{{ m.content }}</span>
              <span v-else class="bubble">{{ m.content }}</span>
            </div>
          </template>
          <p v-if="!historyTranscript.messages.length" class="msg-empty">该会话没有消息</p>
        </div>
        <div v-if="historyTranscript.note" class="history-note-card" data-testid="history-note">
          <b>本次服务小结</b>
          <div class="history-note-card__grid">
            <span>问题概述：{{ historyTranscript.note.summary }}</span>
            <span>问题分类：{{ historyCategoryLabel(historyTranscript.note.category) }}</span>
            <span>用户问题：{{ historyInfoLabel(historyTranscript.note.info) }}</span>
            <span>处理结果：{{ historyResultLabel(historyTranscript.note.result) }}</span>
          </div>
          <p>解决办法：{{ historyTranscript.note.solution }}</p>
        </div>
      </template>
      <div v-else class="sent-hint">加载失败</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 客服工作台（多 Tab 版）：左侧「进行中 / 排队中」，右侧最多 3 个会话 Tab（与后端并发上限对齐）。
 *
 * - 打开 Tab：点左侧会话行；已达 3 个时拒绝并提示（前端硬限，后端 403/排队兜底）。
 * - 结束会话：调 close 接口 → 后端自动补位（把排队第一的客户转 ACTIVE 并推送 ASSIGNED）→ 刷新左侧。
 * - 实时：WebSocket 帧按会话号落到对应 Tab；非 Tab 会话的消息只触发列表刷新（3s 节流）。
 */
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtCent, fmtChatTime, fmtDateTime, shouldShowTimeDivider, type AdminOrderDetailVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import {
  chatApi,
  orderApi,
  type AdminCustomerOrderVO,
  type ChatHistoryRowVO,
  type ChatNoteVO,
  type ChatTranscriptVO,
  type OpenSessionRow,
  type TodayStatsVO,
  type WorkloadVO,
} from '@/api';
import { fileApi } from '@/api/file';
import { usePermissionStore } from '@/store/modules/permission';
import {
  CHAT_CATEGORY_LABELS, CHAT_INFO_LABELS, ChatSessionStatus, PresenceStatus,
  SATISFY_LABELS, SenderType, statusLabel,
} from '@sent/shared';
import { useChatSocket, type IncomingAck, type IncomingMessage } from '@/composables/useChatSocket';
import StatusTag from '@/components/StatusTag.vue';
import ChatOrderCard from '@/components/ChatOrderCard.vue';

interface UiMessage {
  key: string;
  clientMsgId: string;
  senderType: string;
  /** TEXT / IMAGE；IMAGE 时 content 是 fileId */
  msgType: string;
  content: string;
  /** 消息时间（毫秒）：5 分钟时间分隔口径，WS 帧取 timestamp，历史取 createdAt */
  createdAt: number;
  pending?: boolean;
}

interface OpenTab {
  sessionId: number;
  status: string;
  staffId: number | null;
  customerId: number;
}

/** 与后端 AgentPresence.DEFAULT_MAX_SESSIONS / staff_presence.max_sessions 对齐 */
const MAX_TABS = 3;

const permission = usePermissionStore();
const staffId = ref<number>(Number(permission.me?.userId ?? 0) || 1);
const presence = ref<string>(PresenceStatus.ONLINE);
const onlineCount = ref(0);
const workload = ref<WorkloadVO[]>([]);
const maxSessions = ref<number>(MAX_TABS);

const activeRows = ref<OpenSessionRow[]>([]);
const waitingRows = ref<OpenSessionRow[]>([]);

/* ------------------------------------------------------------------
 * 左侧会话栏：搜索 + 坐席身份展示（纯前端过滤，不额外打接口）
 * ------------------------------------------------------------------ */
const keyword = ref('');

function matchRow(row: OpenSessionRow): boolean {
  const kw = keyword.value.trim();
  if (!kw) return true;
  return String(row.sessionId).includes(kw) || String(row.customerId).includes(kw);
}

const activeRowsView = computed(() => activeRows.value.filter(matchRow));
const waitingRowsView = computed(() => waitingRows.value.filter(matchRow));

/** 坐席头像用工号后两位，避免再引一个图片资源 */
const staffInitial = computed(() => String(staffId.value).slice(-2));
const presenceText = computed(() => {
  if (presence.value === PresenceStatus.ONLINE) return '在线';
  if (presence.value === PresenceStatus.BUSY) return '忙碌';
  return '离线';
});

const tabs = ref<OpenTab[]>([]);
const activeTab = ref<string>('');
const drafts = reactive<Record<number, string>>({});
const messageStore = reactive<Record<number, UiMessage[]>>({});

const socket = useChatSocket({
  onMessage: onFrame,
  onAck,
  onTyping: onPeerTyping,
  onConnected: (count) => { onlineCount.value = count; },
  onClosed: (sessionId) => markSessionClosed(sessionId),
});

/**
 * 服务端推 CLOSED（人工结束 / 超时）后，Tab 变灰为「已结束」但保留。
 * 客服必须主动点 Tab 上的 × 才移除——自动移除会让客服来不及保存小结。
 */
function markSessionClosed(sessionId: number): void {
  const id = Number(sessionId);
  const tab = tabs.value.find((t) => t.sessionId === id);
  if (tab && tab.status !== ChatSessionStatus.CLOSED) {
    tab.status = ChatSessionStatus.CLOSED;
    ElMessage.info(`会话 #${id} 已结束，可填写本次情况后关闭`);
  }
  void loadTodayStats();
}
const { connected, connect, close: closeSocket, send, sendTyping } = socket;

/** 今日完成接待数（顶栏右侧，跨零点自动归零） */
const todayStats = ref<TodayStatsVO>({ staffId: 0, date: '', closedCount: 0 });

/** 无评价显示 --，不能把 0% 当成「客户不满意」 */
const satisfactionRateText = computed(() => {
  const rate = Number(todayStats.value.ratePercent ?? -1);
  return rate < 0 ? '--' : String(rate);
});

/** 评价卡 content 是 JSON 字符串，脏数据按「待评价」兜底，不让一条坏记录打挂整个消息列表 */
function satisfactionOf(m: { content: string }): { status: string; score: string } {
  try {
    const parsed = JSON.parse(m.content || '{}');
    return { status: String(parsed.status ?? 'PENDING'), score: String(parsed.score ?? '') };
  } catch {
    return { status: 'PENDING', score: '' };
  }
}

function scoreLabel(score: string): string {
  return SATISFY_LABELS[score] ?? score;
}

/** 历史服务弹窗里的小结枚举标签（与右侧服务面板、质检台共用共享字典）。 */
function historyCategoryLabel(value?: string | null): string {
  return CHAT_CATEGORY_LABELS[value ?? ''] ?? '--';
}
function historyInfoLabel(value?: string | null): string {
  return CHAT_INFO_LABELS[value ?? ''] ?? '--';
}
function historyResultLabel(value?: string | null): string {
  return value ? statusLabel(value) : '--';
}

/** 客服主动发评价卡：同会话幂等，重复点不会刷出第二张 */
async function sendSatisfaction(tab: OpenTab): Promise<void> {
  try {
    await unwrap(chatApi.sendSatisfaction(tab.sessionId));
    ElMessage.success('评价卡已发送，客户可直接打分');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '评价卡发送失败');
  }
}

/** 正在输入的会话集合：收到 TYPING 帧加入，3s 无新帧移除 */
const typingSessions = reactive(new Set<number>());
const typingTimers = new Map<number, ReturnType<typeof setTimeout>>();
/** 每个会话「我正在输入」的上报节流时间戳（2s） */
const typingSentAt = new Map<number, number>();

function onPeerTyping(sessionId: number): void {
  const id = Number(sessionId);
  if (!id) return;
  typingSessions.add(id);
  const existing = typingTimers.get(id);
  if (existing) clearTimeout(existing);
  typingTimers.set(id, setTimeout(() => {
    typingSessions.delete(id);
    typingTimers.delete(id);
  }, 3000));
}

/**
 * 客服打字 → 上报给客户。
 * 节流 1s：太短会每个按键发一帧，太长（2s）会让「停一下再打」的那次提示丢帧，
 * 对方看起来像没反应。弱语义、不重试。
 */
function notifyTyping(sessionId: number): void {
  const id = Number(sessionId);
  const now = Date.now();
  if (now - (typingSentAt.get(id) ?? 0) < 1000) return;
  typingSentAt.set(id, now);
  sendTyping(id);
}

async function loadTodayStats(): Promise<void> {
  try {
    const data = await unwrap(chatApi.todayStats(meStaffId.value));
    todayStats.value = {
      staffId: Number(data?.staffId ?? meStaffId.value),
      date: String(data?.date ?? ''),
      closedCount: Number(data?.closedCount ?? 0),
      ratedCount: Number(data?.ratedCount ?? 0),
      satisfiedCount: Number(data?.satisfiedCount ?? 0),
      ratePercent: Number(data?.ratePercent ?? -1),
    };
  } catch {
    // 统计失败不打断工作台
  }
}

const meStaffId = computed(() => Number(staffId.value));

/* ------------------------------------------------------------------
 * 右侧「顾客历史订单」：跟着当前 Tab 的客户走
 * ------------------------------------------------------------------ */
const custOrders = ref<AdminCustomerOrderVO[]>([]);
const custOrdersLoading = ref(false);
/** 刚点过的那一单：给侧栏高亮反馈 */
const activeOrderNo = ref('');
/** 订单详情弹窗：客服点侧栏订单在当前页查看，不离开工作台 */
const orderDialogVisible = ref(false);
const orderDialogLoading = ref(false);
const orderDetail = ref<AdminOrderDetailVO | null>(null);

const activeCustomerId = computed(() => {
  const tab = tabs.value.find((t) => String(t.sessionId) === activeTab.value);
  return tab ? Number(tab.customerId) : 0;
});

async function loadCustomerOrders(customerId: number): Promise<void> {
  if (!customerId) {
    custOrders.value = [];
    return;
  }
  custOrdersLoading.value = true;
  try {
    const rows = await unwrap(orderApi.customerOrders(customerId, 10));
    // Long 序列化成字符串：金额与时间统一 Number() 归一后再交给格式化函数
    custOrders.value = (rows ?? []).map((r) => ({
      ...r,
      payableCent: Number(r.payableCent ?? 0),
      createdAt: Number(r.createdAt ?? 0),
    }));
  } catch {
    // 拉不到订单不影响接待，静默空态即可
    custOrders.value = [];
  } finally {
    custOrdersLoading.value = false;
  }
}

watch(activeCustomerId, (id) => {
  activeOrderNo.value = '';
  void loadCustomerOrders(id);
  void loadHistoryService();
}, { immediate: true });

/* ------------------------------------------------------------------
 * 右侧服务面板：本次情况小结 / 历史服务（≤10）/ 顾客历史订单（默认收起）
 * ------------------------------------------------------------------ */
const noteOpen = ref(true);
const historyOpen = ref(true);
/** 用户要求：历史订单默认不展开，点一次开、再点一次收 */
const ordersOpen = ref(false);
const noteSaving = ref(false);
const noteForm = reactive({
  summary: '', category: 'INQUIRY', info: 'CLEAR', result: 'DONE', solution: '',
});

function currentSessionId(): number {
  return Number(activeTab.value) || 0;
}

async function loadNote(sessionId = currentSessionId()): Promise<void> {
  if (!sessionId) return;
  try {
    const note: ChatNoteVO | null = await unwrap(chatApi.note(sessionId));
    if (!note) return;
    noteForm.summary = note.summary ?? '';
    noteForm.category = note.category ?? 'INQUIRY';
    noteForm.info = note.info ?? 'CLEAR';
    noteForm.result = note.result ?? 'DONE';
    noteForm.solution = note.solution ?? '';
  } catch {
    // 小结为空是常态，静默保留空白表单
  }
}

async function saveNote(): Promise<void> {
  const sessionId = currentSessionId();
  if (!sessionId) return;
  if (!noteForm.summary.trim() || !noteForm.solution.trim()) {
    ElMessage.warning('问题概述与解决办法不能为空');
    return;
  }
  noteSaving.value = true;
  try {
    await unwrap(chatApi.saveNote(sessionId, {
      staffId: Number(staffId.value),
      summary: noteForm.summary.trim(),
      category: noteForm.category,
      info: noteForm.info,
      result: noteForm.result,
      solution: noteForm.solution.trim(),
    }));
    ElMessage.success('本次情况已保存');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '保存失败');
  } finally {
    noteSaving.value = false;
  }
}

/** 同一客户最近服务记录：服务端硬上限 10，当前会话不重复列出 */
const historyRows = ref<ChatHistoryRowVO[]>([]);
const historyLoading = ref(false);
async function loadHistoryService(): Promise<void> {
  const customer = activeCustomerId.value;
  if (!customer) {
    historyRows.value = [];
    return;
  }
  historyLoading.value = true;
  try {
    historyRows.value = (await unwrap(
      chatApi.customerHistory(customer, currentSessionId(), 10))) ?? [];
  } catch {
    historyRows.value = [];
  } finally {
    historyLoading.value = false;
  }
}

const historyDetailVisible = ref(false);
const historyDetailLoading = ref(false);
const historyTranscript = ref<ChatTranscriptVO | null>(null);
async function openHistoryDetail(sessionId: number): Promise<void> {
  historyDetailVisible.value = true;
  historyDetailLoading.value = true;
  historyTranscript.value = null;
  try {
    historyTranscript.value = await unwrap(chatApi.transcript(sessionId));
  } catch {
    historyTranscript.value = null;
  } finally {
    historyDetailLoading.value = false;
  }
}

/** 历史订单搜索：只按订单号过滤，避免误命中商品价格把列表搅乱 */
const orderKeyword = ref('');
const filteredOrders = computed(() => {
  const kw = orderKeyword.value.trim().toUpperCase();
  if (!kw) return custOrders.value;
  return custOrders.value.filter((o) => String(o.orderNo).toUpperCase().includes(kw));
});

watch(() => activeTab.value, (id) => {
  if (!id) return;
  noteForm.summary = '';
  noteForm.solution = '';
  void loadNote(Number(id));
  void loadHistoryService();
});

/** 侧栏「顾客历史订单」与聊天「订单卡片」共用的详情弹窗加载：只认订单号 */
async function loadOrderDetail(orderNo: string): Promise<void> {
  activeOrderNo.value = orderNo;
  orderDialogVisible.value = true;
  orderDialogLoading.value = true;
  orderDetail.value = null;
  try {
    orderDetail.value = await unwrap(orderApi.detail(orderNo));
  } catch {
    orderDetail.value = null;
  } finally {
    orderDialogLoading.value = false;
  }
}

/** 点侧栏顾客历史订单 */
function openOrderDetail(o: AdminCustomerOrderVO): void {
  void loadOrderDetail(o.orderNo);
}

/** 点聊天消息里的订单卡片（ChatOrderCard emit）：不跳页，当前页弹同一详情弹窗 */
function openOrderDetailByNo(orderNo: string): void {
  void loadOrderDetail(orderNo);
}

function messagesOf(sessionId: number): UiMessage[] {
  if (!messageStore[sessionId]) messageStore[sessionId] = [];
  return messageStore[sessionId];
}

function isOpen(sessionId: number): boolean {
  return tabs.value.some((t) => t.sessionId === sessionId);
}

function positionOf(sessionId: number): number {
  const row = waitingRows.value.find((r) => r.sessionId === sessionId);
  return row?.queuePosition ?? 0;
}

function waitText(since: number): string {
  if (!since) return '--';
  const minutes = Math.max(0, Math.floor((Date.now() - Number(since)) / 60_000));
  if (minutes < 1) return '不足 1 分钟';
  // 历史/演示数据可能是几天前的排队会话，直接写「241893 分钟」没法读
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时`;
  return `${Math.floor(hours / 24)} 天`;
}

function pushMessage(sessionId: number, message: UiMessage): void {
  const list = messagesOf(sessionId);
  if (message.clientMsgId) {
    const index = list.findIndex((item) => !!message.clientMsgId && item.clientMsgId === message.clientMsgId);
    if (index >= 0) {
      // 同一 clientMsgId 的重复帧：清 pending 之外，还要覆盖内容——
      // 评价卡打分后服务端就地把内容改成 RATED 再推一帧，只清 pending 会让客服端
      // 永远停在「请评价本次服务」（实际客户已经打过分了）
      list[index] = {
        ...list[index],
        content: message.content,
        msgType: message.msgType || list[index].msgType,
        pending: false,
      };
      return;
    }
  }
  list.push(message);
}

function onFrame(frame: IncomingMessage): void {
  const sessionId = Number(frame.sessionId);
  // 客户给任一会话打分都会改变今日满意率，立即重取，不用等 20s 轮询
  if ((frame.msgType || '').toUpperCase() === 'SATISFY') void loadTodayStats();
  if (isOpen(sessionId)) {
    pushMessage(sessionId, {
      key: frame.clientMsgId || `seq_${frame.seq}`,
      clientMsgId: frame.clientMsgId,
      senderType: frame.senderType,
      msgType: frame.msgType || 'TEXT',
      content: frame.content,
      createdAt: Number(frame.createdAt ?? Date.now()),
      pending: false,
    });
    return;
  }
  // 未打开 Tab 的会话（含排队客户的 QUEUE 广播）：刷新左侧列表让新会话冒出来
  scheduleListRefresh();
}

function onAck(ack: IncomingAck): void {
  if (ack.event === 'REJECTED' || ack.code) {
    // 服务端拒绝（2026-11-19 起含敏感词 40003）：撤回乐观气泡 + 原文还给输入框，
    // 客服改完再发。留着灰色气泡会让人以为「发出去了只是没回」。
    const sessionId = Number(ack.sessionId ?? 0);
    const list = messageStore[sessionId];
    let rejectedContent = '';
    if (list && ack.clientMsgId) {
      const index = list.findIndex((item) => item.clientMsgId === ack.clientMsgId);
      if (index >= 0) {
        rejectedContent = list[index].content;
        list.splice(index, 1);
      }
    }
    if (rejectedContent && !(drafts[sessionId] ?? '').trim()) {
      drafts[sessionId] = rejectedContent;
    }
    ElMessage.error(ack.reason ? `发送失败：${ack.reason}` : `消息被拒绝：code ${ack.code}`);
    return;
  }
  if (!ack.clientMsgId) return;
  for (const sessionId of Object.keys(messageStore)) {
    const list = messageStore[Number(sessionId)];
    const index = list.findIndex((item) => item.clientMsgId === ack.clientMsgId);
    if (index >= 0) {
      list[index] = { ...list[index], pending: false };
      return;
    }
  }
}

/** 列表刷新节流：3s 内最多一次，避免排队期间每个消息帧都打接口 */
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
/** 兜底轮询间隔：服务端「10 分钟无消息自动结束」没有 WS 通知，靠它把已结束会话清出工作台。 */
const IDLE_SYNC_MS = 20_000;
let idleSyncTimer: ReturnType<typeof setInterval> | undefined;
let refreshedAt = 0;
function scheduleListRefresh(): void {
  if (refreshTimer) return;
  refreshTimer = setTimeout(() => {
    refreshTimer = undefined;
    refreshedAt = Date.now();
    void loadOpen();
  }, Math.max(0, 3000 - (Date.now() - refreshedAt)));
}

/** 后端把 Long 序列化成字符串（防 JS 精度丢失），列表行必须先归一成 number 再进 Tab，
 *  否则 `isOpen(Number(frame.sessionId))` 与字符串 sessionId 永不相等，实时消息会被静默丢弃。 */
function normalizeRow(row: any): OpenSessionRow {
  return {
    sessionId: Number(row.sessionId),
    status: String(row.status ?? ''),
    staffId: row.staffId === null || row.staffId === undefined ? null : Number(row.staffId),
    customerId: Number(row.customerId),
    waitingSinceMillis: Number(row.waitingSinceMillis ?? 0),
    queuePosition: Number(row.queuePosition ?? 0),
  };
}

/**
 * 客户进线自动弹出聊天框（2026-11-16 修正）。
 *
 * 原实现用一次性开关 `autoOpened`：工作台启动时只要列表里有会话（哪怕上一轮遗留）
 * 就把开关点亮，之后新客户进线再也不弹——用户实测「发了个你好，并没有出现弹窗」。
 *
 * 现在改为按「会话号是否已见过」判定：当前没有 Tab 时，遇到没见过的会话就自动打开，
 * 并把它记入 knownSessionIds。好处：
 *   1) 新客户进线（新会话号）一定弹，接待完一轮还能继续弹下一位；
 *   2) 客服手动关掉的会话已记过号，不会被列表刷新反复重开；
 *   3) 正在接待别人（已有 Tab）时只标记不抢占，等 Tab 关完后的下一次刷新再弹出。
 */
const knownSessionIds = new Set<number>();
function autoOpenFreshSession(): void {
  if (tabs.value.length > 0) return;
  const rows = [...activeRows.value, ...waitingRows.value];
  const fresh = rows.find((row) => !knownSessionIds.has(row.sessionId));
  if (!fresh) return;
  knownSessionIds.add(fresh.sessionId);
  void openSession(fresh);
}

async function loadOpen(): Promise<void> {
  try {
    const data = await unwrap(chatApi.openSessions(meStaffId.value));
    activeRows.value = (data?.active ?? []).map(normalizeRow);
    waitingRows.value = (data?.waiting ?? []).map(normalizeRow);
    await syncTabs();
    autoOpenFreshSession();
  } catch (error) {
    activeRows.value = [];
    waitingRows.value = [];
    ElMessage.error((error as Error)?.message || '会话列表加载失败');
  }
}

/**
 * 列表刷新后把 Tab 的状态/接待人指回最新值。
 * 已结束会话不再出现在 openSessions 里，但不能直接删 Tab：用户要求客服主动关闭。
 * 这里回查单会话状态，确认 CLOSED 就把 Tab 置灰保留；其它情况（例如刚分配还没进列表）也保留，
 * 避免一次抖动就把客服正在看的聊天窗删掉。
 */
async function syncTabs(): Promise<void> {
  const rows = [...activeRows.value, ...waitingRows.value];
  const kept: OpenTab[] = [];
  for (const tab of tabs.value) {
    const fresh = rows.find((row) => row.sessionId === tab.sessionId);
    if (fresh) {
      kept.push({ ...tab, status: fresh.status, staffId: fresh.staffId, customerId: fresh.customerId });
      continue;
    }
    if (tab.status === ChatSessionStatus.CLOSED) {
      kept.push(tab);
      continue;
    }
    try {
      const detail = await unwrap(chatApi.sessionDetail(tab.sessionId));
      kept.push({ ...tab, status: String(detail?.status ?? tab.status).toUpperCase() });
    } catch {
      kept.push(tab);
    }
  }
  tabs.value = kept;
  if (!kept.some((t) => String(t.sessionId) === activeTab.value)) {
    activeTab.value = kept.length ? String(kept[0].sessionId) : '';
  }
}

async function loadWorkload(): Promise<void> {
  try {
    workload.value = await unwrap(chatApi.workload());
    const mine = workload.value.find((w) => Number(w.staffId) === meStaffId.value);
    if (mine?.maxSessions) maxSessions.value = Number(mine.maxSessions);
  } catch {
    workload.value = [];
  }
}

async function changePresence(): Promise<void> {
  try {
    await unwrap(chatApi.presence(meStaffId.value, presence.value));
    ElMessage.success(`在线状态已设为 ${statusLabel(presence.value)}`);
    await Promise.all([loadOpen(), loadWorkload()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '状态更新失败');
  }
}

/**
 * 打开（或聚焦）一个会话 Tab。
 * 硬限 3 个：与后端并发上限一致，超出时提示而不是静默排队——一个坐席同时盯 4 个窗口只会拖慢响应。
 */
async function openSession(row: OpenSessionRow): Promise<void> {
  const existing = tabs.value.find((t) => t.sessionId === row.sessionId);
  if (existing) {
    activeTab.value = String(row.sessionId);
    return;
  }
  if (tabs.value.length >= MAX_TABS) {
    ElMessage.warning(`最多同时打开 ${MAX_TABS} 个会话，请先结束一个`);
    return;
  }
  tabs.value.push({
    sessionId: row.sessionId,
    status: row.status,
    staffId: row.staffId,
    customerId: row.customerId,
  });
  activeTab.value = String(row.sessionId);
  await loadHistory(row.sessionId);
}

/**
 * 拉历史并「合并」进消息列表。
 * <p>不能整体替换：打开 Tab 的瞬间如果正好有 WS 推送进来（客户刚发消息），
 * 替换会把那条实时消息抹掉，表现为「第一条消息看不到」。这里按 clientMsgId / key 去重合并。
 */
async function loadHistory(sessionId: number): Promise<void> {
  try {
    const history = await unwrap(chatApi.history(sessionId));
    const fetched: UiMessage[] = (history ?? []).map((item: any) => ({
      key: String(item.messageId),
      clientMsgId: item.clientMsgId ?? '',
      senderType: item.senderType,
      msgType: item.msgType ?? 'TEXT',
      content: item.content,
      // 历史接口字段是 createdAtMillis（ChatDtos.MessageVO），不是 createdAt：
      // 写成 createdAt 会让整段历史时间恒为 0，首条消息上方直接渲染出「1970年01月01日 08:00」。
      // JacksonConfig 把 Long 序列化成字符串，必须 Number() 后才是毫秒数
      createdAt: Number(item.createdAtMillis ?? 0),
    }));
    const existing = messageStore[sessionId] ?? [];
    const merged = [...fetched];
    for (const item of existing) {
      const duplicated = merged.some((m) =>
        (!!m.clientMsgId && m.clientMsgId === item.clientMsgId) || m.key === item.key);
      if (!duplicated) merged.push(item);
    }
    messageStore[sessionId] = merged;
  } catch (error) {
    ElMessage.error((error as Error)?.message || '历史消息加载失败');
  }
}

function closeTab(name: string | number): void {
  const sessionId = Number(name);
  tabs.value = tabs.value.filter((t) => t.sessionId !== sessionId);
  delete messageStore[sessionId];
  if (!tabs.value.length) {
    activeTab.value = '';
  } else if (activeTab.value === String(sessionId)) {
    activeTab.value = String(tabs.value[tabs.value.length - 1].sessionId);
  }
}

function sendMessage(tab: OpenTab): void {
  const content = (drafts[tab.sessionId] ?? '').trim();
  if (!content) return;
  const clientMsgId = send(tab.sessionId, content, 'TEXT');
  if (!clientMsgId) {
    ElMessage.warning('WebSocket 未连接，请稍候或点击重连');
    return;
  }
  pushMessage(tab.sessionId, {
    key: clientMsgId,
    clientMsgId,
    senderType: SenderType.STAFF,
    msgType: 'TEXT',
    content,
    createdAt: Date.now(),
    pending: true,
  });
  drafts[tab.sessionId] = '';
}

/* ---------------- 客服发图片 ---------------- */
const previewSrc = ref('');
const uploadingImg = ref(false);
/** 当前要发图的会话号：隐藏 input 只有一个，靠它记住目标 Tab */
const imageTargetSession = ref<number | null>(null);
const imageInput = ref<HTMLInputElement | null>(null);

function previewImage(fileId: string): void {
  previewSrc.value = fileApi.url(fileId);
}

function pickImage(sessionId: number): void {
  imageTargetSession.value = sessionId;
  imageInput.value?.click();
}

/**
 * 客服发图片：先上传拿 fileId，再以 msgType=IMAGE 通过 WS 发一条消息。
 * 客户侧收到后按 msgType 渲染成图片气泡。
 */
async function onImagePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  const sessionId = imageTargetSession.value;
  input.value = '';
  if (!file || !sessionId) return;
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('图片超过 20MB，请压缩后再发');
    return;
  }
  uploadingImg.value = true;
  try {
    const uploaded = await unwrap(fileApi.upload(file));
    const clientMsgId = send(sessionId, uploaded.fileId, 'IMAGE');
    if (!clientMsgId) {
      ElMessage.warning('WebSocket 未连接，请稍候或点击重连');
      return;
    }
    pushMessage(sessionId, {
      key: clientMsgId,
      clientMsgId,
      senderType: SenderType.STAFF,
      msgType: 'IMAGE',
      content: uploaded.fileId,
      createdAt: Date.now(),
      pending: true,
    });
  } catch (e) {
    ElMessage.error((e as Error).message || '图片发送失败');
  } finally {
    uploadingImg.value = false;
  }
}

async function accept(tab: OpenTab): Promise<void> {
  try {
    await unwrap(chatApi.accept(tab.sessionId, meStaffId.value));
    ElMessage.success(`已接待会话 #${tab.sessionId}`);
    await loadOpen();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '接待失败');
  }
}

/** 结束会话：关闭后后端自动补位排队第一的客户，这里只需刷出新状态 */
async function closeSession(tab: OpenTab): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定结束会话 #${tab.sessionId}？结束后会自动接入排队中的下一位客户。`, '结束会话', {
      confirmButtonText: '确认结束',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    const result = await unwrap(chatApi.close(tab.sessionId, 'DONE'));
    ElMessage.success(result?.promotedSessionId
      ? `会话已结束，已自动接入排队客户 #${result.promotedSessionId}`
      : '会话已结束');
    // 结束后 Tab 保留为灰色「已结束」，由客服点 × 主动关闭，方便先填本次情况
    markSessionClosed(tab.sessionId);
    void loadNote(tab.sessionId);
    void loadHistoryService();
    await Promise.all([loadOpen(), loadTodayStats()]);
  } catch (error) {
    ElMessage.error((error as Error)?.message || '结束会话失败');
  }
}

function reconnect(): void {
  closeSocket();
  connect();
}

/**
 * 打开工作台即上报在线状态：后端建会话时按「在线坐席」自动分配，
 * 不报状态的话客户进线只能落 WAITING，队列永远补不上人。
 */
async function reportPresence(): Promise<void> {
  const id = Number(permission.me?.userId ?? 0);
  if (!Number.isFinite(id) || id <= 0) return;
  staffId.value = id;
  try {
    await unwrap(chatApi.presence(id, presence.value));
  } catch {
    // 静默：上报失败不影响列表与实时消息
  }
}

onMounted(async () => {
  connect();
  void reportPresence();
  await Promise.all([loadOpen(), loadWorkload(), loadTodayStats()]);
  // 超时自动结束（10 分钟无消息）由服务端定时任务收，不回推 WebSocket 帧：
  // 不轮询的话工作台会一直挂着已经结束的 Tab，客服回一句才发现被拒（STATE_CONFLICT）。
  // 20s 一轮全量刷新左侧：自动结束的会话清出列表、Tab 随之关闭，新排队的客户也能自动弹出。
  idleSyncTimer = setInterval(() => { void loadOpen(); void loadTodayStats(); }, IDLE_SYNC_MS);
});

onUnmounted(() => {
  if (refreshTimer) clearTimeout(refreshTimer);
  if (idleSyncTimer) clearInterval(idleSyncTimer);
});
</script>

<style scoped>
/* =====================================================================
 * 客服工作台（2026-11-16 重设计）：左「坐席 + 会话」/ 中「聊天」/ 右「顾客订单」
 * 高度铺满视口，三块各自滚动；配色沿用品牌绿 #12664f，层级靠底色与描边区分。
 * ===================================================================== */
.wb {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 116px);
  min-height: 540px;
}

/* ---------- 顶部状态条 ---------- */
.wb__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 12px;
}
.wb__bar-main { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.wb__title { margin: 0; font-size: 17px; font-weight: 600; color: #1f2d28; letter-spacing: 0.2px; }
.wb__chip { font-size: 12px; color: #6b7a74; }
.wb__chip b { color: #12664f; font-weight: 600; }
.wb__chip--tip { padding: 2px 8px; border-radius: 999px; background: #fff7e6; color: #96631a; }
.wb__bar-tools { display: flex; align-items: center; gap: 2px; }
.wb__bar-tools :deep(.el-button) { padding: 6px 8px; font-size: 12px; color: #5c6b65; }

.today-stat {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-right: 6px;
  padding: 4px 12px;
  border-radius: 18px;
  background: #f0f7f4;
  border: 1px solid #d8e8e1;
}
.today-stat__label { font-size: 12px; color: #5c6b65; }
.today-stat__num, .today-rate__num { font-size: 18px; font-weight: 700; color: #12664f; line-height: 1; }
.today-stat__unit { font-size: 12px; color: #5c6b65; }

/* ---------- 主体三栏（窄屏下右侧订单变抽屉式堆叠） ---------- */
.wb__grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 12px;
}

/* ---------- 左：坐席 + 会话列表 ---------- */
.wb__side {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 12px;
  overflow: hidden;
}
.seat {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 12px;
  border-bottom: 1px solid #eef2f0;
}
.seat__avatar {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #12664f, #2f9e7a);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: grid;
  place-items: center;
}
.seat__body { flex: 1; min-width: 0; }
.seat__name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2d28;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.seat__sub {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 3px;
  font-size: 11px;
  color: #7b8a84;
  white-space: nowrap;
}
.seat__dot { width: 6px; height: 6px; border-radius: 50%; background: #c2c7cf; }
.seat__dot--online { background: #2f9e7a; }
.seat__dot--busy { background: #e6a23c; }
.seat__dot--offline { background: #c2c7cf; }
.seat__presence { width: 78px; flex: none; }

.side-search { padding: 10px 12px; border-bottom: 1px solid #eef2f0; }
.side-search :deep(.el-input__wrapper) { border-radius: 8px; }

.side-list { flex: 1; min-height: 0; overflow-y: auto; padding: 4px 10px 12px; }
.list-panel { margin-top: 10px; }
.side-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 4px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #5c6b65;
}
.side-head .count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #eef5f1;
  color: #12664f;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.side-empty { margin: 0 4px 8px; font-size: 12px; color: #a6b0ac; }

.session-item {
  display: flex;
  gap: 10px;
  margin-bottom: 6px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #fbfcfc;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.session-item:hover { background: #f2f8f5; border-color: #dcebe4; }
.session-item.current { background: #eaf5ef; border-color: #12664f; }
.queue-item { background: #fffbf3; }
.queue-item:hover { background: #fff6e6; border-color: #f0e0c4; }
.s-avatar {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 50%;
  background: #eef5f1;
  color: #12664f;
  font-size: 12px;
  font-weight: 600;
  display: grid;
  place-items: center;
}
.s-avatar--queue { background: #fff3e0; color: #b26a00; }
.s-body { flex: 1; min-width: 0; }
.line1 { display: flex; align-items: center; justify-content: space-between; gap: 6px; font-size: 12.5px; color: #1f2d28; }
.line2 {
  margin-top: 4px;
  font-size: 11.5px;
  color: #7b8a84;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 中：聊天 ---------- */
.wb__main {
  display: flex;
  min-width: 0;
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 12px;
  overflow: hidden;
}
.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #a6b0ac;
}
.chat-empty__icon { font-size: 34px; opacity: 0.45; }
.chat-empty__title { margin: 8px 0 0; font-size: 14px; font-weight: 600; color: #40514b; }
.chat-empty__hint { margin: 0; font-size: 12px; }

.chat-tabs { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.chat-tabs :deep(.el-tabs__header) { margin: 0; padding: 8px 12px 0; background: #fbfcfc; border-bottom: 1px solid #eef2f0; }
.chat-tabs :deep(.el-tabs__content) { flex: 1; min-height: 0; }
.chat-tabs :deep(.el-tab-pane) { height: 100%; }
.chat-tabs :deep(.el-tabs__item) { height: 34px; line-height: 34px; font-size: 12.5px; }
.tab-label { display: inline-flex; align-items: center; gap: 6px; }

.tab-body { height: 100%; display: flex; flex-direction: column; }
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f3;
}
.chat-head__who { display: flex; align-items: center; gap: 10px; min-width: 0; }
.chat-head__avatar {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 50%;
  background: #eef5f1;
  color: #12664f;
  font-size: 12px;
  font-weight: 600;
  display: grid;
  place-items: center;
}
.chat-head__info { min-width: 0; }
.chat-head__name { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #1f2d28; }
.chat-head__sub { margin-top: 2px; font-size: 11.5px; color: #8a94a6; }
.chat-head__actions { display: flex; gap: 6px; flex: none; }

.message-box { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; background: #f7faf8; }
.msg-empty { margin: 28px 0 0; text-align: center; font-size: 12px; color: #a6b0ac; }
/* 5 分钟时间分隔：居中灰色小字，与用户端同一口径 */
.msg-time { text-align: center; margin: 10px 0 6px; font-size: 11px; color: #9aa4b2; line-height: 1.4; }
.message-line { display: flex; margin-bottom: 10px; }
.message-line.mine { justify-content: flex-end; }
.bubble {
  max-width: 72%;
  padding: 9px 13px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e3ebe7;
  box-shadow: 0 1px 2px rgba(16, 40, 32, 0.04);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}
.message-line.mine .bubble { background: #12664f; color: #fff; border-color: #12664f; box-shadow: none; }
.bubble small { opacity: 0.7; }
.bubble--img { padding: 4px; line-height: 0; }
.bubble-img { max-width: 200px; max-height: 200px; border-radius: 8px; cursor: zoom-in; display: block; }

.send-row { display: flex; align-items: flex-end; gap: 8px; padding: 10px 14px 4px; background: #fff; }
/* 评价按钮在图片按钮正上方（2026-11-17 参考图） */
.send-row__tools { display: flex; flex-direction: column; gap: 4px; flex: none; }
.send-row__rate { margin-left: 0; }
.satisfy-card {
  display: inline-flex; flex-direction: column; gap: 2px;
  max-width: 76%; padding: 8px 12px; border-radius: 10px;
  background: #fff7e6; border: 1px solid #ffd591; color: #874d00; font-size: 12px;
}
.satisfy-card small { color: #b37f2b; font-size: 11px; }
.message-line.mine .satisfy-card { background: #fff7e6; color: #874d00; }
.send-row__img { flex: none; }
.send-row :deep(.el-textarea__inner) { min-height: 36px !important; border-radius: 8px; }
.send-tip { margin: 0; padding: 0 14px 10px; text-align: right; font-size: 11px; color: #a6b0ac; }

/* 客户正在输入 */
.typing-hint { display: flex; align-items: center; gap: 4px; margin-top: 6px; font-size: 12px; color: #8a94a6; }
.typing-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #12664f;
  animation: typing-bounce 1.1s infinite ease-in-out;
}
.typing-dot:nth-child(2) { animation-delay: 0.15s; }
.typing-dot:nth-child(3) { animation-delay: 0.3s; }
@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-3px); opacity: 1; }
}

/* ---------- 右：本次情况 / 历史服务 / 顾客历史订单 ---------- */
.cust-orders {
  width: 292px;
  flex: none;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 12px 14px 10px;
  background: #fbfcfc;
  border-left: 1px solid #eef2f0;
}
/* 宽屏：右侧服务面板与客户栏一起放宽，聊天区仍留足宽度 */
@media (min-width: 1600px) {
  .wb__grid { grid-template-columns: 320px minmax(0, 1fr); }
  .cust-orders { width: 340px; }
}
.side-section { border-top: 1px solid #e9efec; padding: 8px 0; }
.side-section__toggle {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: none;
  background: transparent;
  padding: 4px 0;
  color: #33463f;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
.side-section__toggle:hover { color: #12664f; }
.side-section__body { display: flex; flex-direction: column; gap: 6px; padding-top: 6px; }
.note-form label { font-size: 11px; color: #7b8a84; }
.note-form :deep(.el-select) { width: 100%; }
.order-search { margin-bottom: 6px; }
.history-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid #e6ece9;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
.history-row:hover { border-color: #12664f; background: #f2f8f5; }
.history-row b { font-size: 11.5px; color: #33463f; }
.history-row span { font-size: 11px; color: #8a94a6; }
/* 历史服务详情：宽屏大弹窗 + 与客服聊天一致的气泡回放 */
.history-meta { margin: 0 0 12px; font-size: 12px; color: #8a94a6; }
.history-message-box {
  height: 58vh;
  min-height: 420px;
  border: 1px solid #e6ece9;
  border-radius: 10px;
}
.history-system-card {
  max-width: 76%; padding: 8px 12px; border-radius: 10px; font-size: 12px;
  background: #eef2f0; color: #6b7a74; word-break: break-word;
}
.history-note-card {
  margin-top: 14px; padding: 12px 14px; border: 1px solid #d7e9df;
  border-radius: 10px; background: #f0f7f4; font-size: 13px; color: #40514b;
}
.history-note-card > b { display: block; margin-bottom: 8px; color: #12664f; }
.history-note-card__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 18px; }
.history-note-card p { margin: 8px 0 0; line-height: 1.6; word-break: break-word; }
@media (max-width: 900px) {
  .history-note-card__grid { grid-template-columns: 1fr; }
}
.cust-orders__head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.cust-orders__head b { font-size: 13px; color: #33463f; }
.cust-orders__cid { font-size: 11px; color: #8a94a6; }
.cust-orders__hint { padding: 18px 0; text-align: center; font-size: 12px; color: #8a94a6; }
.cust-orders__list { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding-right: 2px; }
.cust-order {
  padding: 9px 10px;
  border: 1px solid #e6ece9;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.cust-order:hover { border-color: #12664f; background: #f2f8f5; }
.cust-order--active { border-color: #12664f; background: #eaf5ef; }
.cust-order__top { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.cust-order__no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: #4a5b54;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cust-order__foot { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; margin-top: 6px; }
.cust-order__time { font-size: 11px; color: #8a94a6; }
.cust-order__amount { font-size: 12px; color: #d46b08; }
.cust-orders__tip { margin: 8px 0 0; text-align: center; font-size: 11px; color: #a6b0ac; }

.hidden-input { display: none; }

/* ---------- 订单详情弹窗（append-to-body，样式需 :deep） ---------- */
.order-detail-dialog :deep(.order-dlg-header) { position: relative; display: flex; align-items: center; min-height: 24px; }
.order-detail-dialog :deep(.order-dlg-title) { font-size: 16px; font-weight: 600; color: #1f2937; }
/* 左上角 × 关闭：客服一眼能找到，点它即关，不误触右上角默认叉 */
.order-detail-dialog :deep(.order-dlg-close) {
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #6b7280;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.order-detail-dialog :deep(.order-dlg-close:hover) { background: #f0f7f4; color: #12664f; }
.order-detail-dialog :deep(.order-dlg-hint) { padding: 24px 0; text-align: center; font-size: 13px; color: #8a94a6; }
.order-detail-dialog :deep(.order-dlg-meta) { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 20px; padding: 4px 0 16px; }
.order-detail-dialog :deep(.order-dlg-meta > div) { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.order-detail-dialog :deep(.order-dlg-meta span) { min-width: 64px; color: #8a94a6; }
.order-detail-dialog :deep(.order-dlg-lines) { margin-top: 4px; }
.order-detail-dialog :deep(.order-dlg-amount) { margin-top: 16px; text-align: right; font-size: 13px; color: #4b5563; }
.order-detail-dialog :deep(.order-dlg-pay) { color: #d46b08; font-size: 18px; }

/* 窄屏：会话栏与服务记录一起收窄，但绝不隐藏——服务记录必须打开工作台就能看见 */
@media (max-width: 1280px) {
  .wb__grid { grid-template-columns: 240px minmax(0, 1fr); }
  .cust-orders { width: 240px; }
}
@media (max-width: 900px) {
  .wb__grid { grid-template-columns: 1fr; }
  .cust-orders { width: auto; border-left: none; border-top: 1px solid #eef2f0; }
}
</style>
