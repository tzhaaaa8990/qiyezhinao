package com.ruoyi.wechat.service;

import com.ruoyi.wechat.entity.WechatConfig;
import com.ruoyi.wechat.mapper.WechatConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatService {

    private final WechatConfigMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    private static final String API_BASE = "https://qyapi.weixin.qq.com/cgi-bin";

    public List<WechatConfig> list() { return mapper.listAll(); }

    @Transactional
    public void save(WechatConfig config) {
        if (config.getId() == null) mapper.insert(config);
        else mapper.update(config);
    }

    public void toggle(Long id, String status) { mapper.toggleStatus(id, status); }
    public void delete(Long id) { mapper.deleteById(id); }

    // ── AccessToken ──

    public String getAccessToken() {
        WechatConfig cfg = mapper.getActive();
        if (cfg == null) return null;
        try {
            String url = String.format("%s/gettoken?corpid=%s&corpsecret=%s", API_BASE, cfg.getCorpId(), cfg.getCorpSecret());
            String body = httpGet(url);
            int s = body.indexOf("\"access_token\":\"") + 16;
            if (s < 16) return null;
            int e = body.indexOf("\"", s);
            return body.substring(s, e);
        } catch (Exception ex) { log.error("getAccessToken failed", ex); return null; }
    }

    // ── 外部联系人 / 客户 ──

    /**
     * 获取成员的外部联系人列表
     */
    public String getExternalContactList(String userId) {
        try {
            String token = getAccessToken();
            if (token == null) return "[]";
            return httpGet(API_BASE + "/externalcontact/list?access_token=" + token + "&userid=" + userId);
        } catch (Exception e) { log.error("getExternalContactList failed", e); return "[]"; }
    }

    public String getExternalContactDetail(String externalUserId) {
        try {
            String token = getAccessToken();
            if (token == null) return "{}";
            return httpGet(API_BASE + "/externalcontact/get?access_token=" + token + "&external_userid=" + externalUserId);
        } catch (Exception e) { log.error("getExternalContactDetail failed", e); return "{}"; }
    }

    // ── 消息发送 ──

    /**
     * 通过 response_url 主动回复 (AI Bot 异步回复)
     */
    public boolean replyViaResponseUrl(String responseUrl, String content) {
        try {
            String json = "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"" +
                content.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"}}";
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body().contains("\"errcode\":0");
        } catch (Exception e) { log.error("reply failed", e); return false; }
    }

    /**
     * 通过群机器人 Webhook 发送消息
     */
    public boolean sendWebhook(String webhookUrl, String content) {
        try {
            String json = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" +
                content.replace("\\","\\\\").replace("\"","\\\"") + "\"}}";
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body().contains("\"errcode\":0");
        } catch (Exception e) { log.error("webhook failed", e); return false; }
    }

    // ── 回调加解密 ──

    /**
     * 验证回调URL签名 + 解密 echostr
     */
    public String verifyUrl(String signature, String timestamp, String nonce, String echostr) {
        WechatConfig cfg = mapper.getActive();
        if (cfg == null) return "";
        try {
            if (!checkSignature(cfg.getToken(), timestamp, nonce, signature)) return "signature failed";
            return decrypt(cfg.getEncodingAesKey(), echostr);
        } catch (Exception e) { log.error("verifyUrl failed", e); return ""; }
    }

    /**
     * 解密回调消息体
     */
    public String decryptMessage(String token, String encodingAesKey, String signature,
                                  String timestamp, String nonce, String encrypted) {
        try {
            if (!checkSignature(token, timestamp, nonce, signature)) return null;
            return decrypt(encodingAesKey, encrypted);
        } catch (Exception e) { log.error("decryptMessage failed", e); return null; }
    }

    private boolean checkSignature(String token, String timestamp, String nonce, String signature) throws Exception {
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        String raw = arr[0] + arr[1] + arr[2];
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString().equals(signature);
    }

    private String decrypt(String encodingAesKey, String encrypted) throws Exception {
        byte[] aesKey = org.apache.commons.codec.binary.Base64.decodeBase64(encodingAesKey + "=");
        byte[] encryptedBytes = org.apache.commons.codec.binary.Base64.decodeBase64(encrypted);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
            new IvParameterSpec(aesKey, 0, 16));
        byte[] decrypted = cipher.doFinal(encryptedBytes);
        // 去掉 PKCS7 填充
        int pad = decrypted[decrypted.length - 1];
        byte[] unpadded = Arrays.copyOf(decrypted, decrypted.length - pad);
        // 格式：16字节随机 + 4字节内容长度 + 内容 + receiveid
        byte[] content = Arrays.copyOfRange(unpadded, 20, unpadded.length);
        return new String(content, StandardCharsets.UTF_8);
    }

    // ── 工具 ──

    private String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}
