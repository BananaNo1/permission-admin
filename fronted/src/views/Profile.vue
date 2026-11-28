<script setup lang="ts">
import { ref } from 'vue'
import { NCard, NForm, NFormItem, NInput, NButton } from 'naive-ui'
import { notice } from '@/api/http'
import { changePassword } from '@/api/auth'
import { useAuth } from '@/stores/auth'
import { clearRoutes } from '@/router'
import { useRouter } from 'vue-router'

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const busy = ref(false)
const auth = useAuth()
const router = useRouter()

async function save() {
  if (newPassword.value !== confirmPassword.value) {
    notice.value = '两次新密码不一致';
    return
  }
  busy.value = true
  try {
    await changePassword(oldPassword.value, newPassword.value)
    auth.clear();
    clearRoutes();
    await router.replace('/login')
  } catch { /* 客户端统一显示错误。 */
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NCard title="修改密码" style="max-width: 520px">
    <NForm @submit.prevent="save">
      <NFormItem label="原密码">
        <NInput v-model:value="oldPassword" type="password" autocomplete="current-password" />
      </NFormItem>
      <NFormItem label="新密码（12～64字符，UTF-8最多72字节）">
        <NInput v-model:value="newPassword" type="password" autocomplete="new-password" />
      </NFormItem>
      <NFormItem label="确认新密码">
        <NInput v-model:value="confirmPassword" type="password" autocomplete="new-password" />
      </NFormItem>
      <NButton attr-type="submit" type="primary" :loading="busy">保存并重新登录</NButton>
    </NForm>
  </NCard>
</template>