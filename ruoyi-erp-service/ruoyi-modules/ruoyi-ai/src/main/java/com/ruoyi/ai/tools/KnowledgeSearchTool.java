package com.ruoyi.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * AI 知识库搜索工具 —— 通过 REST 调用知识库模块
 */
@Slf4j
@Component
public class KnowledgeSearchTool {

    public static final String PERM = "ai:rag:list";
    private static final String SEARCH_URL = "http://localhost:8080/rag/search";
    private final HttpClient http = HttpClient.newHttpClient();

    @Tool(description = "搜索企业知识库。用户询问公司制度、流程、标准、培训资料时调用。返回匹配的文档片段。")
    public String searchKnowledge(@ToolParam(description = "搜索关键词") String keyword,
                                   @ToolParam(description = "返回条数，默认5") Integer topK) {
        int k = topK != null ? topK : 5;
        try {
            String url = SEARCH_URL + "?keyword=" + java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&topK=" + k;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                // 简单提取 data 字段
                String body = resp.body();
                int start = body.indexOf("\"data\":\"") + 8;
                if (start < 8) return "知识库未返回有效内容。";
                int end = body.indexOf("\"}", start);
                if (end < 0) end = body.lastIndexOf("\"");
                String content = body.substring(start, end)
                    .replace("\\n", "\n").replace("\\\"", "\"");
                return content.isEmpty() ? "知识库中没有找到相关内容。" : "以下是从企业知识库中检索到的内容：\n" + content;
            }
            return "知识库检索失败。";
        } catch (Exception e) {
            log.error("知识库检索异常", e);
            return "知识库检索服务暂不可用。";
        }
    }
}
