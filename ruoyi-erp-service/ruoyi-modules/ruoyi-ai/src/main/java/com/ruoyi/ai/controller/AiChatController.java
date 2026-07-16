package com.ruoyi.ai.controller;

import com.ruoyi.ai.service.AgentService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 对话接口（Sa-Token 全局拦截器已保证登录才能访问）
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class AiChatController {

    private final AgentService agentService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@NotBlank(message = "消息不能为空") @RequestParam String message) {
        return agentService.chat(message);
    }
}
