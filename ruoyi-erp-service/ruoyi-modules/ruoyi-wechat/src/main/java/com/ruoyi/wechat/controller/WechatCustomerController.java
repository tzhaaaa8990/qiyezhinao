package com.ruoyi.wechat.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 企微联系人导入：外部客户 + 内部员工
 */
@Slf4j
@RestController
@RequestMapping("/wechat/customer")
@RequiredArgsConstructor
public class WechatCustomerController {

    private final WechatService wechatService;
    private final JdbcTemplate jdbc;

    /**
     * 一键拉取：先拉外部客户，再拉内部成员，全部存 wechat_customer 表
     */
    @GetMapping("/fetch")
    public R<String> fetch() {
        String accessToken = wechatService.getAccessToken();
        if (accessToken == null) return R.fail("未配置企微或连接失败");

        List<String> userIds = wechatService.getAllUserIds();
        if (userIds.isEmpty()) return R.fail("未获取到企业成员，请检查通讯录权限");

        int extCount = 0, intCount = 0;
        log.info("拉取联系人：{} 个成员", userIds.size());

        for (String uid : userIds) {
            // 1. 拉该成员的客户（外部联系人）
            String listJson = wechatService.getExternalContactList(uid);
            if (listJson.contains("\"external_userid\"")) {
                for (String eid : extractValues(listJson, "external_userid")) {
                    String detail = wechatService.getExternalContactDetail(eid);
                    if (!detail.contains("\"errcode\"")) {
                        saveCustomerFromExternal(detail, uid);
                        extCount++;
                    }
                }
            }
            // 2. 拉该成员本身的详细信息（内部员工）
            String userJson = wechatService.getUserDetail(uid);
            if (!userJson.contains("\"errcode\"")) {
                saveEmployee(userJson);
                intCount++;
            }
        }
        return R.ok(String.format("已拉取：%d 个外部客户 + %d 个内部员工", extCount, intCount));
    }

    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(jdbc.queryForList(
            "SELECT c.*, m.merchant_name AS erpName FROM wechat_customer c " +
            "LEFT JOIN basic_merchant m ON c.erp_merchant_id=m.id ORDER BY c.type, c.create_time DESC"));
    }

    /** 同步选中到 ERP 往来单位 */
    @PostMapping("/sync")
    public R<String> sync(@RequestBody List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM wechat_customer WHERE id=?", id);
            if (rows.isEmpty()) continue;
            Map<String, Object> c = rows.get(0);
            String prefix = "1".equals(c.get("type").toString()) ? "WX" : "WXEMP";
            String merchantName = c.get("name").toString();
            if ("1".equals(c.get("type").toString())) {
                merchantName = merchantName + "（个人）";
            }
            String merchantNo = prefix + c.get("external_userid").toString().substring(0, Math.min(12, c.get("external_userid").toString().length()));
            jdbc.update(
                "INSERT INTO basic_merchant(merchant_no,merchant_name,merchant_type_customer," +
                "mobile,email,address,contact_person,remark,create_time) VALUES(?,?,1,?,?,?,?,?,NOW())",
                merchantNo, merchantName, c.get("mobile"),
                c.get("email"), c.get("address"), merchantName, c.get("corp_name"));
            Long merchantId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("UPDATE wechat_customer SET erp_merchant_id=?,synced='1' WHERE id=?", merchantId, id);
            count++;
        }
        return R.ok("已同步 " + count + " 个到 ERP");
    }

    // ── 存储逻辑 ──

    private void saveCustomerFromExternal(String detailJson, String addByUserId) {
        saveContact(
            extractValue(detailJson, "external_userid"),
            extractNestedValue(detailJson, "external_contact", "name"),
            1, // 外部客户
            extractNestedValue(detailJson, "external_contact", "corp_name"),
            extractNestedValue(detailJson, "external_contact", "mobile"),
            extractNestedValue(detailJson, "external_contact", "email"),
            extractNestedValue(detailJson, "external_contact", "address"),
            addByUserId
        );
    }

    private void saveEmployee(String userJson) {
        saveContact(
            extractValue(userJson, "userid"),
            extractValue(userJson, "name"),
            3, // 内部员工
            extractValue(userJson, "department"), // 存部门名
            extractValue(userJson, "mobile"),
            extractValue(userJson, "email"),
            extractNestedValue(userJson, "extattr", "address"),
            null
        );
    }

    private void saveContact(String externalId, String name, int type, String corp,
                              String mobile, String email, String address, String addBy) {
        if (name == null) name = "未知";
        jdbc.update(
            "INSERT INTO wechat_customer(external_userid,name,type,corp_name,mobile,email,address,add_time) " +
            "VALUES(?,?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE " +
            "name=VALUES(name),mobile=VALUES(mobile),email=VALUES(email),corp_name=VALUES(corp_name)",
            externalId, name, type, corp, mobile, email, address);
    }

    // ── JSON 工具 ──

    private String[] extractValues(String json, String key) {
        List<String> list = new ArrayList<>();
        String prefix = "\"" + key + "\":\"";
        int idx = 0;
        while ((idx = json.indexOf(prefix, idx)) >= 0) {
            idx += prefix.length();
            int end = json.indexOf("\"", idx);
            if (end > idx) list.add(json.substring(idx, end));
            idx = end;
        }
        return list.toArray(new String[0]);
    }

    private String extractValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\":\"");
        if (idx < 0) {
            // 嵌套: "key": {
            idx = json.indexOf("\"" + key + "\":");
            if (idx < 0) return null;
            idx += key.length() + 3;
            if (idx < json.length() && json.charAt(idx) == '\"') { idx++; int e = json.indexOf("\"", idx); return e > idx ? json.substring(idx, e) : null; }
            return null;
        }
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
