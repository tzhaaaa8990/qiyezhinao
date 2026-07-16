<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 创建卡片 -->
      <el-col :span="6" class="mb16">
        <el-card class="lib-card lib-create" shadow="hover" @click="handleCreate">
          <div class="create-inner">
            <el-icon :size="28"><Plus /></el-icon>
            <span>创建知识库</span>
          </div>
        </el-card>
      </el-col>

      <!-- 知识库卡片 -->
      <el-col :span="6" class="mb16" v-for="lib in libraryList" :key="lib.id">
        <el-card class="lib-card" shadow="hover" @click="$emit('open', lib)">
          <div class="lib-head">
            <el-icon :size="22" color="#409EFF"><Collection /></el-icon>
            <span class="lib-name">{{ lib.name }}</span>
            <el-dropdown trigger="click" @command="cmd => handleCommand(cmd, lib)" @click.stop>
              <el-icon class="lib-more" @click.stop><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="lib-desc">{{ lib.description || '暂无描述' }}</div>
          <div class="lib-stats">
            <span>{{ lib.docCount || 0 }} 文档</span>
            <el-divider direction="vertical" />
            <span>{{ formatChars(lib.charCount) }} 字符</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 创建/编辑知识库 -->
    <el-dialog :title="title" v-model="open" width="480px" append-to-body>
      <el-form ref="libRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="知识库名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="这个知识库放什么内容" maxlength="200" />
        </el-form-item>
        <el-form-item label="分段长度">
          <el-input-number v-model="form.chunkSize" :min="100" :max="2000" :step="100" />
          <span class="tip">默认分段长度(字符)</span>
        </el-form-item>
        <el-form-item label="分段重叠">
          <el-input-number v-model="form.overlapSize" :min="0" :max="300" :step="10" />
          <span class="tip">相邻分段共享字符数,0=不重叠(建议50)</span>
        </el-form-item>
        <el-form-item label="分段标识">
          <el-select v-model="form.separator" style="width: 200px">
            <el-option label="空行(\n\n)" value="\n\n" />
            <el-option label="换行(\n)" value="\n" />
            <el-option label="井号标题(#)" value="#" />
            <el-option label="分隔线(---)" value="---" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LibraryGrid">
import { Plus, Collection, MoreFilled } from '@element-plus/icons-vue'
import { listLibrary, saveLibrary, delLibrary } from '@/api/rag'

defineEmits(['open'])

const { proxy } = getCurrentInstance()

const libraryList = ref([])
const open = ref(false)
const saving = ref(false)
const title = ref('')

const data = reactive({
  form: {},
  rules: {
    name: [{ required: true, message: '名称不能为空', trigger: 'blur' }]
  }
})
const { form, rules } = toRefs(data)

function getList() {
  listLibrary().then(response => {
    libraryList.value = response.data
  })
}

function handleCreate() {
  form.value = { id: null, name: '', description: '', chunkSize: 500, overlapSize: 50, separator: '\\n\\n' }
  title.value = '创建知识库'
  open.value = true
}

function handleCommand(cmd, lib) {
  if (cmd === 'edit') {
    form.value = { ...lib }
    title.value = '编辑知识库'
    open.value = true
  } else if (cmd === 'delete') {
    proxy.$modal.confirm('删除知识库【' + lib.name + '】将同时删除其全部文档和分段，确认删除吗？').then(() => {
      return delLibrary(lib.id)
    }).then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
  }
}

function submitForm() {
  proxy.$refs['libRef'].validate(valid => {
    if (valid) {
      saving.value = true
      saveLibrary(form.value).then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      }).finally(() => {
        saving.value = false
      })
    }
  })
}

function formatChars(n) {
  if (!n) return '0'
  if (n < 1000) return String(n)
  if (n < 1000000) return (n / 1000).toFixed(1) + 'k'
  return (n / 1000000).toFixed(1) + 'M'
}

getList()
defineExpose({ getList })
</script>

<style lang="scss" scoped>
.mb16 { margin-bottom: 16px; }
.lib-card {
  height: 150px;
  cursor: pointer;
}
.lib-create {
  display: flex;
  align-items: center;
  justify-content: center;
  border-style: dashed;
  color: #909399;
  :deep(.el-card__body) {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .create-inner {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    font-size: 14px;
  }
  &:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); }
}
.lib-head {
  display: flex;
  align-items: center;
  gap: 8px;
  .lib-name {
    flex: 1;
    font-size: 15px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lib-more { color: #909399; padding: 4px; }
}
.lib-desc {
  margin-top: 10px;
  height: 40px;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.lib-stats {
  margin-top: 12px;
  font-size: 12px;
  color: #606266;
}
.tip { margin-left: 10px; font-size: 12px; color: #909399; }
</style>
