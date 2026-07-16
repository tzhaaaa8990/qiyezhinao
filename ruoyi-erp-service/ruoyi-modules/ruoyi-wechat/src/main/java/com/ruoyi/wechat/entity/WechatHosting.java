package com.ruoyi.wechat.entity;

import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WechatHosting extends BaseEntity {
    private Long id;
    private String name;
    private String chatId;
    private String chatType;
    private String enabled;
    private Integer intervalSeconds;
    private String autoReply;
    private String replyPrompt;
    private Long libraryId;      // 绑定知识库(NULL=不使用)
    private String workHoursOnly;
    private String remark;
}
