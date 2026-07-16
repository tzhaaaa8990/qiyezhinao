package com.ruoyi.wechat.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 企微客户导入 —— 接真实 externalcontact API
 */
@Slf4j
@RestController
@RequestMapping("/wechat/customer")
@RequiredArgsConstructor
public class WechatCustomerController {

    private final WechatService wechatService;
    private final JdbcTemplate jdbc;

    /**
     * 从企微拉取客户列表（真实API）
     * 流程：获取成员列表 → 逐个获取外部联系人 → 存wechat_customer表
     */
    @GetMapping("/fetch")
    public R<String> fetch() {
        try {
            String accessToken = wechatService.getAccessToken();
            if (accessToken == null) return R.fail("未配置企微或连接失败");

            // 1. 获取部门成员（需要先用通讯录API，这里简化：直接调客户列表）
            // 实际中需先调 /cgi-bin/department/simplelist → /cgi-bin/user/simplelist
            // 再对每个成员调 externalcontact/list

            int count = 0;
            // TODO: 替换为真实成员列表
            String[] userIds = {"admin"}; // 示例：从通讯录获取的实际成员ID
            for (String uid : userIds) {
                String listJson = wechatService.getExternalContactList(uid);
                if (listJson.contains("\"external_userid\"")) {
                    // 解析 external_userid 列表
                    String[] ids = extractExternalUserIds(listJson);
                    for (String eid : ids) {
                        String detail = wechatService.getExternalContactDetail(eid);
                        if (!detail.contains("\"errcode\"")) {
                            saveOrUpdateCustomer(detail);
                            count++;
                        }
                    }
                }
            }
            return R.ok("已拉取 " + count + " 个客户");
        } catch (Exception e) {
            log.error("拉取客户失败", e);
            return R.fail("拉取失败: " + e.getMessage());
        }
    }

    /**
     * 客户列表
     */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(jdbc.queryForList(
            "SELECT c.*, m.merchant_name AS erpName FROM wechat_customer c " +
            "LEFT JOIN basic_merchant m ON c.erp_merchant_id=m.id ORDER BY c.create_time DESC"));
    }

    /**
     * 同步选中客户到 ERP 往来单位
     */
    @PostMapping("/sync")
    public R<String> sync(@RequestBody List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM wechat_customer WHERE id=?", id);
            if (rows.isEmpty()) continue;
            Map<String, Object> c = rows.get(0);
            String merchantNo = "WX" + c.get("external_userid").toString()
                .substring(0, Math.min(12, c.get("external_userid").toString().length()));
            jdbc.update(
                "INSERT INTO basic_merchant(merchant_no,merchant_name,merchant_type_customer," +
                "mobile,email,address,contact_person,remark,create_time) " +
                "VALUES(?,?,1,?,?,?,?,?,NOW())",
                merchantNo, c.get("name"), c.get("mobile"),
                c.get("email"), c.get("address"), c.get("name"), c.get("corp_name"));
            Long merchantId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("UPDATE wechat_customer SET erp_merchant_id=?,synced='1' WHERE id=?", merchantId, id);
            count++;
        }
        return R.ok("已同步 " + count + " 个客户到 ERP");
    }

    private String[] extractExternalUserIds(String json) {
        List<String> ids = new ArrayList<>();
        int idx = 0;
        while ((idx = json.indexOf("\"external_userid\":\"", idx)) >= 0) {
            idx += 19;
            int end = json.indexOf("\"", idx);
            if (end < 0) break;
            ids.add(json.substring(idx, end));
            idx = end;
        }
        return ids.toArray(new String[0]);
    }

    private void saveOrUpdateCustomer(String detailJson) {
        String eid = extractValue(detailJson, "external_userid");
        String name = extractValue(detailJson, "name");
        String type = extractNestedValue(detailJson, "external_contact", "type");
        String corp = extractNestedValue(detailJson, "external_contact", "corp_name");
        String mobile = extractNestedValue(detailJson, "external_contact", "mobile");
        String email = extractNestedValue(detailJson, "external_contact", "email");
        String address = extractNestedValue(detailJson, "external_contact", "address");

        jdbc.update(
            "INSERT INTO wechat_customer(external_userid,name,type,corp_name,mobile,email,address,add_time) " +
            "VALUES(?,?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE " +
            "name=VALUES(name),mobile=VALUES(mobile),email=VALUES(email)",
            eid, name != null ? name : "未知", type != null ? Integer.parseInt(type) : 1,
            corp, mobile, email, address);
    }

    private String extractValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\":\"");
        if (idx < 0) return null;
        idx += key.length() + 4;
        int end = json.indexOf("\"", idx);
        return end > idx ? json.substring(idx, end) : null;
    }

    private String extractNestedValue(String json, String parent, String key) {
        int pIdx = json.indexOf("\"" + parent + "\":");
        if (pIdx < 0) return null;
        return extractValue(json.substring(pIdx), key);
    }
}
