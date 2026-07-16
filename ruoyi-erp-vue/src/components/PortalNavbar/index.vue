<template>
  <div class="portal-navbar">
    <div class="portal-left">
      <img :src="logo" class="portal-logo" />
      <span class="portal-title">{{ title }}</span>
    </div>

    <div class="portal-right">
      <system-switcher :current="current" />
      <div class="right-menu-item hover-effect" v-hasPermi="['system:config:edit']"
           @click="configDialogRef.open()" title="系统配置">
        <svg-icon icon-class="system" />
      </div>
      <system-config-dialog ref="configDialogRef" />
      <el-dropdown @command="handleCommand" class="right-menu-item hover-effect" trigger="click">
        <div class="avatar-wrapper">
          <img :src="userStore.avatar" class="user-avatar" />
          <el-icon><caret-bottom /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <router-link to="/user/profile">
              <el-dropdown-item>个人中心</el-dropdown-item>
            </router-link>
            <el-dropdown-item divided command="logout">
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import logo from '@/assets/logo/logo.png'
import SystemSwitcher from '@/components/SystemSwitcher/index.vue'
import SystemConfigDialog from '@/layout/components/SystemConfigDialog.vue'
import useUserStore from '@/store/modules/user'

defineProps({
  // 当前系统: aiKit | knowledge
  current: {
    type: String,
    required: true
  }
})

const title = '智辉科技企业智能体'
const userStore = useUserStore()
const configDialogRef = ref()

function handleCommand(command) {
  if (command === 'logout') {
    logout()
  }
}

function logout() {
  ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      location.href = import.meta.env.VITE_APP_CONTEXT_PATH + 'index';
    })
  }).catch(() => { });
}
</script>

<style lang="scss" scoped>
.portal-navbar {
  height: 50px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 12px;
  background: #F8F9FD;
  border-bottom: 1px solid var(--el-border-color-light);

  .portal-left {
    display: flex;
    align-items: center;
    gap: 10px;

    .portal-logo {
      width: 32px;
      height: 32px;
    }
    .portal-title {
      font-size: 14px;
      font-weight: 600;
    }
  }

  .portal-right {
    display: flex;
    align-items: center;
    height: 100%;

    .right-menu-item {
      display: flex;
      align-items: center;
      padding: 0 8px;
      height: 100%;
      font-size: 18px;
      color: #5a5e66;

      &.hover-effect {
        cursor: pointer;
        transition: background 0.3s;

        &:hover {
          background: rgba(0, 0, 0, 0.025);
        }
      }
    }

    .avatar-wrapper {
      display: flex;
      align-items: center;
      gap: 4px;

      .user-avatar {
        cursor: pointer;
        width: 34px;
        height: 34px;
        border-radius: 10px;
      }
    }
  }
}
</style>
