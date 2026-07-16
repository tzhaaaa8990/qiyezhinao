<template>
  <div class="ai-widget" v-if="settingsStore.aiWidget" :style="widgetStyle">
    <!-- 浮动按钮 -->
    <div v-if="!opened" class="ai-btn" @mousedown.prevent="onDragStart" @touchstart.prevent="onDragStart" @click="onBtnClick" title="AI 助手">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
    </div>
    <!-- 对话面板 -->
    <div v-if="opened" class="ai-panel">
      <div class="ai-header">
        <span>AI 经营分析</span>
        <span class="lib-badge" v-if="libraryNames">{{ libraryNames }}</span>
        <el-button link @click="opened = false"><el-icon><Close /></el-icon></el-button>
      </div>
      <div class="ai-body" ref="bodyRef">
        <div v-if="messages.length === 0 && !loading" class="ai-hint">问我经营问题，如"本月销售额多少"<br><span v-if="libraryNames" style="font-size: 12px; color: #909399;">检索范围：{{ libraryNames }}</span></div>
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
const libraryNames = ref('')  // 关联知识库名称，空=全部
let cachedLibraryIds = []     // 缓存的库ID列表
let cachedLibNames = {}       // ID→名称映射

// 拖拽
const savedPos = (() => { try { return JSON.parse(localStorage.getItem('ai-widget-pos')) || {} } catch { return {} } })()
const pos = reactive({ right: (savedPos.right != null ? savedPos.right : 24), bottom: (savedPos.bottom != null ? savedPos.bottom : 24) })
const widgetStyle = computed(() => ({ right: pos.right + 'px', bottom: pos.bottom + 'px', left: 'auto', top: 'auto' }))
let dragState = { on: false, startX: 0, startY: 0, startRight: 0, startBottom: 0, moved: false }
function onDragStart(e) {
  dragState.on = true; dragState.moved = false
  const p = e.touches ? e.touches[0] : e
  dragState.startX = p.clientX; dragState.startY = p.clientY
  dragState.startRight = pos.right; dragState.startBottom = pos.bottom
  document.addEventListener('mousemove', onDragMove); document.addEventListener('mouseup', onDragEnd)
  document.addEventListener('touchmove', onDragMove, { passive: false }); document.addEventListener('touchend', onDragEnd)
}
function onDragMove(e) {
  if (!dragState.on) return; e.preventDefault?.()
  const p = e.touches ? e.touches[0] : e
  const dx = dragState.startX - p.clientX, dy = dragState.startY - p.clientY
  if (Math.abs(dx) > 2 || Math.abs(dy) > 2) dragState.moved = true
  pos.right = Math.max(0, Math.min(window.innerWidth - 60, dragState.startRight + dx))
  pos.bottom = Math.max(0, Math.min(window.innerHeight - 60, dragState.startBottom + dy))
}
function onDragEnd() {
  dragState.on = false
  document.removeEventListener('mousemove', onDragMove); document.removeEventListener('mouseup', onDragEnd)
  document.removeEventListener('touchmove', onDragMove); document.removeEventListener('touchend', onDragEnd)
  localStorage.setItem('ai-widget-pos', JSON.stringify({ right: pos.right, bottom: pos.bottom }))
}
const onBtnClick = (e) => { if (dragState.moved) { dragState.moved = false; return } opened.value = true }

// 挂载时加载关联的知识库
onMounted(async () => {
  try {
    // 获取配置的库ID
    const base = import.meta.env.VITE_APP_BASE_API
    const token = getToken()
    const cfgResp = await fetch(base + '/system/config/configKey/ai.widget.libraryIds',
      { headers: { Authorization: 'Bearer ' + token } })
    if (cfgResp.ok) {
      const cfg = await cfgResp.json()
      const ids = (cfg.data || cfg.msg || '').split(',').map(Number).filter(Boolean)
      if (ids.length > 0) {
        // 获取库名称
        const libResp = await fetch(base + '/rag/library/list',
          { headers: { Authorization: 'Bearer ' + token } })
        if (libResp.ok) {
          const libs = (await libResp.json()).data
          cachedLibNames = {}
          libs.forEach(l => { cachedLibNames[l.id] = l.name })
          cachedLibraryIds = ids.filter(id => cachedLibNames[id])
          libraryNames.value = cachedLibraryIds.map(id => cachedLibNames[id]).join('、')
        }
      }
    }
  } catch { /* 忽略，不影响使用 */ }
})

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return
  input.value = ''

  // 检索关联知识库，注入上下文
  let contextPrefix = ''
  if (cachedLibraryIds.length > 0) {
    try {
      const base = import.meta.env.VITE_APP_BASE_API
      const token = getToken()
      const allHits = []
      for (const libId of cachedLibraryIds) {
        try {
          const resp = await fetch(base + '/rag/search?keyword=' + encodeURIComponent(q) + '&topK=3&libraryId=' + libId,
            { headers: { Authorization: 'Bearer ' + token } })
          if (resp.ok) {
            const data = await resp.json()
            if (data.data) allHits.push('【' + (cachedLibNames[libId] || '知识库') + '】\n' + data.data)
          }
        } catch { /* 单个库检索失败不影响其他 */ }
      }
      if (allHits.length > 0) {
        contextPrefix = '以下是知识库中与用户问题相关的资料，请参考这些内容回答（不要提到"知识库材料"这些字，直接回答）：\n\n' + allHits.join('\n---\n') + '\n\n---\n用户问题：'
      }
    } catch { /* 检索失败不阻断对话 */ }
  }
  const displayMsg = contextPrefix ? contextPrefix + q : q
  messages.value.push({ role: 'user', content: q })
  const reply = reactive({ role: 'assistant', content: '' })
  messages.value.push(reply)
  loading.value = true
  try {
    const url = import.meta.env.VITE_APP_BASE_API + '/ai/chat?message=' + encodeURIComponent(displayMsg)
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
  z-index: 2000;
  user-select: none;
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

  .lib-badge {
    flex: 1;
    margin-left: 8px;
    font-size: 11px;
    font-weight: normal;
    color: var(--el-color-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
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
