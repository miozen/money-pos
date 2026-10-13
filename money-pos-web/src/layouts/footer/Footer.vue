<template>
    <footer class="flex flex-col flex-shrink-0 w-full gap-3 px-6 py-4 md:flex-row md:justify-between md:items-center border-t border-gray-200 dark:border-gray-700">
        <p class="text-sm text-gray-500 dark:text-gray-400 transition-colors">
            &#169; <span>{{ new Date().getFullYear() }}</span> 万象收银
        </p>

        <p class="hidden md:flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 transition-colors">
            <span>系统版本</span>
            <span class="font-mono">v{{ MONEY_POS_VERSION }}</span>
        </p>
    </footer>
</template>

<script setup>
import { MONEY_POS_VERSION } from '@/release.js'
</script>
