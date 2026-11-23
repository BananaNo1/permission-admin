import type { MenuVO } from '@/types/menu'

const nodeTypes: Record<string, string> = { DIRECTORY: '目录', PAGE: '页面' }

export function describePermission(menu: MenuVO) {
  const title = [nodeTypes[menu.type], menu.name, menu.status === 'DISABLED' ? '已禁用' : null]
    .filter(Boolean).join(' · ')
  return { title, description: menu.description ?? undefined, code: menu.permissionCode }
}
