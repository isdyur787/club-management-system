<template>
  <div>
    <div class="page-header">
      <h2>汇编文档</h2>
      <el-space>
        <el-button type="primary" @click="handleGenerate" :loading="generating">生成汇编</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </el-space>
    </div>

    <el-card v-loading="loading">
      <div v-if="compilation">
        <el-descriptions :column="2" border style="margin-bottom: 20px">
          <el-descriptions-item label="标题">{{ compilation.title }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ formatTime(compilation.createdAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <div class="compilation-content" v-html="formatContent(compilation.content)"></div>
      </div>
      <el-empty v-else :description="'暂无汇编文档，请点击【生成汇编】按钮'" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCompilation, generateCompilation } from '@/api/ai'
import { formatTime } from '@/utils/time'

const route = useRoute()
const router = useRouter()
const activityId = route.params.activityId
const loading = ref(false)
const generating = ref(false)
const compilation = ref(null)

/**
 * 将纯文本内容安全渲染为 HTML
 * 先转义 HTML 实体防止 XSS，再将换行符转为 <br>
 */
const escapeHtml = (str) => {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

const formatContent = (content) => {
  if (!content) return ''
  return escapeHtml(content).replace(/\n/g, '<br>')
}

const fetchCompilation = async () => {
  if (!activityId) {
    ElMessage.warning('缺少活动参数')
    router.back()
    return
  }
  loading.value = true
  try {
    const res = await getCompilation(activityId)
    compilation.value = res.data
  } catch (e) {
    if (e.response?.status !== 404) {
      ElMessage.error('加载汇编失败')
    }
    compilation.value = null
  } finally {
    loading.value = false
  }
}

const handleGenerate = async () => {
  try {
    await ElMessageBox.confirm('生成汇编将基于当前打卡记录，是否继续？', '提示')
  } catch {
    return // 用户取消，直接返回
  }
  generating.value = true
  try {
    const res = await generateCompilation(activityId)
    compilation.value = res.data
    ElMessage.success('汇编生成成功')
  } catch (e) {
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

onMounted(fetchCompilation)
</script>

<style scoped>
.compilation-content {
  line-height: 1.8;
  color: #303133;
  font-size: 14px;
}
</style>
