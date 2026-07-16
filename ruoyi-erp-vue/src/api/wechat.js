import request from '@/utils/request'

// ========== 企业微信配置 ==========
export function listConfig() {
  return request({
    url: '/wechat/config/list',
    method: 'get'
  })
}

export function saveConfig(data) {
  return request({
    url: '/wechat/config/save',
    method: 'post',
    data: data
  })
}

export function toggleConfig(id, status) {
  return request({
    url: '/wechat/config/toggle/' + id,
    method: 'put',
    params: { status }
  })
}

export function delConfig(id) {
  return request({
    url: '/wechat/config/' + id,
    method: 'delete'
  })
}

export function testAccessToken() {
  return request({
    url: '/wechat/config/access-token',
    method: 'get'
  })
}

// ========== 客户导入 ==========
export function fetchCustomers() {
  return request({
    url: '/wechat/customer/fetch',
    method: 'get'
  })
}

export function listCustomer() {
  return request({
    url: '/wechat/customer/list',
    method: 'get'
  })
}

export function syncCustomer(ids) {
  return request({
    url: '/wechat/customer/sync',
    method: 'post',
    data: ids
  })
}

// ========== AI 托管 ==========
export function listHosting() {
  return request({
    url: '/wechat/hosting/list',
    method: 'get'
  })
}

export function saveHosting(data) {
  return request({
    url: '/wechat/hosting/save',
    method: 'post',
    data: data
  })
}

export function toggleHosting(id, enabled) {
  return request({
    url: '/wechat/hosting/toggle/' + id,
    method: 'put',
    params: { enabled }
  })
}

// 绑定/解绑知识库(libraryId 传 null 即解绑)
export function setHostingLibrary(id, libraryId) {
  return request({
    url: '/wechat/hosting/library/' + id,
    method: 'put',
    params: libraryId != null ? { libraryId } : {}
  })
}

export function delHosting(id) {
  return request({
    url: '/wechat/hosting/' + id,
    method: 'delete'
  })
}

export function hostingLogs(hostingId, limit = 50) {
  return request({
    url: '/wechat/hosting/logs/' + hostingId,
    method: 'get',
    params: { limit }
  })
}
