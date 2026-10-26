<template>
  <div class="dashboard-container">
    <div class="stat-cards">
      <StatCard :value="overview.totalUsers || 0" label="总用户数" color="blue">
        <template #icon><User /></template>
      </StatCard>
      <StatCard :value="overview.totalClubs || 0" label="社团数量" color="green">
        <template #icon><OfficeBuilding /></template>
      </StatCard>
      <StatCard :value="overview.totalActivities || 0" label="活动总数" color="orange">
        <template #icon><Calendar /></template>
      </StatCard>
      <StatCard :value="overview.totalParticipants || 0" label="总参与人次" color="red">
        <template #icon><UserFilled /></template>
      </StatCard>
    </div>

    <el-card>
      <template #header>
        <span style="font-weight: 600">最近活动</span>
      </template>
      <el-table :data="overview.recentActivities || []" stripe>
        <el-table-column prop="title" label="活动名称" />
        <el-table-column prop="clubName" label="所属社团" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getOverview } from '@/api/statistics'
import { formatTime } from '@/utils/time'
import { User, OfficeBuilding, Calendar, UserFilled } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import { getStatusType, getStatusText } from '@/utils/statusMap'

const overview = ref({})

onMounted(async () => {
  try {
    const res = await getOverview()
    overview.value = res.data
  } catch (e) {
    // 忽略
  }
})
</script>
