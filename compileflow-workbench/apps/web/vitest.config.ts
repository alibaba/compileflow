import path from 'node:path'

import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['**/__tests__/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        'node_modules/',
        'src/test/',
        '**/*.d.ts',
        '**/*.css',
        '**/*.module.css',
        '**/*.config.*',
        '**/mockData.ts',
        '**/__tests__/**',
        'dist/',
        '*.config.ts',
        '**/types/**',
      ],
      // Ratchet from the current baseline; raise these as suites improve.
      thresholds: {
        lines: 42,
        functions: 37,
        branches: 32,
        statements: 41,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
