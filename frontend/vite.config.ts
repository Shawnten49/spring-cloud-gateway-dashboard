import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

declare const process: { env: Record<string, string | undefined> }

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': '/src'
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/**/*.test.ts', 'src/test/**', 'src/main.ts', 'src/env.d.ts'],
      // 起步门槛（先实测后设低，随组件测试覆盖提升逐步收紧，见 docs/优化方案.md 2.5）
      thresholds: {
        lines: 30,
        statements: 30,
        functions: 40,
        branches: 40
      }
    }
  }
})
