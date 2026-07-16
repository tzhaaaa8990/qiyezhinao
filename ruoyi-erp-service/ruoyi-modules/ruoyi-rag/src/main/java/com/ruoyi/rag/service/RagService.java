package com.ruoyi.rag.service;

import com.ruoyi.rag.entity.RagChunk;
import com.ruoyi.rag.entity.RagDocument;
import com.ruoyi.rag.entity.RagLibrary;
import com.ruoyi.rag.mapper.RagDocumentMapper;
import com.ruoyi.rag.mapper.RagLibraryMapper;
import com.ruoyi.system.service.SysConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 知识库核心服务：解析 → 分段 → 向量化 → 语义检索
 * 分库管理，分段参数可调，支持导入前预览与命中测试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final RagDocumentMapper mapper;
    private final RagLibraryMapper libraryMapper;
    private final SysConfigService configService;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final Tika TIKA = new Tika();
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final String DEFAULT_SEPARATOR = "\\n\\n";

    // ==================== 知识库管理 ====================

    public List<RagLibrary> listLibraries() {
        return libraryMapper.listWithStats();
    }

    public void saveLibrary(RagLibrary library) {
        if (library.getChunkSize() == null) library.setChunkSize(DEFAULT_CHUNK_SIZE);
        if (library.getSeparator() == null || library.getSeparator().isBlank()) library.setSeparator(DEFAULT_SEPARATOR);
        if (library.getId() == null) {
            libraryMapper.insert(library);
        } else {
            libraryMapper.update(library);
        }
    }

    /** 删除知识库（级联删除文档与分段） */
    @Transactional
    public void deleteLibrary(Long id) {
        mapper.deleteChunksByLibrary(id);
        mapper.deleteByLibrary(id);
        libraryMapper.deleteById(id);
    }

    // ==================== 文档导入 ====================

    /**
     * 预览分段：解析文件并按参数分段，不入库
     */
    public List<RagChunk> preview(MultipartFile file, int chunkSize, String separator, boolean clean) throws IOException {
        String text = parseText(file);
        if (text == null || text.isBlank()) return Collections.emptyList();
        return splitText(text, null, chunkSize, separator, clean);
    }

    /**
     * 上传文档：解析、分段、向量化、入库
     */
    @Transactional
    public RagDocument upload(MultipartFile file, Long libraryId, int chunkSize, String separator, boolean clean, String createBy) throws IOException {
        RagDocument doc = new RagDocument();
        doc.setLibraryId(libraryId != null ? libraryId : 1L);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(getExt(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setDocType("通用");
        doc.setCreateBy(createBy);
        mapper.insertDocument(doc);

        String text = parseText(file);
        if (text == null || text.isBlank()) {
            mapper.markDone(doc.getId(), 0);
            return doc;
        }

        List<RagChunk> chunks = splitText(text, doc.getId(), chunkSize, separator, clean);
        for (RagChunk chunk : chunks) {
            // 向量化每个分段（HTTP调embedding API）
            try {
                float[] vec = callEmbedding(chunk.getContent());
                if (vec != null) chunk.setEmbedding(encodeVector(vec));
            } catch (Exception e) {
                log.warn("向量化失败(chunk={}): {}", chunk.getChunkIndex(), e.getMessage());
            }
            mapper.insertChunk(chunk);
        }

        mapper.markDone(doc.getId(), chunks.size());
        doc.setChunkCount(chunks.size());
        doc.setStatus("1");
        return doc;
    }

    public List<RagDocument> list(Long libraryId) {
        return libraryId != null ? mapper.listByLibrary(libraryId) : mapper.listAll();
    }

    @Transactional
    public void delete(Long id) {
        mapper.deleteChunksByDocId(id);
        mapper.deleteById(id);
    }

    // ==================== 分段管理 ====================

    public List<RagChunk> getChunks(Long docId) {
        return mapper.getChunksByDocId(docId);
    }

    /** 编辑分段内容并重新向量化 */
    public void updateChunk(Long chunkId, String content) {
        RagChunk chunk = mapper.getChunkById(chunkId);
        if (chunk == null) return;
        chunk.setContent(content);
        chunk.setTokenCount(content.length() / 2);
        try {
            float[] vec = callEmbedding(content);
            chunk.setEmbedding(vec != null ? encodeVector(vec) : null);
        } catch (Exception e) {
            log.warn("分段重新向量化失败(id={}): {}", chunkId, e.getMessage());
            chunk.setEmbedding(null);
        }
        mapper.updateChunk(chunk);
    }

    @Transactional
    public void deleteChunk(Long chunkId) {
        RagChunk chunk = mapper.getChunkById(chunkId);
        if (chunk == null) return;
        mapper.deleteChunkById(chunkId);
        mapper.refreshChunkCount(chunk.getDocumentId());
    }

    /**
     * 重建向量索引：对库内全部分段重新向量化
     * @return [成功数, 失败数]
     */
    public int[] rebuildLibrary(Long libraryId) {
        List<RagChunk> chunks = mapper.getChunksByLibrary(libraryId);
        int ok = 0, fail = 0;
        for (RagChunk chunk : chunks) {
            try {
                float[] vec = callEmbedding(chunk.getContent());
                if (vec != null) {
                    chunk.setEmbedding(encodeVector(vec));
                    mapper.updateChunk(chunk);
                    ok++;
                } else {
                    fail++;
                }
            } catch (Exception e) {
                log.warn("重建向量失败(chunk={}): {}", chunk.getId(), e.getMessage());
                fail++;
            }
        }
        return new int[]{ok, fail};
    }

    // ==================== 检索 ====================

    /** 命中测试结果 */
    @Data
    public static class HitResult {
        private Long chunkId;
        private Long documentId;
        private String docName;
        private String content;
        private Float score;      // 余弦相似度，关键词兜底时为 null
        private String matchType; // vector | keyword
    }

    /**
     * 命中测试：库内向量检索，返回分段+相似度
     */
    public List<HitResult> hitTest(Long libraryId, String query, int topK) {
        List<RagChunk> chunks = mapper.getChunksByLibrary(libraryId);
        Map<Long, String> docNames = new HashMap<>();
        for (RagDocument d : mapper.listByLibrary(libraryId)) {
            docNames.put(d.getId(), d.getFileName());
        }

        float[] queryVec = null;
        try {
            queryVec = callEmbedding(query);
        } catch (Exception e) {
            log.warn("查询向量化失败: {}", e.getMessage());
        }

        List<HitResult> results = new ArrayList<>();
        if (queryVec != null) {
            List<Map.Entry<RagChunk, Float>> scored = new ArrayList<>();
            for (RagChunk c : chunks) {
                float[] chunkVec = decodeVector(c.getEmbedding());
                if (chunkVec == null) continue;
                scored.add(new AbstractMap.SimpleEntry<>(c, cosine(queryVec, chunkVec)));
            }
            scored.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                results.add(toHit(scored.get(i).getKey(), scored.get(i).getValue(), "vector", docNames));
            }
        }
        // 向量不可用或无结果时关键词兜底
        if (results.isEmpty()) {
            for (RagChunk c : chunks) {
                if (c.getContent().contains(query)) {
                    results.add(toHit(c, null, "keyword", docNames));
                    if (results.size() >= topK) break;
                }
            }
        }
        return results;
    }

    private HitResult toHit(RagChunk c, Float score, String matchType, Map<Long, String> docNames) {
        HitResult hit = new HitResult();
        hit.setChunkId(c.getId());
        hit.setDocumentId(c.getDocumentId());
        hit.setDocName(docNames.getOrDefault(c.getDocumentId(), String.valueOf(c.getDocumentId())));
        hit.setContent(c.getContent());
        hit.setScore(score);
        hit.setMatchType(matchType);
        return hit;
    }

    /**
     * 语义检索（供 AI Agent 工具与托管引擎调用）：向量化查询 → 余弦相似度排序 → TopK
     * @param libraryId 限定知识库，null 表示跨库
     */
    public String searchContext(String query, int topK, Long libraryId) {
        List<RagChunk> allChunks = libraryId != null
            ? mapper.getChunksByLibrary(libraryId)
            : mapper.getAllChunks();
        if (allChunks.isEmpty()) return fallbackKeyword(query, topK);

        float[] queryVec;
        try {
            queryVec = callEmbedding(query);
        } catch (Exception e) {
            return fallbackKeyword(query, topK);
        }
        if (queryVec == null) return fallbackKeyword(query, topK);

        // 余弦相似度计算 + 排序
        List<Map.Entry<RagChunk, Float>> scored = new ArrayList<>();
        for (RagChunk c : allChunks) {
            float[] chunkVec = decodeVector(c.getEmbedding());
            if (chunkVec == null) continue;
            float sim = cosine(queryVec, chunkVec);
            scored.add(new AbstractMap.SimpleEntry<>(c, sim));
        }
        scored.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            RagChunk c = scored.get(i).getKey();
            sb.append("【").append(c.getDocumentId()).append("】")
              .append(c.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String fallbackKeyword(String keyword, int topK) {
        List<RagChunk> chunks = mapper.search(keyword, topK);
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RagChunk c : chunks) {
            sb.append("【").append(c.getDocumentId()).append("】")
              .append(c.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    // --- 文本解析与分段 ---

    private String parseText(MultipartFile file) throws IOException {
        try {
            return TIKA.parseToString(file.getInputStream());
        } catch (Exception e) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 智能分段：按分段标识符切开，再按分段长度合并；单段超长时硬切
     */
    private List<RagChunk> splitText(String text, Long docId, int chunkSize, String separator, boolean clean) {
        if (chunkSize <= 0) chunkSize = DEFAULT_CHUNK_SIZE;
        if (separator == null || separator.isEmpty()) separator = DEFAULT_SEPARATOR;
        // 前端传的是字面量 "\n\n"，还原为真实换行
        String sep = separator.replace("\\n", "\n").replace("\\t", "\t");

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        if (clean) {
            normalized = normalized.replaceAll("[ \\t]+\\n", "\n")   // 行尾空白
                                   .replaceAll("\\n{3,}", "\n\n")     // 连续空行
                                   .replaceAll("[ \\t]{2,}", " ");    // 连续空格
        }

        // 按标识符切段
        String[] segments = normalized.split(java.util.regex.Pattern.quote(sep));

        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String seg : segments) {
            String s = seg.trim();
            if (s.isEmpty()) continue;
            if (current.length() > 0 && current.length() + s.length() + sep.length() > chunkSize) {
                merged.add(current.toString());
                current.setLength(0);
            }
            if (s.length() > chunkSize) {
                // 单段超长：先落盘已积累内容，再硬切
                if (current.length() > 0) {
                    merged.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < s.length(); i += chunkSize) {
                    merged.add(s.substring(i, Math.min(i + chunkSize, s.length())));
                }
            } else {
                if (current.length() > 0) current.append(sep);
                current.append(s);
            }
        }
        if (current.length() > 0) merged.add(current.toString());

        List<RagChunk> list = new ArrayList<>();
        int idx = 0;
        for (String content : merged) {
            RagChunk c = new RagChunk();
            c.setDocumentId(docId);
            c.setChunkIndex(idx++);
            c.setContent(content);
            c.setTokenCount(content.length() / 2);
            list.add(c);
        }
        return list;
    }

    // --- Embedding HTTP 调用（独立配置，与聊天模型解耦） ---

    private float[] callEmbedding(String text) throws Exception {
        String baseUrl = configService.selectConfigByKey("ai.embedding.baseUrl");
        String apiKey = configService.selectConfigByKey("ai.embedding.apiKey");
        String model = configService.selectConfigByKey("ai.embedding.model");
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) return null;

        String json = String.format("{\"input\":\"%s\",\"model\":\"%s\"}",
            text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"),
            model);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/embeddings"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("embedding 接口返回 {}: {}", resp.statusCode(), abbreviate(resp.body()));
            return null;
        }
        // 解析 response JSON: data[0].embedding
        String body = resp.body();
        int embStart = body.indexOf("\"embedding\":[");
        if (embStart < 0) return null;
        embStart += 13; // skip "embedding":[
        int embEnd = body.indexOf("]", embStart);
        if (embEnd < 0) return null;
        String[] parts = body.substring(embStart, embEnd).split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) vec[i] = Float.parseFloat(parts[i].trim());
        return vec;
    }

    private String abbreviate(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) : s);
    }

    // --- 向量工具 ---

    private String encodeVector(float[] vec) {
        if (vec == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", vec[i]));
        }
        return sb.toString();
    }

    private float[] decodeVector(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        String[] parts = encoded.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { vec[i] = Float.parseFloat(parts[i]); }
            catch (NumberFormatException e) { return null; }
        }
        return vec;
    }

    private float cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8));
    }

    private String getExt(String name) {
        if (name == null) return "unknown";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "unknown";
    }
}
