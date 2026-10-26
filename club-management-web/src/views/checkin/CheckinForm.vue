<template>
  <div>
    <div class="page-header">
      <h2>提交打卡</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card v-loading="loading">
      <div v-if="!task">
        <el-empty description="该活动暂无打卡任务" />
      </div>
      <div v-else>
        <el-descriptions :column="2" border style="margin-bottom: 24px">
          <el-descriptions-item label="任务名称">{{ task.title }}</el-descriptions-item>
          <el-descriptions-item label="打卡类型">{{ taskTypeMap[task.type] || task.type }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ formatTime(task.deadline) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="task.status === 'ACTIVE' ? 'success' : 'info'">{{ task.status === 'ACTIVE' ? '进行中' : '已结束' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <div v-if="myRecord">
          <el-alert title="你已提交过打卡" type="success" :closable="false" show-icon style="margin-bottom: 16px" />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="打卡内容">{{ myRecord.content }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(myRecord.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="积分">+{{ myRecord.points || 0 }}</el-descriptions-item>
            <el-descriptions-item v-if="myRecord.aiSuggestion" label="AI 建议">{{ myRecord.aiSuggestion }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="打卡内容" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入打卡内容（心得、反思等）" />
          </el-form-item>
          <el-form-item label="照片链接" prop="photoUrl">
            <el-input v-model="form.photoUrl" placeholder="可选：粘贴照片 URL" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSubmit" :loading="submitting">提交打卡</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCheckinTask, getMyCheckinRecord, submitCheckinRecord } from '@/api/ai'
import { formatTime } from '@/utils/time'

const route = useRoute()
const activityId = route.params.activityId
const loading = ref(false)
const submitting = ref(false)
const task = ref(null)
const myRecord = ref(null)
const formRef = ref(null)

const taskTypeMap = {
  REFLECTION: '心得反思',
  PHOTO: '照片打卡',
  SUGGESTION: '建议反馈'
}

const form = reactive({
  content: '',
  photoUrl: ''
})

const rules = {
  content: [{ required: true, message: '请输入打卡内容', trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const taskRes = await getCheckinTask(activityId)
    task.value = taskRes.data
    try {
      const recRes = await getMyCheckinRecord(activityId)
      myRecord.value = recRes.data
    } catch (e) {
      myRecord.value = null
    }
  } catch (e) {
    task.value = null
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await submitCheckinRecord({
      activityId: Number(activityId),
      taskId: task.value.id,
      content: form.content,
      photoUrl: form.photoUrl || null
    })
    ElMessage.success('打卡提交成功')
    fetchData()
  } finally {
    submitting.value = false
  }
}

onMounted(fetchData)
</script>
