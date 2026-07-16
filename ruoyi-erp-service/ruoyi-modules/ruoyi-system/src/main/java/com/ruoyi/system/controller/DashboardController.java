package com.ruoyi.system.controller;

import com.ruoyi.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 数据大屏/首页 实时数据接口
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbc;

    /**
     * 首页概览:入库/出库/库存预警
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        // ---- 入库统计 ----
        // 待入库 = 采购单已审核但未完成收货
        Long pendingReceipt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM purchase_order WHERE checked_status=1 AND stock_status=0", Long.class);
        // 今日入库 = 今天完成的收货单
        Long todayReceipt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_receipt_doc WHERE DATE(create_time)=CURDATE()", Long.class);
        // 今日入库明细行数
        Long todayReceiptLines = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_receipt_doc_detail WHERE DATE(create_time)=CURDATE()", Long.class);

        Map<String, Object> inbound = new LinkedHashMap<>();
        inbound.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order", Long.class));
        inbound.put("pendingReceipt", pendingReceipt);
        inbound.put("supplierCount", jdbc.queryForObject("SELECT COUNT(*) FROM basic_merchant WHERE merchant_type_supplier=1", Long.class));
        inbound.put("monthPurchase", jdbc.queryForObject("SELECT IFNULL(SUM(actual_amount),0) FROM purchase_order WHERE doc_date >= DATE_FORMAT(CURDATE(),'%Y-%m-01')", Object.class));
        inbound.put("pendingFinish", pendingReceipt);
        inbound.put("todayReceipt", todayReceiptLines != null ? todayReceiptLines : 0L);
        data.put("inbound", inbound);

        // ---- 出库统计 ----
        Long pendingShipment = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sales_order WHERE checked_status=1 AND stock_status=0", Long.class);
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM sales_order", Long.class));
        outbound.put("customerCount", jdbc.queryForObject("SELECT COUNT(*) FROM basic_merchant WHERE merchant_type_customer=1", Long.class));
        outbound.put("monthSales", jdbc.queryForObject("SELECT IFNULL(SUM(actual_amount),0) FROM sales_order WHERE doc_date >= DATE_FORMAT(CURDATE(),'%Y-%m-01')", Object.class));
        outbound.put("pendingSort", jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE doc_date >= DATE_FORMAT(CURDATE(),'%Y-%m-01')", Long.class));
        outbound.put("pendingShip", pendingShipment);
        data.put("outbound", outbound);

        // ---- 其他 ----
        // 待审核单据(采购+销售)
        Long pendingCheck = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ((SELECT id FROM purchase_order WHERE checked_status=0) UNION ALL (SELECT id FROM sales_order WHERE checked_status=0)) t", Long.class);
        // 库存预警(SKU库存 < 10 的)
        Long lowStock = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_inventory WHERE qty > 0 AND qty < 10", Long.class);

        Map<String, Object> other = new LinkedHashMap<>();
        other.put("total", 0L);
        other.put("pendingCheck", pendingCheck);
        other.put("goodsVariety", jdbc.queryForObject("SELECT COUNT(*) FROM basic_goods", Long.class));
        other.put("todayArrival", jdbc.queryForObject(
            "SELECT COUNT(*) FROM purchase_order WHERE DATE(delivery_date)=CURDATE()", Long.class));
        other.put("lowStock", lowStock);
        data.put("other", other);

        // ---- 仓库货物占比 ----
        List<Map<String, Object>> warehousePie = jdbc.queryForList(
            "SELECT w.warehouse_name AS name, IFNULL(SUM(i.qty),0) AS value FROM basic_warehouse w " +
            "LEFT JOIN wms_inventory i ON i.warehouse_id=w.id GROUP BY w.id, w.warehouse_name HAVING value > 0");
        data.put("warehouseGoods", warehousePie);

        // ---- 销售额趋势(最近12个月,按月聚合) ----
        List<Map<String, Object>> monthlySales = jdbc.queryForList(
            "SELECT DATE_FORMAT(doc_date, '%Y-%m') AS month, SUM(actual_amount) AS amount FROM sales_order " +
            "WHERE checked_status=1 " +
            "GROUP BY DATE_FORMAT(doc_date, '%Y-%m') ORDER BY month DESC LIMIT 12");
        data.put("monthlySales", monthlySales);

        // ---- 今日出入库流水 ----
        List<Map<String, Object>> todayFlow = jdbc.queryForList(
            "SELECT '入库' AS type, d.create_time AS time, sku.sku_name AS item, dd.qty " +
            "FROM wms_receipt_doc_detail dd " +
            "JOIN wms_receipt_doc d ON d.id=dd.pid " +
            "JOIN basic_sku sku ON sku.id=dd.sku_id " +
            "WHERE DATE(d.create_time)=CURDATE() " +
            "UNION ALL " +
            "SELECT '出库', d.create_time, sku.sku_name, dd.qty " +
            "FROM wms_shipment_doc_detail dd " +
            "JOIN wms_shipment_doc d ON d.id=dd.pid " +
            "JOIN basic_sku sku ON sku.id=dd.sku_id " +
            "WHERE DATE(d.create_time)=CURDATE() " +
            "ORDER BY time DESC LIMIT 20");
        data.put("todayFlow", todayFlow);

        // ---- 库存总览 ----
        data.put("warehouseCount", jdbc.queryForObject("SELECT COUNT(*) FROM basic_warehouse", Long.class));
        data.put("goodsVariety", jdbc.queryForObject("SELECT COUNT(DISTINCT sku_id) FROM wms_inventory WHERE qty > 0", Long.class));
        data.put("totalQty", jdbc.queryForObject("SELECT IFNULL(SUM(qty),0) FROM wms_inventory", Long.class));

        // ---- 近7日趋势 ----
        data.put("sales7d", get7DayTrend("sales_order", "actual_amount"));
        data.put("purchase7d", get7DayTrend("purchase_order", "actual_amount"));
        data.put("movement7d", get7DayTrend("wms_movement_doc", "0")); // 调拨单无金额
        data.put("refund7d", get7DayTrend("sales_refund", "actual_amount"));

        return R.ok(data);
    }

    private List<Map<String, Object>> get7DayTrend(String table, String amountCol) {
        return jdbc.queryForList(
            "SELECT DATE(doc_date) AS day, COUNT(*) AS count, IFNULL(SUM(" + amountCol + "),0) AS amount " +
            "FROM " + table + " GROUP BY DATE(doc_date) ORDER BY day DESC LIMIT 7");
    }
}
