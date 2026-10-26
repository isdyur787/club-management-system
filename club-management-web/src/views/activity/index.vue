<template>
  <div>
    <div class="page-header">
      <h2>活动管理</h2>
      <el-button v-if="canCreate" type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 创建活动
      </el-button>
    </div>

    <div class="filter-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词">
          <el-input v-model="queryForm.keyword" placeholder="搜索活动标题" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryForm.type" placeholder="全部" clearable>
            <el-option label="校内" value="CAMPUS" />
            <el-option label="校外" value="OFF_CAMPUS" />
            <el-option label="大型" value="LARGE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchActivities">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="activityList" stripe v-loading="loading">
        <el-table-column prop="title" label="活动名称" min-width="150" />
        <el-table-column prop="clubName" label="所属社团" width="120" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            {{ getTypeText(row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="150" />
        <el-table-column label="人数" width="100">
          <template #default="{ row }">{{ row.currentParticipants }}/{{ row.maxParticipants }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button link type="warning" v-if="row.status === 'DRAFT'" @click="handleSubmit(row.id)">提交审批</el-button>
            <el-button link type="success" v-if="row.status === 'APPROVED'" @click="handleStart(row.id)">开始</el-button>
            <el-button link type="danger" v-if="row.status === 'DRAFT'" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchActivities"
          @current-change="fetchActivities"
        />
      </div>
    </div>

    <el-dialog v-model="createDialogVisible" title="创建活动" width="600px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item label="活动描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="活动类型" prop="type">
          <el-select v-model="createForm.type" style="width: 100%">
            <el-option label="校内活动" value="CAMPUS" />
            <el-option label="校外活动" value="OFF_CAMPUS" />
            <el-option label="大型活动" value="LARGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动地点" prop="location">
          <el-input v-model="createForm.location" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="createForm.startTime" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="createForm.endTime" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item label="人数上限" prop="maxParticipants">
          <el-input-number v-model="createForm.maxParticipants" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="预算(元)" prop="budget">
          <el-input-number v-model="createForm.budget" :min="0" :max="100000" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="createLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listActivities, createActivity, deleteActivity, submitForApproval, startActivity } from '@/api/activity'
import { useUserStore } from '@/store'
import { getStatusType, getStatusText, getTypeText } from '@/utils/statusMap'
import { formatTime } from '@/utils/time'
import { ActivityStatus, ActivityStatusTextMap, ActivityType, ActivityTypeTextMap } from '@/constants/enums'

const router = useRouter()
const userStore = useUserStore()
const canCreate = userStore.roles.includes('CLUB_PRESIDENT') || userStore.roles.includes('ADMIN')
const loading = ref(false)
const activityList = ref([])
const total = ref(0)
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: '',
  type: ''
})

const createForm = reactive({
  title: '',
  description: '',
  type: 'CAMPUS',
  location: '',
  startTime: null,
  endTime: null,
  maxParticipants: 50,
  budget: 0,
  needRegistration: true,
  needApproval: true
})

const createRules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入活动描述', trigger: 'blur' }],
  type: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  location: [{ required: true, message: '请输入活动地点', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  maxParticipants: [{ required: true, message: '请输入人数上限', trigger: 'blur' }],
  budget: [{ required: true, message: '请输入预算', trigger: 'blur' }]
}

const fetchActivities = async () => {
  loading.value = true
  try {
    const res = await listActivities(queryForm)
    activityList.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const searchActivities = () => {
  queryForm.pageNum = 1
  fetchActivities()
}

const resetQuery = () => {
  queryForm.keyword = ''
  queryForm.status = ''
  queryForm.type = ''
  queryForm.pageNum = 1
  fetchActivities()
}

const showCreateDialog = () => { createDialogVisible.value = true }

const formatDateTime = (val) => {
  if (!val) return null
  const d = val instanceof Date ? val : new Date(val)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const submitCreate = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  createLoading.value = true
  try {
    await createActivity({
      ...createForm,
      startTime: formatDateTime(createForm.startTime),
      endTime: formatDateTime(createForm.endTime)
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    fetchActivities()
  } finally {
    createLoading.value = false
  }
}

const viewDetail = (id) => router.push(`/activities/${id}`)

const handleSubmit = async (id) => {
  await ElMessageBox.confirm('确定提交审批吗？', '提示')
  await submitForApproval(id)
  ElMessage.success('已提交审批')
  fetchActivities()
}

const handleStart = async (id) => {
  await ElMessageBox.confirm('确定开始活动吗？', '提示')
  await startActivity(id)
  ElMessage.success('活动已开始')
  fetchActivities()
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确定删除该活动吗？', '提示', { type: 'warning' })
  await deleteActivity(id)
  ElMessage.success('删除成功')
  fetchActivities()
}

onMounted(fetchActivities)
</script>
