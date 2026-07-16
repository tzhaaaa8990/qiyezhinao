<template>
  <div class="app-container">
    <el-card>
      <el-row :gutter="10" class="mb8" type="flex" justify="space-between">
        <el-col :span="6"><span style="font-size: large">企业微信配置</span></el-col>
        <el-col :span="12" style="text-align: right">
          <el-button type="success" plain icon="Link" :loading="testLoading" @click="handleTest">测试连接</el-button>
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="configList" border class="mt20" empty-text="暂无配置">
        <el-table-column label="企业ID" prop="corpId" />
        <el-table-column label="应用AgentId" prop="agentId" width="120" align="center" />
        <el-table-column label="回调Token" prop="token" show-overflow-tooltip />
        <el-table-column label="群机器人Webhook" prop="webhookUrl" show-overflow-tooltip />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch v-model="scope.row.status" active-value="1" inactive-value="0" @change="handleToggle(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="right" class-name="small-padding fixed-width" width="160">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/修改配置 -->
    <el-drawer :title="title" v-model="open" size="50%" append-to-body>
      <el-form ref="configRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="企业ID" prop="corpId">
          <el-input v-model="form.corpId" placeholder="企微后台-我的企业-企业ID" />
        </el-form-item>
        <el-form-item label="应用Secret" prop="corpSecret">
          <el-input v-model="form.corpSecret" type="password" show-password placeholder="企微后台-应用管理-Secret" />
        </el-form-item>
        <el-form-item label="应用AgentId" prop="agentId">
          <el-input-number v-model="form.agentId" :controls="false" placeholder="应用AgentId" />
        </el-form-item>
        <el-form-item label="回调Token">
          <el-input v-model="form.token" placeholder="接收消息回调配置的Token" />
        </el-form-item>
        <el-form-item label="EncodingAESKey">
          <el-input v-model="form.encodingAesKey" placeholder="接收消息回调配置的EncodingAESKey" />
        </el-form-item>
        <el-form-item label="群机器人Webhook">
          <el-input v-model="form.webhookUrl" placeholder="群机器人Webhook地址（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="WechatConfig">
import { listConfig, saveConfig, toggleConfig, delConfig, testAccessToken } from "@/api/wechat";

const { proxy } = getCurrentInstance();

const configList = ref([]);
const loading = ref(true);
const buttonLoading = ref(false);
const testLoading = ref(false);
const open = ref(false);
const title = ref("");

const data = reactive({
  form: {},
  rules: {
    corpId: [{ required: true, message: "企业ID不能为空", trigger: "blur" }],
    corpSecret: [{ required: true, message: "应用Secret不能为空", trigger: "blur" }],
    agentId: [{ required: true, message: "AgentId不能为空", trigger: "blur" }]
  }
});

const { form, rules } = toRefs(data);

/** 查询配置列表 */
function getList() {
  loading.value = true;
  listConfig().then(response => {
    configList.value = response.data;
  }).finally(() => {
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    corpId: null,
    corpSecret: null,
    agentId: null,
    token: null,
    encodingAesKey: null,
    webhookUrl: null,
    status: '1'
  };
  proxy.resetForm("configRef");
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加企微配置";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  form.value = { ...row };
  open.value = true;
  title.value = "修改企微配置";
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["configRef"].validate(valid => {
    if (valid) {
      buttonLoading.value = true;
      saveConfig(form.value).then(() => {
        proxy.$modal.msgSuccess("保存成功");
        open.value = false;
        getList();
      }).finally(() => {
        buttonLoading.value = false;
      });
    }
  });
}

/** 启用/停用 */
function handleToggle(row) {
  toggleConfig(row.id, row.status).then(() => {
    proxy.$modal.msgSuccess(row.status === '1' ? "已启用" : "已停用");
  }).catch(() => {
    row.status = row.status === '1' ? '0' : '1';
  });
}

/** 测试连接 */
function handleTest() {
  testLoading.value = true;
  testAccessToken().then(() => {
    proxy.$modal.msgSuccess("连接成功，AccessToken 获取正常");
  }).finally(() => {
    testLoading.value = false;
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('确认删除企业【' + row.corpId + '】的配置吗？').then(() => {
    return delConfig(row.id);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  });
}

getList();
</script>
