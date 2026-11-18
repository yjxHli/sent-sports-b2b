<template>
  <div class="container profile-edit">
    <h1 class="profile-edit__title">编辑个人信息</h1>

    <section class="profile-edit__card" v-loading="loading">
      <div class="profile-edit__field">
        <label>头像</label>
        <div class="profile-edit__avatar-row">
          <img v-if="avatarFileId" class="profile-edit__avatar" :src="fileApi.url(avatarFileId)" alt="头像预览" />
          <div v-else class="profile-edit__avatar profile-edit__avatar--fallback">
            {{ (nickname || '森').slice(0, 1) }}
          </div>
          <input ref="fileRef" type="file" accept="image/*" class="hidden-input" @change="pickAvatar" />
          <button class="profile-edit__upload" data-testid="upload-avatar" @click="fileRef?.click()">
            上传头像
          </button>
          <button v-if="avatarFileId" class="profile-edit__remove" data-testid="remove-avatar"
                  @click="avatarFileId = ''">移除</button>
          <small>图片先上传文件服务，资料里只保存头像 ID；保存后生效</small>
        </div>
      </div>

      <div class="profile-edit__field">
        <label for="nickname-input">昵称</label>
        <input id="nickname-input" v-model="nickname" maxlength="20"
               data-testid="nickname-input" placeholder="请输入昵称" />
        <small class="profile-edit__counter">{{ nickname.length }}/20</small>
      </div>

      <div class="profile-edit__actions">
        <button class="profile-edit__cancel" @click="router.back()">取消</button>
        <button class="profile-edit__save" :disabled="saving" data-testid="save-profile" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 编辑个人信息（2026-11-17 用户要求）：我的账号点头像进入，可改头像与昵称，保存即落库。
 * 头像先走文件服务拿 fileId，customer 表只存 ID（二进制绝不进业务表）。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { profileApi } from '@/api/profile';
import { fileApi } from '@/api/file';
import { unwrap } from '@/utils/http';

const router = useRouter();
const loading = ref(true);
const saving = ref(false);
const nickname = ref('');
const avatarFileId = ref<string>('');
const fileRef = ref<HTMLInputElement | null>(null);

onMounted(async () => {
  try {
    const me = await unwrap(profileApi.me());
    nickname.value = String(me?.nickname ?? '');
    avatarFileId.value = String(me?.avatarFileId ?? '');
  } catch {
    ElMessage.error('个人资料加载失败，请刷新重试');
  } finally {
    loading.value = false;
  }
});

async function pickAvatar(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件');
    return;
  }
  try {
    const uploaded = await unwrap(fileApi.upload(file));
    avatarFileId.value = uploaded.fileId;
    ElMessage.success('头像已上传，点击保存后生效');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '头像上传失败');
  } finally {
    input.value = '';
  }
}

async function save(): Promise<void> {
  const name = nickname.value.trim();
  if (!name || name.length > 20) {
    ElMessage.warning('昵称长度需在 1~20 个字符之间');
    return;
  }
  saving.value = true;
  try {
    await unwrap(profileApi.update({ nickname: name, avatarFileId: avatarFileId.value || null }));
    ElMessage.success('个人信息已保存');
    router.push('/mine');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '保存失败，请重试');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.profile-edit { padding: 24px 0 60px; }
.profile-edit__title { font-size: 22px; margin: 0 0 18px; color: var(--sent-text); }
.profile-edit__card {
  max-width: 560px; background: #fff; border: 1px solid var(--sent-border, #e6eaf0);
  border-radius: 10px; padding: 24px;
}
.profile-edit__field { display: flex; align-items: flex-start; gap: 16px; margin-bottom: 22px; }
.profile-edit__field label { width: 56px; flex: none; line-height: 36px; color: #606b7a; font-size: 14px; }
.profile-edit__avatar-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.profile-edit__avatar { width: 64px; height: 64px; border-radius: 50%; object-fit: cover; border: 1px solid #e6eaf0; }
.profile-edit__avatar--fallback {
  display: flex; align-items: center; justify-content: center;
  background: #eef4f1; color: #12664f; font-size: 24px; font-weight: 700;
}
.profile-edit__avatar-row small { width: 100%; color: #9aa4b2; font-size: 11px; }
.profile-edit__upload, .profile-edit__remove, .profile-edit__cancel {
  padding: 7px 14px; border-radius: 6px; cursor: pointer; font-size: 13px;
  border: 1px solid #d9e0e8; background: #fff; color: #4b5563;
}
.profile-edit__upload:hover, .profile-edit__remove:hover, .profile-edit__cancel:hover { border-color: var(--sent-primary); }
.profile-edit__field input:not([type='file']) {
  flex: 1; height: 36px; padding: 0 12px; border: 1px solid #d9e0e8; border-radius: 6px;
  outline: none; font-size: 14px;
}
.profile-edit__field input:not([type='file']):focus { border-color: var(--sent-primary); }
.profile-edit__counter { color: #9aa4b2; font-size: 12px; line-height: 36px; }
.profile-edit__actions { display: flex; justify-content: flex-end; gap: 10px; border-top: 1px solid #eef1f5; padding-top: 18px; }
.profile-edit__save {
  padding: 8px 24px; border: none; border-radius: 6px; cursor: pointer;
  background: var(--sent-primary); color: #fff; font-size: 14px;
}
.profile-edit__save:disabled { opacity: 0.6; cursor: not-allowed; }
.hidden-input { display: none; }
</style>
