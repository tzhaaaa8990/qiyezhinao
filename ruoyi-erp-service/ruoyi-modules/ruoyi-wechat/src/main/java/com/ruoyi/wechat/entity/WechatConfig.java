package com.ruoyi.wechat.entity;

import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业微信配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WechatConfig extends BaseEntity {
    private Long id;
    private String corpId;       // 企业ID
    private String corpSecret;   // 应用Secret
    private Integer agentId;     // 应用AgentId
    private String token;        // 回调Token
    private String encodingAesKey; // 回调加密Key
    private String webhookUrl;   // 群机器人Webhook
    private String status;       // 0-停用 1-启用
}
