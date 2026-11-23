<script setup lang="ts">
import {ref} from 'vue'
import {NModal, NInput, NButton, NFormItem} from 'naive-ui'
import type {UserVO} from '@/types/user'
import {resetUserPassword} from '@/api/user'

const props = defineProps<{ user: UserVO }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const password = ref('')
const busy = ref(false)

async function save() {
  if (busy.value) return
  busy.value = true
  try {
    await resetUserPassword(props.user.id, {newPassword: password.value, version: props.user.version});
    emit('saved')
  } catch { /* 保留输入。 */
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NModal :show="true" preset="card" title="重置密码" style="width: 480px"
          @update:show="value => { if (!value) emit('close') }">
    <p>重置 {{ user.username }} 的密码后，其已有登录将失效。</p>
    <NFormItem label="新密码（12～64字符）">
      <NInput v-model:value="password" type="password" autocomplete="new-password"/>
    </NFormItem>
    <NButton type="primary" :loading="busy" @click="save">确认重置</NButton>
  </NModal>
</template>