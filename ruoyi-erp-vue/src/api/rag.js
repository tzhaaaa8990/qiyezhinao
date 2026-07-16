import request from '@/utils/request'

// ========== 知识库 ==========
export function listLibrary() {
  return request({
    url: '/rag/library/list',
    method: 'get'
  })
}

export function saveLibrary(data) {
  return request({
    url: '/rag/library/save',
    method: 'post',
    data: data
  })
}

export function delLibrary(id) {
  return request({
    url: '/rag/library/' + id,
    method: 'delete'
  })
}

// 重建库内向量索引
export function rebuildLibrary(id) {
  return request({
    url: '/rag/library/' + id + '/rebuild',
    method: 'post',
    timeout: 300000
  })
}

// ========== 文档 ==========
// 预览分段(不入库)
export function previewChunks(formData) {
  return request({
    url: '/rag/document/preview',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 上传文档(带分段参数)
export function uploadDocument(formData) {
  return request({
    url: '/rag/document/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

export function listDocument(libraryId) {
  return request({
    url: '/rag/document/list',
    method: 'get',
    params: { libraryId }
  })
}

export function delDocument(id) {
  return request({
    url: '/rag/document/' + id,
    method: 'delete'
  })
}

// ========== 分段 ==========
export function docChunks(id) {
  return request({
    url: '/rag/document/' + id + '/chunks',
    method: 'get'
  })
}

export function updateChunk(id, content) {
  return request({
    url: '/rag/chunk/' + id,
    method: 'put',
    data: { content }
  })
}

export function delChunk(id) {
  return request({
    url: '/rag/chunk/' + id,
    method: 'delete'
  })
}

// ========== 检索 ==========
// 命中测试(库内,带相似度分数)
export function hitTest(libraryId, keyword, topK = 5) {
  return request({
    url: '/rag/hit-test',
    method: 'get',
    params: { libraryId, keyword, topK }
  })
}
