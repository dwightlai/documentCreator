import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:18089'
  const proxy = {
    '/api': { target: proxyTarget, changeOrigin: true },
    '/actuator': { target: proxyTarget, changeOrigin: true },
  }
  return {
  plugins: [vue(), Components({ resolvers: [ElementPlusResolver()] })],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    host: '0.0.0.0',
    port: 5175,
    strictPort: true,
    proxy,
  },
  preview: { host: '0.0.0.0', port: 4173, proxy },
  build: { target: 'es2022', sourcemap: true },
  test: { environment: 'jsdom', globals: true, include: ['src/**/*.spec.ts', 'src/**/*.test.ts'] },
  }
})
