package com.ruoyi.rag.entity;

import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RagDocument extends BaseEntity {
    private Long id;
    private Long libraryId;  // 所属知识库
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String docType;
    private String status;   // 0-处理中 1-已完成
    private Integer chunkCount;
    private String remark;
}
