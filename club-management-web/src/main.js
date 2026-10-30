/**
 * 前端启动入口：创建 Vue 应用，安装 Pinia、路由和中文版 Element Plus，加载全局样式并挂载 #app。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
const app = createApp(App)

// 图标已在各组件中按需引入并局部注册，无需全局注册

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
