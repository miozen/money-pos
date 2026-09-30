<template>
  <PageWrapper>
    <MemberBenefitWorkspace :initial-member="initialMember" />
  </PageWrapper>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PageWrapper from '@/components/PageWrapper.vue'
import MemberBenefitWorkspace from './MemberBenefitWorkspace.vue'

const route = useRoute()
const initialMember = computed(() => {
  const id = Number(route.query.memberId)
  return Number.isFinite(id) && id > 0
    ? { id, name: route.query.memberName || '', phone: route.query.memberPhone || '' }
    : null
})
</script>
