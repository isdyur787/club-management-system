<template>
  <div>
    <div class="page-header">
      <h2>活动详情</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card v-loading="loading">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="活动标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="所属社团">{{ detail.clubName }}</el-descriptions-item>
        <el-descriptions-item label="活动类型">{{ getTypeText(detail.type) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detail.status)">{{ getStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="活动地点">{{ detail.location }}</el-descriptions-item>
        <el-descriptions-item label="人数">{{ detail.currentParticipants }}/{{ detail.maxParticipants }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(detail.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(detail.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="预算">¥{{ detail.budget }}</el-descriptions-item>
        <el-descriptions-item label="创建者">{{ detail.creator?.realName }}</el-descriptions-item>
        <el-descriptions-item label="活动描述" :span="2">{{ detail.description }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.summary" label="活动总结" :span="2">{{ detail.summary }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="detail.approvalRecords?.length" style="margin-top: 20px">
      <template #header><span style="font-weight: 600">审批记录</span></template>
      <el-table :data="detail.approvalRecords" stripe>
        <el-table-column prop="approverName" label="审批人" />
        <el-table-column prop="approvalLevel" label="审批级别">
          <template #default="{ row }">
            {{ { 1: '一级审批', 2: '二级审批', 3: '三级审批' }[row.approvalLevel] || `第${row.approvalLevel}级` }}
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果">
          <template #default="{ row }">
            <el-tag :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '通过' : '驳回' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="意见" />
        <el-table-column label="时间">
          <template #default="{ row }">{{ formatTime(row.approvedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px" v-if="detail.status === 'APPROVED'">
      <template #header><span style="font-weight: 600">签到管理</span></template>
      <el-button type="primary" @click="handleGenerateCode" :loading="codeLoading">生成签到码</el-button>
      <div v-if="signInCode" style="margin-top: 16px">
        <el-alert :title="`签到码：${signInCode}`" type="success" show-icon :closable="false" />
        <p style="margin-top: 8px; color: #909399">有效期至：{{ expireAt }}</p>
      </div>
    </el-card>

    <!-- 打卡与汇编管理 -->
    <el-card style="margin-top: 20px" v-if="detail.status === 'COMPLETED' || detail.status === 'APPROVED' || detail.status === 'IN_PROGRESS'">
      <template #header><span style="font-weight: 600">打卡与成果管理</span></template>
      <el-space wrap>
        <el-button type="primary" @click="$router.push(`/checkin/manage/${detail.id}`)">
          打卡任务管理
        </el-button>
        <el-button @click="$router.push(`/checkin/form/${detail.id}`)">
          提交打卡
        </el-button>
        <el-button @click="$router.push(`/ai/report/${detail.id}`)">
          成果报告
        </el-button>
        <el-button @click="$router.push(`/ai/compilation/${detail.id}`)">
          汇编文档
        </el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getActivityDetail } from '@/api/activity'
import { generateSignInCode } from '@/api/registration'
import { getStatusType, getStatusText, getTypeText } from '@/utils/statusMap'
import { formatTime } from '@/utils/time'

const route = useRoute()
const loading = ref(false)
const detail = ref({})
const codeLoading = ref(false)
const signInCode = ref('')
const expireAt = ref('')

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getActivityDetail(route.params.id)
    detail.value = res.data
  } finally {
    loading.value = false
  }
}

const handleGenerateCode = async () => {
  codeLoading.value = true
  try {
    const res = await generateSignInCode(route.params.id)
    signInCode.value = res.data.signInCode
    expireAt.value = res.data.expireAt
  } finally {
    codeLoading.value = false
  }
}

onMounted(fetchDetail)
</script>
