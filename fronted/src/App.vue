<script setup lang="ts">
import { NConfigProvider, NDialogProvider, NMessageProvider, NAlert, zhCN, dateZhCN, type GlobalThemeOverrides } from 'naive-ui'
import { notice } from '@/api/http'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#1d4ed8',
    primaryColorHover: '#245bdf',
    primaryColorPressed: '#1e40af',
    primaryColorSuppl: '#1d4ed8',
    borderRadius: '8px',
    fontFamily: '"Segoe UI", "Microsoft YaHei", sans-serif',
  },
  Card: { borderRadius: '14px' },
  DataTable: { thColor: '#f0f4f9' },
  Layout: { siderColorInverted: '#14284c' },
  Menu: {
    itemColorActiveInverted: '#2453b1',
    itemColorActiveHoverInverted: '#2453b1',
    itemColorHoverInverted: '#243e6b',
    itemTextColorInverted: '#e0eaff',
    itemTextColorActiveInverted: '#ffffff',
    itemTextColorActiveHoverInverted: '#ffffff',
  },
}
</script>

<template>
  <NConfigProvider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <NDialogProvider><NMessageProvider>
      <NAlert v-if="notice" type="error" closable class="global-alert" @close="notice = ''">{{ notice }}</NAlert>
      <RouterView />
    </NMessageProvider></NDialogProvider>
  </NConfigProvider>
</template>