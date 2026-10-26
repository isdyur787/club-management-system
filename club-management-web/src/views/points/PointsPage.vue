<template>
  <div class="points-page">
    <div class="page-header">
      <h2>积分中心</h2>
      <el-select v-model="clubId" placeholder="选择社团" style="width:180px" @change="onClubChange">
        <el-option v-for="club in clubList" :key="club.id" :value="club.id" :label="club.clubName" />
      </el-select>
    </div>

    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="8">
        <el-card>
          <el-statistic title="我的总积分" :value="summary.totalPoints" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <el-statistic title="我的排名" :value="summary.rank" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <el-statistic title="社团成员数" :value="summary.memberCount" />
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-bottom: 20px">
      <template #header>积分流水</template>
      <el-table :data="logs">
        <el-table-column prop="description" label="说明" />
        <el-table-column prop="points" label="积分" width="80">
          <template #default="{ row }">
            <span :style="{ color: row.points > 0 ? '#67c23a' : '#f56c6c' }">
              {{ row.points > 0 ? '+' : '' }}{{ row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="类型" width="150">
          <template #default="{ row }">
            <el-tag size="small">{{ reasonMap[row.reason] || row.reason }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
      <el-pagination v-model:current-page="pageNum" :total="total"
                     @current-change="fetchLogs" style="margin-top: 10px" />
    </el-card>

    <el-card>
      <template #header>社团积分排行榜</template>
      <el-table :data="rankingList">
        <el-table-column type="index" label="排名" width="60" />
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="totalPoints" label="总积分" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyPointsLogs, getPointsRanking, getPointsSummary } from '@/api/ai'
import { listClubs } from '@/api/club'

const clubId = ref(null)
const clubList = ref([])

const summary = ref({ totalPoints: 0, rank: 0, memberCount: 0 })
const logs = ref([])
const rankingList = ref([])
const pageNum = ref(1)
const total = ref(0)

const reasonMap = {
  'SIGN_IN': '签到',
  'SIGN_OUT': '签退',
  'CHECKIN_REFLECTION': '打卡心得',
  'CHECKIN_PHOTO': '打卡照片',
  'CHECKIN_SUGGESTION': '打卡建议',
  'ADOPTED_SUGGESTION': '建议采纳'
}

const fetchClubs = async () => {
  try {
    const res = await listClubs({ pageNum: 1, pageSize: 100 })
    clubList.value = res.data?.list || []
    if (clubList.value.length > 0 && !clubId.value) {
      clubId.value = clubList.value[0].id
    }
  } catch (e) { /* ignore */ }
}

const onClubChange = () => {
  fetchSummary()
  fetchLogs()
  fetchRanking()
}

const fetchSummary = async () => {
  try {
    const res = await getPointsSummary(clubId.value)
    summary.value = res.data
  } catch (e) { /* ignore */ }
}

const fetchLogs = async () => {
  try {
    const res = await getMyPointsLogs(clubId.value, { pageNum: pageNum.value, pageSize: 10 })
    logs.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) { /* ignore */ }
}

const fetchRanking = async () => {
  try {
    const res = await getPointsRanking(clubId.value, 20)
    rankingList.value = res.data || []
  } catch (e) { /* ignore */ }
}

onMounted(async () => {
  await fetchClubs()
  if (clubId.value) {
    fetchSummary()
    fetchLogs()
    fetchRanking()
  }
})
</script>
