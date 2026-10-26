<template>
  <div class="points-page">
    <div class="page-header">
      <div><h2>积分中心</h2><p class="page-description">查看个人积累、社团动态与成员排行</p></div>
      <div class="page-actions">
        <el-select v-model="clubId" aria-label="选择社团" placeholder="选择社团" @change="onClubChange">
          <el-option v-for="club in clubList" :key="club.id" :value="club.id" :label="club.clubName" />
        </el-select>
        <el-button :icon="Refresh" :loading="overviewLoading" :disabled="!clubId" @click="refresh">刷新</el-button>
      </div>
    </div>
    <el-empty v-if="!clubsLoading && !clubList.length" description="加入已审批的社团后，可查看积分与排行榜" />
    <template v-else>
      <div class="points-cards" v-loading="overviewLoading">
        <StatCard :value="summary.totalPoints" label="我的积分" color="blue"><template #icon><Coin /></template></StatCard>
        <StatCard :value="summary.rank > 0 ? `第 ${summary.rank} 名` : '未上榜'" label="我的社团排名" color="orange"><template #icon><Medal /></template></StatCard>
        <StatCard :value="summary.monthPoints" label="本月社团积分" color="green"><template #icon><Calendar /></template></StatCard>
        <StatCard :value="summary.clubTotalPoints" label="社团累计积分" color="purple"><template #icon><Trophy /></template></StatCard>
      </div>
      <div class="overview-panels" v-loading="overviewLoading">
        <el-card shadow="never" class="trend-card">
          <template #header>
            <div class="panel-heading">
              <div><h3><el-icon><TrendCharts /></el-icon>近 7 日积分变化</h3><p>按社团积分流水统计，包含获得与扣除</p></div>
              <div class="weekly-total"><strong>{{ signed(weeklyTotal) }}</strong><span>近 7 日合计</span></div>
            </div>
          </template>
          <div class="trend-chart" aria-label="近7日社团积分变化">
            <div v-for="day in summary.dailyPoints" :key="day.date" class="trend-column">
              <div class="bar-space">
                <div class="trend-bar" :class="{ negative: day.points < 0, zero: day.points === 0 }"
                     :style="{ height: `${barHeight(day.points)}%` }" :title="`${day.date}：${signed(day.points)} 分`"><span>{{ signed(day.points) }}</span></div>
              </div>
              <span class="day-label">{{ day.date.slice(5).replace('-', '/') }}</span>
            </div>
          </div>
          <div class="chart-caption">柱高表示变动幅度，蓝色为增加，红色为扣除</div>
        </el-card>
        <el-card shadow="never" class="sources-card">
          <template #header><h3><el-icon><PieChart /></el-icon>积分来源</h3><p>按累计积分变动幅度比较</p></template>
          <el-empty v-if="!summary.sourcePoints.length" description="暂无积分来源" :image-size="60" />
          <div v-for="source in summary.sourcePoints" :key="source.reason" class="source-row">
            <div class="source-meta">
              <span class="reason-symbol" :class="reasonInfo(source.reason).color"><el-icon><component :is="reasonInfo(source.reason).icon" /></el-icon></span>
              <span>{{ reasonInfo(source.reason).label }}</span><span class="entry-count">{{ source.entryCount }} 笔</span>
              <strong :class="{ deduction: source.points < 0 }">{{ signed(source.points) }}</strong>
            </div>
            <el-progress :percentage="sourcePercentage(source.points)" :show-text="false" :stroke-width="5" :color="source.points < 0 ? '#f56c6c' : '#8bbcf8'" />
          </div>
        </el-card>
      </div>
      <div class="detail-panels">
        <el-card shadow="never" class="logs-card">
          <template #header>
            <div class="panel-heading">
              <h3><el-icon><Tickets /></el-icon>积分流水</h3>
              <el-radio-group v-model="flowTab" size="small" @change="onFlowChange">
                <el-radio-button value="mine">我的记录</el-radio-button><el-radio-button value="club">社团动态</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="logs" v-loading="logsLoading" stripe>
            <el-table-column v-if="flowTab === 'club'" prop="realName" label="成员" width="90" />
            <el-table-column prop="description" label="说明" min-width="170" show-overflow-tooltip />
            <el-table-column label="积分" width="76" align="right"><template #default="{ row }"><strong :class="row.points > 0 ? 'points-gain' : 'deduction'">{{ signed(row.points) }}</strong></template></el-table-column>
            <el-table-column label="类型" width="104"><template #default="{ row }"><el-tag size="small" effect="plain" :type="row.points < 0 ? 'danger' : 'info'">{{ reasonInfo(row.reason).label }}</el-tag></template></el-table-column>
            <el-table-column label="时间" width="163"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
            <template #empty>
              <el-empty :description="flowTab === 'mine' ? '暂无个人积分，参与活动后将在这里记录' : '暂无社团积分流水'" :image-size="65">
                <el-button v-if="flowTab === 'mine'" text type="primary" @click="flowTab = 'club'; onFlowChange()">查看社团动态</el-button>
              </el-empty>
            </template>
          </el-table>
          <el-pagination v-if="total > 0" v-model:current-page="pageNum" :total="total" :page-size="10" layout="total, prev, pager, next" @current-change="fetchLogs" />
        </el-card>
        <el-card shadow="never" class="ranking-card" v-loading="overviewLoading">
          <template #header><h3><el-icon><Trophy /></el-icon>社团积分榜</h3><p>{{ summary.scoredMemberCount }} / {{ summary.memberCount }} 位成员已有积分记录</p></template>
          <el-empty v-if="!rankingList.length" description="暂无积分排名，期待第一位参与者" :image-size="65" />
          <div v-for="person in rankingList" :key="person.userId" class="ranking-row" :class="{ myself: person.userId === currentUserId }">
            <span class="rank-number" :class="`rank-${person.rank}`"><el-icon v-if="person.rank <= 3"><Medal /></el-icon><template v-else>{{ person.rank }}</template></span>
            <el-avatar :size="34" :src="person.avatar || undefined">{{ person.realName?.slice(0, 1) || '成员' }}</el-avatar>
            <div class="rank-person"><span>{{ person.realName }}</span><el-tag v-if="person.userId === currentUserId" size="small" effect="plain">我</el-tag></div>
            <strong class="rank-score">{{ person.totalPoints }}<small> 分</small></strong>
          </div>
        </el-card>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Coin, Medal, Calendar, Trophy, TrendCharts, PieChart, Tickets, Refresh, Location, EditPen, Camera, ChatDotRound, Select, SwitchButton, Warning } from '@element-plus/icons-vue'
import { getPointsClubs, getClubPointsLogs, getMyPointsLogs, getPointsRanking, getPointsSummary } from '@/api/ai'
import StatCard from '@/components/StatCard.vue'
import { useUserStore } from '@/store'
import { formatTime } from '@/utils/time'

const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.userId ?? userStore.userInfo?.id)
const clubId = ref(null)
const clubList = ref([])
const clubsLoading = ref(true)
const overviewLoading = ref(false)
const logsLoading = ref(false)
const flowTab = ref(userStore.roles.some(role => ['ADMIN', 'TEACHER'].includes(role)) ? 'club' : 'mine')
const emptySummary = () => ({ totalPoints: 0, rank: 0, memberCount: 0, clubTotalPoints: 0, monthPoints: 0, scoredMemberCount: 0, dailyPoints: [], sourcePoints: [] })
const summary = ref(emptySummary())
const logs = ref([])
const rankingList = ref([])
const pageNum = ref(1)
const total = ref(0)
let overviewVersion = 0
let logsVersion = 0

const reasonMap = {
  SIGN_IN: { label: '活动签到', color: 'blue', icon: Location },
  SIGN_OUT: { label: '活动签退', color: 'cyan', icon: SwitchButton },
  CHECKIN_REFLECTION: { label: '打卡心得', color: 'purple', icon: EditPen },
  CHECKIN_PHOTO: { label: '活动照片', color: 'orange', icon: Camera },
  CHECKIN_SUGGESTION: { label: '改进建议', color: 'green', icon: ChatDotRound },
  ADOPTED_SUGGESTION: { label: '建议采纳', color: 'orange', icon: Select },
  PENALTY: { label: '积分扣除', color: 'red', icon: Warning },
  VIOLATION: { label: '违规扣分', color: 'red', icon: Warning }
}
const reasonInfo = reason => reasonMap[reason] || { label: reason, color: 'blue', icon: Tickets }
const signed = value => value > 0 ? `+${value}` : String(value || 0)
const weeklyTotal = computed(() => summary.value.dailyPoints.reduce((sum, day) => sum + day.points, 0))
const maxDaily = computed(() => Math.max(1, ...summary.value.dailyPoints.map(day => Math.abs(day.points))))
const maxSource = computed(() => Math.max(1, ...summary.value.sourcePoints.map(source => Math.abs(source.points))))
const barHeight = points => Math.max(1.5, Math.abs(points) / maxDaily.value * 100)
const sourcePercentage = points => Math.round(Math.abs(points) / maxSource.value * 100)

const fetchOverview = async () => {
  if (!clubId.value) return
  const version = ++overviewVersion
  overviewLoading.value = true
  try {
    const [overview, ranking] = await Promise.all([getPointsSummary(clubId.value), getPointsRanking(clubId.value, 20)])
    if (version !== overviewVersion) return
    summary.value = { ...emptySummary(), ...overview.data }
    rankingList.value = ranking.data || []
  } catch {
    if (version === overviewVersion) { summary.value = emptySummary(); rankingList.value = [] }
  } finally { if (version === overviewVersion) overviewLoading.value = false }
}
const fetchLogs = async () => {
  if (!clubId.value) return
  const version = ++logsVersion
  logsLoading.value = true
  try {
    const request = flowTab.value === 'mine' ? getMyPointsLogs : getClubPointsLogs
    const response = await request(clubId.value, { pageNum: pageNum.value, pageSize: 10 })
    if (version !== logsVersion) return
    logs.value = response.data.list || []
    total.value = response.data.total || 0
  } catch {
    if (version === logsVersion) { logs.value = []; total.value = 0 }
  } finally { if (version === logsVersion) logsLoading.value = false }
}
const onFlowChange = () => { pageNum.value = 1; logs.value = []; total.value = 0; fetchLogs() }
const refresh = () => { fetchOverview(); fetchLogs() }
const onClubChange = () => {
  pageNum.value = 1
  summary.value = emptySummary()
  rankingList.value = []
  logs.value = []
  total.value = 0
  refresh()
}
onMounted(async () => {
  try {
    const response = await getPointsClubs()
    clubList.value = response.data || []
    if (clubList.value.length) { clubId.value = clubList.value[0].id; refresh() }
  } catch { clubList.value = [] }
  finally { clubsLoading.value = false }
})
</script>

<style scoped>
.page-description { margin: 7px 0 0; color: #909399; font-size: 13px; }
.page-actions { display: flex; gap: 12px; align-items: center; }
.page-actions .el-select { width: 180px; }
.points-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; margin-bottom: 20px; }
.overview-panels, .detail-panels { display: grid; grid-template-columns: minmax(0, 1.8fr) minmax(280px, 1fr); gap: 20px; margin-bottom: 20px; align-items: start; }
.overview-panels { align-items: stretch; }
.el-card { border-radius: 10px; border-color: #e9edf3; }
h3 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 15px; font-weight: 600; color: #303133; }
h3 .el-icon { color: var(--primary-color); font-size: 18px; }
.el-card p, .panel-heading p { font-size: 12px; color: #909399; margin: 7px 0 0; }
.panel-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.weekly-total { display: flex; flex-direction: column; align-items: end; gap: 4px; }
.weekly-total strong { font-size: 25px; color: var(--primary-color); }
.weekly-total span { font-size: 12px; color: #909399; }
.trend-chart { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 18px; height: 200px; padding-top: 23px; }
.trend-column { display: flex; flex-direction: column; align-items: center; min-width: 0; }
.bar-space { flex: 1; width: 100%; display: flex; align-items: end; justify-content: center; border-bottom: 1px solid #e8edf4; }
.trend-bar { position: relative; width: min(38px, 70%); border-radius: 6px 6px 0 0; background: linear-gradient(180deg, #82b9ff, #409eff); min-height: 3px; }
.trend-bar.negative { background: #f89898; }
.trend-bar.zero { background: #e9edf3; }
.trend-bar span { position: absolute; bottom: calc(100% + 7px); left: 50%; transform: translateX(-50%); color: #606266; font-size: 12px; white-space: nowrap; }
.day-label { font-size: 12px; color: #909399; margin-top: 12px; }
.chart-caption { font-size: 11px; color: #a8abb2; margin-top: 15px; }
.source-row { margin-bottom: 13px; }
.source-row:last-child { margin-bottom: 0; }
.source-meta { display: flex; align-items: center; gap: 7px; font-size: 12px; margin-bottom: 7px; color: #606266; }
.reason-symbol { display: inline-flex; align-items: center; justify-content: center; width: 23px; height: 23px; border-radius: 6px; }
.blue { background: #ecf5ff; color: #409eff; } .green { background: #f0f9eb; color: #67c23a; }
.purple { background: #f4effa; color: #9b59b6; } .orange { background: #fdf6ec; color: #e6a23c; }
.cyan { background: #e8f7fa; color: #00a1b8; } .red { background: #fef0f0; color: #f56c6c; }
.entry-count { color: #a8abb2; font-size: 11px; }
.source-meta strong { margin-left: auto; color: #606266; }
.source-meta strong.deduction, .deduction { color: #f56c6c; }
.points-gain { color: #43a46c; }
.el-pagination { justify-content: flex-end; margin-top: 18px; }
.ranking-row { display: flex; align-items: center; gap: 10px; padding: 11px 4px; border-bottom: 1px solid #f0f2f5; }
.ranking-row:last-child { border-bottom: 0; }
.ranking-row.myself { background: #f2f8ff; border-radius: 7px; }
.ranking-row .el-avatar { background: #eaf2fc; color: #5b87b8; flex-shrink: 0; }
.rank-number { width: 25px; flex-shrink: 0; text-align: center; font-size: 13px; color: #a8abb2; }
.rank-number .el-icon { font-size: 24px; } .rank-1 { color: #d9a13c; } .rank-2 { color: #9caec2; } .rank-3 { color: #c59068; }
.rank-person { display: flex; align-items: center; gap: 6px; min-width: 0; font-size: 13px; color: #606266; }
.rank-person > span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rank-score { margin-left: auto; color: #303133; font-size: 16px; white-space: nowrap; }
.rank-score small { color: #a8abb2; font-size: 11px; font-weight: 400; }
.ranking-card :deep(.el-card__body) { max-height: 650px; overflow-y: auto; }
@media (max-width: 1280px) { .points-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } .detail-panels { grid-template-columns: 1fr; } }
@media (max-width: 1050px) { .overview-panels { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .page-header, .panel-heading { flex-wrap: wrap; } .page-actions { width: 100%; } .page-actions .el-select { flex: 1; } .points-cards { grid-template-columns: 1fr; } .trend-chart { gap: 8px; } }
</style>
