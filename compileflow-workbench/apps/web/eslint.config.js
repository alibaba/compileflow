import js from '@eslint/js'
import tseslint from '@typescript-eslint/eslint-plugin'
import tsparser from '@typescript-eslint/parser'
import reactPlugin from 'eslint-plugin-react'
import reactHooksPlugin from 'eslint-plugin-react-hooks'
import importPlugin from 'eslint-plugin-import'
import jsxA11yPlugin from 'eslint-plugin-jsx-a11y'
import prettierConfig from 'eslint-config-prettier'

export default [
    {
        ignores: [
            'dist',
            'node_modules',
            '*.config.js',
            'coverage',
            '.vite/**/*',
            'build/**/*',
            'blob-report/**/*',
            'playwright-report/**/*',
            'public/**/*',
            'test-results/**/*',
        ],
    },
    js.configs.recommended,
    {
        files: ['**/*.{ts,tsx}'],
        languageOptions: {
            parser: tsparser,
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
                ecmaFeatures: {
                    jsx: true,
                },
                project: ['./tsconfig.json', './tsconfig.node.json', './tsconfig.e2e.json'],
            },
            globals: {
                window: 'readonly',
                document: 'readonly',
                navigator: 'readonly',
                console: 'readonly',
                setTimeout: 'readonly',
                clearTimeout: 'readonly',
                setInterval: 'readonly',
                clearInterval: 'readonly',
                localStorage: 'readonly',
                sessionStorage: 'readonly',
                URL: 'readonly',
                Blob: 'readonly',
                NodeJS: 'readonly',
                requestAnimationFrame: 'readonly',
                cancelAnimationFrame: 'readonly',
                Buffer: 'readonly',
                Promise: 'readonly',
                fetch: 'readonly',
                describe: 'readonly',
                it: 'readonly',
                expect: 'readonly',
                beforeEach: 'readonly',
                afterEach: 'readonly',
                beforeAll: 'readonly',
                afterAll: 'readonly',
                vi: 'readonly',
                global: 'readonly',
                process: 'readonly',
            },
        },
        plugins: {
            '@typescript-eslint': tseslint,
            react: reactPlugin,
            'react-hooks': reactHooksPlugin,
            import: importPlugin,
            'jsx-a11y': jsxA11yPlugin,
        },
        rules: {
            // React
            'react/react-in-jsx-scope': 'off',
            'react/prop-types': 'off',

            // TypeScript
            'no-undef': 'off', // TypeScript already checks this
            'no-unused-vars': 'off', // Use @typescript-eslint/no-unused-vars for TS files
            '@typescript-eslint/no-explicit-any': 'error',
            '@typescript-eslint/explicit-module-boundary-types': 'off',
            '@typescript-eslint/no-floating-promises': 'error',
            '@typescript-eslint/no-unused-vars': [
                'error',
                {
                    argsIgnorePattern: '^_',
                    varsIgnorePattern: '^_',
                    caughtErrorsIgnorePattern: '^_',
                },
            ],

            // Code Complexity
            complexity: ['warn', 15],
            'max-lines-per-function': [
                'warn',
                {
                    max: 150,
                    skipBlankLines: true,
                    skipComments: true,
                },
            ],

            // Import
            'import/order': [
                'error',
                {
                    groups: ['builtin', 'external', 'internal', 'parent', 'sibling', 'index'],
                    'newlines-between': 'always',
                    alphabetize: {order: 'asc', caseInsensitive: true},
                },
            ],
            'import/no-cycle': ['error', {ignoreExternal: true}],

            // General
            'no-console': ['warn', {allow: ['warn', 'error']}],
            'prefer-const': 'error',
            'no-var': 'error',

            // Accessibility
            'jsx-a11y/anchor-is-valid': 'warn',
        },
        settings: {
            react: {
                version: 'detect',
            },
        },
    },
    {
        files: ['**/__tests__/**/*.{ts,tsx}', '**/*.{test,spec}.{ts,tsx}'],
        rules: {
            // Test suites often group many scenarios under one describe block; production
            // component/function size limits make those suites noisier without reducing risk.
            'max-lines-per-function': 'off',
        },
    },
    {
        files: ['src/shared/**/*.{ts,tsx}'],
        ignores: ['src/shared/**/__tests__/**'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    patterns: [
                        {
                            group: [
                                '@/authoring/**',
                                '@/learn/**',
                                '@/operate/**',
                                '@/settings/**',
                                '@/shell/**',
                            ],
                            message:
                                'Shared code must not depend on a product domain; move the integration to the owning domain.',
                        },
                    ],
                },
            ],
        },
    },
    prettierConfig,
]
