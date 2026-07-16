<template>
  <div class="app-container home">
    <div class="station-top">
      <el-row :gutter="12" class="mt5">
        <el-col :span="6">
          <div class="top-item-box item-box-one" style="display: flex;">
            <div style="flex:2;height:100%;">
              <div>入库</div>
              <div style="text-align:center;margin-top:30px;"><span style="font-size:26px;font-weight:bold;">{{ overview.inbound.total }}</span>
              </div>
            </div>
            <div style="flex:3;display: flex;flex-direction:column;justify-content:space-evenly">
              <div>待入库：{{ overview.inbound.pendingReceipt }}</div>
              <div>供应商：{{ overview.inbound.supplierCount }}</div>
              <div>本月采购：¥{{ overview.inbound.monthPurchase }}</div>
              <div>待完成：{{ overview.inbound.pendingFinish }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="top-item-box item-box-two" style="display: flex;">
            <div style="flex:2;height:100%;">
              <div>出库</div>
              <div style="text-align:center;margin-top:30px;"><span style="font-size:26px;font-weight:bold;"
              >{{ overview.outbound.total }}</span></div>
            </div>
            <div style="flex:3;display: flex;flex-direction:column;justify-content:space-evenly">
              <div>待出库：{{ overview.outbound.pendingShip }}</div>
              <div>客户数：{{ overview.outbound.customerCount }}</div>
              <div>本月订单：{{ overview.outbound.pendingSort }}</div>
              <div>本月销售：¥{{ overview.outbound.monthSales }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="top-item-box item-box-three" style="display: flex;">
            <div style="flex:2;height:100%;">
              <div>其他</div>
              <div style="text-align:center;margin-top:30px;"><span style="font-size:26px;font-weight:bold;">{{ overview.other.pendingCheck }}</span></div>
            </div>
            <div style="flex:3;display: flex;flex-direction:column;justify-content:space-evenly">
              <div>待审核：{{ overview.other.pendingCheck }}</div>
              <div>商品种类：{{ overview.other.goodsVariety }}</div>
              <div>今日到货：{{ overview.other.todayArrival }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="top-item-box item-box-four" style="display: flex;">
            <div style="flex:2;height:100%;">
              <div>库存预警</div>
              <div style="text-align:center;margin-top:30px;"><span style="font-size:26px;font-weight:bold;"
              >{{ overview.other.lowStock }}</span></div>
            </div>
            <div style="flex:3;display: flex;flex-direction:column;justify-content:space-evenly">
              <div>仓库数：{{ overview.warehouseCount }}</div>
              <div>商品种类：{{ overview.goodsVariety }}</div>
              <div>库存总量：{{ overview.totalQty }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    <div class="station-middle">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-card class="box-card" shadow="never">
            <div class="card-title">仓库货物占比</div>
            <div style="height: calc(100% - 30px);">
              <StationPie height="100%" :pieData="overview.warehouseGoods.filter(g=>g.value>0)"></StationPie>
              <div></div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="18">
          <el-card class="box-card" shadow="never">
            <div style="display:flex;justify-content: space-between;align-items: center;">
              <div class="card-title">月度销售趋势（元）</div>
              <el-radio-group v-model="tabPosition" @change="dateChange">
                <!-- <el-radio-button label="day">当日</el-radio-button> -->
                <el-radio-button label="month">本月</el-radio-button>
                <el-radio-button label="year">今年</el-radio-button>
              </el-radio-group>
            </div>
            <div style="height: calc(100% - 30px);">
              <StationBar height="100%" :chartData="barChartData" :xName="barXName"/>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
    <div class="station-bottom">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-card class="box-card" shadow="never">
            <div class="card-title">近7日销售出库</div>
            <div style="height: calc(100% - 30px);">
              <StationLine height="100%" itemColor="#ee4368" yName="件" :chartData="lineDataOne"/>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card" shadow="never">
            <div class="card-title">近7日采购入库</div>
            <div style="height: calc(100% - 30px);">
              <StationLine height="100%" :chartData="lineDataTwo" yName="件" itemColor="#5470c6"/>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card" shadow="never">
            <div class="card-title">本月销售额</div>
            <div style="display:flex;align-items:center;justify-content:center;height:calc(100% - 30px)">
              <span style="font-size:36px;font-weight:bold;color:#ee4368">¥{{ overview.outbound.monthSales || 0 }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card" shadow="never">
            <div class="card-title">本月采购额</div>
            <div style="display:flex;align-items:center;justify-content:center;height:calc(100% - 30px)">
              <span style="font-size:36px;font-weight:bold;color:#5470c6">¥{{ overview.inbound.monthPurchase || 0 }}</span>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>
<script setup>
import StationPie from './components/StationPie.vue'
import StationLine from './components/StationLine.vue'
import StationBar from './components/StationBar.vue'
import { onMounted, ref } from 'vue'
import request from '@/utils/request'

const tabPosition = ref('month')
const barChartData = ref({ yData: [] })
const barXName = ref('日')
const overview = ref({
  inbound: {}, outbound: {}, other: { pendingCheck: 0, abnormal: 0, todayArrival: 0, lowStock: 0 },
  warehouseGoods: [], monthlySales: [], sales7d: [], purchase7d: [], movement7d: [], refund7d: []
})
const lineDataOne = ref({ xData: [], yData: [] })
const lineDataTwo = ref({ xData: [], yData: [] })
const lineDataThree = ref({ xData: [], yData: [] })
const lineDataFour = ref({ xData: [], yData: [] })

/** 7日趋势数据转图表格式 */
function fill7Day(refData, apiData) {
  const xData = [], yData = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(); d.setDate(d.getDate() - i)
    const day = d.toISOString().slice(5, 10)
    xData.push(day)
    const row = (apiData || []).find(r => r.day === d.toISOString().slice(0, 10))
    yData.push(row ? row.count || 0 : 0)
  }
  refData.xData = xData
  refData.yData = yData
}

function loadData() {
  request({ url: '/dashboard/overview', method: 'get' }).then(res => {
    overview.value = res.data
    // 仓库货物占比传给饼图(StationPie 读 warehouseGoods)
    // 生产入库趋势: 月度销售金额
    const salesByMonth = res.data.monthlySales || []
    barChartData.value = {
      xData: salesByMonth.map(r => r.month).reverse(),
      yData: salesByMonth.map(r => r.amount).reverse()
    }
    barXName.value = '日'
    // 近7日趋势
    fill7Day(lineDataOne, res.data.sales7d)
    fill7Day(lineDataTwo, res.data.purchase7d)
    fill7Day(lineDataThree, res.data.movement7d)
    fill7Day(lineDataFour, res.data.refund7d)
  })
}

// 时间类型选择: 月度-聚合
function dateChange(value) {
  if (value === 'month' || value === 'year') {
    const sales = overview.value.monthlySales || []
    const grouped = {}
    sales.forEach(r => {
      const key = value === 'year' ? r.month.slice(0, 4) : r.month
      grouped[key] = (grouped[key] || 0) + r.amount
    })
    barChartData.value = {
      xData: Object.keys(grouped),
      yData: Object.values(grouped)
    }
    barXName.value = value === 'year' ? '月' : '日'
  }
}

onMounted(() => { loadData() })
</script>


<style scoped>
.app-container {
  min-height: calc(100vh - 84px);
  padding: 12px 12px 0 12px;
}

.top-item-box {
  height: 160px;
  background: #fff;
  margin-bottom: 12px;
  border-radius: 12px;
  color: #fff;
  padding: 16px;
}

.item-box-one {
  background: linear-gradient(30deg, #1a94db, #4db1eb, #7acaf9);
  box-shadow: 0 4px 12px #8ed2fa;
}

.item-box-two {
  background: linear-gradient(30deg, #c7a327, #d5ba47, #e3cf65);
  box-shadow: 0 4px 12px #ece7cd;
}

.item-box-three {
  background: linear-gradient(30deg, #6365f7, #9177f1, #cd8ee9);
  box-shadow: 0 4px 12px #dcc9e6;
}

.item-box-four {
  background: linear-gradient(30deg, #ed3a60, #f1557a, #f67da0);
  box-shadow: 0 4px 12px #e7cfd6;
}

.box-card {
  height: 400px;
  margin-bottom: 12px;
  background-color: #fff;
  border-color: #ebe6f5;
}

.box-card >>> .el-card__body {
  height: 100%;
}

.card-title {
  font-weight: bold;
  height: 30px;
  display: flex;
  align-items: center;
}

.card-title::before {
  content: '';
  height: 70%;
  width: 5px;
  background: #3671e8;
  margin-right: 8px;
}

</style>
