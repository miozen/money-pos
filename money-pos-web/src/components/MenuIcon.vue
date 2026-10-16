<script setup>
import { computed, useAttrs } from 'vue';
import SvgIcon from '@/components/SvgIcon.vue';
import { builtInMenuIconMap } from '@/constants/menuIcons.js';

defineOptions({ inheritAttrs: false });
const props = defineProps({ name: { type: String, default: '' } });
const attrs = useAttrs();
const component = computed(() => builtInMenuIconMap[props.name]);
</script>

<template>
    <el-icon v-if="component" v-bind="attrs"><component :is="component" /></el-icon>
    <SvgIcon v-else-if="name" dir="open" :name="name" v-bind="attrs" />
</template>
