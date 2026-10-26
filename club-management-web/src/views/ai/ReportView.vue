<template>
  <div>
    <div class="page-header">
      <h2>成果报告</h2>
      <el-space>
        <el-button type="primary" @click="handleGenerate" :loading="generating">生成报告</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </el-space>
    </div>

    <el-card v-loading="loading">
      <div v-if="report">
        <el-descriptions :column="2" border style="margin-bottom: 20px">
          <el-descriptions-item label="报告标题">{{ report.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType">{{ statusText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生成时间" :span="2">{{ formatTime(report.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">报告内容</el-divider>
        <div class="report-content">{{ report.content }}</div>

        <template v-if="report.status === 'DRAFT'">
          <el-divider />
          <el-space>
            <el-button type="success" @click="handleConfirm">确认通过</el-button>
            <el-button type="danger" @click="showRejectDialog = true">打回修改</el-button>
          </el-space>
        </template>
      </div>
      <el-empty v-else :description="'暂无成果报告，请点击【生成报告】按钮'" />
    </el-card>

    <el-dialog v-model="showRejectDialog" title="打回原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入打回原因" />
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="primary" @click="handleReject">确认打回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateReport, getReport, confirmReport, rejectReport } from '@/api/ai'
import { formatTime } from '@/utils/time'

const route = useRoute()
const activityId = route.params.activityId
const loading = ref(false)
const generating = ref(false)
const report = ref(null)
const showRejectDialog = ref(false)
const rejectReason = ref('')

const statusMap = { DRAFT: '草稿', CONFIRMED: '已确认', REJECTED: '已打回' }
const statusTypeMap = { DRAFT: 'warning', CONFIRMED: 'success', REJECTED: 'danger' }
const statusText = computed(() => statusMap[report.value?.status] || '未知')
const statusType = computed(() => statusTypeMap[report.value?.status] || 'info')

const fetchReport = async () => {
  loading.value = true
  try {
    const res = await getReport(activityId)
    report.value = res.data
  } catch (e) {
    report.value = null
  } finally { loading.value = false }
}

const handleGenerate = async () => {
  await ElMessageBox.confirm('将基于活动数据和打卡记录生成成果报告，是否继续？', '提示')
  generating.value = true
  try {
    const res = await generateReport({ activityId: Number(activityId) })
    report.value = res.data
    ElMessage.success('报告生成成功')
  } catch (e) {
    ElMessage.error('生成失败，请稍后重试')
  } finally { generating.value = false }
}

const handleConfirm = async () => {
  await ElMessageBox.confirm('确认通过后报告将定稿，是否继续？', '提示')
  try {
    await confirmReport(activityId)
    ElMessage.success('已确认')
    fetchReport()
  } catch (e) { ElMessage.error('操作失败') }
}

const handleReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请输入打回原因')
    return
  }
  try {
    await rejectReport(activityId, { reason: rejectReason.value })
    showRejectDialog.value = false
    rejectReason.value = ''
    ElMessage.success('已打回')
    fetchReport()
  } catch (e) { ElMessage.error('操作失败') }
}

onMounted(fetchReport)
</script>

<style scoped>
.report-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
  font-size: 14px;
  background: #f5f7fa;
  padding: 20px;
  border-radius: 6px;
}
</style>
