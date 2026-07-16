<template>
  <div class="knowledge-page">
    <portal-navbar current="knowledge" />

    <div class="knowledge-content">
      <library-detail v-if="currentLibrary" :library="currentLibrary" @back="currentLibrary = null" @deleted="currentLibrary = null" />
      <library-grid v-else @open="currentLibrary = $event" />
    </div>

    <ai-widget />
  </div>
</template>

<script setup name="KnowledgePortal">
import PortalNavbar from '@/components/PortalNavbar/index.vue'
import AiWidget from '@/layout/components/AiWidget.vue'
import LibraryGrid from './LibraryGrid.vue'
import LibraryDetail from './LibraryDetail.vue'

const currentLibrary = ref(null)
</script>

<style lang="scss" scoped>
.knowledge-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}
.knowledge-content {
  flex: 1;
  overflow-y: auto;
}
</style>
