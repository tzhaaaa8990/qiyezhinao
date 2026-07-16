package com.ruoyi.rag.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.rag.entity.RagChunk;
import com.ruoyi.rag.entity.RagDocument;
import com.ruoyi.rag.entity.RagLibrary;
import com.ruoyi.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口
 */
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    // ==================== 知识库 ====================

    /**
     * 知识库列表（含文档数/字符数统计）
     */
    @GetMapping("/library/list")
    public R<List<RagLibrary>> libraryList() {
        return R.ok(ragService.listLibraries());
    }

    /**
     * 新增/修改知识库
     */
    @PostMapping("/library/save")
    public R<Void> saveLibrary(@RequestBody RagLibrary library) {
        if (library.getCreateBy() == null) library.setCreateBy("admin");
        ragService.saveLibrary(library);
        return R.ok();
    }

    /**
     * 删除知识库（级联删除文档与分段）
     */
    @DeleteMapping("/library/{id}")
    public R<Void> deleteLibrary(@PathVariable Long id) {
        ragService.deleteLibrary(id);
        return R.ok();
    }

    /**
     * 重建库内向量索引
     */
    @PostMapping("/library/{id}/rebuild")
    public R<String> rebuild(@PathVariable Long id) {
        int[] result = ragService.rebuildLibrary(id);
        if (result[0] == 0 && result[1] > 0) {
            return R.fail("重建失败 " + result[1] + " 条，请检查向量模型配置(系统配置里的向量API Key)");
        }
        return R.ok("重建完成：成功 " + result[0] + " 条，失败 " + result[1] + " 条");
    }

    // ==================== 文档 ====================

    /**
     * 预览分段（不入库）
     */
    @PostMapping("/document/preview")
    public R<List<RagChunk>> preview(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "chunkSize", defaultValue = "500") int chunkSize,
                                      @RequestParam(value = "separator", defaultValue = "\\n\\n") String separator,
                                      @RequestParam(value = "clean", defaultValue = "true") boolean clean,
                                      @RequestParam(value = "overlap", defaultValue = "50") int overlap) {
        try {
            return R.ok(ragService.preview(file, chunkSize, separator, clean, overlap));
        } catch (Exception e) {
            return R.fail("预览失败：" + e.getMessage());
        }
    }

    /**
     * 上传文档（按分段参数入库并向量化）
     */
    @PostMapping("/document/upload")
    public R<RagDocument> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "libraryId", defaultValue = "1") Long libraryId,
                                  @RequestParam(value = "chunkSize", defaultValue = "500") int chunkSize,
                                  @RequestParam(value = "separator", defaultValue = "\\n\\n") String separator,
                                  @RequestParam(value = "clean", defaultValue = "true") boolean clean,
                                  @RequestParam(value = "overlap", defaultValue = "50") int overlap) {
        try {
            RagDocument doc = ragService.upload(file, libraryId, chunkSize, separator, clean, overlap, "admin");
            return R.ok(doc);
        } catch (Exception e) {
            return R.fail("上传失败：" + e.getMessage());
        }
    }

    /**
     * 文档列表
     */
    @GetMapping("/document/list")
    public R<List<RagDocument>> list(@RequestParam(value = "libraryId", required = false) Long libraryId) {
        return R.ok(ragService.list(libraryId));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/document/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ragService.delete(id);
        return R.ok();
    }

    // ==================== 分段 ====================

    /**
     * 查看文档分段
     */
    @GetMapping("/document/{id}/chunks")
    public R<List<RagChunk>> chunks(@PathVariable Long id) {
        return R.ok(ragService.getChunks(id));
    }

    /**
     * 编辑分段内容（自动重新向量化）
     */
    @PutMapping("/chunk/{id}")
    public R<Void> updateChunk(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ragService.updateChunk(id, body.getOrDefault("content", ""));
        return R.ok();
    }

    /**
     * 删除分段
     */
    @DeleteMapping("/chunk/{id}")
    public R<Void> deleteChunk(@PathVariable Long id) {
        ragService.deleteChunk(id);
        return R.ok();
    }

    // ==================== 检索 ====================

    /**
     * 命中测试：库内检索，返回分段与相似度分数
     */
    @GetMapping("/hit-test")
    public R<List<RagService.HitResult>> hitTest(@RequestParam Long libraryId,
                                                  @RequestParam String keyword,
                                                  @RequestParam(defaultValue = "5") int topK) {
        return R.ok(ragService.hitTest(libraryId, keyword, topK));
    }

    /**
     * 知识库搜索（供 AI Agent 工具与托管引擎调用，libraryId 可限定库）
     */
    @SaIgnore
    @GetMapping("/search")
    public R<String> search(@RequestParam String keyword,
                             @RequestParam(defaultValue = "5") int topK,
                             @RequestParam(required = false) Long libraryId) {
        String ctx = ragService.searchContext(keyword, topK, libraryId);
        return R.ok(ctx);
    }
}
