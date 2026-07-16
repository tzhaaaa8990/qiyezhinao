<template>
  <div class="ai-kit-page">
    <portal-navbar current="aiKit" />

    <div class="kit-body">
      <!-- 左侧导航 -->
      <el-menu class="kit-nav" :default-active="active" @select="active = $event">
        <el-menu-item index="customer"><el-icon><User /></el-icon>客户导入</el-menu-item>
        <el-menu-item index="hosting"><el-icon><Monitor /></el-icon>AI托管设置</el-menu-item>
        <el-menu-item index="config"><el-icon><Setting /></el-icon>企业微信配置</el-menu-item>
        <el-menu-item index="analysis"><el-icon><ChatDotRound /></el-icon>AI经营分析</el-menu-item>
      </el-menu>

      <!-- 内容区 -->
      <div class="kit-content">
        <component :is="modules[active]" />
      </div>
    </div>

    <ai-widget />
  </div>
</template>

<script setup name="AiKit">
import { User, Monitor, Setting, ChatDotRound } from '@element-plus/icons-vue'
import PortalNavbar from '@/components/PortalNavbar/index.vue'
import AiWidget from '@/layout/components/AiWidget.vue'
import Customer from '@/views/wechat/customer/index.vue'
import Hosting from '@/views/wechat/hosting/index.vue'
import Config from '@/views/wechat/config/index.vue'
import Analysis from '@/views/ai/analysis/index.vue'

const route = useRoute()

const modules = { customer: Customer, hosting: Hosting, config: Config, analysis: Analysis }
const active = ref(route.query.tab && modules[route.query.tab] ? route.query.tab : 'customer')
</script>

<style lang="scss" scoped>
.ai-kit-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}
.kit-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.kit-nav {
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
}
.kit-content {
  flex: 1;
  overflow-y: auto;
}
</style>
