import js from '@eslint/js'
import tseslint from '@typescript-eslint/eslint-plugin'
import tsparser from '@typescript-eslint/parser'
import prettierConfig from 'eslint-config-prettier'

export default [
    {
        ignores: ['dist', 'node_modules', 'coverage'],
    },
    js.configs.recommended,
    {
        files: ['**/*.ts'],
        languageOptions: {
            parser: tsparser,
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
                project: './tsconfig.test.json',
            },
            globals: {
                Buffer: 'readonly',
                clearTimeout: 'readonly',
                console: 'readonly',
                fetch: 'readonly',
                process: 'readonly',
                setTimeout: 'readonly',
                URL: 'readonly',
                WebSocket: 'readonly',
                afterEach: 'readonly',
                beforeEach: 'readonly',
                describe: 'readonly',
                expect: 'readonly',
                it: 'readonly',
                vi: 'readonly',
            },
        },
        plugins: {
            '@typescript-eslint': tseslint,
        },
        rules: {
            'no-undef': 'off',
            'no-unused-vars': 'off',
            '@typescript-eslint/no-explicit-any': 'error',
            '@typescript-eslint/no-unused-vars': [
                'error',
                {
                    argsIgnorePattern: '^_',
                    varsIgnorePattern: '^_',
                    caughtErrorsIgnorePattern: '^_',
                },
            ],
            'no-var': 'error',
            'prefer-const': 'error',
        },
    },
    prettierConfig,
]
