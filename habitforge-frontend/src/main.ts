import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import '@vant/touch-emulator'
import 'vant/lib/index.css'
import './assets/styles/main.scss'

const app = createApp(App)
app.use(pinia)
app.use(router)
app.mount('#app')
