package com.ruoyi.rag.entity;

import lombok.Data;

/**
 * 文档分块
 */
@Data
public class RagChunk {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String embedding;  // 向量 JSON: "0.123,-0.456,..."
    private Integer tokenCount;
}
