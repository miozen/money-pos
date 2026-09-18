<template>
    <MoneyCrudTable :money-crud="moneyCrud">
        <template #brand="{scope}">{{ brandsKv[scope.row.brandId] }}</template>
        <template #status="{scope}"><el-tag :type="statusColor[scope.row.status] || 'primary'">{{ dict.goodsStatusKv[scope.row.status] }}</el-tag></template>

        <template #salePrice="{scope}">
            <MoneyDisplay :value="scope.row.salePrice" color="text-red-600" />
        </template>
        <template #purchasePrice="{scope}">
            <MoneyDisplay :value="scope.row.purchasePrice" color="text-gray-400" />
        </template>

        <template #stock="{scope}">
            <el-input v-if="editCell.id === scope.row.id"
                      v-model="scope.row.stock"
                      style="width: 55px"
                      @change="value => updateCell(value, scope.row)"
                      @focusout="updateCell(scope.row.stock, scope.row)" />
            <span v-else
                  @click="startEditCell(scope.row.id, 'stock', scope.row.stock)"
                  class="font-mono font-bold cursor-pointer hover:text-blue-500">
                {{ scope.row.stock }}
            </span>
        </template>

        <template #opt="{scope}"><MoneyUD :money-crud="moneyCrud" :scope="scope" /></template>
    </MoneyCrudTable>
</template>

<script setup>
import { ref } from 'vue';
import MoneyCrudTable from "@/components/Crud/MoneyCrudTable.vue";
import MoneyUD from "@/components/Crud/MoneyUD.vue";
import goodsApi from "@/api/gms/goods.js";

const props = defineProps({
    moneyCrud: { type: Object, required: true },
    brandsKv: { type: Object, required: true },
    dict: { type: Object, required: true }
});

const statusColor = { 'SOLD_OUT': 'warning', 'UN_SHELVE': 'info' };

// 🌟 原子编辑与防呆拦截
const editCell = ref({});
const startEditCell = (id, field, origin) => { editCell.value = { id, field, origin }; };

const updateCell = (value, row) => {
    const num = Number(value);
    if (value === null || value === '' || value === editCell.value.origin || !Number.isInteger(num) || num < 0) {
        row[editCell.value.field] = editCell.value.origin;
        editCell.value = {};
        return;
    }
    goodsApi.edit({ id: row.id, [editCell.value.field]: num })
        .then(() => props.moneyCrud.messageOk())
        .catch(() => row[editCell.value.field] = editCell.value.origin);
    editCell.value = {};
};
</script>