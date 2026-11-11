<template>
  <div class="chat-page">
    <div class="sent-bar">
      <strong>在线客服</strong>
      <span class="chat-state" :class="{ 'chat-state--on': online }">
        {{ online ? '在线' : '连接中…' }}
      </span>
    </div>

    <div ref="listRef" class="chat-body">
      <div
        v-for="m in messages"
        :key="m.seq + (m.clientMsgId || '')"
        class="chat-msg"
        :class="m.self ? 'chat-msg--self' : 'chat-msg--other'"
      >
        <div class="chat-bubble" :class="{ 'chat-bubble--pending': m.pending }">
          {{ m.content }}
        </div>
      </div>
      <el-empty v-if="messages.length === 0" description="描述您的问题，客服会尽快回复" :image-size="90" />
    </div>

    <div class="chat-input">
      <el-input
        v-model="draft"
        placeholder="输入消息，回车发送"
        @keyup.enter="doSend"
      />
      <el-button type="primary" :loading="sending" @click="doSend">发送</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useChatSession } from '@/composables/useChatSession';
import { useAuthStore } from '@/store/modules/useAuthStore';

const router = useRouter();
const auth = useAuthStore();
const draft = ref('');
const sending = ref(false);
const listRef = ref<HTMLElement | null>(null);

const customerId = auth.userId;
const chat = customerId ? useChatSession(customerId, false) : null;
const messages = chat ? chat.messages : ref([]);
const online = chat ? chat.online : ref(false);

function scrollBottom(): void {
  nextTick(() => {
    const el = listRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

async function doSend(): Promise<void> {
  const text = draft.value.trim();
  if (!text || !chat) return;
  sending.value = true;
  try {
    await chat.send(text);
    draft.value = '';
    scrollBottom();
  } catch (e) {
    ElMessage.error((e as Error).message || '发送失败');
  } finally {
    sending.value = false;
  }
}

onMounted(() => {
  if (!customerId) {
    ElMessage.warning('请先登录');
    router.replace('/login');
    return;
  }
  chat?.start();
  scrollBottom();
});
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.chat-state {
  font-size: 12px;
  color: #b5b9c0;
}
.chat-state--on {
  color: var(--sent-primary);
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: var(--sent-bg);
}
.chat-msg {
  display: flex;
  margin-bottom: 10px;
}
.chat-msg--self {
  justify-content: flex-end;
}
.chat-msg--other {
  justify-content: flex-start;
}
.chat-bubble {
  max-width: 72%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.4;
  word-break: break-word;
}
.chat-msg--self .chat-bubble {
  background: var(--sent-primary);
  color: #fff;
}
.chat-msg--other .chat-bubble {
  background: #fff;
  color: #1f2329;
}
.chat-bubble--pending {
  opacity: 0.6;
}
.chat-input {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #ececec;
}
</style>
