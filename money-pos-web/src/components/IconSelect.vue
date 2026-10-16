<!-- 图标选择器 -->
<script setup>
import { ref, computed } from 'vue';
import iconNames from 'virtual:svg-icons-names';
import MenuIcon from '@/components/MenuIcon.vue';
import { builtInMenuIcons } from '@/constants/menuIcons.js';

const { defaultIcon, dir } = defineProps({
    dir: {
        type: String,
        default: 'open',
    },
    defaultIcon: {
        type: String,
        default: 'app',
    },
});

const emits = defineEmits(['selected']);
const icon = ref(defaultIcon);

// 动态生成图标列表
const prefix = computed(() => `icon-${dir ? dir + '-' : ''}`);
const icons = computed(() =>
    iconNames
        .filter((name) => name.startsWith(prefix.value))
        .map((name) => name.replace(prefix.value, ''))
);
const options = computed(() => [...builtInMenuIcons, ...icons.value.map(value => ({ label: `历史 SVG：${value}`, value }))]);

// 确保默认值为有效图标
if (!options.value.some(item => item.value === icon.value)) {
    icon.value = options.value[0]?.value || '';
}
</script>

<template>
    <el-select
        v-model="icon"
        filterable
        placeholder="请选择图标"
        @change="(value) => $emit('selected', value)"
    >
        <template #prefix>
        <MenuIcon :name="icon" class="w-4 h-4" />
        </template>
        <div class="grid grid-cols-3 px-1">
            <el-option
                v-for="item in options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
                class="flex items-center gap-1"
            >
                <span><MenuIcon :name="item.value" class="w-4 h-4" /></span>
                <span>{{ item.label }}</span>
            </el-option>
        </div>
    </el-select>
</template>
