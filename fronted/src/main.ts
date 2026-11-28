import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router, { clearRoutes, refreshCurrentRoute } from './router'
import { AUTHORIZATION_CHANGED } from '@/api/http'
import { useAuth } from '@/stores/auth'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
window.addEventListener('session-expired', () => {
    useAuth().clear();
    clearRoutes()
    if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})
window.addEventListener('focus', () => { void refreshCurrentRoute() })
window.addEventListener(AUTHORIZATION_CHANGED, () => {
    useAuth().invalidate()
    void refreshCurrentRoute()
})
app.mount('#app')