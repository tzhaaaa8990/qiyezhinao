package com.ruoyi.wechat.service;

import com.ruoyi.system.service.SysConfigService;
import com.ruoyi.wechat.entity.WechatHosting;
import com.ruoyi.wechat.entity.WechatConfig;
import com.ruoyi.wechat.mapper.WechatConfigMapper;
import com.ruoyi.wechat.mapper.WechatHostingMapper;
import com.ruoyi.wechat.entity.WechatMessageLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AI托管核心引擎：解密回调 → 匹配规则 → AI处理 → 自动回复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatHostingService {

    private final WechatHostingMapper hostingMapper;
    private final WechatConfigMapper configMapper;
    private final SysConfigService configService;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * 处理企微回调消息（完整流程）
     */
    public String handleCallback(String signature, String timestamp, String nonce, String encryptedBody) {
        WechatConfig cfg = configMapper.getActive();
        if (cfg == null) {
            log.warn("无活跃企微配置，跳过回调");
            return "";
        }

        // 1. 验证签名 + 解密
        String plainJson = decryptCallback(cfg, signature, timestamp, nonce, encryptedBody);
        if (plainJson == null || plainJson.isEmpty()) {
            log.warn("解密失败，签名校验不通过");
            return "";
        }

        log.info("收到企微消息: {}", plainJson.substring(0, Math.min(200, plainJson.length())));

        // 2. 解析消息字段
        String msgId = extractJsonValue(plainJson, "msgid");
        String msgType = extractJsonValue(plainJson, "msgtype");
        String chatId = extractJsonValue(plainJson, "chatid");
        String chatType = extractJsonValue(plainJson, "chattype");
        String fromUser = extractJsonValue(plainJson, "from", "userid");
        String content = extractJsonValue(plainJson, "text", "content");
        if (content == null) content = extractJsonValue(plainJson, "content");
        String responseUrl = extractJsonValue(plainJson, "response_url");

        if (msgType == null) return "";

        // 3. 匹配托管规则（先按chatId，再按fromUser）
        WechatHosting hosting = findMatchingHosting(chatId, fromUser, chatType);
        if (hosting == null || !"1".equals(hosting.getEnabled())) {
            log.info("无匹配托管规则或已停用 chatId={} fromUser={}", chatId, fromUser);
            return "";
        }

        // 4. 工作时间检查
        if ("1".equals(hosting.getWorkHoursOnly()) && !isWorkHours()) {
            log.info("非工作时间，跳过托管");
            return "";
        }

        // 5. 记录接收消息
        WechatMessageLog logEntry = new WechatMessageLog();
        logEntry.setHostingId(hosting.getId());
        logEntry.setMsgId(msgId);
        logEntry.setFromUser(fromUser);
        logEntry.setMsgType(msgType);
        logEntry.setContent(content);
        logEntry.setDirection("0");
        logEntry.setStatus("0");
        hostingMapper.insertLog(logEntry);

        // 6. AI处理 + 自动回复（异步）
        if ("1".equals(hosting.getAutoReply())) {
            processWithAI(hosting, logEntry, content, responseUrl);
        }

        return ""; // 回调不阻塞
    }

    private void processWithAI(WechatHosting hosting, WechatMessageLog logEntry,
                                String content, String responseUrl) {
        executor.submit(() -> {
            try {
                String systemPrompt = hosting.getReplyPrompt();
                if (systemPrompt == null || systemPrompt.isBlank()) {
                    systemPrompt = """
                        你是一个真实的企业员工，正在用企业微信和客户聊天。你的回复必须像真人一样自然。
                        要求：
                        - 口语化，像朋友聊天，不要用"您好"、"请"这类客服腔
                        - 简短。大部分回复不超过两句话，20-50字
                        - 偶尔用语气词（嗯、哈、哦、好的、没问题）
                        - 不要列清单、不要用序号、不要用标题
                        - 不要加粗、不用标记符号
                        - 如果不知道答案，就说"我问下同事，稍等哈"
                        - 绝对不要暴露你是AI
                        - 像你平时微信聊天一样就行了
                        """;
                }

                // 绑定了知识库：检索相关资料注入提示词
                if (hosting.getLibraryId() != null) {
                    String knowledge = searchKnowledge(hosting.getLibraryId(), content);
                    if (knowledge != null && !knowledge.isBlank()) {
                        systemPrompt += "\n\n以下是企业知识库中与客户问题相关的资料，回答时参考使用（不要提到\"知识库\"这个词）：\n" + knowledge;
                    }
                }

                // 模拟真人打字延迟（2-5秒）
                Thread.sleep(2000 + new Random().nextInt(3000));

                // 调 AI —— 通过 REST 接口（模块隔离）
                String aiReply = callAiChat(systemPrompt, content);

                logEntry.setAiReply(aiReply);
                // 用纯文本回复，不用 markdown
                boolean sent = replyPlainText(responseUrl, aiReply);
                logEntry.setDirection(sent ? "1" : "0");
                logEntry.setStatus(sent ? "1" : "3");
            } catch (Exception e) {
                log.error("AI处理失败", e);
                logEntry.setStatus("3");
                replyPlainText(responseUrl, "稍等一下哈，系统有点卡");
            }
            hostingMapper.updateLog(logEntry);
        });
    }

    /**
     * 检索指定知识库（REST 调用 rag 模块，模块隔离）
     */
    private String searchKnowledge(Long libraryId, String query) {
        try {
            String url = "http://localhost:8080/rag/search?keyword="
                + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&topK=3&libraryId=" + libraryId;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String body = resp.body();
            int start = body.indexOf("\"data\":\"");
            if (start < 0) return null;
            start += 8;
            int end = body.lastIndexOf("\"");
            if (end <= start) return null;
            return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
        } catch (Exception e) {
            log.warn("知识库检索失败(libraryId={}): {}", libraryId, e.getMessage());
            return null;
        }
    }

    /**
     * 通过 HTTP 调用本地 AI 聊天接口（模块隔离，不直接依赖 ruoyi-ai）
     */
    private String callAiChat(String systemPrompt, String userMessage) {
        try {
            String baseUrl = configService.selectConfigByKey("ai.baseUrl");
            String apiKey = configService.selectConfigByKey("ai.apiKey");
            String model = configService.selectConfigByKey("ai.model");
            if (apiKey == null || apiKey.isBlank()) return "AI未配置，请联系管理员。";

            String body = String.format(
                "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}]}",
                model,
                systemPrompt.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"),
                userMessage.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            // 提取 choices[0].message.content
            String rb = resp.body();
            int idx = rb.indexOf("\"content\":\"");
            if (idx < 0) return "AI返回异常";
            idx += 11;
            int end = rb.indexOf("\"", idx);
            return end > idx ? rb.substring(idx, end).replace("\\n","\n").replace("\\\"","\"") : "AI返回为空";
        } catch (Exception e) {
            log.error("callAiChat failed", e);
            return "AI服务暂不可用。";
        }
    }

    // ── 加解密 ──

    private String decryptCallback(WechatConfig cfg, String signature, String timestamp,
                                    String nonce, String encrypted) {
        try {
            // 1. 签名校验
            String[] arr = {cfg.getToken(), timestamp, nonce, encrypted};
            Arrays.sort(arr);
            String raw = arr[0] + arr[1] + arr[2] + arr[3];
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            if (!sb.toString().equals(signature)) {
                log.warn("签名校验失败");
                return null;
            }

            // 2. AES解密
            byte[] aesKey = org.apache.commons.codec.binary.Base64.decodeBase64(cfg.getEncodingAesKey() + "=");
            byte[] encryptedBytes = org.apache.commons.codec.binary.Base64.decodeBase64(encrypted);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new IvParameterSpec(aesKey, 0, 16));
            byte[] decrypted = cipher.doFinal(encryptedBytes);

            // 去填充
            int pad = decrypted[decrypted.length - 1];
            byte[] unpadded = Arrays.copyOf(decrypted, decrypted.length - pad);

            // 格式: 16字节随机 + 4字节内容长度 + 内容 + receiveid
            int msgLen = ((unpadded[16] & 0xff) << 24) | ((unpadded[17] & 0xff) << 16)
                        | ((unpadded[18] & 0xff) << 8) | (unpadded[19] & 0xff);
            byte[] content = Arrays.copyOfRange(unpadded, 20, 20 + msgLen);
            return new String(content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密异常", e);
            return null;
        }
    }

    // ── 匹配托管 ──

    private WechatHosting findMatchingHosting(String chatId, String fromUser, String chatType) {
        List<WechatHosting> enabled = hostingMapper.listEnabled();
        for (WechatHosting h : enabled) {
            if (h.getChatId() != null && h.getChatId().equals(chatId)) return h;
            if (h.getChatId() != null && h.getChatId().equals(fromUser)) return h;
        }
        return null;
    }

    // ── 回复 ──

    /**
     * 模拟真人回复：用 markdown 类型但只发纯文本（无格式）
     */
    private boolean replyPlainText(String responseUrl, String content) {
        if (responseUrl == null || responseUrl.isEmpty()) return false;
        try {
            String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t");
            String json = String.format(
                "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"%s\"}}", escaped);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.body().contains("\"errcode\":0");
            log.info("企微回复 {}: {}", ok ? "成功" : "失败", resp.body().substring(0, Math.min(100, resp.body().length())));
            return ok;
        } catch (Exception e) {
            log.error("回复失败", e);
            return false;
        }
    }

    // ── 工具 ──

    private String extractJsonValue(String json, String... keys) {
        String search = json;
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            if (i == keys.length - 1) {
                // 最后一个key：提取值
                int idx = search.indexOf("\"" + k + "\":\"");
                if (idx < 0) {
                    idx = search.indexOf("\"" + k + "\": \"");
                    if (idx < 0) return null;
                    idx += k.length() + 5;
                } else {
                    idx += k.length() + 4;
                }
                int end = search.indexOf("\"", idx);
                return end > idx ? search.substring(idx, end) : null;
            } else {
                int idx = search.indexOf("\"" + k + "\":");
                if (idx < 0) return null;
                search = search.substring(idx);
            }
        }
        return null;
    }

    private boolean isWorkHours() {
        int hour = LocalTime.now().getHour();
        int day = LocalDateTime.now().getDayOfWeek().getValue();
        return day <= 5 && hour >= 9 && hour < 18;
    }
}
