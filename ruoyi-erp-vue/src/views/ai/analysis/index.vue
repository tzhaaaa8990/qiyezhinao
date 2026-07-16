<template>
  <div class="app-container ai-chat">
    <div class="msg-list" ref="listRef">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">{{ m.content }}</div>
      </div>
      <div v-if="loading" class="msg assistant"><div class="bubble">思考中…</div></div>
    </div>
    <div class="input-bar">
      <el-input v-model="input" placeholder="问点经营问题，比如：本月销售额是多少？"
        @keyup.enter="send" :disabled="loading" clearable />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup name="AiAnalysis">
import { getToken } from '@/utils/auth'

const messages = ref([])
const input = ref('')
const loading = ref(false)
const listRef = ref()

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
      // SSE 帧格式：data:xxx\n\n
      const frames = buf.split('\n\n')
      buf = frames.pop()
      for (const f of frames) {
        const lines = f.split('\n')
        const parts = []
        for (const line of lines) {
          if (line.startsWith('data:')) parts.push(line.replace(/^data:\s?/, ''))
        }
        if (parts.length) reply.content += parts.join('\n')
      }
      await nextTick()
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
    // 处理 buffer 中可能残余的最后一帧
    if (buf) {
      const lines = buf.split('\n')
      const parts = []
      for (const line of lines) {
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
.ai-chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 130px);
  .msg-list {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    .msg {
      display: flex;
      margin-bottom: 12px;
      &.user { justify-content: flex-end; .bubble { background: #409eff; color: #fff; } }
      &.assistant { justify-content: flex-start; .bubble { background: #f4f4f5; } }
      .bubble {
        max-width: 70%;
        padding: 10px 14px;
        border-radius: 8px;
        white-space: pre-wrap;
        word-break: break-word;
        line-height: 1.6;
      }
    }
  }
  .input-bar {
    display: flex;
    gap: 8px;
    padding: 12px 16px;
    border-top: 1px solid var(--el-border-color-light);
  }
}
</style>
