<template>
  <div>
    <div class="page-header">
      <h2>打卡任务管理</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card v-loading="loading" style="margin-bottom: 20px">
      <template #header><span style="font-weight: 600">任务信息</span></template>
      <div v-if="task">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务名称">{{ task.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ taskTypeMap[task.type] || task.type }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ formatTime(task.deadline) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="task.status === 'ACTIVE' ? 'success' : 'info'">{{ task.status === 'ACTIVE' ? '进行中' : '已结束' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else description="暂无任务" />
    </el-card>

    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center">
          <span style="font-weight: 600">打卡记录</span>
          <el-space>
            <el-button type="success" size="small" @click="handleSettle" :loading="settling">积分结算</el-button>
          </el-space>
        </div>
      </template>
      <el-table :data="records" stripe>
        <el-table-column prop="userName" label="用户" width="100" />
        <el-table-column prop="content" label="打卡内容" show-overflow-tooltip />
        <el-table-column prop="points" label="积分" width="80">
          <template #default="{ row }">
            <span :style="{ color: row.points > 0 ? '#67c23a' : '#909399' }">{{ row.points || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="AI 建议" width="180">
          <template #default="{ row }">
            <span v-if="row.aiSuggestion" style="color:#606266">{{ row.aiSuggestion.substring(0, 30) }}...</span>
            <span v-else style="color:#c0c4cc">无</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="success" v-if="row.aiSuggestion && !row.adopted" @click="handleAdopt(row.id)">
              采纳建议
            </el-button>
            <el-tag v-if="row.adopted" size="small" type="success">已采纳</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pageNum" :total="total" :page-size="20"
                     @current-change="fetchRecords" style="margin-top:16px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCheckinTask, getCheckinRecords, adoptSuggestion, settlePoints } from '@/api/ai'
import { formatTime } from '@/utils/time'

const route = useRoute()
const activityId = route.params.activityId
const loading = ref(false)
const settling = ref(false)
const task = ref(null)
const records = ref([])
const pageNum = ref(1)
const total = ref(0)

const taskTypeMap = {
  REFLECTION: '心得反思',
  PHOTO: '照片打卡',
  SUGGESTION: '建议反馈'
}

const fetchTask = async () => {
  try {
    const res = await getCheckinTask(activityId)
    task.value = res.data
  } catch (e) { task.value = null }
}

const fetchRecords = async () => {
  loading.value = true
  try {
    const res = await getCheckinRecords(activityId, { pageNum: pageNum.value, pageSize: 20 })
    records.value = res.data.list || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

const handleAdopt = async (id) => {
  await ElMessageBox.confirm('确认采纳该建议？采纳后将给予额外积分。', '提示')
  try {
    await adoptSuggestion(id)
    ElMessage.success('已采纳')
    fetchRecords()
  } catch (e) { ElMessage.error('操作失败') }
}

const handleSettle = async () => {
  await ElMessageBox.confirm('确认进行积分结算？结算后将为所有打卡记录发放积分。', '提示')
  settling.value = true
  try {
    await settlePoints(activityId)
    ElMessage.success('积分结算完成')
    fetchRecords()
  } finally { settling.value = false }
}

onMounted(() => { fetchTask(); fetchRecords() })
</script>
