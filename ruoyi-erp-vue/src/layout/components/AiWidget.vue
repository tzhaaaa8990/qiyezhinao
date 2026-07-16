<template>
  <div class="ai-widget" v-if="settingsStore.aiWidget">
    <!-- 浮动按钮 -->
    <div v-if="!opened" class="ai-btn" @click="opened = true" title="AI 助手">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
    </div>
    <!-- 对话面板 -->
    <div v-if="opened" class="ai-panel">
      <div class="ai-header">
        <span>AI 经营分析</span>
        <el-button link @click="opened = false"><el-icon><Close /></el-icon></el-button>
      </div>
      <div class="ai-body" ref="bodyRef">
        <div v-if="messages.length === 0 && !loading" class="ai-hint">问我经营问题，如"本月销售额多少"</div>
        <div v-for="(m, i) in messages" :key="i" :class="['ai-msg', m.role]">
          <div class="ai-bubble">{{ m.content }}</div>
        </div>
        <div v-if="loading" class="ai-msg assistant"><div class="ai-bubble ai-thinking">思考中…</div></div>
      </div>
      <div class="ai-foot">
        <el-input v-model="input" placeholder="输入问题，回车发送" @keyup.enter="send" :disabled="loading" size="small" />
        <el-button type="primary" size="small" :loading="loading" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()

const opened = ref(false)
const messages = ref([])
const input = ref('')
const loading = ref(false)
const bodyRef = ref()

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: q })
  const reply = reactive({ role: 'assistant', content: '' })
  messages.value.push(reply)
  loading.value = true
  try {
    const url = import.meta.env.VITE_APP_BASE_API + '/ai/chat?message=' + encodeURIComponent(q)
    const resp = await fetch(url, { headers: { Authorization: 'Bearer ' + getToken() } })
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const frames = buf.split('\n\n')
      buf = frames.pop()
      for (const f of frames) {
        const parts = []
        for (const line of f.split('\n')) {
          if (line.startsWith('data:')) parts.push(line.replace(/^data:\s?/, ''))
        }
        if (parts.length) reply.content += parts.join('\n')
      }
      await nextTick()
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
    if (buf) {
      const parts = []
      for (const line of buf.split('\n')) {
        if (line.startsWith('data:')) parts.push(line.replace(/^data:\s?/, ''))
      }
      if (parts.length) reply.content += parts.join('\n')
    }
  } catch (e) {
    reply.content = reply.content || '请求失败：' + e.message
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.ai-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
}
.ai-btn {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0,0,0,.15);
  transition: transform .2s;
  &:hover { transform: scale(1.1); }
}
.ai-panel {
  width: 380px;
  height: 520px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 30px rgba(0,0,0,.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ai-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.ai-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  .ai-msg {
    margin-bottom: 10px;
    display: flex;
    &.user { justify-content: flex-end; .ai-bubble { background: var(--el-color-primary); color: #fff; } }
    &.assistant { justify-content: flex-start; .ai-bubble { background: #f4f4f5; } }
  }
  .ai-bubble {
    max-width: 85%;
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 13px;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .ai-thinking { color: #909399; font-style: italic; }
  .ai-hint {
    text-align: center;
    color: #909399;
    font-size: 13px;
    margin-top: 60px;
  }
}
.ai-foot {
  display: flex;
  gap: 6px;
  padding: 10px 14px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
