package com.ruoyi.wechat.entity;

import lombok.Data;

@Data
public class WechatMessageLog {
    private Long id;
    private Long hostingId;
    private String msgId;
    private String fromUser;
    private String msgType;
    private String content;
    private String aiReply;
    private String direction;
    private String status;
    private String createTime;
}
