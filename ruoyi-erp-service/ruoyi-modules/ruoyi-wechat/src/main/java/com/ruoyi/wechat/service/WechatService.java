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
    public void save(WechatConfig c) { if (c.getId()==null) mapper.insert(c); else mapper.update(c); }
    public void toggle(Long id, String s) { mapper.toggleStatus(id, s); }
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

    // ── 通讯录成员 ──
    public List<String> getAllUserIds() {
        List<String> ids = new ArrayList<>();
        try {
            String token = getAccessToken();
            if (token == null) return ids;
            String deptJson = httpGet(API_BASE + "/department/simplelist?access_token=" + token);
            List<String> deptIds = extractList(deptJson, "department_id");
            if (deptIds.isEmpty()) deptIds.add("1");
            for (String did : deptIds) {
                String u = httpGet(API_BASE + "/user/simplelist?access_token=" + token + "&department_id=" + did + "&fetch_child=1");
                ids.addAll(extractList(u, "userid"));
            }
        } catch (Exception e) { log.error("getAllUserIds failed", e); }
        return ids;
    }

    /** 获取成员详细信息 */
    public String getUserDetail(String userId) {
        try {
            String token = getAccessToken();
            if (token == null) return "{}";
            return httpGet(API_BASE + "/user/get?access_token=" + token + "&userid=" + userId);
        } catch (Exception e) { log.error("getUserDetail failed", e); return "{}"; }
    }

    // ── 外部联系人（客户） ──
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

    // ── 消息回复 ──
    public boolean replyViaResponseUrl(String responseUrl, String content) {
        try {
            String json = "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"" +
                content.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"}}";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(responseUrl))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
            return http.send(req, HttpResponse.BodyHandlers.ofString()).body().contains("\"errcode\":0");
        } catch (Exception e) { log.error("reply failed", e); return false; }
    }

    public boolean sendWebhook(String webhookUrl, String content) {
        try {
            String json = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" +
                content.replace("\\","\\\\").replace("\"","\\\"") + "\"}}";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(webhookUrl))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
            return http.send(req, HttpResponse.BodyHandlers.ofString()).body().contains("\"errcode\":0");
        } catch (Exception e) { log.error("webhook failed", e); return false; }
    }

    // ── 回调加解密 ──
    public String verifyUrl(String signature, String timestamp, String nonce, String echostr) {
        WechatConfig cfg = mapper.getActive();
        if (cfg == null) return "";
        try {
            if (!checkSignature(cfg.getToken(), timestamp, nonce, signature)) return "signature failed";
            return decrypt(cfg.getEncodingAesKey(), echostr);
        } catch (Exception e) { log.error("verifyUrl failed", e); return ""; }
    }

    private boolean checkSignature(String token, String timestamp, String nonce, String signature) throws Exception {
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] d = sha1.digest((arr[0]+arr[1]+arr[2]).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString().equals(signature);
    }

    private String decrypt(String aesKey64, String encrypted) throws Exception {
        byte[] aesKey = Base64.decodeBase64(aesKey64 + "=");
        byte[] enc = Base64.decodeBase64(encrypted);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(aesKey, 0, 16));
        byte[] dec = cipher.doFinal(enc);
        int pad = dec[dec.length - 1];
        byte[] unpadded = Arrays.copyOf(dec, dec.length - pad);
        int msgLen = ((unpadded[16]&0xff)<<24)|((unpadded[17]&0xff)<<16)|((unpadded[18]&0xff)<<8)|(unpadded[19]&0xff);
        return new String(Arrays.copyOfRange(unpadded, 20, 20 + msgLen), StandardCharsets.UTF_8);
    }

    // ── 工具 ──
    private String httpGet(String url) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString()).body();
    }

    private static List<String> extractList(String json, String key) {
        List<String> result = new ArrayList<>();
        String prefix = "\"" + key + "\":\"";
        int idx = 0;
        while ((idx = json.indexOf(prefix, idx)) >= 0) {
            idx += prefix.length();
            int end;
            if (json.charAt(idx) != '\"') {
                end = json.indexOf(",", idx);
                if (end < 0) end = json.indexOf("}", idx);
                if (end > idx) result.add(json.substring(idx, end).trim());
            } else {
                idx++;
                end = json.indexOf("\"", idx);
                if (end > idx) result.add(json.substring(idx, end));
            }
            idx = end >= 0 ? end : idx + 1;
        }
        return result;
    }
}
