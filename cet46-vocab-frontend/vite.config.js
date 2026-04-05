import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      resolvers: [ElementPlusResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    headers: {
      'Cache-Control': 'no-store'
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined

          if (id.includes('/node_modules/vue/') || id.includes('/node_modules/@vue/')) {
            return 'vue-core'
          }
          if (id.includes('/node_modules/vue-router/')) {
            return 'vue-router'
          }
          if (id.includes('/node_modules/pinia/')) {
            return 'pinia'
          }
          if (id.includes('/node_modules/axios/')) {
            return 'axios'
          }
          if (id.includes('/node_modules/echarts/')) {
            return 'echarts'
          }
          if (id.includes('/node_modules/@element-plus/icons-vue/')) {
            return 'element-icons'
          }

          if (id.includes('/node_modules/element-plus/')) {
            return 'element-plus'
          }

          return 'vendor'
        }
      }
    }
  }
})
