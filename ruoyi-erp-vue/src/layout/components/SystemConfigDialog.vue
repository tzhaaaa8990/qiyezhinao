<template>
  <el-dialog v-model="visible" title="系统配置" width="480px" append-to-body>
    <el-form :model="form" label-width="90px">
      <el-form-item label="接口地址">
        <el-input v-model="form['ai.baseUrl']" placeholder="https://api.deepseek.com" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form['ai.apiKey']" type="password" show-password placeholder="sk-..." />
      </el-form-item>
      <el-form-item label="模型名称">
        <el-input v-model="form['ai.model']" placeholder="deepseek-chat" />
      </el-form-item>
      <el-divider content-position="left">知识库向量模型</el-divider>
      <el-form-item label="向量接口">
        <el-input v-model="form['ai.embedding.baseUrl']" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
      </el-form-item>
      <el-form-item label="向量Key">
        <el-input v-model="form['ai.embedding.apiKey']" type="password" show-password placeholder="阿里百炼 API Key (sk-...)" />
      </el-form-item>
      <el-form-item label="向量模型">
        <el-input v-model="form['ai.embedding.model']" placeholder="text-embedding-v4" />
      </el-form-item>
      <el-form-item label="AI悬浮挂件">
        <el-switch v-model="aiWidgetVisible" active-text="显示" inactive-text="隐藏" @change="toggleAiWidget" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { getConfigKey, updateConfigByKey } from '@/api/system/config'
import { ElMessage } from 'element-plus'
import useSettingsStore from '@/store/modules/settings'

const visible = ref(false)
const saving = ref(false)
const KEYS = ['ai.baseUrl', 'ai.apiKey', 'ai.model', 'ai.embedding.baseUrl', 'ai.embedding.apiKey', 'ai.embedding.model']
const form = reactive({})

const settingsStore = useSettingsStore()
const aiWidgetVisible = ref(settingsStore.aiWidget)

// 切换AI悬浮挂件显示，写入本地布局配置持久化
function toggleAiWidget(value) {
  settingsStore.changeSetting({ key: 'aiWidget', value })
  const layoutSetting = JSON.parse(localStorage.getItem('layout-setting')) || {}
  layoutSetting.aiWidget = value
  localStorage.setItem('layout-setting', JSON.stringify(layoutSetting))
}

async function open() {
  for (const k of KEYS) {
    const res = await getConfigKey(k)
    form[k] = res.data ?? res.msg ?? ''
  }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    for (const k of KEYS) {
      await updateConfigByKey(k, form[k] ?? '')
    }
    ElMessage.success('保存成功，立即生效')
    visible.value = false
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
