<template>
  <div class="app-container">
    <el-card>
      <el-row :gutter="10" class="mb8" type="flex" justify="space-between">
        <el-col :span="6"><span style="font-size: large">企微客户</span></el-col>
        <el-col :span="12" style="text-align: right">
          <el-button type="primary" plain icon="Download" :loading="fetchLoading" @click="handleFetch">从企微拉取</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="customerList" border class="mt20" empty-text="暂无客户，点击「从企微拉取」导入">
        <el-table-column label="名称" prop="name" />
        <el-table-column label="类型" prop="type" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.type === 2 ? 'warning' : 'success'">{{ scope.row.type === 2 ? '企业用户' : '微信用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所在企业" prop="corp_name" />
        <el-table-column label="手机" prop="mobile" width="130" />
        <el-table-column label="邮箱" prop="email" />
        <el-table-column label="地址" prop="address" show-overflow-tooltip />
        <el-table-column label="添加时间" prop="add_time" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="WechatCustomer">
import { fetchCustomers, listCustomer } from "@/api/wechat";

const { proxy } = getCurrentInstance();

const customerList = ref([]);
const loading = ref(true);
const fetchLoading = ref(false);

/** 查询客户列表 */
function getList() {
  loading.value = true;
  listCustomer().then(response => {
    customerList.value = response.data;
  }).finally(() => {
    loading.value = false;
  });
}

/** 从企微拉取客户 */
function handleFetch() {
  fetchLoading.value = true;
  fetchCustomers().then(response => {
    proxy.$modal.msgSuccess(response.data || "拉取完成");
    getList();
  }).finally(() => {
    fetchLoading.value = false;
  });
}

getList();
</script>
