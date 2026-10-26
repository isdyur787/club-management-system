import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const proxy = {
    '/api': {
      target: env.VITE_API_TARGET || `http://127.0.0.1:${env.VITE_API_PORT || 8081}`,
      // 保留浏览器的 Host，使同源代理在 dev / preview 下均无需额外 CORS 来源。
      changeOrigin: false
    }
  }
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '127.0.0.1',
      port: Number(env.VITE_PORT || 5173),
      strictPort: true,
      proxy
    },
    preview: {
      host: '127.0.0.1',
      port: 4173,
      strictPort: true,
      proxy
    }
  }
})
