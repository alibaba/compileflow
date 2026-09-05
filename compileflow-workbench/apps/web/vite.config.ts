import { createRequire } from 'node:module'
import path from 'node:path'

import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

import { resolveBuildConfig } from './src/shared/config/buildConfigSchema'

const require = createRequire(import.meta.url)
const WEB_PACKAGE_VERSION = (require('./package.json') as { version: string }).version
const DEFAULT_DEV_GATEWAY_PORT = 3001

function resolveDevGatewayPort(value: string | undefined): number {
  if (value === undefined) {
    return DEFAULT_DEV_GATEWAY_PORT
  }
  const normalized = value.trim()
  if (!/^\d+$/.test(normalized)) {
    throw new Error('COMPILEFLOW_DEV_GATEWAY_PORT must be an integer between 1 and 65535')
  }
  const port = Number(normalized)
  if (!Number.isSafeInteger(port) || port < 1 || port > 65_535) {
    throw new Error('COMPILEFLOW_DEV_GATEWAY_PORT must be an integer between 1 and 65535')
  }
  return port
}

function buildConnectSrc(isProduction: boolean): string {
  const origins = new Set<string>(["'self'"])
  if (!isProduction) {
    origins.add('http://localhost:*')
    origins.add('http://127.0.0.1:*')
    origins.add('ws://localhost:*')
    origins.add('ws://127.0.0.1:*')
  }
  return Array.from(origins).join(' ')
}

function buildContentSecurityPolicy(connectSrc: string, isProd: boolean): string {
  const scriptSrc = isProd ? "'self'" : "'self' 'unsafe-inline' 'unsafe-eval'"
  return [
    "default-src 'self'",
    `script-src ${scriptSrc}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    "font-src 'self' data:",
    `connect-src ${connectSrc}`,
    // Monaco language workers are served as same-origin Vite worker modules / blobs.
    "worker-src 'self' blob:",
    "frame-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ')
}

function manualChunks(id: string): string | undefined {
  const normalized = id.split(path.sep).join('/')
  if (!normalized.includes('/node_modules/')) return undefined

  if (
    normalized.includes('/react/') ||
    normalized.includes('/react-dom/') ||
    normalized.includes('/react-router-dom/')
  ) {
    return 'vendor-react'
  }

  if (
    normalized.includes('/@reduxjs/toolkit/') ||
    normalized.includes('/react-redux/') ||
    normalized.includes('/redux-undo/')
  ) {
    return 'vendor-redux'
  }

  if (normalized.includes('/@antv/x6/') || normalized.includes('/@antv/x6-react-shape/')) {
    return 'vendor-x6'
  }

  if (
    normalized.includes('/lodash-es/') ||
    normalized.includes('/axios/') ||
    normalized.includes('/dayjs/')
  ) {
    return 'vendor-utils'
  }

  return undefined
}

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const fileEnv = loadEnv(mode, process.cwd(), 'VITE_COMPILEFLOW_')
  // Shell-exported VITE_* must win over .env.development so
  // `VITE_COMPILEFLOW_OPERATE_MODE=real pnpm dev` actually proxies to :8080.
  const env = {
    ...fileEnv,
    VITE_COMPILEFLOW_OPERATE_MODE:
      process.env.VITE_COMPILEFLOW_OPERATE_MODE ?? fileEnv.VITE_COMPILEFLOW_OPERATE_MODE,
    VITE_COMPILEFLOW_DEBUG: process.env.VITE_COMPILEFLOW_DEBUG ?? fileEnv.VITE_COMPILEFLOW_DEBUG,
    VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES:
      process.env.VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES ??
      fileEnv.VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES,
  }
  const buildConfig = resolveBuildConfig({ ...env, MODE: mode }, WEB_PACKAGE_VERSION)
  const isDeploymentBuild = buildConfig.buildMode === 'production'
  const connectSrc = buildConnectSrc(isDeploymentBuild)
  const contentSecurityPolicy = buildContentSecurityPolicy(connectSrc, isDeploymentBuild)
  const apiTarget =
    buildConfig.operateMode === 'mock'
      ? `http://127.0.0.1:${resolveDevGatewayPort(process.env.COMPILEFLOW_DEV_GATEWAY_PORT)}`
      : (process.env.COMPILEFLOW_API_PROXY_TARGET ?? 'http://127.0.0.1:8080')

  return {
    envPrefix: 'VITE_COMPILEFLOW_',
    plugins: [
      react(),
      {
        name: 'workbench-csp',
        transformIndexHtml() {
          return [
            {
              tag: 'meta',
              attrs: {
                'http-equiv': 'Content-Security-Policy',
                content: contentSecurityPolicy,
              },
              injectTo: 'head-prepend',
            },
          ]
        },
      },
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
        '@shell': path.resolve(__dirname, './src/shell'),
        '@learn': path.resolve(__dirname, './src/learn'),
        '@operate': path.resolve(__dirname, './src/operate'),
        '@shared': path.resolve(__dirname, './src/shared'),
      },
      dedupe: ['@antv/x6', 'react', 'react-dom'],
    },
    define: {
      __COMPILEFLOW_APP_VERSION__: JSON.stringify(WEB_PACKAGE_VERSION),
      'process.env': {},
    },
    server: {
      port: 5173,
      host: '127.0.0.1',
      watch: {
        ignored: ['**/playwright-report/**', '**/test-results/**'],
      },
      proxy: {
        '/api': apiTarget,
        '/health':
          buildConfig.operateMode === 'mock'
            ? apiTarget
            : {
                target: apiTarget,
                rewrite: () => '/actuator/health',
              },
      },
    },
    build: {
      outDir: 'dist',
      manifest: true,
      sourcemap: !isDeploymentBuild,
      rollupOptions: {
        output: {
          manualChunks,
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        },
      },
      minify: 'terser',
      terserOptions: {
        compress: {
          drop_console: true,
          drop_debugger: true,
        },
      },
    },
    optimizeDeps: {
      include: [
        'react',
        'react-dom',
        'react-router-dom',
        'antd',
        '@ant-design/icons',
        '@reduxjs/toolkit',
        'react-redux',
        'lodash-es',
      ],
      exclude: ['monaco-editor'],
    },
  }
})
