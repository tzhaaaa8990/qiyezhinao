package com.ruoyi.ai.config;

import com.ruoyi.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 sys_config 当前值动态构建 ChatClient / EmbeddingModel。
 */
@Component
@RequiredArgsConstructor
public class AiModelFactory {

    private final SysConfigService configService;

    private OpenAiApi buildApi() {
        String baseUrl = configService.selectConfigByKey("ai.baseUrl");
        String apiKey = configService.selectConfigByKey("ai.apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先在右上角[系统配置]中填写 AI ApiKey");
        }
        return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
    }

    public ChatClient createChatClient() {
        String model = configService.selectConfigByKey("ai.model");
        OpenAiApi api = buildApi();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
            .build();
        return ChatClient.create(chatModel);
    }

    /**
     * 向量化文本，返回 float[]
     */
    public float[] embed(String text) {
        OpenAiApi api = buildApi();
        EmbeddingModel em = new OpenAiEmbeddingModel(api);
        List<float[]> result = em.embed(List.of(text));
        if (result.isEmpty()) return new float[0];
        return result.get(0);
    }
}
