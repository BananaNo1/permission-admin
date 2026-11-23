<script setup lang="ts">
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {NCard, NForm, NFormItem, NInput, NButton} from 'naive-ui'
import {useAuth} from '@/stores/auth'
import {notice} from '@/api/http'

const auth = useAuth()
const router = useRouter()
const route = useRoute()
const username = ref('admin')
const password = ref('')
const busy = ref(false)

async function login() {
  if (!username.value || !password.value || busy.value) return
  busy.value = true;
  notice.value = ''
  try {
    await auth.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.startsWith('/login') ? redirect : '/')
  } catch { /* HTTP 客户端展示错误，保留输入。 */
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-shell">
    <section class="login-intro"><h1>权限管理后台</h1>
      <p>用户、角色与组织权限的统一管理入口</p></section>
    <NCard title="登录" class="login-card">
      <NForm @submit.prevent="login">
        <NFormItem label="账号">
          <NInput v-model:value="username" autocomplete="username"/>
        </NFormItem>
        <NFormItem label="密码">
          <NInput v-model:value="password" type="password" show-password-on="click" autocomplete="current-password"/>
        </NFormItem>
        <NButton attr-type="submit" type="primary" block :loading="busy">登录</NButton>
      </NForm>
    </NCard>
  </main>
</template>