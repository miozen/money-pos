<script setup>
import Logo from '@/layouts/Logo.vue';
import { sidebarState } from '@/composables';
import { MONEY_POS_VERSION } from '@/release.js';

const tenantTitle = window.tenant?.tenantName || import.meta.env.VITE_TITLE;
</script>

<template>
    <div class="flex flex-shrink-0 flex-col px-3 py-2">
        <router-link
            to="/"
            :class="[
                'inline-flex items-center gap-2 transition-transform hover:scale-105',
                {
                    'justify-start': sidebarState.isOpen || sidebarState.isHovered,
                    'justify-center': !sidebarState.isOpen && !sidebarState.isHovered,
                },
            ]"
        >
            <span class="sr-only">Money</span>
            <Logo class="w-10 h-auto" />
            <h2 v-show="sidebarState.isOpen || sidebarState.isHovered" class="ml-1 pt-1 font-serif text-lg font-semibold transition-all duration-300">
                {{ tenantTitle }}
            </h2>
        </router-link>
        <p v-show="sidebarState.isOpen || sidebarState.isHovered" class="ml-[3.25rem] mt-0.5 font-mono text-xs text-gray-500 dark:text-gray-400">
            v{{ MONEY_POS_VERSION }}
        </p>
    </div>
</template>
