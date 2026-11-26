<template>
  <div class="wb">
    <!-- 顶部条：状态与工具收成一行，把空间让给会话 -->
    <header class="wb__bar">
      <div class="wb__bar-main">
        <!-- 页面身份由布局顶栏承担，这里不再重复 h2 标题，把宽度留给状态与统计 -->
        <el-tag :type="connected ? 'success' : 'info'" effect="dark" size="small">
          {{ connected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}
        </el-tag>
        <span class="wb__chip">在线客服 <b>{{ onlineCount }}</b></span>
        <span class="wb__chip">我的并发 <b>{{ openTabCount }}/{{ maxSessions }}</b></span>
        <!-- 2026-11-25 用户要求删掉左侧「排队中」列表（在线即自动接待），只在这里告知还有人在等 -->
        <span v-if="waitingCount > 0" class="wb__chip wb__chip--queue" data-testid="queue-hint">
          另有 <b>{{ waitingCount }}</b> 位客户在排队
        </span>
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
              <span data-testid="seat-presence-hint">{{ presenceHint }}</span>
            </div>
          </div>
          <!-- 状态开关已上移到布局顶栏（cs-presence），这里只做只读回显（见 .seat__sub 的 presenceText） -->
        </div>

        <div class="side-search">
          <el-input v-model="keyword" size="small" clearable placeholder="搜索客户 ID / 会话号" />
        </div>

        <div class="side-list">
          <section class="list-panel" data-testid="active-panel">
            <h3 class="side-head">进行中 <span class="count">{{ activeRows.length }}</span></h3>
            <p v-if="!activeRowsView.length" class="side-empty">暂无进行中会话</p>
            <div
              v-for="row in activeRowsView"
              :key="row.sessionId"
              class="session-item"
              :class="{ current: isOpen(row.sessionId) }"
              :data-session-id="row.sessionId"
              @click="openSession(row)"
            >
              <div class="s-avatar">
                <img v-if="row.customerAvatarFileId" :src="fileApi.url(row.customerAvatarFileId)" alt="" />
                <span v-else>{{ customerInitial(row) }}</span>
              </div>
              <div class="s-body">
                <div class="line1">
                  <strong class="s-name" data-testid="session-customer-name">{{ customerLabel(row) }}</strong>
                  <StatusTag :status="row.status" />
                </div>
                <div class="line2">
                  <!-- 「进行中」里已经没有排队态了（WAITING 只在离线/忙碌/满位时短暂出现），
                       一律写「已等待」会让客服以为客户还在等接线 -->
                  会话 #{{ row.sessionId }} ·
                  {{ row.status === ChatSessionStatus.WAITING ? '已排队' : '已接入' }}
                  {{ waitText(row.waitingSinceMillis) }}
                </div>
              </div>
            </div>
          </section>

          <!-- 已结束但未关闭的会话（2026-11-25）：取消顶部 Tab 条后，回看与填写本次情况都靠这一栏 -->
          <section v-if="closedRows.length" class="list-panel" data-testid="closed-panel">
            <h3 class="side-head">已结束 <span class="count">{{ closedRows.length }}</span></h3>
            <div
              v-for="row in closedRows"
              :key="row.sessionId"
              class="session-item session-item--closed"
              :class="{ current: isOpen(row.sessionId) }"
              :data-session-id="row.sessionId"
              @click="openSession(row)"
            >
              <div class="s-avatar s-avatar--closed"><span>{{ customerInitial(row) }}</span></div>
              <div class="s-body">
                <div class="line1">
                  <strong class="s-name">{{ customerLabel(row) }}</strong>
                  <el-tag size="small" type="info" effect="plain">已结束</el-tag>
                </div>
                <div class="line2">会话 #{{ row.sessionId }} · 可回看、填本次情况</div>
              </div>
              <button
                type="button"
                class="s-close"
                title="关闭该会话（填完本次情况后移除）"
                data-testid="close-ended"
                @click.stop="closeTab(row.sessionId)"
              >×</button>
            </div>
          </section>
        </div>
      </aside>

      <!-- 中：单会话聊天区（切会话走左侧列表）+ 右：服务记录三 Tab -->
      <main class="wb__main">
        <div v-if="!activeTabRow" class="chat-empty">
          <div class="chat-empty__icon">💬</div>
          <p class="chat-empty__title">还没有进行中的会话</p>
          <p class="chat-empty__hint">
            {{ canAccept
              ? '客户进线会自动接入并弹出聊天框；也可以从左侧点会话打开（最多同时 3 个）'
              : '当前不是在线状态：切换顶栏「在线」后，客户进线才会自动接入' }}
          </p>
        </div>

        <!-- 单会话视图（2026-11-25）：顶部 Tab 条已删除，切换会话走左侧列表 -->
        <div v-else class="chat-panel" :data-session-id="activeTabRow.sessionId">
          <div class="tab-body">
                <!-- 会话头：进线客户的头像与姓名、状态、可做的动作 -->
                <header class="chat-head">
                  <div class="chat-head__who">
                    <div class="chat-head__avatar">
                      <img
                        v-if="activeTabRow.customerAvatarFileId"
                        :src="fileApi.url(activeTabRow.customerAvatarFileId)"
                        alt=""
                      />
                      <span v-else>{{ customerInitial(activeTabRow) }}</span>
                    </div>
                    <div class="chat-head__info">
                      <div class="chat-head__name" data-testid="chat-customer-name">
                        {{ customerLabel(activeTabRow) }}
                        <el-tag
                          v-if="activeTabRow.status === ChatSessionStatus.WAITING"
                          size="small" type="warning"
                        >排队中</el-tag>
                        <el-tag
                          v-else-if="activeTabRow.status === ChatSessionStatus.CLOSED"
                          size="small" type="info" effect="plain"
                        >已结束</el-tag>
                        <el-tag v-else size="small" type="success" effect="plain">进行中</el-tag>
                      </div>
                      <div class="chat-head__sub">
                        客户 {{ activeTabRow.customerId }} · 会话 #{{ activeTabRow.sessionId }}
                      </div>
                    </div>
                  </div>
                <!-- 客户标签（V64）：离头像与姓名稍远（margin-left 40px），已打的标签排在「标签」按钮右边。
                     标签是客服之间的内部判断：用户端没有任何接口能读到，客户不会知道被标成什么。 -->
                <div class="chat-head__tags" data-testid="customer-tags">
                  <button
                    type="button"
                    class="tag-add-btn"
                    :class="{ 'tag-add-btn--on': tagDialogVisible }"
                    :disabled="!activeCustomerId"
                    data-testid="tag-add-btn"
                    @click="openTagDialog"
                  >＋ 标签</button>
                  <span
                    v-for="tag in customerTags"
                    :key="tag.tagId"
                    class="tag-chip"
                    :class="`tag-chip--${tag.colorKey}`"
                    :title="`${tag.categoryLabel} · 点「＋ 标签」可取消`"
                    :data-testid="`tag-chip-${tag.tagId}`"
                  >{{ tag.name }}</span>
                  <span v-if="!customerTags.length" class="chat-head__tags-hint">未打标签</span>
                </div>
                  <div class="chat-head__actions">
                    <el-button
                      v-if="activeTabRow.status === ChatSessionStatus.WAITING"
                      type="primary"
                      size="small"
                      :disabled="!canAccept"
                      :title="canAccept ? '' : '当前离线/忙碌，切换到在线后才能接待'"
                      data-testid="accept-btn"
                      @click="accept(activeTabRow)"
                    >
                      接待
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      plain
                      :disabled="activeTabRow.status === ChatSessionStatus.CLOSED"
                      data-testid="close-session"
                      @click="closeSession(activeTabRow)"
                    >结束会话</el-button>
                  </div>
                </header>

                <!-- 会话已结束横幅（2026-11-25）：Tab 条取消后，结束状态必须在聊天区一眼可见 -->
                <div
                  v-if="activeTabRow.status === ChatSessionStatus.CLOSED"
                  class="chat-ended"
                  data-testid="chat-ended-banner"
                >
                  <b>本次会话已结束</b>
                  <span>可在右侧「本次情况」填写并提交工单，提交后才能关闭这个会话窗口</span>
                </div>

                <div class="message-box">
                  <template v-for="(m, mi) in messagesOf(activeTabRow.sessionId)" :key="m.key">
                  <!-- 微信式时间提示：相邻消息间隔满 5 分钟重新显示当前时间（首条必显） -->
                  <div
                    v-if="shouldShowTimeDivider(messagesOf(activeTabRow.sessionId)[mi - 1]?.createdAt, m.createdAt)"
                    class="msg-time"
                    data-testid="chat-time-divider"
                  >{{ fmtChatTime(m.createdAt) }}</div>
                  <!-- 服务评价卡：客户打分前显示「等待评价」，打分后就地显示档位 -->
                  <div v-if="m.msgType === 'SATISFY'" class="message-line">
                    <span class="satisfy-card" data-testid="satisfaction-card">
                      <template v-if="satisfactionOf(m).status !== 'RATED'">
                        <b>⭐ 请评价本次服务</b>
                        <small>满意 / 一般 / 不满意</small>
                      </template>
                      <template v-else>
                        <b>本次服务评价：{{ scoreLabel(satisfactionOf(m).score) }}</b>
                      </template>
                    </span>
                  </div>
                  <!-- 系统旁白 / 机器人话术：居中浅灰提示。
                       以前一律当普通气泡渲染，客服会以为是客户说的话 -->
                  <div
                    v-else-if="isSystemNote(m)"
                    class="msg-system"
                    data-testid="msg-system"
                  >{{ m.content }}</div>
                  <div
                    v-else
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
                    <span v-else class="bubble">
                      {{ m.content }}
                      <small v-if="m.pending">· 发送中</small>
                    </span>
                  </div>
                  </template>
                  <p v-if="!messagesOf(activeTabRow.sessionId).length" class="msg-empty">还没有消息，先打个招呼吧</p>
                  <!-- 客户正在输入：收到 TYPING 帧亮 3s，无新帧自动熄灭 -->
                  <div v-if="typingSessions.has(activeTabRow.sessionId)" class="typing-hint">
                    <span class="typing-dot" /><span class="typing-dot" /><span class="typing-dot" />
                    客户正在输入…
                  </div>
                </div>

                <!-- 输入区（2026-11-25）：左侧三个工具按「图片 / 表情 / 请评价」排列 -->
                <div class="send-row">
                  <div class="send-row__tools">
                    <button
                      type="button"
                      class="tool-btn"
                      :disabled="!connected || uploadingImg || activeTabRow.status === ChatSessionStatus.CLOSED"
                      title="发送图片"
                      data-testid="send-image"
                      @click="pickImage(activeTabRow.sessionId)"
                    >
                      <span class="tool-btn__icon">🖼</span><span class="tool-btn__text">图片</span>
                    </button>

                    <div class="emoji-anchor">
                      <button
                        type="button"
                        class="tool-btn"
                        :disabled="activeTabRow.status === ChatSessionStatus.CLOSED"
                        title="插入表情"
                        data-testid="emoji-toggle"
                        @click="emojiOpen = !emojiOpen"
                      >
                        <span class="tool-btn__icon">😊</span><span class="tool-btn__text">表情</span>
                      </button>
                      <div v-if="emojiOpen" class="emoji-panel" data-testid="emoji-panel">
                        <button
                          v-for="e in EMOJIS"
                          :key="e"
                          type="button"
                          class="emoji-item"
                          :data-testid="`emoji-item`"
                          @click="insertEmoji(e)"
                        >{{ e }}</button>
                      </div>
                    </div>

                    <button
                      type="button"
                      class="tool-btn"
                      :disabled="activeTabRow.status === ChatSessionStatus.WAITING
                        || activeTabRow.status === ChatSessionStatus.CLOSED"
                      title="发送服务评价卡，客户可打满意 / 一般 / 不满意"
                      data-testid="send-satisfaction"
                      @click="sendSatisfaction(activeTabRow)"
                    >
                      <span class="tool-btn__icon">⭐</span><span class="tool-btn__text">请评价</span>
                    </button>
                  </div>

                  <el-input
                    v-model="drafts[activeTabRow.sessionId]"
                    type="textarea"
                    :rows="1"
                    resize="none"
                    :disabled="activeTabRow.status === ChatSessionStatus.CLOSED"
                    :placeholder="activeTabRow.status === ChatSessionStatus.CLOSED ? '本次会话已结束' : '输入消息后回车发送'"
                    @input="notifyTyping(activeTabRow.sessionId)"
                    @keydown.enter.exact.prevent="sendMessage(activeTabRow)"
                  />
                  <el-button
                    type="primary"
                    :disabled="!connected || activeTabRow.status === ChatSessionStatus.CLOSED"
                    data-testid="send-btn"
                    @click="sendMessage(activeTabRow)"
                  >发送</el-button>
                </div>
                <p class="send-tip">Enter 发送 · 支持图片与订单卡片</p>
          </div>
        </div>

        <!-- 右侧：本次情况小结 + 历史服务 + 顾客历史订单（订单默认收起） -->
        <!-- 2026-11-18：无会话时也常驻显示，客服打开工作台即可看到表单与默认选项，
             不必先手动点开一个会话；无会话时保存按钮禁用、历史/订单区显示空态。 -->
        <aside class="cust-orders">
            <header class="cust-orders__head">
              <b>服务记录</b>
              <span class="cust-orders__cid">客户 {{ activeCustomerId || '--' }}</span>
            </header>

            <!-- 三个平级 Tab（2026-11-25 用户要求）：选中项用品牌绿加粗 + 下划线 -->
            <div class="record-tabs" role="tablist">
              <button
                type="button" role="tab" class="record-tab"
                :class="{ 'record-tab--active': recordTab === 'note' }"
                :aria-selected="recordTab === 'note'"
                data-testid="record-tab-note"
                @click="recordTab = 'note'"
              >本次情况</button>
              <button
                type="button" role="tab" class="record-tab"
                :class="{ 'record-tab--active': recordTab === 'history' }"
                :aria-selected="recordTab === 'history'"
                data-testid="record-tab-history"
                @click="recordTab = 'history'"
              >历史服务<span class="record-tab__count">{{ historyRows.length }}</span></button>
              <button
                type="button" role="tab" class="record-tab"
                :class="{ 'record-tab--active': recordTab === 'orders' }"
                :aria-selected="recordTab === 'orders'"
                data-testid="record-tab-orders"
                @click="recordTab = 'orders'"
              >顾客订单</button>
              <!-- 第 4 个 Tab（V64）：客服发券。与「本次情况」并列，因为补偿券往往就是这通电话的结论 -->
              <button
                type="button" role="tab" class="record-tab"
                :class="{ 'record-tab--active': recordTab === 'coupon' }"
                :aria-selected="recordTab === 'coupon'"
                data-testid="record-tab-coupon"
                @click="recordTab = 'coupon'"
              >优惠券发放<span v-if="giftRecords.length" class="record-tab__count">{{ giftRecords.length }}</span></button>
            </div>

            <!-- Tab 1：本次情况（2026-11-25 二次改版）：并发接待时切会话即填，不再强制先开抽屉 -->
            <div v-show="recordTab === 'note'" class="record-body note-form" data-testid="session-note-panel">
              <label>问题描述 <em class="required">*</em></label>
              <el-input v-model="noteForm.summary" size="small" maxlength="255"
                        placeholder="客户反馈的问题" data-testid="note-inline-summary" />

              <div class="note-row2">
                <div class="note-cell">
                  <label>问题分类 <em class="required">*</em></label>
                  <el-select v-model="noteForm.category" size="small" data-testid="note-inline-category">
                    <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt.value"
                               :label="opt.label" :value="opt.value" />
                  </el-select>
                </div>
                <div class="note-cell">
                  <label>明确信息 <em class="required">*</em></label>
                  <el-select v-model="noteForm.info" size="small" data-testid="note-inline-info">
                    <el-option label="明确信息" value="CLEAR" />
                    <el-option label="客户未回复" value="NO_REPLY" />
                  </el-select>
                </div>
              </div>

              <label>子分类</label>
              <el-input v-model="noteForm.subCategory" size="small" maxlength="64"
                        placeholder="如：印字Logo / 尺码" data-testid="note-inline-sub-category" />

              <label>客户诉求</label>
              <el-input v-model="noteForm.customerDemand" type="textarea" :rows="2" maxlength="500"
                        placeholder="客户希望达成什么" data-testid="note-inline-customer-demand" />

              <label>核实到的订单 / 商品</label>
              <el-input v-model="noteForm.verifiedInfo" size="small" maxlength="500"
                        placeholder="如：订单 S001 / SKU F001" data-testid="note-inline-verified-info" />

              <label>处理结果 <em class="required">*</em></label>
              <el-select v-model="noteForm.result" size="small" data-testid="note-inline-result">
                <el-option label="已完成" value="DONE" />
                <el-option label="未解决" value="UNRESOLVED" />
              </el-select>

              <label>解决办法 <em class="required">*</em></label>
              <el-input v-model="noteForm.solution" type="textarea" :rows="3" maxlength="1024"
                        placeholder="填写本次解决办法（必填）" data-testid="note-inline-solution" />

              <div class="note-inline-foot">
                <span class="note-inline-follow">
                  需回访
                  <el-switch v-model="noteForm.needFollowup" size="small" data-testid="note-inline-followup" />
                </span>
                <span class="note-inline-attach">附件 {{ attachmentFiles.length }} 张</span>
              </div>
              <!-- 回访联动（V64）：只有勾了「需回访」才展开回访时间与联系方式。
                   2026-11-26 用户要求：联系方式不再默认带客户登录手机号，必须由客服手填
                   （真实回访可能打给同事、座机或加微信，默认值反而容易打错人）。 -->
              <template v-if="noteForm.needFollowup">
                <!-- data-testid 挂在外层 div：el-date-picker 不透传 attrs 到根节点，直接写会丢 -->
                <div class="note-inline-date" data-testid="note-inline-followup-time">
                  <label class="note-inline-date__label">回访时间</label>
                  <el-date-picker
                    v-model="noteForm.followupTime"
                    type="datetime"
                    size="small"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="选择回访时间"
                    class="note-inline-date__picker"
                  />
                </div>
                <div class="note-inline-contact" data-testid="note-inline-followup-phone">
                  <label class="note-inline-contact__label">回访联系方式</label>
                  <el-input
                    v-model="noteForm.followupContact"
                    size="small"
                    maxlength="64"
                    placeholder="手动填写：手机 / 座机 / 微信"
                    class="note-inline-contact__input"
                    data-testid="note-inline-followup-contact"
                  />
                </div>
              </template>

              <el-upload
                class="note-inline-upload"
                data-testid="note-attachment-upload"
                :auto-upload="false"
                :show-file-list="false"
                :multiple="true"
                :on-change="onAttachmentChange"
              >
                <el-button size="small" class="note-inline-upload__btn">上传图片凭证</el-button>
              </el-upload>
              <ul v-if="attachmentFiles.length" class="note-inline-files">
                <li v-for="file in attachmentFiles" :key="file.fileId">
                  <a :href="fileApi.url(file.fileId)" target="_blank">{{ file.fileName }}</a>
                  <el-button link type="danger" size="small" @click="removeAttachment(file.fileId)">移除</el-button>
                </li>
              </ul>

              <!-- 2026-11-26 用户要求：工单分两态。
                   「暂时保存」= 草稿，可以先走开去接待别的客户，空闲回来接着填；
                   「提交工单」= 校验必填并让这次记录出现在管理端客服审查里。 -->
              <div class="note-inline-actions">
                <span
                  class="note-inline-status"
                  :class="`note-inline-status--${currentTicketStatus.toLowerCase()}`"
                  data-testid="note-submit-status"
                >{{ currentTicketStatus === 'SUBMITTED' ? '已提交' : '草稿未提交' }}</span>
                <div class="note-inline-btns">
                  <el-button
                    size="small"
                    class="note-inline-save"
                    :loading="noteSaving"
                    :disabled="!activeCustomerId"
                    data-testid="note-inline-save"
                    @click="saveInlineTicket"
                  >暂时保存</el-button>
                  <el-button
                    type="primary"
                    size="small"
                    :loading="noteSubmitting"
                    :disabled="!activeCustomerId"
                    data-testid="note-inline-submit"
                    @click="submitTicket"
                  >提交工单</el-button>
                </div>
              </div>
            </div>

            <!-- Tab 2：历史服务 -->
            <div v-show="recordTab === 'history'" class="record-body" data-testid="history-panel">
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

            <!-- Tab 3：顾客订单 -->
            <div v-show="recordTab === 'orders'" class="record-body" data-testid="orders-panel">
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

            <!-- Tab 4：优惠券发放（V64）。客服单张最高 20 元，发放后直接进客户账户 + 站内信，
                 不发聊天气泡——券是账户资产，客户在「我的优惠券」里看得到。 -->
            <div v-show="recordTab === 'coupon'" class="record-body gift-form" data-testid="coupon-panel">
              <label>选择优惠券 <em class="required">*</em></label>
              <el-select
                v-model="giftForm.templateId"
                size="small"
                clearable
                placeholder="客服单张最高 20 元"
                data-testid="gift-template"
              >
                <el-option
                  v-for="opt in giftOptions"
                  :key="opt.templateId"
                  :label="`${opt.name} · ¥${fmtCent(Number(opt.amountCent ?? 0))}`"
                  :value="Number(opt.templateId)"
                />
              </el-select>
              <p v-if="giftOptionsLoading" class="cust-orders__hint">加载中…</p>
              <p v-else-if="!giftOptions.length" class="cust-orders__hint">
                暂无可发放的小额券（面额需 ≤ 20 元），请让运营先建券
              </p>

              <label>发放原因</label>
              <el-input
                v-model="giftForm.reason"
                size="small"
                maxlength="60"
                placeholder="如：物流延迟补偿"
                data-testid="gift-reason"
              />

              <el-button
                type="primary"
                size="small"
                class="note-inline-save"
                :loading="giftSubmitting"
                :disabled="!activeCustomerId || !giftForm.templateId"
                data-testid="gift-submit"
                @click="submitGift"
              >发放到客户账户</el-button>
              <p class="gift-tip">发放后立即到账，并给客户发一条站内信（不会出现在聊天窗口）</p>

              <div class="gift-records">
                <header class="gift-records__head">
                  <b>最近发放</b>
                  <span>{{ giftRecords.length }} 张</span>
                </header>
                <div v-for="r in giftRecords" :key="r.couponId" class="gift-row" data-testid="gift-row">
                  <div class="gift-row__top">
                    <b>{{ r.name }} · ¥{{ fmtCent(Number(r.amountCent ?? 0)) }}</b>
                    <StatusTag :label="giftStatusLabel(r.status)"
                               :tone="r.status === 'UNUSED' ? 'success' : 'info'" />
                  </div>
                  <span class="gift-row__meta">
                    {{ fmtDateTime(Number(r.createdAtMillis ?? 0)) }} · {{ giftSourceLabel(r.source) }}
                  </span>
                  <small v-if="r.reason" class="gift-row__reason">{{ r.reason }}</small>
                </div>
                <p v-if="!giftRecords.length" class="cust-orders__hint">该客户还没有收到过优惠券</p>
              </div>
            </div>
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
              <span v-else-if="isSystemNote(m)" class="msg-system">{{ m.content }}</span>
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

    <!-- 客户标签弹窗（V64）：按四大类分组 + 可搜索；勾选即打标，取消勾选即移除。
         「删除标签也点添加按钮」——所以这里没有任何单独的删除入口，取消勾选就是删除。 -->
    <el-dialog
      v-model="tagDialogVisible"
      width="580px"
      append-to-body
      class="tag-dialog"
      title="客户标签"
    >
      <div class="tag-dlg" data-testid="tag-dialog">
        <el-input
          v-model="tagKeyword"
          size="small"
          clearable
          placeholder="搜索标签（如「索券」「差评」）"
          data-testid="tag-search"
        />
        <p class="tag-dlg__hint">勾选即打标，取消勾选即移除。标签只有客服与后台人员可见，客户看不到。</p>
        <div class="tag-dlg__scroll">
          <section v-for="group in tagGroups" :key="group.category" class="tag-group">
            <header class="tag-group__head">
              <span class="tag-group__dot" :class="`tag-chip--${group.colorKey}`"></span>
              <b>{{ group.label }}</b>
              <span class="tag-group__count">{{ group.items.length }}</span>
            </header>
            <div class="tag-group__body">
              <button
                v-for="tag in group.items"
                :key="tag.tagId"
                type="button"
                class="tag-opt"
                :class="{ 'tag-opt--on': isTagSelected(tag.tagId) }"
                :data-testid="`tag-option-${tag.tagId}`"
                @click="toggleTag(tag.tagId)"
              >
                <span class="tag-opt__check">{{ isTagSelected(tag.tagId) ? '✓' : '' }}</span>
                <span class="tag-chip" :class="`tag-chip--${tag.colorKey}`">{{ tag.name }}</span>
              </button>
            </div>
          </section>
          <p v-if="tagLoading" class="tag-dlg__empty">标签目录加载中…</p>
          <p v-else-if="!tagGroups.length" class="tag-dlg__empty">没有匹配的标签</p>
        </div>
      </div>
      <template #footer>
        <el-button size="small" data-testid="tag-cancel" @click="tagDialogVisible = false">取消</el-button>
        <el-button
          size="small"
          type="primary"
          :loading="tagSaving"
          data-testid="tag-save"
          @click="saveTags"
        >保存</el-button>
      </template>
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
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus';
import { fmtCent, fmtChatTime, fmtDateTime, shouldShowTimeDivider, type AdminOrderDetailVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import {
  chatApi,
  couponGiftApi,
  customerTagApi,
  orderApi,
  type AdminCustomerOrderVO,
  type ChatHistoryRowVO,
  type ChatNoteVO,
  type ChatTranscriptVO,
  type CustomerTagVO,
  type GiftRecordVO,
  type GiftTemplateVO,
  type OpenSessionRow,
  type TodayStatsVO,
  type WorkloadVO,
} from '@/api';
import { fileApi } from '@/api/file';
import { usePermissionStore } from '@/store/modules/permission';
import { usePresenceStore } from '@/store/modules/presence';
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
  /** 进线客户昵称（2026-11-25）：未设置时兜底成「客户 {id}」 */
  customerName?: string | null;
  /** 进线客户头像 fileId；无头像用姓名首字兜底 */
  customerAvatarFileId?: string | null;
}

/** 与后端 AgentPresence.DEFAULT_MAX_SESSIONS / staff_presence.max_sessions 对齐 */
const MAX_TABS = 3;

/**
 * openSession 的入参：后端列表行（OpenSessionRow）与 tabs 里的行都能喂进来，
 * 只要求「谁、哪个会话、什么状态」这些展示字段。
 */
type SessionLike = Pick<OpenSessionRow, 'sessionId' | 'status' | 'staffId' | 'customerId'> & {
  customerName?: string | null;
  customerAvatarFileId?: string | null;
};

const permission = usePermissionStore();
const presenceStore = usePresenceStore();
const staffId = ref<number>(Number(permission.me?.userId ?? 0) || 1);
/** 在线状态的唯一真源在 presence store（顶栏开关与这里共用），工作台只读不写 */
const presence = computed(() => presenceStore.presence);
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
/** 排队人数只做顶部一行提示（2026-11-25 用户要求删掉排队列表）；过滤不影响这个真实总数 */
const waitingCount = computed(() => waitingRows.value.length);

/** 会话列表项显示「进线用户是谁」：有昵称用昵称，没有兜底成「客户 {id}」 */
function customerLabel(row: { customerId: number; customerName?: string | null }): string {
  const name = (row.customerName ?? '').trim();
  return name || `客户 ${row.customerId}`;
}

/** 无头像时的兜底字：昵称首字（英文取首字母大写），「客户 9002」这种兜底名字取「客」 */
function customerInitial(row: { customerId: number; customerName?: string | null }): string {
  const name = (row.customerName ?? '').trim();
  if (!name) return '客';
  return name.slice(0, 1).toUpperCase();
}

/** 坐席头像用工号后两位，避免再引一个图片资源 */
const staffInitial = computed(() => String(staffId.value).slice(-2));
/** 坐席状态副标题：说清「现在能不能接人」，别让客服对着空列表猜 */
const presenceHint = computed(() => {
  if (presence.value === PresenceStatus.ONLINE) return '在线 · 自动接入';
  if (presence.value === PresenceStatus.BUSY) return '忙碌 · 暂停自动接入';
  return '离线 · 不接待新客户';
});
/** 离线看不到排队（后端也不下发），忙碌仍可见但不能自动接入 */
const canAccept = computed(() => presence.value === PresenceStatus.ONLINE);

const tabs = ref<OpenTab[]>([]);
const activeTab = ref<string>('');
const drafts = reactive<Record<number, string>>({});
const messageStore = reactive<Record<number, UiMessage[]>>({});

/** 当前聊天区显示的会话（2026-11-25：取消顶部 Tab 条，改由左侧列表切换） */
const activeTabRow = computed<OpenTab | null>(
  () => tabs.value.find((t) => String(t.sessionId) === activeTab.value) ?? null,
);
/** 已结束但还没关闭的会话：留在左侧列表里（标「已结束」），客服填完本次情况后手动移除 */
const closedRows = computed(() => tabs.value.filter((t) => t.status === ChatSessionStatus.CLOSED));
/** 并发名额只算「未结束」的会话，已结束的不占位 */
const openTabCount = computed(() => tabs.value.filter((t) => t.status !== ChatSessionStatus.CLOSED).length);

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

/**
 * 是不是「旁白类」消息：系统提示（已接入 / 已结束）与智能助手的自动话术。
 * <p>这两类都不是坐在电脑前的那个人说的，客服端必须居中成提示，
 * 混进左右气泡里会让人以为「客户说了这句话」或「我同事说过这句」。
 * <p>评价卡虽然 senderType 也是 SYSTEM，但要按卡片渲染，调用处先判 msgType=SATISFY。
 */
function isSystemNote(m: { senderType: string; msgType?: string }): boolean {
  if ((m.msgType || 'TEXT').toUpperCase() === 'SATISFY') return false;
  return m.senderType === SenderType.SYSTEM || m.senderType === SenderType.BOT;
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

/* ---------------- 表情面板（2026-11-25 输入区三工具之一） ---------------- */
/** 常用表情：客服高频场景（安抚/确认/致谢/物流），不求全，能一键插入即可 */
const EMOJIS = [
  '😀', '😄', '😊', '🙂', '😉', '😍', '🤔', '😅', '😂', '😭', '😢', '😡',
  '👍', '👎', '👏', '🙏', '💪', '🎉', '❤️', '⭐', '✅', '❌', '📦', '🚚',
];
const emojiOpen = ref(false);

/**
 * 点表情插到当前草稿末尾。
 * 光标级插入要穿过 Element Plus 的 textarea 内部节点，KISS 起见先做追加 ——
 * 客服的实际用法是「打完字补一个表情」，追加等价。
 */
function insertEmoji(emoji: string): void {
  const tab = activeTabRow.value;
  if (!tab || tab.status === ChatSessionStatus.CLOSED) return;
  drafts[tab.sessionId] = (drafts[tab.sessionId] ?? '') + emoji;
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

/* ------------------------------------------------------------------
 * 客户标签（V64）：会话头部「＋ 标签」+ 分组搜索弹窗
 * 声明位置必须在下面的 immediate watch 之前——回调会同步跑，
 * 写在后面会和 historyRows 一样踩 const 的 TDZ。
 * ------------------------------------------------------------------ */
const customerTags = ref<CustomerTagVO[]>([]);
const tagCatalog = ref<CustomerTagVO[]>([]);
const tagDialogVisible = ref(false);
const tagKeyword = ref('');
const tagSelected = ref<number[]>([]);
const tagSaving = ref(false);
const tagLoading = ref(false);

/** 分类固定顺序：优质 → 中性 → 风险 → 恶意（与后端 FIELD() 排序一致，不在这里另立口径） */
const TAG_CATEGORY_ORDER = ['EXCELLENT', 'NEUTRAL', 'RISK', 'MALICIOUS'] as const;

const tagGroups = computed(() => {
  const kw = tagKeyword.value.trim().toLowerCase();
  const rows = kw
    ? tagCatalog.value.filter((t) =>
      t.name.toLowerCase().includes(kw) || String(t.categoryLabel ?? '').includes(kw))
    : tagCatalog.value;
  return TAG_CATEGORY_ORDER
    .map((category) => {
      const items = rows.filter((t) => t.category === category);
      const first = items[0];
      return {
        category,
        label: first?.categoryLabel ?? category,
        colorKey: first?.colorKey ?? 'neutral',
        items,
      };
    })
    .filter((group) => group.items.length > 0);
});

async function loadCustomerTags(customerId: number): Promise<void> {
  if (!customerId) {
    customerTags.value = [];
    return;
  }
  try {
    customerTags.value = (await unwrap(customerTagApi.ofCustomer(customerId))) ?? [];
  } catch {
    // 标签拉不到不影响接待，静默空态
    customerTags.value = [];
  }
}

async function openTagDialog(): Promise<void> {
  const customerId = activeCustomerId.value;
  if (!customerId) return;
  tagKeyword.value = '';
  tagSelected.value = customerTags.value.map((t) => Number(t.tagId));
  tagDialogVisible.value = true;
  if (tagCatalog.value.length) return;
  tagLoading.value = true;
  try {
    tagCatalog.value = (await unwrap(customerTagApi.catalog())) ?? [];
  } catch {
    tagCatalog.value = [];
    ElMessage.error('标签目录加载失败，请稍后再试');
  } finally {
    tagLoading.value = false;
  }
}

function toggleTag(tagId: number): void {
  const id = Number(tagId);
  tagSelected.value = tagSelected.value.includes(id)
    ? tagSelected.value.filter((x) => x !== id)
    : [...tagSelected.value, id];
}

/**
 * 是否已选（2026-11-26 修复）：后端 `JacksonConfig` 把 Long 序列化成字符串，
 * `tag.tagId` 运行时是 "9101" 而不是 9101。之前直接用 `tagSelected.includes(tagId)`
 * 永远为 false —— 点了标签、「保存」能存上，但勾选圈和整颗胶囊的高亮一直不出现。
 */
function isTagSelected(tagId: number | string): boolean {
  return tagSelected.value.includes(Number(tagId));
}

async function saveTags(): Promise<void> {
  const customerId = activeCustomerId.value;
  if (!customerId) return;
  tagSaving.value = true;
  try {
    const rows = await unwrap(customerTagApi.apply(customerId, tagSelected.value));
    customerTags.value = rows ?? [];
    tagDialogVisible.value = false;
    ElMessage.success(rows?.length ? '标签已更新' : '标签已全部取消');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '标签保存失败');
  } finally {
    tagSaving.value = false;
  }
}

/* ------------------------------------------------------------------
 * 客服发券（V64）：「优惠券发放」Tab
 * ------------------------------------------------------------------ */
const giftOptions = ref<GiftTemplateVO[]>([]);
const giftRecords = ref<GiftRecordVO[]>([]);
const giftOptionsLoading = ref(false);
const giftSubmitting = ref(false);
const giftForm = reactive<{ templateId: number | null; reason: string }>({
  templateId: null,
  reason: '',
});

const GIFT_SOURCE_LABELS: Record<string, string> = {
  ACTIVITY: '平台活动',
  AFTER_SALE: '客服补偿',
  CLAIM: '领券中心',
};

/** 券状态沿用全站口径：只会有未使用 / 已使用 / 已过期三种（已退款返还的券会回到未使用） */
function giftStatusLabel(status: string): string {
  if (status === 'USED') return '已使用';
  if (status === 'EXPIRED') return '已过期';
  return '未使用';
}

function giftSourceLabel(source?: string | null): string {
  if (!source) return '发放';
  return GIFT_SOURCE_LABELS[source] ?? source;
}

async function loadGiftData(customerId: number): Promise<void> {
  if (!customerId) {
    giftRecords.value = [];
    return;
  }
  giftOptionsLoading.value = !giftOptions.value.length;
  try {
    if (!giftOptions.value.length) {
      giftOptions.value = (await unwrap(couponGiftApi.options())) ?? [];
    }
    giftRecords.value = (await unwrap(couponGiftApi.records(customerId, 10))) ?? [];
  } catch {
    giftRecords.value = [];
  } finally {
    giftOptionsLoading.value = false;
  }
}

async function submitGift(): Promise<void> {
  const customerId = activeCustomerId.value;
  if (!customerId || !giftForm.templateId) return;
  giftSubmitting.value = true;
  try {
    const result = await unwrap(couponGiftApi.gift(
      customerId, Number(giftForm.templateId), giftForm.reason.trim()));
    ElMessage.success(`已发放「${result.name}」${fmtCent(Number(result.amountCent ?? 0))} 元，客户已收到站内信`);
    giftForm.reason = '';
    giftRecords.value = (await unwrap(couponGiftApi.records(customerId, 10))) ?? [];
  } catch (e) {
    ElMessage.error((e as Error)?.message || '发券失败');
  } finally {
    giftSubmitting.value = false;
  }
}

/* 同一客户最近服务记录：服务端硬上限 10，当前会话不重复列出。
 * 这两个 ref 必须声明在下面的 immediate watch 之前——immediate 让回调在本轮 setup
 * 里同步执行，若声明在后会踩 const 的 TDZ（旧版报 "Cannot access 'historyRows' before initialization"，
 * 结果是首屏「历史服务」永远不加载）。 */
const historyRows = ref<ChatHistoryRowVO[]>([]);
const historyLoading = ref(false);

watch(activeCustomerId, (id) => {
  activeOrderNo.value = '';
  giftForm.templateId = null;
  giftForm.reason = '';
  void loadCustomerOrders(id);
  void loadHistoryService();
  void loadCustomerTags(id);
  void loadGiftData(id);
}, { immediate: true });

/* ------------------------------------------------------------------
 * 右侧服务面板：本次情况 / 历史服务（≤10）/ 顾客订单，三个平级 Tab（2026-11-25）
 * 本次情况在 V61 升级为「服务工单记录」：全部字段内联在右侧并自行滚动（2026-11-25 三次改版取消抽屉）。
 * ------------------------------------------------------------------ */
type RecordTab = 'note' | 'history' | 'orders' | 'coupon';
/** 默认停在「本次情况」：接待完一轮就是填工单，不用再多点一次 */
const recordTab = ref<RecordTab>('note');
const noteSaving = ref(false);
const noteSubmitting = ref(false);
const CATEGORY_OPTIONS = [
  { label: '商品咨询', value: 'INQUIRY' },
  { label: '订单售后', value: 'AFTER_SALE' },
  { label: '商品品质', value: 'QUALITY' },
  { label: '物流配送', value: 'LOGISTICS' },
  { label: '其他', value: 'OTHER' },
];

type TicketAttachment = { fileId: string; fileName: string };

const noteForm = reactive({
  summary: '', category: 'INQUIRY', subCategory: '', customerDemand: '', verifiedInfo: '',
  info: 'CLEAR', result: 'DONE', solution: '', needFollowup: false, followupTime: '',
  /** 回访联系方式：客服手填，不再默认取客户登录手机号（V65）。 */
  followupContact: '',
});
const attachmentFiles = ref<TicketAttachment[]>([]);
const ticketBasic = reactive({ nickname: '', phone: '', email: '', consultedAtText: '' });

/**
 * 各会话工单的提交状态（2026-11-26 用户要求）。
 * <p>只有 SUBMITTED 才允许关闭「已结束」会话的窗口——草稿可以先放着去接待别人，
 * 但窗口一关就再也回不到这条会话，半成品会永远进不了主管审查。</p>
 */
const ticketStatusBySession = reactive<Record<number, 'DRAFT' | 'SUBMITTED'>>({});
const currentTicketStatus = computed<'DRAFT' | 'SUBMITTED'>(() => {
  const id = Number(activeTab.value) || 0;
  return (id && ticketStatusBySession[id]) || 'DRAFT';
});

function currentSessionId(): number {
  return Number(activeTab.value) || 0;
}

function draftKey(sessionId: number): string {
  return `sent-ticket-draft:${sessionId}`;
}

/** 切客户 Tab 不丢草稿：按会话号存 sessionStorage，重进自动恢复。 */
let draftSyncPaused = false;

function persistDraft(): void {
  if (draftSyncPaused) return;
  const sessionId = currentSessionId();
  if (!sessionId) return;
  try {
    sessionStorage.setItem(draftKey(sessionId), JSON.stringify({
      ...noteForm,
      attachmentFiles: attachmentFiles.value,
    }));
  } catch {
    // 隐私模式下 sessionStorage 可能不可用，草稿丢失不阻断工单保存
  }
}

function restoreDraft(sessionId: number): boolean {
  let raw: string | null = null;
  try {
    raw = sessionStorage.getItem(draftKey(sessionId));
  } catch {
    return false;
  }
  if (!raw) return false;
  try {
    const saved = JSON.parse(raw);
    Object.assign(noteForm, {
      summary: saved.summary ?? '',
      category: saved.category ?? 'INQUIRY',
      subCategory: saved.subCategory ?? '',
      customerDemand: saved.customerDemand ?? '',
      verifiedInfo: saved.verifiedInfo ?? '',
      info: saved.info ?? 'CLEAR',
      result: saved.result ?? 'DONE',
      solution: saved.solution ?? '',
      needFollowup: Boolean(saved.needFollowup),
      followupTime: saved.followupTime ?? '',
      followupContact: saved.followupContact ?? '',
    });
    attachmentFiles.value = Array.isArray(saved.attachmentFiles) ? saved.attachmentFiles : [];
    return true;
  } catch {
    return false;
  }
}

function clearDraft(sessionId: number): void {
  try {
    sessionStorage.removeItem(draftKey(sessionId));
  } catch {
    // 同上：草稿清理失败不影响主流程
  }
}

function resetTicketForm(): void {
  noteForm.summary = '';
  noteForm.category = 'INQUIRY';
  noteForm.subCategory = '';
  noteForm.customerDemand = '';
  noteForm.verifiedInfo = '';
  noteForm.info = 'CLEAR';
  noteForm.result = 'DONE';
  noteForm.solution = '';
  noteForm.needFollowup = false;
  noteForm.followupTime = '';
  noteForm.followupContact = '';
  attachmentFiles.value = [];
  ticketBasic.nickname = '';
  ticketBasic.phone = '';
  ticketBasic.email = '';
  ticketBasic.consultedAtText = '';
}

function parseAttachments(json?: string | null): TicketAttachment[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item: unknown) => {
      if (typeof item === 'string') return { fileId: item, fileName: item };
      const row = item as { fileId?: string; fileName?: string };
      return { fileId: String(row.fileId ?? ''), fileName: String(row.fileName ?? row.fileId ?? '') };
    }).filter((row) => row.fileId);
  } catch {
    return [];
  }
}

async function loadNote(sessionId = currentSessionId()): Promise<void> {
  if (!sessionId) return;
  // 先暂停自动保存：resetTicketForm 清空输入框会触发 watch，若此时写回草稿就会把旧草稿抹掉。
  draftSyncPaused = true;
  resetTicketForm();
  try {
    const note: ChatNoteVO | null = await unwrap(chatApi.note(sessionId));
    if (note) {
      noteForm.summary = note.summary ?? '';
      noteForm.category = note.category ?? 'INQUIRY';
      noteForm.subCategory = note.subCategory ?? '';
      noteForm.customerDemand = note.customerDemand ?? '';
      noteForm.verifiedInfo = note.verifiedInfo ?? '';
      noteForm.info = note.info ?? 'CLEAR';
      noteForm.result = note.result ?? 'DONE';
      noteForm.solution = note.solution ?? '';
      noteForm.needFollowup = Boolean(note.needFollowup);
      noteForm.followupTime = note.followupTimeMillis
        ? fmtDateTime(Number(note.followupTimeMillis)) : '';
      noteForm.followupContact = note.followupContact ?? '';
      attachmentFiles.value = parseAttachments(note.attachmentsJson);
      // 只有后端确认已提交，才允许关闭这个会话窗口；无工单时 NoteVO 会返回 DRAFT。
      ticketStatusBySession[sessionId] = note.submitStatus === 'SUBMITTED' ? 'SUBMITTED' : 'DRAFT';
      ticketBasic.nickname = note.customerNickname ?? '';
      ticketBasic.phone = note.customerPhone ?? '';
      ticketBasic.email = note.customerEmail ?? '';
      ticketBasic.consultedAtText = note.consultedAtMillis
        ? fmtDateTime(Number(note.consultedAtMillis)) : '';
    }
  } catch {
    // 工单为空是常态，静默保留空白表单
    ticketStatusBySession[sessionId] = 'DRAFT';
  }
  // 服务端值先落，再用本地草稿覆盖：切换客户 Tab 后填到一半的内容不丢。
  restoreDraft(sessionId);
  draftSyncPaused = false;
}

async function onAttachmentChange(uploadFile: UploadFile): Promise<void> {
  if (!uploadFile.raw) return;
  try {
    const saved = await unwrap(fileApi.upload(uploadFile.raw));
    attachmentFiles.value.push({
      fileId: String(saved.fileId),
      fileName: uploadFile.name || saved.fileName || String(saved.fileId),
    });
    ElMessage.success('附件已上传');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '附件上传失败');
  }
}

function removeAttachment(fileId: string): void {
  attachmentFiles.value = attachmentFiles.value.filter((file) => file.fileId !== fileId);
}

/**
 * 保存工单：submit=false 暂存草稿（不校验必填）/ submit=true 提交（校验必填并进审查）。
 * 返回 false 表示必填未过或保存失败。
 */
async function persistTicket(submit: boolean): Promise<boolean> {
  const sessionId = currentSessionId();
  if (!sessionId) return false;
  if (submit) {
    const missing = !noteForm.summary.trim() ? '问题描述'
      : !noteForm.solution.trim() ? '解决办法'
        : noteForm.needFollowup && !noteForm.followupContact.trim() ? '回访联系方式' : '';
    if (missing) {
      ElMessage.warning(`${missing}为必填项`);
      // 不弹窗打断：切回「本次情况」并把光标落到缺的那一栏
      recordTab.value = 'note';
      await nextTick();
      const target = missing === '问题描述' ? 'note-inline-summary'
        : missing === '解决办法' ? 'note-inline-solution' : 'note-inline-followup-contact';
      const el = document.querySelector<HTMLElement>(
        `[data-testid="${target}"] input, [data-testid="${target}"] textarea`);
      el?.focus();
      return false;
    }
  }
  if (submit) noteSubmitting.value = true; else noteSaving.value = true;
  try {
    const saved = await unwrap(chatApi.saveNote(sessionId, {
      staffId: Number(staffId.value),
      summary: noteForm.summary.trim(),
      category: noteForm.category,
      info: noteForm.info,
      result: noteForm.result,
      solution: noteForm.solution.trim(),
      subCategory: noteForm.subCategory.trim(),
      customerDemand: noteForm.customerDemand.trim(),
      verifiedInfo: noteForm.verifiedInfo.trim(),
      needFollowup: noteForm.needFollowup,
      followupTime: noteForm.followupTime ? String(noteForm.followupTime) : null,
      followupContact: noteForm.followupContact.trim() || null,
      attachmentsJson: JSON.stringify(attachmentFiles.value),
      submit,
    }));
    if (saved) {
      ticketBasic.nickname = saved.customerNickname ?? ticketBasic.nickname;
      ticketBasic.phone = saved.customerPhone ?? ticketBasic.phone;
      ticketBasic.email = saved.customerEmail ?? ticketBasic.email;
      ticketBasic.consultedAtText = saved.consultedAtMillis
        ? fmtDateTime(Number(saved.consultedAtMillis)) : ticketBasic.consultedAtText;
      ticketStatusBySession[sessionId] = saved.submitStatus === 'SUBMITTED' || submit
        ? 'SUBMITTED' : 'DRAFT';
    } else {
      ticketStatusBySession[sessionId] = submit ? 'SUBMITTED' : 'DRAFT';
    }
    clearDraft(sessionId);
    return true;
  } catch (e) {
    ElMessage.error((e as Error)?.message || (submit ? '工单提交失败' : '草稿保存失败'));
    return false;
  } finally {
    if (submit) noteSubmitting.value = false; else noteSaving.value = false;
  }
}

/** 暂时保存：可以先走开去接待别的客户，空闲时回来接着填（不校验必填）。 */
async function saveInlineTicket(): Promise<void> {
  if (await persistTicket(false)) {
    ElMessage.success('草稿已保存，可稍后继续填写');
  }
}

/** 提交工单：校验必填并让这次记录出现在管理端「客服审查」。已提交后仍可修改再提交（覆盖同会话号）。 */
async function submitTicket(): Promise<void> {
  if (await persistTicket(true)) {
    ElMessage.success('工单已提交，可在客服审查中查看');
  }
}

watch(noteForm, persistDraft, { deep: true });
watch(attachmentFiles, persistDraft, { deep: true });

/* 回访联动（V64）：取消勾选「需回访」时把时间清掉。
 * 不清会让上一次选的时间留在草稿里，改回来时会莫名其妙带着旧时间——
 * 客服以为没填过，客户却收到一条按旧时间排的回访。 */
watch(() => noteForm.needFollowup, (need) => {
  if (!need) {
    noteForm.followupTime = '';
    // 联系方式同理：不勾回访却留着上次的号码，下一次勾上会误以为是「系统带出来的」
    noteForm.followupContact = '';
  }
});

/** 拉取同一客户的最近服务记录（声明见上方 watch 之前的 historyRows） */
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
  // 切换客户：先落服务端工单，再用该会话的本地草稿覆盖（loadNote 内部完成）。
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
    customerName: row.customerName ?? null,
    customerAvatarFileId: row.customerAvatarFileId ?? null,
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
/**
 * 客户进线自动打开聊天框（2026-11-16 修正；2026-11-25 配合「删除排队列表」再收敛）。
 *
 * 用户要求左侧只留「进行中」，不再有排队栏 —— 排队客户由后端在有并发位时自动分配成 ACTIVE
 * （StaffAssignmentPolicy / PresenceUseCase 补位），前端只对「已分配给我的进行中会话」自动弹窗。
 * 因此这里只看 activeRows，不再拿 waitingRows 兜底：给排队中的客户开聊天窗也发不出消息。
 *
 * 仍按「会话号是否已见过」判定：当前没有未结束的会话时，遇到没见过的会话就自动打开，
 * 并记入 knownSessionIds；客服手动关掉的会话已记过号，不会被列表刷新反复重开。
 */
const knownSessionIds = new Set<number>();
function autoOpenFreshSession(): void {
  if (openTabCount.value > 0) return;
  const rows = activeRows.value.slice().sort((a, b) => b.sessionId - a.sessionId);
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
      kept.push({
        ...tab,
        status: fresh.status,
        staffId: fresh.staffId,
        customerId: fresh.customerId,
        customerName: fresh.customerName ?? tab.customerName ?? null,
        customerAvatarFileId: fresh.customerAvatarFileId ?? tab.customerAvatarFileId ?? null,
      });
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

/**
 * 顶栏切换状态后工作台要跟着走：
 * - 切「离线」后端会把名下会话释放回排队池 → 左侧列表必须重拉，否则显示幽灵会话；
 * - 切「在线」才可能被分配新会话 → 并发上限/workload 也要刷新。
 * 状态本身由 presence store 持有，这里只做副作用（拉数据），不重复维护一份状态。
 */
watch(() => presenceStore.presence, () => {
  void Promise.all([loadOpen(), loadWorkload()]);
});

/**
 * 打开（或聚焦）一个会话。左侧列表是唯一切换入口（2026-11-25 取消顶部 Tab 条）。
 * 硬限 3 个**未结束**的会话：与后端并发上限一致，超出时提示而不是静默排队。
 * 已结束的会话不占名额，点开只是回看与填写本次情况（发送区已禁用）。
 *
 * 入参同时接受后端列表行与人手维护的 tab（已结束列表由 tabs 派生），只要求展示字段。
 */
async function openSession(row: SessionLike): Promise<void> {
  const existing = tabs.value.find((t) => t.sessionId === row.sessionId);
  if (existing) {
    // 已结束的会话列表行可能来自 tabs 派生，状态以 tabs 里的为准；这里顺手同步一次客户展示信息
    existing.customerName = row.customerName ?? existing.customerName;
    existing.customerAvatarFileId = row.customerAvatarFileId ?? existing.customerAvatarFileId;
    activeTab.value = String(row.sessionId);
    return;
  }
  if (openTabCount.value >= MAX_TABS) {
    ElMessage.warning(`最多同时接待 ${MAX_TABS} 个会话，请先结束一个`);
    return;
  }
  tabs.value.push({
    sessionId: row.sessionId,
    status: row.status,
    staffId: row.staffId,
    customerId: row.customerId,
    customerName: row.customerName ?? null,
    customerAvatarFileId: row.customerAvatarFileId ?? null,
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

/**
 * 关闭会话窗口（2026-11-26 用户要求）：
 * 会话已结束但工单没提交时不允许关——关掉就再也回不到这条会话，半成品永远进不了审查。
 * 会话还在进行中时关掉只是收起面板（左侧列表仍可点开），不拦。
 */
function closeTab(name: string | number): void {
  const sessionId = Number(name);
  const tab = tabs.value.find((t) => t.sessionId === sessionId);
  if (tab && tab.status === ChatSessionStatus.CLOSED
      && (ticketStatusBySession[sessionId] ?? 'DRAFT') !== 'SUBMITTED') {
    ElMessage.warning('请先提交工单，再关闭这个会话窗口');
    activeTab.value = String(sessionId);
    recordTab.value = 'note';
    return;
  }
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
  // 前端先挡一道（后端 AssignSessionUseCase 同样会拒），避免离线时点了按钮只看到红字报错
  if (!canAccept.value) {
    ElMessage.warning('当前离线或忙碌，切换到「在线」后才能接待客户');
    return;
  }
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
  // 2026-11-26 用户口径：结束会话不强制填/提交工单，客服可以先结束对话，
  // 再在空闲时补工单；只有「关闭这个会话窗口」才要求工单已提交（见 closeTab）。
  try {
    const result = await unwrap(chatApi.close(tab.sessionId, 'DONE'));
    ElMessage.success(result?.promotedSessionId
      ? `会话已结束，已自动接入排队客户 #${result.promotedSessionId}`
      : '会话已结束');
    // 结束后 Tab 保留为灰色「已结束」，由客服提交工单后点 × 关闭
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
 * 进工作台时从后端读回坐席状态（不再主动上报）。
 *
 * <p>为什么不再 report()：2026-11-21 起登录默认离线，若进页面就上报，会把客服
 * 在别的标签页刚切好的「在线」又冲回离线；反之若上报一个默认 ONLINE，又会绕过
 * 「登录后必须手动点在线」。所以这里只同步、不写。</p>
 */
async function syncPresence(): Promise<void> {
  const id = Number(permission.me?.userId ?? 0);
  if (Number.isFinite(id) && id > 0) staffId.value = id;
  await presenceStore.sync();
}

onMounted(async () => {
  connect();
  // 必须先同步工号再拉列表：`staffId` 默认值是 1，若 permission.me 尚未就绪就 loadOpen，
  // 会拿 1 去查别人的会话（表现为「进行中 0、客户却在排队」）。2026-11-25 改成串行。
  await syncPresence();
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
  /* 独立客服端：外层 cs-body 已经是 (100vh - 顶栏)，这里再显式写一次 calc，
     是为了让「工作台必须铺满全屏」这件事在组件自己也成立（被单独挂载时也不会塌）。
     min-height:0 + overflow:hidden：高度只由视口决定，滚动全部下沉到三栏内部。 */
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 12px;
  height: calc(100vh - var(--cs-topbar-h));
  min-height: 0;
  padding: 12px 14px 14px;
  box-sizing: border-box;
  overflow: hidden;
  background: #f4f7f5;
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
/* 排队人数提示（2026-11-25 删掉排队列表后的唯一告知出口） */
.wb__chip--queue { padding: 2px 8px; border-radius: 999px; background: #fff2e6; color: #b2531a; }
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
  grid-template-columns: 264px minmax(0, 1fr);
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
/* 坐席状态只在 .seat__sub 里以「在线/忙碌 · 自动接入」回显，不再单独占一个选择器 */

.side-search { padding: 10px 12px; border-bottom: 1px solid #eef2f0; }
.side-search :deep(.el-input__wrapper) { border-radius: 8px; }

.side-list { flex: 1; min-height: 0; overflow-y: auto; padding: 4px 10px 12px; }
.list-panel { margin-top: 10px; }
/* 离线占位：浅灰虚线框，视觉上明确「这里现在没有数据」而不是加载失败 */
.list-panel--offline .side-empty {
  border: 1px dashed #d8dfdb;
  border-radius: 8px;
  background: #fafbfa;
  padding: 14px 10px;
  line-height: 1.7;
}
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
/* 已结束（2026-11-25）：留在列表里但压一层灰，别和「进行中」抢注意力 */
.session-item--closed { background: #f7f8f8; }
.session-item--closed.current { background: #f0f3f2; border-color: #c9d6d1; }
.session-item--closed .s-name { color: #6b7a74; font-weight: 500; }
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
  overflow: hidden;
}
.s-avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
.s-avatar--closed { background: #eceeed; color: #9aa5a1; }
.s-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 已结束行的「关闭」：填完本次情况后由客服手动移除，避免列表越积越长 */
.s-close {
  flex: none;
  align-self: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #9aa5a1;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}
.s-close:hover { background: #e8ecea; color: #12664f; }
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
/* 三块各自是独立卡片：中↔右之间和左↔中之间一样留 12px，不再是一张白卡内部贴边分栏 */
.wb__main {
  display: flex;
  gap: 12px;
  min-width: 0;
  background: transparent;
  border: none;
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
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 12px;
}
.chat-empty__icon { font-size: 34px; opacity: 0.45; }
.chat-empty__title { margin: 8px 0 0; font-size: 14px; font-weight: 600; color: #40514b; }
.chat-empty__hint { margin: 0; font-size: 12px; }

/* 单会话视图（2026-11-25）：顶部 Tab 条去掉后，聊天区直接铺满中栏 */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 12px;
  overflow: hidden;
}

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
  overflow: hidden;
}
.chat-head__avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
.chat-head__info { min-width: 0; }
.chat-head__name { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #1f2d28; }
.chat-head__sub { margin-top: 2px; font-size: 11.5px; color: #8a94a6; }
.chat-head__actions { display: flex; gap: 6px; flex: none; }

/* 会话已结束横幅：Tab 条取消后，这里是小窗里唯一的状态出口 */
.chat-ended {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 14px;
  background: #f4f6f5;
  border-bottom: 1px solid #e6ece9;
  color: #6b7a74;
  font-size: 11.5px;
}
.chat-ended b { font-size: 12.5px; color: #4a5a54; }

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

.send-row { display: flex; align-items: center; gap: 8px; padding: 10px 14px 4px; background: #fff; }
/* 输入框左侧三个工具（2026-11-25）：图片 / 表情 / 请评价，横向一排、与输入框等高 */
.send-row__tools { display: flex; align-items: center; gap: 6px; flex: none; }
.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e2eae6;
  border-radius: 8px;
  background: #fff;
  color: #40514b;
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.tool-btn:hover:not(:disabled) { border-color: #12664f; color: #12664f; background: #f4faf7; }
.tool-btn:disabled { color: #b9c2be; border-color: #eef2f0; cursor: not-allowed; }
.tool-btn__icon { font-size: 13px; line-height: 1; }
.tool-btn__text { white-space: nowrap; }

/* 表情面板：贴按钮上方弹一小格，点一个插一个，不遮挡输入框 */
.emoji-anchor { position: relative; }
.emoji-panel {
  position: absolute;
  bottom: 42px;
  left: 0;
  z-index: 10;
  width: 232px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  padding: 8px;
  background: #fff;
  border: 1px solid #e2eae6;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(16, 40, 32, 0.12);
}
.emoji-item {
  border: none;
  background: transparent;
  font-size: 17px;
  line-height: 1;
  padding: 4px 0;
  border-radius: 6px;
  cursor: pointer;
}
.emoji-item:hover { background: #eef6f2; }
.satisfy-card {
  display: inline-flex; flex-direction: column; gap: 2px;
  max-width: 76%; padding: 8px 12px; border-radius: 10px;
  background: #fff7e6; border: 1px solid #ffd591; color: #874d00; font-size: 12px;
}
.satisfy-card small { color: #b37f2b; font-size: 11px; }
.message-line.mine .satisfy-card { background: #fff7e6; color: #874d00; }
.send-row :deep(.el-textarea__inner) { min-height: 34px !important; border-radius: 8px; }
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
  width: 336px;
  flex: none;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 12px 14px 10px;
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 12px;
  min-height: 0;
}
/* 宽屏：右侧工单栏放宽到能一屏填完（400px），聊天区实测约 870px 仍够用 */
@media (min-width: 1600px) {
  .wb__grid { grid-template-columns: 276px minmax(0, 1fr); }
  .cust-orders { width: 400px; }
}
/* 1920x1080 及以上：三栏一起撑开，不留下大片空白（聊天区靠 minmax(0,1fr) 自动吃掉剩余宽度） */
@media (min-width: 1920px) {
  .wb__grid { grid-template-columns: 300px minmax(0, 1fr); gap: 14px; }
  .cust-orders { width: 460px; }
}
/* 三个平级 Tab（2026-11-25）：选中项品牌绿加粗 + 2px 下划线，未选灰字 */
.record-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #e9efec;
  margin-bottom: 8px;
}
.record-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px 9px;
  border: none;
  background: transparent;
  color: #6b7a74;
  font-family: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.15s;
}
.record-tab:hover { color: #12664f; }
.record-tab--active { color: #12664f; font-weight: 700; }
.record-tab--active::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: #12664f;
}
.record-tab__count {
  min-width: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #eef5f1;
  color: #12664f;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 15px;
  text-align: center;
}
.record-body { display: flex; flex-direction: column; gap: 6px; flex: 1; min-height: 0; overflow-y: auto; }
.note-form label { font-size: 11px; color: #7b8a84; }
.note-form :deep(.el-select) { width: 100%; }
.note-form .required { color: #d9604f; font-style: normal; }
/* 内联工单：两列只给短字段（分类 / 明确信息），长文本一律整行，避免 400px 栏位挤成豆腐块 */
.note-row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 8px; }
.note-cell { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.note-inline-foot {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; padding: 2px 0; font-size: 11px; color: #7b8a84;
}
.note-inline-follow { display: inline-flex; align-items: center; gap: 6px; }
.note-inline-attach { color: #9aa8a2; }
/* 回访时间与附件也内联：控件撑满整栏，右栏自己滚动，不再靠弹窗承载 */
.note-form .note-inline-date { width: 100%; display: flex; flex-direction: column; gap: 4px; }
.note-form .note-inline-date :deep(.el-date-editor) { width: 100%; }
.note-inline-date__label,
.note-inline-contact__label { font-size: 11px; color: #7b8a84; }
/* 回访联系方式（2026-11-26）：由只读展示改为客服手填输入框 */
.note-inline-contact {
  display: flex; flex-direction: column; gap: 4px;
  padding: 6px 8px;
  border: 1px dashed #cfe0d8;
  border-radius: 6px;
  background: #f7fbf9;
}
.note-inline-contact__input { width: 100%; }
.note-form .note-inline-upload { width: 100%; }
.note-form .note-inline-upload :deep(.el-upload) { width: 100%; }
.note-form .note-inline-upload__btn {
  width: 100%;
  border-style: dashed; border-color: #cfe0d8; color: #12664f; background: #f7fbf9;
}
.note-inline-files { margin: 0; padding: 0; list-style: none; font-size: 11px; color: #5f6f68; }
.note-inline-files li { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 2px 0; }
.note-inline-files a { color: #12664f; text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 工单底部：左侧提交状态、右侧「暂时保存 / 提交工单」（2026-11-26 用户要求两键分离） */
.note-inline-actions { display: flex; flex-direction: column; gap: 6px; margin-top: 2px; }
.note-inline-status {
  align-self: flex-start;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid #f2dab0;
  background: #fdf3e1;
  color: #9a5b0c;
  font-size: 11px;
  line-height: 17px;
}
.note-inline-status--submitted {
  border-color: #c8e2d8;
  background: #e7f1ec;
  color: #12664f;
}
.note-inline-btns { display: flex; gap: 8px; }
.note-inline-btns .el-button { flex: 1; margin-left: 0; }
.gift-form .note-inline-save { width: 100%; }

/* ---------- 客户标签（V64） ---------- */
/* 四大类配色集中在这里：客服端与管理端要一致，色值只写一次，改的时候一处改全站。
   文字色对比度都 ≥ 4.5:1，浅底深字，不用纯饱和色（刺眼且显廉价）。 */
.tag-chip--excellent { --tag-fg: #12664f; --tag-bg: #e7f1ec; --tag-bd: #c8e2d8; }
.tag-chip--neutral { --tag-fg: #5b6b64; --tag-bg: #f1f4f2; --tag-bd: #e0e7e3; }
.tag-chip--risk { --tag-fg: #9a5b0c; --tag-bg: #fdf3e1; --tag-bd: #f2dab0; }
.tag-chip--malicious { --tag-fg: #ad3628; --tag-bg: #fdeeeb; --tag-bd: #f5cdc5; }

.tag-chip {
  display: inline-flex;
  align-items: center;
  max-width: 120px;
  padding: 1px 7px;
  border: 1px solid var(--tag-bd, #e0e7e3);
  border-radius: 999px;
  background: var(--tag-bg, #f1f4f2);
  color: var(--tag-fg, #5b6b64);
  font-size: 11.5px;
  line-height: 17px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 标签区离头像/姓名远一点（margin-left:40px），已打的标签排在按钮右边 */
.chat-head__tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-left: 40px;
  min-width: 0;
  flex: 1;
}
/* 1440 以下把「离头像远一点」的间距收到 20px：聊天区窄了以后，
   40px 的空档会把客户姓名挤成省略号，名片反而认不出是谁 */
@media (max-width: 1440px) {
  .chat-head__tags { margin-left: 20px; }
}
.chat-head__tags-hint { font-size: 11.5px; color: #a6b0ac; }
.tag-add-btn {
  flex: none;
  padding: 3px 9px;
  border: 1px dashed #c4d6cd;
  border-radius: 999px;
  background: #f7fbf9;
  color: #12664f;
  font-family: inherit;
  font-size: 11.5px;
  line-height: 16px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.tag-add-btn:hover:not(:disabled) { border-color: #12664f; background: #eef6f2; }
.tag-add-btn:disabled { color: #b3bfba; border-color: #e2e9e5; background: #fafcfb; cursor: not-allowed; }
.tag-add-btn--on { border-style: solid; border-color: #12664f; }

/* 弹窗：分组 + 搜索，点整行切换勾选（不是只有小方块能点，客服操作快得多） */
.tag-dlg { display: flex; flex-direction: column; gap: 8px; }
.tag-dlg__hint { margin: 0; font-size: 11.5px; color: #7b8a84; }
.tag-dlg__scroll { max-height: 380px; overflow-y: auto; padding-right: 2px; }
.tag-dlg__empty { margin: 12px 0; font-size: 12px; color: #9aa8a2; text-align: center; }
.tag-group { padding: 6px 0 8px; border-bottom: 1px dashed #edf2ef; }
.tag-group:last-child { border-bottom: none; }
.tag-group__head { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.tag-group__head b { font-size: 12px; color: #33463f; }
.tag-group__dot { width: 8px; height: 8px; border-radius: 50%; background: var(--tag-fg, #5b6b64); }
.tag-group__count { font-size: 10.5px; color: #9aa8a2; font-variant-numeric: tabular-nums; }
.tag-group__body { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-opt {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  /* 未选中也占 2px 边框，选中时只换颜色，不产生 1px 抖动 */
  padding: 3px 9px 3px 5px;
  border: 2px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.15s, background-color 0.15s, box-shadow 0.15s;
}
.tag-opt:hover { background: #f4f8f6; }
.tag-opt__check {
  width: 15px;
  height: 15px;
  border: 1.5px solid #cfdad5;
  border-radius: 50%;
  font-size: 10px;
  line-height: 12px;
  text-align: center;
  color: #fff;
  font-weight: 700;
  box-sizing: border-box;
}
/* 选中态（2026-11-26 用户反馈）：原来只是浅色底，扫一眼分不出选没选。
   改成整颗胶囊描边 + 绿色实心勾 + 外发光，远看也能一眼分辨。 */
.tag-opt--on {
  border-color: #12664f;
  background: #e7f1ec;
  box-shadow: 0 0 0 3px rgba(18, 102, 79, 0.12);
}
.tag-opt--on .tag-opt__check {
  border-color: #12664f;
  background: #12664f;
}
.tag-opt--on .tag-chip { border-color: #b9dccd; font-weight: 600; }

/* ---------- 客服发券（V64） ---------- */
.gift-form label { font-size: 11px; color: #7b8a84; }
.gift-form .required { color: #d9604f; font-style: normal; }
.gift-form :deep(.el-select) { width: 100%; }
.gift-tip { margin: 0; font-size: 11px; color: #9aa8a2; }
.gift-records { display: flex; flex-direction: column; gap: 6px; margin-top: 4px; }
.gift-records__head {
  display: flex; align-items: baseline; justify-content: space-between;
  padding-bottom: 4px; border-bottom: 1px solid #edf2ef;
  font-size: 11.5px; color: #7b8a84;
}
.gift-records__head b { font-size: 12px; color: #33463f; }
.gift-row {
  display: flex; flex-direction: column; gap: 2px;
  padding: 7px 9px;
  border: 1px solid #e6ece9;
  border-radius: 8px;
  background: #fbfdfc;
}
.gift-row__top { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.gift-row__top b { font-size: 12px; color: #1f2d28; font-variant-numeric: tabular-nums; }
.gift-row__meta { font-size: 11px; color: #7b8a84; font-variant-numeric: tabular-nums; }
.gift-row__reason { font-size: 11px; color: #5f6f68; }

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
/* 系统旁白 / 机器人话术：聊天区正中的浅灰提示，左右气泡都不是它的位置 */
.msg-system {
  width: fit-content;
  max-width: 76%;
  margin: 8px auto;
  padding: 5px 12px;
  border-radius: 10px;
  background: #eef2f0;
  color: #6b7a74;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  word-break: break-word;
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
  .wb__grid { grid-template-columns: 232px minmax(0, 1fr); }
  /* 内联工单需要最低可用宽度：240 会把输入框压成一条缝，收到 300 仍能填 */
  .cust-orders { width: 300px; }
}
@media (max-width: 900px) {
  .wb__grid { grid-template-columns: 1fr; }
  /* 堆叠时右栏仍是独立卡片，别把边框拆掉 */
  .cust-orders { width: auto; min-height: 320px; }
}
</style>
