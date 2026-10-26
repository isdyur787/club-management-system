<template>
  <div>
    <div class="page-header">
      <h2>我的报名</h2>
    </div>

    <div class="table-card">
      <el-table :data="registrationList" stripe v-loading="loading">
        <el-table-column prop="activityTitle" label="活动名称" />
        <el-table-column prop="clubName" label="所属社团" />
        <el-table-column prop="location" label="地点" />
        <el-table-column label="开始时间">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'primary'">
              {{ { 0: '已取消', 1: '已报名', 2: '审核通过', 3: '审核驳回' }[row.status] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间">
          <template #default="{ row }">{{ formatTime(row.registeredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" v-if="row.status === 1" @click="handleCancel(row.activityId)">取消报名</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchRegistrations"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myRegistrations, cancelRegistration } from '@/api/registration'
import { formatTime } from '@/utils/time'

const loading = ref(false)
const registrationList = ref([])
const total = ref(0)

const queryForm = reactive({ pageNum: 1, pageSize: 10 })

const fetchRegistrations = async () => {
  loading.value = true
  try {
    const res = await myRegistrations(queryForm)
    registrationList.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const handleCancel = async (activityId) => {
  await ElMessageBox.confirm('确定取消报名吗？', '提示', { type: 'warning' })
  await cancelRegistration(activityId)
  ElMessage.success('已取消报名')
  fetchRegistrations()
}

onMounted(fetchRegistrations)
</script>
