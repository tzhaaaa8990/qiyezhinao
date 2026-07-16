package com.ruoyi.wechat.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.wechat.entity.WechatHosting;
import com.ruoyi.wechat.entity.WechatMessageLog;
import com.ruoyi.wechat.mapper.WechatHostingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wechat/hosting")
@RequiredArgsConstructor
public class WechatHostingController {

    private final WechatHostingMapper mapper;

    @GetMapping("/list")
    public R<List<WechatHosting>> list() {
        return R.ok(mapper.listAll());
    }

    @PostMapping("/save")
    public R<Void> save(@RequestBody WechatHosting hosting) {
        hosting.setCreateBy("admin");
        if (hosting.getId() == null) {
            mapper.insert(hosting);
        } else {
            mapper.update(hosting);
        }
        return R.ok();
    }

    @PutMapping("/toggle/{id}")
    public R<Void> toggle(@PathVariable Long id, @RequestParam String enabled) {
        mapper.toggle(id, enabled);
        return R.ok();
    }

    /**
     * 绑定/解绑知识库（不传 libraryId 即解绑）
     */
    @PutMapping("/library/{id}")
    public R<Void> setLibrary(@PathVariable Long id, @RequestParam(required = false) Long libraryId) {
        mapper.setLibrary(id, libraryId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/logs/{hostingId}")
    public R<List<WechatMessageLog>> logs(@PathVariable Long hostingId,
                                           @RequestParam(defaultValue = "50") int limit) {
        return R.ok(mapper.getLogs(hostingId, limit));
    }
}
