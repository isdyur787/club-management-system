<template>
  <div class="knowledge-view">
    <el-card>
      <template #header>
        <div class="header">
          <span>知识库管理</span>
          <el-space>
            <el-select v-model="clubId" placeholder="选择社团" style="width:150px" @change="fetchDocuments">
              <el-option v-for="club in clubList" :key="club.id" :value="club.id" :label="club.clubName" />
            </el-select>
            <el-button v-if="canUpload" type="primary" @click="showUploadDialog = true">上传文档</el-button>
          </el-space>
        </div>
      </template>

      <el-table :data="documents">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="docType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ typeMap[row.docType] || row.docType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ sourceTypeMap[row.sourceType] || row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="80" />
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showUploadDialog" title="上传知识库文档">
      <el-form :model="uploadForm">
        <el-form-item label="文档标题">
          <el-input v-model="uploadForm.title" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="uploadForm.docType">
            <el-option value="charter" label="社团章程" />
            <el-option value="recruitment" label="招新说明" />
            <el-option value="faq" label="FAQ" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".txt,.md,.docx"
            :on-change="handleFileChange"
          >
            <el-button>选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 txt/md/docx 格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeDocuments, uploadKnowledgeDocument, deleteKnowledgeDocument } from '@/api/ai'
import { formatTime } from '@/utils/time'
import { listClubs } from '@/api/club'
import { useUserStore } from '@/store'

const userStore = useUserStore()
const canUpload = userStore.roles.includes('CLUB_PRESIDENT') || userStore.roles.includes('ADMIN')

const clubId = ref(null)
const clubList = ref([])

const documents = ref([])
const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)

const uploadForm = ref({
  title: '',
  docType: 'charter'
})

const sourceTypeMap = {
  upload: '用户上传',
  system: '系统生成',
  manual: '手动录入'
}

const typeMap = {
  charter: '社团章程',
  recruitment: '招新说明',
  faq: 'FAQ',
  activity_report: '活动报告',
  checkin_compilation: '打卡汇编',
  upload: '用户上传',
  system: '系统生成'
}

const fetchDocuments = async () => {
  if (!clubId.value) return
  try {
    const res = await getKnowledgeDocuments({ clubId: clubId.value, pageNum: 1, pageSize: 50 })
    documents.value = res.data.list || []
  } catch (e) { /* ignore */ }
}

const fetchClubs = async () => {
  try {
    const res = await listClubs({ pageNum: 1, pageSize: 100 })
    clubList.value = res.data?.list || []
    if (clubList.value.length > 0 && !clubId.value) {
      clubId.value = clubList.value[0].id
      fetchDocuments()
    }
  } catch (e) { /* ignore */ }
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('docType', uploadForm.value.docType)
    formData.append('title', uploadForm.value.title)
    formData.append('clubId', clubId.value)
    await uploadKnowledgeDocument(formData)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    fetchDocuments()
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确认删除该文档？', '提示', { type: 'warning' })
  try {
    await deleteKnowledgeDocument(id)
    ElMessage.success('删除成功')
    fetchDocuments()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(fetchClubs)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
