<template>
  <div class="statistics-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2>数据统计</h2>
        <p class="page-description">查看社团运营概况与年度考核表现</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="fetchStatistics">刷新数据</el-button>
    </div>

    <div class="statistics-cards">
      <StatCard :value="overview.totalUsers || 0" label="总用户数" color="blue">
        <template #icon><User /></template>
      </StatCard>
      <StatCard :value="overview.totalClubs || 0" label="社团数量" color="green">
        <template #icon><OfficeBuilding /></template>
      </StatCard>
      <StatCard :value="overview.totalActivities || 0" label="活动总数" color="orange">
        <template #icon><Calendar /></template>
      </StatCard>
      <StatCard :value="overview.totalParticipants || 0" label="累计参与人次" color="purple">
        <template #icon><UserFilled /></template>
      </StatCard>
    </div>

    <div class="statistics-panels">
      <el-card shadow="never" class="ranking-card">
        <template #header>
          <div class="panel-title"><el-icon><DataAnalysis /></el-icon>社团评分对比</div>
          <p class="panel-description">{{ currentYear }} 年得分前六名，条形长度以最高得分为基准</p>
        </template>
        <el-empty v-if="!scores.length" description="暂无评分数据" />
        <div v-else class="ranking-chart" aria-label="社团考核得分对比">
          <div v-for="item in topScores" :key="item.clubId" class="ranking-item">
            <div class="ranking-label">
              <span :class="['rank-badge', `rank-${item.rank}`]">{{ item.rank }}</span>
              <span class="club-name">{{ item.clubName }}</span>
              <strong>{{ formatScore(item.totalScore) }} <small>分</small></strong>
            </div>
            <el-progress :percentage="scorePercentage(item.totalScore)" :show-text="false"
                         :stroke-width="12" :color="chartColors[(item.rank - 1) % chartColors.length]" />
          </div>
        </div>
        <div v-if="scores.length" class="score-summary">
          <el-icon><Trophy /></el-icon>
          <span>平均考核分 <strong>{{ averageScore }}</strong></span>
          <span class="club-count">共 {{ scores.length }} 个社团</span>
        </div>
      </el-card>

      <el-card shadow="never" class="score-table-card">
        <template #header>
          <div class="panel-title"><el-icon><Medal /></el-icon>社团考核评分</div>
          <p class="panel-description">保留各项评分明细，按总分从高到低排列</p>
        </template>
        <el-table :data="scores" stripe max-height="460" empty-text="暂无评分数据">
          <el-table-column label="排名" width="72">
            <template #default="{ row }">
              <span :class="['rank-badge', `rank-${row.rank}`]">
                <el-icon v-if="row.rank <= 3"><Medal /></el-icon>{{ row.rank }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="clubName" label="社团名称" min-width="125" show-overflow-tooltip />
          <el-table-column label="活动频次分" min-width="115">
            <template #default="{ row }">
              <span class="score-cell"><el-icon><TrendCharts /></el-icon>{{ formatScore(row.activityScore) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="参与率分" min-width="105">
            <template #default="{ row }">
              <span class="score-cell"><el-icon><UserFilled /></el-icon>{{ formatScore(row.participationScore) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="总分" min-width="85">
            <template #default="{ row }"><strong class="total-score">{{ formatScore(row.totalScore) }}</strong></template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { User, OfficeBuilding, Calendar, UserFilled, DataAnalysis, Trophy, Medal, TrendCharts, Refresh } from '@element-plus/icons-vue'
import { getClubScores, getOverview } from '@/api/statistics'
import StatCard from '@/components/StatCard.vue'

const loading = ref(false)
const scores = ref([])
const overview = ref({})
const currentYear = new Date().getFullYear()
const chartColors = ['#e6a23c', '#409eff', '#67c23a', '#9093e8', '#36b6c5', '#8fa4b7']
const topScores = computed(() => scores.value.slice(0, 6))
const highestScore = computed(() => Math.max(0, ...scores.value.map(item => Number(item.totalScore) || 0)))
const averageScore = computed(() => scores.value.length
  ? formatScore(scores.value.reduce((sum, item) => sum + Number(item.totalScore || 0), 0) / scores.value.length)
  : '0.0')

const formatScore = value => Number(value || 0).toFixed(1)
const scorePercentage = value => highestScore.value > 0
  ? Math.max(0, Math.min(100, Number(value || 0) / highestScore.value * 100))
  : 0

const fetchStatistics = async () => {
  loading.value = true
  try {
    const [scoreRes, overviewRes] = await Promise.all([getClubScores({ year: currentYear }), getOverview()])
    scores.value = scoreRes.data || []
    overview.value = overviewRes.data || {}
  } catch (e) {
    // 请求拦截器已显示错误，保留上一次成功加载的数据。
  } finally {
    loading.value = false
  }
}

onMounted(fetchStatistics)
</script>

<style scoped>
.page-description, .panel-description {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.statistics-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}
.statistics-panels {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: 20px;
  align-items: start;
}
.ranking-card, .score-table-card {
  min-width: 0;
  border: 0;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text-primary);
}
.panel-title .el-icon {
  color: var(--primary-color);
  font-size: 20px;
}
.ranking-chart {
  display: grid;
  gap: 22px;
}
.ranking-label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 14px;
}
.club-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.ranking-label strong {
  white-space: nowrap;
  color: var(--text-primary);
}
.ranking-label small {
  color: var(--text-secondary);
  font-weight: 400;
}
.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 26px;
  height: 26px;
  padding: 0 4px;
  border-radius: 7px;
  background: #f1f4f8;
  color: #718096;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.rank-1 {
  color: #b7791f;
  background: #fff3d6;
}
.rank-2 {
  color: #547694;
  background: #eaf1f8;
}
.rank-3 {
  color: #b57042;
  background: #fff0e6;
}
.score-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  padding: 14px 12px;
  border-radius: 8px;
  background: #f5f8fc;
  font-size: 13px;
  color: var(--text-secondary);
}
.score-summary > .el-icon {
  color: #e6a23c;
  font-size: 18px;
}
.score-summary strong {
  color: var(--primary-color);
  font-size: 18px;
  margin-left: 4px;
}
.club-count {
  margin-left: auto;
}
.score-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.score-cell .el-icon {
  color: #94a3b8;
}
.total-score {
  color: var(--primary-color);
}
@media (max-width: 1280px) {
  .statistics-panels {
    grid-template-columns: minmax(0, 1fr);
  }
  .statistics-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .statistics-cards {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
  .page-header {
    align-items: flex-start;
    gap: 12px;
  }
  .page-header .el-button {
    flex-shrink: 0;
  }
}
</style>
