<template>
  <el-dropdown trigger="click" @command="handleSwitch">
    <div class="system-switcher">
      <span>{{ SYSTEMS[current].label }}</span>
      <el-icon><caret-bottom /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="(sys, key) in SYSTEMS"
          :key="key"
          :command="key"
          :disabled="key === current"
        >{{ sys.label }}</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
const props = defineProps({
  // 当前系统: erp | aiKit | knowledge
  current: {
    type: String,
    required: true
  }
})

const router = useRouter()

const SYSTEMS = {
  erp: { label: 'ERP系统', path: '/' },
  aiKit: { label: 'AI获客系统', path: '/ai-kit' },
  knowledge: { label: '知识库配置', path: '/knowledge' }
}

function handleSwitch(key) {
  router.push(SYSTEMS[key].path)
}
</script>

<style lang="scss" scoped>
.system-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 50px;
  padding: 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #5a5e66;
  cursor: pointer;
  transition: background 0.3s;

  &:hover {
    background: rgba(0, 0, 0, 0.025);
  }
}
</style>
