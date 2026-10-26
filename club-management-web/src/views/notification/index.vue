<template>
  <div>
    <div class="page-header">
      <h2>通知中心</h2>
      <el-button @click="handleMarkAllRead">全部已读</el-button>
    </div>

    <div class="filter-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="类型">
          <el-select v-model="queryForm.type" placeholder="全部" clearable>
            <el-option label="系统通知" value="SYSTEM" />
            <el-option label="活动通知" value="ACTIVITY" />
            <el-option label="审批通知" value="APPROVAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.isRead" placeholder="全部" clearable>
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchNotifications">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="notificationList" stripe v-loading="loading">
        <el-table-column label="状态" width="60">
          <template #default="{ row }">
            <el-badge :is-dot="true" :hidden="row.isRead === 1" type="danger">
              <span style="padding-left: 8px">{{ row.isRead === 1 ? '' : '' }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="200">
          <template #default="{ row }">
            <span :style="{ fontWeight: row.isRead === 0 ? '600' : '400' }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ { SYSTEM: '系统', ACTIVITY: '活动', APPROVAL: '审批', CLUB: '社团' }[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" v-if="row.isRead === 0" @click="handleMarkRead(row.id)">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchNotifications"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { myNotifications, markAsRead, markAllAsRead } from '@/api/notification'
import { formatTime } from '@/utils/time'

const loading = ref(false)
const notificationList = ref([])
const total = ref(0)
const queryForm = reactive({ pageNum: 1, pageSize: 10, type: '', isRead: null })

const fetchNotifications = async () => {
  loading.value = true
  try {
    const res = await myNotifications(queryForm)
    notificationList.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const handleMarkRead = async (id) => {
  await markAsRead(id)
  ElMessage.success('已标记已读')
  fetchNotifications()
}

const searchNotifications = () => {
  queryForm.pageNum = 1
  fetchNotifications()
}

const handleMarkAllRead = async () => {
  await markAllAsRead()
  ElMessage.success('已全部标记已读')
  fetchNotifications()
}

onMounted(fetchNotifications)
</script>
