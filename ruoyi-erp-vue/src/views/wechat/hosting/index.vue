<template>
  <div class="hosting-workbench">
    <!-- 左侧:会话列表 -->
    <div class="session-panel">
      <div class="panel-head">
        <span>托管会话</span>
        <el-button type="primary" size="small" icon="Plus" @click="handleAdd">新建</el-button>
      </div>
      <div class="session-list">
        <div v-for="h in hostingList" :key="h.id"
             :class="['session-item', { active: current && current.id === h.id }]"
             @click="selectSession(h)">
          <span :class="['status-dot', h.enabled === '1' ? 'on' : 'off']" />
          <div class="session-info">
            <div class="session-name">{{ h.name }}</div>
            <div class="session-chat">{{ h.chatType === '1' ? '群聊' : '单聊' }} · {{ h.chatId }}</div>
          </div>
        </div>
        <el-empty v-if="hostingList.length === 0" description="暂无托管会话" :image-size="60" />
      </div>
    </div>

    <!-- 右侧:对话工作台 -->
    <div class="chat-panel" v-if="current">
      <!-- 会话控制台 -->
      <div class="chat-console">
        <span class="console-name">{{ current.name }}</span>
        <div class="console-controls">
          <span class="ctl-label">知识库</span>
          <el-select :model-value="current.libraryId" placeholder="不使用" clearable size="small"
                     style="width: 150px" @change="handleLibraryChange">
            <el-option v-for="lib in libraryList" :key="lib.id" :label="lib.name" :value="lib.id" />
          </el-select>
          <span class="ctl-label">托管</span>
          <el-switch :model-value="current.enabled" active-value="1" inactive-value="0" @change="handleToggle" />
          <el-button link type="primary" icon="Setting" @click="handleEdit(current)">设置</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(current)">删除</el-button>
        </div>
      </div>

      <!-- 对话记录(5秒自动刷新) -->
      <div class="chat-body" ref="chatRef" v-loading="logLoading && logList.length === 0">
        <template v-for="log in logList" :key="log.id">
          <div class="msg left" v-if="log.content">
            <div class="bubble customer">
              <div class="bubble-meta">{{ log.fromUser }} · {{ log.createTime }}</div>
              {{ log.content }}
            </div>
          </div>
          <div class="msg right" v-if="log.aiReply">
            <div class="bubble ai">
              <div class="bubble-meta">AI · {{ statusText[log.status] || '' }}</div>
              {{ log.aiReply }}
            </div>
          </div>
          <div class="msg right" v-else-if="log.status === '3'">
            <el-tag type="danger" size="small">需人工处理</el-tag>
          </div>
        </template>
        <el-empty v-if="!logLoading && logList.length === 0" description="暂无消息，客户在企微发消息后这里会实时显示" />
      </div>
    </div>
    <div class="chat-panel empty-panel" v-else>
      <el-empty description="选择左侧会话查看对话记录" />
    </div>

    <!-- 新建/编辑会话 -->
    <el-drawer :title="title" v-model="open" size="45%" append-to-body>
      <el-form ref="hostingRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="从客户选择" v-if="!form.id">
          <el-select filterable clearable placeholder="选择已导入的企微客户自动填充" style="width: 100%" @change="fillFromCustomer">
            <el-option v-for="c in customerList" :key="c.id" :label="c.name + (c.corp_name ? '(' + c.corp_name + ')' : '')" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会话名称" prop="name">
          <el-input v-model="form.name" placeholder="如:张总-售后咨询" />
        </el-form-item>
        <el-form-item label="聊天ID" prop="chatId">
          <el-input v-model="form.chatId" placeholder="单聊填客户ID，群聊填群ID" />
        </el-form-item>
        <el-form-item label="会话类型" prop="chatType">
          <el-radio-group v-model="form.chatType">
            <el-radio label="0">单聊</el-radio>
            <el-radio label="1">群聊</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="知识库">
          <el-select v-model="form.libraryId" placeholder="不使用" clearable style="width: 200px">
            <el-option v-for="lib in libraryList" :key="lib.id" :label="lib.name" :value="lib.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="自动回复">
          <el-switch v-model="form.autoReply" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="仅工作时间">
          <el-switch v-model="form.workHoursOnly" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="回复提示词">
          <el-input v-model="form.replyPrompt" type="textarea" :rows="4" placeholder="自定义 AI 回复提示词，留空使用默认拟人话术" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="WechatHosting">
import { listHosting, saveHosting, toggleHosting, delHosting, hostingLogs, setHostingLibrary, listCustomer } from "@/api/wechat";
import { listLibrary } from "@/api/rag";

const { proxy } = getCurrentInstance();

const hostingList = ref([]);
const libraryList = ref([]);
const customerList = ref([]);
const current = ref(null);
const logList = ref([]);
const logLoading = ref(false);
const chatRef = ref();

const open = ref(false);
const buttonLoading = ref(false);
const title = ref("");

const statusText = { '0': '待发送', '1': '已回复', '2': '已忽略', '3': '需人工' };

const data = reactive({
  form: {},
  rules: {
    name: [{ required: true, message: "会话名称不能为空", trigger: "blur" }],
    chatId: [{ required: true, message: "聊天ID不能为空", trigger: "blur" }]
  }
});
const { form, rules } = toRefs(data);

let pollTimer = null;

function getList(keepCurrent = true) {
  listHosting().then(response => {
    hostingList.value = response.data;
    if (keepCurrent && current.value) {
      const found = hostingList.value.find(h => h.id === current.value.id);
      current.value = found || null;
    }
  });
}

function selectSession(h) {
  current.value = h;
  logList.value = [];
  loadLogs(true);
}

/** 拉取消息记录(倒序取50条转正序展示) */
function loadLogs(scroll = false) {
  if (!current.value) return;
  logLoading.value = true;
  hostingLogs(current.value.id).then(response => {
    logList.value = [...response.data].reverse();
    if (scroll) {
      nextTick(() => {
        if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight;
      });
    }
  }).finally(() => {
    logLoading.value = false;
  });
}

// ===== 会话控制台 =====
function handleToggle(value) {
  toggleHosting(current.value.id, value).then(() => {
    current.value.enabled = value;
    proxy.$modal.msgSuccess(value === '1' ? "已开启托管" : "已停止托管");
    getList();
  });
}

function handleLibraryChange(libraryId) {
  setHostingLibrary(current.value.id, libraryId ?? null).then(() => {
    current.value.libraryId = libraryId ?? null;
    proxy.$modal.msgSuccess(libraryId ? "已绑定知识库，AI 将参考库内资料回复" : "已解绑知识库");
  });
}

// ===== 会话增删改 =====
function reset() {
  form.value = {
    id: null, name: null, chatId: null, chatType: '0', enabled: '0',
    intervalSeconds: 30, autoReply: '1', replyPrompt: null, libraryId: null,
    workHoursOnly: '0', remark: null
  };
  proxy.resetForm("hostingRef");
}

function handleAdd() {
  reset();
  if (customerList.value.length === 0) {
    listCustomer().then(response => { customerList.value = response.data; });
  }
  title.value = "新建托管会话";
  open.value = true;
}

function fillFromCustomer(customerId) {
  const c = customerList.value.find(item => item.id === customerId);
  if (c) {
    form.value.name = c.name;
    form.value.chatId = c.external_userid;
    form.value.chatType = '0';
  }
}

function handleEdit(row) {
  reset();
  form.value = { ...row };
  title.value = "会话设置";
  open.value = true;
}

function submitForm() {
  proxy.$refs["hostingRef"].validate(valid => {
    if (valid) {
      buttonLoading.value = true;
      saveHosting(form.value).then(() => {
        proxy.$modal.msgSuccess("保存成功");
        open.value = false;
        getList();
      }).finally(() => {
        buttonLoading.value = false;
      });
    }
  });
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除会话【' + row.name + '】吗？').then(() => {
    return delHosting(row.id);
  }).then(() => {
    if (current.value && current.value.id === row.id) current.value = null;
    getList();
    proxy.$modal.msgSuccess("删除成功");
  });
}

// ===== 实时轮询 =====
onMounted(() => {
  pollTimer = setInterval(() => {
    if (current.value) loadLogs();
  }, 5000);
});
onUnmounted(() => {
  clearInterval(pollTimer);
});

getList(false);
listLibrary().then(response => { libraryList.value = response.data; });
</script>

<style lang="scss" scoped>
.hosting-workbench {
  display: flex;
  height: 100%;
  min-height: calc(100vh - 90px);
  padding: 12px;
  gap: 12px;
}
.session-panel {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;

  .panel-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 14px;
    font-weight: 600;
    font-size: 14px;
    border-bottom: 1px solid var(--el-border-color-light);
  }
  .session-list {
    flex: 1;
    overflow-y: auto;
  }
  .session-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    cursor: pointer;
    &:hover { background: #f5f7fa; }
    &.active { background: var(--el-color-primary-light-9); }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      flex-shrink: 0;
      &.on { background: #67c23a; }
      &.off { background: #c0c4cc; }
    }
    .session-info { overflow: hidden; }
    .session-name { font-size: 14px; }
    .session-chat {
      font-size: 12px;
      color: #909399;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
.chat-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &.empty-panel { justify-content: center; }

  .chat-console {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 16px;
    border-bottom: 1px solid var(--el-border-color-light);

    .console-name { font-weight: 600; font-size: 14px; }
    .console-controls {
      display: flex;
      align-items: center;
      gap: 8px;
      .ctl-label { font-size: 13px; color: #606266; }
    }
  }
  .chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    background: #f5f7fa;

    .msg {
      display: flex;
      margin-bottom: 12px;
      &.left { justify-content: flex-start; }
      &.right { justify-content: flex-end; }
    }
    .bubble {
      max-width: 65%;
      padding: 8px 12px;
      border-radius: 8px;
      font-size: 13px;
      line-height: 1.6;
      white-space: pre-wrap;
      word-break: break-word;

      &.customer { background: #fff; }
      &.ai { background: var(--el-color-primary); color: #fff;
        .bubble-meta { color: rgba(255,255,255,.75); }
      }
      .bubble-meta {
        font-size: 11px;
        color: #909399;
        margin-bottom: 4px;
      }
    }
  }
}
</style>
