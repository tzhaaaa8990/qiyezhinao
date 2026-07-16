package com.ruoyi.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.ruoyi.ai.config.AiModelFactory;
import com.ruoyi.ai.tools.KnowledgeSearchTool;
import com.ruoyi.ai.tools.SalesAnalysisTool;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
        你是智辉科技企业智能体，一个 ERP 经营分析助手。今天是 %s。
        数据库为 MySQL，业务表（只读）：
        sales_order(销售订单: id, merchant_id 客户, total_amount 金额, create_time),
        sales_order_detail(销售明细: order_id, sku_id, quantity 数量, amount 金额),
        purchase_order(采购订单: id, merchant_id 供应商, total_amount, create_time),
        wms_inventory(库存: sku_id, warehouse_id, quantity),
        basic_goods(商品: id, goods_name), basic_sku(id, goods_id, sku_name),
        basic_warehouse(仓库: id, warehouse_name), basic_merchant(往来单位: id, merchant_name),
        financial_trans_history(资金流水: amount, trans_time)。
        回答经营问题时：先思考需要的 SQL，调用工具查询，再用简洁中文总结数据结论。
        金额单位默认为元。如果用户问公司制度、标准、流程等问题，优先搜索知识库。
        如果没有可用工具，直接告知用户"你没有经营分析权限"。
        """;

    private final AiModelFactory modelFactory;
    private final SalesAnalysisTool salesAnalysisTool;
    private final KnowledgeSearchTool knowledgeSearchTool;

    public AgentService(AiModelFactory modelFactory, SalesAnalysisTool salesAnalysisTool,
                        KnowledgeSearchTool knowledgeSearchTool) {
        this.modelFactory = modelFactory;
        this.salesAnalysisTool = salesAnalysisTool;
        this.knowledgeSearchTool = knowledgeSearchTool;
    }

    /**
     * 核心权限设计：按当前登录用户的 RuoYi 权限字符串过滤可挂载的工具集
     */
    private Object[] toolsForCurrentUser() {
        List<Object> tools = new ArrayList<>();
        if (StpUtil.hasPermission(SalesAnalysisTool.PERM)) {
            tools.add(salesAnalysisTool);
        }
        if (StpUtil.hasPermission(KnowledgeSearchTool.PERM)) {
            tools.add(knowledgeSearchTool);
        }
        return tools.toArray();
    }

    public Flux<String> chat(String message) {
        return modelFactory.createChatClient().prompt()
            .system(SYSTEM_PROMPT.formatted(LocalDate.now()))
            .user(message)
            .tools(toolsForCurrentUser())
            .stream()
            .content();
    }
}
