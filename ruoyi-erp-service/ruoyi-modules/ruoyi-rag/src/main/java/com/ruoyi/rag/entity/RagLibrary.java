package com.ruoyi.rag.entity;

import lombok.Data;

/**
 * 知识库
 */
@Data
public class RagLibrary {
    private Long id;
    private String name;
    private String description;
    private Integer chunkSize;   // 默认分段长度(字符)
    private String separator;    // 默认分段标识符
    private String createBy;
    private String createTime;
    private String updateTime;

    // 统计字段(查询时带出)
    private Integer docCount;    // 文档数
    private Long charCount;      // 字符总数
}
