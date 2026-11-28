<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NModal, NForm, NFormItem, NInput, NSelect, NButton } from 'naive-ui'
import type { UserVO } from '@/types/user'
import type { DeptVO } from '@/types/dept'
import { createUser, updateUser } from '@/api/user'
import { listDepts } from '@/api/dept'
import { notice } from '@/api/http'

const props = defineProps<{ user: UserVO | null }>()
const emit = defineEmits<{ saved: []; close: [] }>()
const username = ref(props.user?.username || '')
const nickname = ref(props.user?.nickname || '')
const password = ref('')
const deptId = ref<string | null>(props.user?.deptId || null)
const depts = ref<DeptVO[]>([])
const busy = ref(false)
onMounted(async () => {
  try {
    depts.value = await listDepts()
  } catch { /* 全局反馈。 */
  }
})

async function save() {
  if (!username.value || !nickname.value || !deptId.value) {
    notice.value = '请填写必填项';
    return
  }
  if (busy.value) return
  busy.value = true
  try {
    if (props.user) await updateUser(props.user.id, {
      nickname: nickname.value,
      deptId: deptId.value,
      version: props.user.version
    })
    else await createUser({
      username: username.value,
      nickname: nickname.value,
      deptId: deptId.value,
      password: password.value
    })
    emit('saved')
  } catch { /* 保留输入。 */
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NModal :show="true" preset="card" :title="user ? '编辑用户' : '新增用户'" style="width: 480px"
          @update:show="value => { if (!value) emit('close') }">
    <NForm @submit.prevent="save">
      <NFormItem label="账号（小写字母开头，3～32位）" required>
        <NInput v-model:value="username" :disabled="!!user" />
      </NFormItem>
      <NFormItem label="姓名" required>
        <NInput v-model:value="nickname" />
      </NFormItem>
      <NFormItem v-if="!user" label="初始密码（12～64字符）" required>
        <NInput v-model:value="password" type="password" autocomplete="new-password" />
      </NFormItem>
      <NFormItem label="部门" required>
        <NSelect v-model:value="deptId"
                 :options="depts.map(row => ({ label: row.name, value: row.id, disabled: !row.selectable }))" />
      </NFormItem>
      <NButton attr-type="submit" type="primary" :loading="busy">保存</NButton>
    </NForm>
  </NModal>
</template>