<template>
  <div class="app-container">
    <!-- 头部 -->
    <div class="detail-head">
      <el-button link icon="Back" @click="$emit('back')">返回</el-button>
      <el-icon :size="20" color="#409EFF"><Collection /></el-icon>
      <span class="detail-name">{{ library.name }}</span>
    </div>

    <el-tabs v-model="activeTab" class="mt10">
      <!-- ============ 文档 ============ -->
      <el-tab-pane label="文档" name="docs">
        <el-card shadow="never">
          <el-row class="mb8" type="flex" justify="end">
            <el-button type="primary" icon="Upload" @click="openWizard">上传文档</el-button>
          </el-row>
          <el-table v-loading="docLoading" :data="documentList" border empty-text="暂无文档，点击「上传文档」添加">
            <el-table-column label="文件名" prop="fileName" />
            <el-table-column label="大小" width="100" align="center">
              <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="分段数" prop="chunkCount" width="90" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.status === '1' ? 'success' : 'warning'">{{ scope.row.status === '1' ? '已完成' : '处理中' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" prop="createTime" width="170" />
            <el-table-column label="操作" align="right" width="170">
              <template #default="scope">
                <el-button link type="primary" icon="View" @click="openChunks(scope.row)">分段</el-button>
                <el-button link type="primary" icon="Delete" @click="handleDeleteDoc(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ 命中测试 ============ -->
      <el-tab-pane label="命中测试" name="hit">
        <el-card shadow="never">
          <el-form :inline="true" @submit.prevent>
            <el-form-item>
              <el-input v-model="hitKeyword" placeholder="输入客户可能问的问题，测试知识库能否命中" style="width: 420px" clearable @keyup.enter="handleHitTest" />
            </el-form-item>
            <el-form-item label="返回条数">
              <el-input-number v-model="hitTopK" :min="1" :max="20" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" :loading="hitLoading" @click="handleHitTest">测试</el-button>
            </el-form-item>
          </el-form>

          <el-empty v-if="hitTested && hitResults.length === 0" description="未命中任何分段，检查向量配置或文档内容" />
          <el-card v-for="(hit, i) in hitResults" :key="hit.chunkId" class="hit-item" shadow="never">
            <template #header>
              <div class="hit-head">
                <el-tag size="small">#{{ i + 1 }}</el-tag>
                <span class="hit-doc">{{ hit.docName }}</span>
                <template v-if="hit.score != null">
                  <el-progress :percentage="Math.round(hit.score * 100)" :stroke-width="8" style="width: 180px" />
                </template>
                <el-tag v-else size="small" type="warning">关键词匹配(向量未生效)</el-tag>
              </div>
            </template>
            <div class="hit-content">{{ hit.content }}</div>
          </el-card>
        </el-card>
      </el-tab-pane>

      <!-- ============ 设置 ============ -->
      <el-tab-pane label="设置" name="settings">
        <el-card shadow="never" style="max-width: 560px">
          <el-form :model="settingForm" label-width="100px">
            <el-form-item label="名称">
              <el-input v-model="settingForm.name" maxlength="50" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="settingForm.description" type="textarea" :rows="3" maxlength="200" />
            </el-form-item>
            <el-form-item label="默认分段长度">
              <el-input-number v-model="settingForm.chunkSize" :min="100" :max="2000" :step="100" />
            </el-form-item>
            <el-form-item label="分段重叠">
              <el-input-number v-model="settingForm.overlapSize" :min="0" :max="300" :step="10" />
              <span class="tip">相邻分段共享字符数(Excel/Markdown/文本通用,0=不重叠)</span>
            </el-form-item>
            <el-form-item label="默认分段标识">
              <el-select v-model="settingForm.separator" style="width: 200px">
                <el-option label="空行(\n\n)" value="\n\n" />
                <el-option label="换行(\n)" value="\n" />
                <el-option label="井号标题(#)" value="#" />
                <el-option label="分隔线(---)" value="---" />
              </el-select>
            </el-form-item>
            <el-form-item label="召回数量">
              <el-input-number v-model="settingForm.topK" :min="1" :max="20" />
              <span class="tip">AI 回复时最多引用的分段数</span>
            </el-form-item>
            <el-form-item label="相似度阈值">
              <el-slider v-model="settingForm.scoreThreshold" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" style="width: 360px" />
            </el-form-item>
            <el-form-item>
              <span class="tip">相似度低于阈值的分段不会被召回；0 表示不过滤。命中测试同样生效。</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="settingSaving" @click="saveSettings">保存</el-button>
              <el-button type="warning" plain :loading="rebuilding" @click="handleRebuild">重建向量索引</el-button>
            </el-form-item>
          </el-form>
          <el-divider />
          <el-button type="danger" plain icon="Delete" @click="handleDeleteLibrary">删除知识库</el-button>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 上传向导 ============ -->
    <el-dialog title="上传文档" v-model="wizardOpen" width="720px" append-to-body :close-on-click-modal="false">
      <el-steps :active="wizardStep" simple class="mb16">
        <el-step title="选择文件与分段设置" />
        <el-step title="预览分段" />
        <el-step title="导入" />
      </el-steps>

      <!-- 步骤1:文件+参数 -->
      <div v-show="wizardStep === 0">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="f => { wizardFile = f.raw }"
          :on-remove="() => { wizardFile = null }"
          accept=".txt,.md,.pdf,.doc,.docx,.xlsx,.pptx"
        >
          <el-icon :size="40" color="#c0c4cc"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到这里，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 txt / md / pdf / doc / docx / xlsx / pptx</div>
          </template>
        </el-upload>
        <el-form :inline="true" class="mt10">
          <el-form-item label="分段长度">
            <el-input-number v-model="wizardParams.chunkSize" :min="100" :max="2000" :step="100" />
          </el-form-item>
          <el-form-item label="重叠字符">
            <el-input-number v-model="wizardParams.overlap" :min="0" :max="300" :step="10" />
          </el-form-item>
          <el-form-item label="分段标识">
            <el-select v-model="wizardParams.separator" style="width: 160px">
              <el-option label="空行(\n\n)" value="\n\n" />
              <el-option label="换行(\n)" value="\n" />
              <el-option label="井号标题(#)" value="#" />
              <el-option label="分隔线(---)" value="---" />
            </el-select>
          </el-form-item>
          <el-form-item label="自动清洗">
            <el-switch v-model="wizardParams.clean" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤2:预览 -->
      <div v-show="wizardStep === 1" v-loading="previewLoading" class="preview-list">
        <el-alert :title="'共 ' + previewList.length + ' 个分段，确认无误后点击「开始导入」'" type="info" :closable="false" class="mb8" />
        <el-card v-for="chunk in previewList" :key="chunk.chunkIndex" class="chunk-item" shadow="never">
          <template #header>分段 #{{ chunk.chunkIndex + 1 }} <span class="chunk-len">{{ chunk.content.length }} 字符</span></template>
          <div class="chunk-content">{{ chunk.content }}</div>
        </el-card>
      </div>

      <!-- 步骤3:导入结果 -->
      <el-result v-show="wizardStep === 2" icon="success" title="导入完成"
        :sub-title="'已生成 ' + importedCount + ' 个分段并完成向量化'" />

      <template #footer>
        <el-button v-if="wizardStep === 1" @click="wizardStep = 0">上一步</el-button>
        <el-button v-if="wizardStep === 0" type="primary" :disabled="!wizardFile" :loading="previewLoading" @click="handlePreview">预览分段</el-button>
        <el-button v-if="wizardStep === 1" type="primary" :disabled="previewList.length === 0" :loading="importing" @click="handleImport">开始导入</el-button>
        <el-button v-if="wizardStep === 2" type="primary" @click="wizardOpen = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- ============ 分段管理 ============ -->
    <el-drawer :title="'分段管理 - ' + chunkDocName" v-model="chunkOpen" size="55%" append-to-body>
      <div v-loading="chunkLoading">
        <el-card v-for="(chunk, i) in chunkList" :key="chunk.id" class="chunk-item" shadow="never">
          <template #header>
            <div class="chunk-head">
              <span>分段 #{{ i + 1 }} <span class="chunk-len">{{ chunk.content.length }} 字符</span>
                <el-tag v-if="!chunk.embedding" size="small" type="warning" class="ml8">未向量化</el-tag>
              </span>
              <span>
                <el-button link type="primary" icon="Edit" @click="openChunkEdit(chunk)">编辑</el-button>
                <el-button link type="primary" icon="Delete" @click="handleDeleteChunk(chunk)">删除</el-button>
              </span>
            </div>
          </template>
          <div class="chunk-content">{{ chunk.content }}</div>
        </el-card>
        <el-empty v-if="!chunkLoading && chunkList.length === 0" description="暂无分段" />
      </div>
    </el-drawer>

    <!-- 分段编辑 -->
    <el-dialog title="编辑分段" v-model="chunkEditOpen" width="640px" append-to-body>
      <el-input v-model="chunkEditContent" type="textarea" :rows="12" />
      <template #footer>
        <el-button @click="chunkEditOpen = false">取 消</el-button>
        <el-button type="primary" :loading="chunkSaving" @click="saveChunkEdit">保存并重新向量化</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LibraryDetail">
import { Collection, UploadFilled } from '@element-plus/icons-vue'
import {
  saveLibrary, delLibrary, rebuildLibrary,
  previewChunks, uploadDocument, listDocument, delDocument,
  docChunks, updateChunk, delChunk, hitTest
} from '@/api/rag'

const props = defineProps({
  library: { type: Object, required: true }
})
const emit = defineEmits(['back', 'deleted'])

const { proxy } = getCurrentInstance()

const activeTab = ref('docs')
const documentList = ref([])
const docLoading = ref(false)

// 上传向导
const wizardOpen = ref(false)
const wizardStep = ref(0)
const wizardFile = ref(null)
const wizardParams = reactive({ chunkSize: props.library.chunkSize || 500, overlap: props.library.overlapSize ?? 50, separator: props.library.separator || '\\n\\n', clean: true })
const previewList = ref([])
const previewLoading = ref(false)
const importing = ref(false)
const importedCount = ref(0)

// 命中测试
const hitKeyword = ref('')
const hitTopK = ref(5)
const hitResults = ref([])
const hitLoading = ref(false)
const hitTested = ref(false)

// 设置
const settingForm = reactive({ topK: 3, scoreThreshold: 0, overlapSize: 50, ...props.library })
const settingSaving = ref(false)
const rebuilding = ref(false)

// 分段管理
const chunkOpen = ref(false)
const chunkLoading = ref(false)
const chunkList = ref([])
const chunkDocName = ref('')
const chunkDocId = ref(null)
const chunkEditOpen = ref(false)
const chunkEditContent = ref('')
const chunkEditId = ref(null)
const chunkSaving = ref(false)

function getDocs() {
  docLoading.value = true
  listDocument(props.library.id).then(response => {
    documentList.value = response.data
  }).finally(() => {
    docLoading.value = false
  })
}

// ===== 上传向导 =====
function openWizard() {
  wizardStep.value = 0
  wizardFile.value = null
  previewList.value = []
  wizardOpen.value = true
}

function buildFormData() {
  const fd = new FormData()
  fd.append('file', wizardFile.value)
  fd.append('chunkSize', wizardParams.chunkSize)
  fd.append('overlap', wizardParams.overlap)
  fd.append('separator', wizardParams.separator)
  fd.append('clean', wizardParams.clean)
  return fd
}

function handlePreview() {
  previewLoading.value = true
  previewChunks(buildFormData()).then(response => {
    previewList.value = response.data
    wizardStep.value = 1
  }).finally(() => {
    previewLoading.value = false
  })
}

function handleImport() {
  const fd = buildFormData()
  fd.append('libraryId', props.library.id)
  importing.value = true
  uploadDocument(fd).then(response => {
    importedCount.value = response.data.chunkCount
    wizardStep.value = 2
    getDocs()
  }).finally(() => {
    importing.value = false
  })
}

function handleDeleteDoc(row) {
  proxy.$modal.confirm('确认删除文档【' + row.fileName + '】及其全部分段吗？').then(() => {
    return delDocument(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getDocs()
  })
}

// ===== 命中测试 =====
function handleHitTest() {
  if (!hitKeyword.value.trim()) return
  hitLoading.value = true
  hitTest(props.library.id, hitKeyword.value, hitTopK.value).then(response => {
    hitResults.value = response.data
    hitTested.value = true
  }).finally(() => {
    hitLoading.value = false
  })
}

// ===== 设置 =====
function saveSettings() {
  settingSaving.value = true
  saveLibrary(settingForm).then(() => {
    proxy.$modal.msgSuccess('保存成功')
    Object.assign(props.library, settingForm)
  }).finally(() => {
    settingSaving.value = false
  })
}

function handleRebuild() {
  proxy.$modal.confirm('将对库内全部分段重新向量化(消耗API额度)，确认继续吗？').then(() => {
    rebuilding.value = true
    return rebuildLibrary(props.library.id)
  }).then(response => {
    proxy.$modal.msgSuccess(response.data)
  }).finally(() => {
    rebuilding.value = false
  })
}

function handleDeleteLibrary() {
  proxy.$modal.confirm('删除知识库【' + props.library.name + '】将同时删除全部文档和分段，确认删除吗？').then(() => {
    return delLibrary(props.library.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    emit('deleted')
  })
}

// ===== 分段管理 =====
function openChunks(row) {
  chunkDocName.value = row.fileName
  chunkDocId.value = row.id
  chunkOpen.value = true
  loadChunks()
}

function loadChunks() {
  chunkLoading.value = true
  docChunks(chunkDocId.value).then(response => {
    chunkList.value = response.data
  }).finally(() => {
    chunkLoading.value = false
  })
}

function openChunkEdit(chunk) {
  chunkEditId.value = chunk.id
  chunkEditContent.value = chunk.content
  chunkEditOpen.value = true
}

function saveChunkEdit() {
  chunkSaving.value = true
  updateChunk(chunkEditId.value, chunkEditContent.value).then(() => {
    proxy.$modal.msgSuccess('已保存并重新向量化')
    chunkEditOpen.value = false
    loadChunks()
  }).finally(() => {
    chunkSaving.value = false
  })
}

function handleDeleteChunk(chunk) {
  proxy.$modal.confirm('确认删除该分段吗？').then(() => {
    return delChunk(chunk.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    loadChunks()
    getDocs()
  })
}

function formatSize(size) {
  if (size == null) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

getDocs()
</script>

<style lang="scss" scoped>
.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  .detail-name { font-size: 16px; font-weight: 600; }
}
.mt10 { margin-top: 10px; }
.mb8 { margin-bottom: 8px; }
.mb16 { margin-bottom: 16px; }
.ml8 { margin-left: 8px; }
.preview-list {
  max-height: 46vh;
  overflow-y: auto;
}
.chunk-item {
  margin-bottom: 10px;
  .chunk-head { display: flex; justify-content: space-between; align-items: center; }
  .chunk-len { font-size: 12px; color: #909399; margin-left: 6px; }
  .chunk-content {
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 140px;
    overflow-y: auto;
  }
}
.hit-item {
  margin-bottom: 10px;
  .hit-head { display: flex; align-items: center; gap: 10px; }
  .hit-doc { font-size: 13px; color: #606266; }
  .hit-content { font-size: 13px; line-height: 1.6; white-space: pre-wrap; }
}
</style>
