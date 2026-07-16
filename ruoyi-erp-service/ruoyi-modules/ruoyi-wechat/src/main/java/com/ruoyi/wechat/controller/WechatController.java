package com.ruoyi.wechat.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wechat.entity.WechatConfig;
import com.ruoyi.wechat.service.WechatService;
import com.ruoyi.wechat.service.WechatHostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/wechat")
@RequiredArgsConstructor
public class WechatController {

    private final WechatService wechatService;
    private final WechatHostingService hostingService;

    @GetMapping("/config/list")
    public R<List<WechatConfig>> list() {
        return R.ok(wechatService.list());
    }

    @PostMapping("/config/save")
    public R<Void> save(@RequestBody WechatConfig config) {
        config.setCreateBy("admin");
        wechatService.save(config);
        return R.ok();
    }

    @PutMapping("/config/toggle/{id}")
    public R<Void> toggle(@PathVariable Long id, @RequestParam String status) {
        wechatService.toggle(id, status);
        return R.ok();
    }

    @DeleteMapping("/config/{id}")
    public R<Void> delete(@PathVariable Long id) {
        wechatService.delete(id);
        return R.ok();
    }

    @GetMapping("/config/access-token")
    public R<String> getAccessToken() {
        String token = wechatService.getAccessToken();
        return token != null ? R.ok(token) : R.fail("获取失败，检查配置");
    }

    /**
     * 企微回调 - URL验证（GET）
     * 企微后台配置回调URL时触发
     */
    @SaIgnore
    @GetMapping("/callback")
    public String verify(@RequestParam("msg_signature") String signature,
                         @RequestParam String timestamp,
                         @RequestParam String nonce,
                         @RequestParam String echostr) {
        String result = wechatService.verifyUrl(signature, timestamp, nonce, echostr);
        log.info("回调验证: {}", result.isEmpty() ? "失败" : "成功");
        return result;
    }

    /**
     * 企微回调 - 接收消息（POST）
     * AI Bot 推送 JSON 加密消息
     */
    @SaIgnore
    @PostMapping("/callback")
    public String receive(@RequestParam("msg_signature") String signature,
                          @RequestParam String timestamp,
                          @RequestParam String nonce,
                          @RequestBody String body) {
        try {
            // AI Bot 回调是 JSON 格式: {"encrypt":"..."}
            String encrypted;
            if (body.contains("\"encrypt\"")) {
                int s = body.indexOf("\"encrypt\":\"") + 11;
                int e = body.indexOf("\"", s);
                encrypted = body.substring(s, e);
            } else {
                // 兼容旧 XML: <Encrypt>...</Encrypt>
                int s = body.indexOf("<Encrypt>") + 10;
                int e = body.indexOf("</Encrypt>");
                if (s < 10 || e < 0) return "";
                encrypted = body.substring(s, e);
            }

            // 交给托管引擎
            hostingService.handleCallback(signature, timestamp, nonce, encrypted);
            return "";
        } catch (Exception e) {
            log.error("回调处理异常", e);
            return "";
        }
    }
}
