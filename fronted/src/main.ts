import {createApp} from 'vue'
import {createPinia} from 'pinia'
import App from './App.vue'
import router, {clearRoutes} from './router'
import {useAuth} from '@/stores/auth'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
window.addEventListener('session-expired', () => {
    useAuth().clear();
    clearRoutes()
    if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})
window.addEventListener('focus', () => {
    if (useAuth().me) {
        clearRoutes()
        void router.replace(router.currentRoute.value.fullPath)
    }
})
app.mount('#app')