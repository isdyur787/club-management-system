<template>
  <div>
    <div class="page-header">
      <h2>社团管理</h2>
      <el-button v-if="canCreate" type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 创建社团
      </el-button>
    </div>

    <div class="filter-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="社团名称">
          <el-input v-model="queryForm.clubName" placeholder="请输入社团名称" clearable />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="queryForm.category" placeholder="全部" clearable>
            <el-option label="学术" value="学术" />
            <el-option label="体育" value="体育" />
            <el-option label="文艺" value="文艺" />
            <el-option label="志愿" value="志愿" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryForm.approvalStatus" placeholder="全部" clearable>
            <el-option label="待审批" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchClubs">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="clubList" stripe v-loading="loading">
        <el-table-column prop="clubName" label="社团名称" />
        <el-table-column prop="category" label="类别" width="100" />
        <el-table-column prop="presidentName" label="社长" width="120" />
        <el-table-column prop="memberCount" label="成员数" width="100" />
        <el-table-column prop="approvalStatus" label="审批状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.approvalStatus === 1 ? 'success' : row.approvalStatus === 2 ? 'danger' : 'warning'">
              {{ row.approvalStatus === 1 ? '已通过' : row.approvalStatus === 2 ? '已驳回' : '待审批' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button link type="success" v-if="row.approvalStatus === 0" @click="handleApprove(row.id, true)">通过</el-button>
            <el-button link type="danger" v-if="row.approvalStatus === 0" @click="handleApprove(row.id, false)">驳回</el-button>
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
          @size-change="fetchClubs"
          @current-change="fetchClubs"
        />
      </div>
    </div>

   <el-dialog v-model="detailDialogVisible" title="社团详情" width="520px">
      <div v-loading="detailLoaded">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="社团名称" :span="2">{{ detailData.clubName }}</el-descriptions-item>
          <el-descriptions-item label="类别">{{ detailData.category }}</el-descriptions-item>
          <el-descriptions-item label="成员数">{{ detailData.memberCount }} 人</el-descriptions-item>
          <el-descriptions-item label="社长">{{ detailData.president?.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">
            <el-tag :type="getApprovalStatusType(detailData.approvalStatus)">{{ getApprovalStatusText(detailData.approvalStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ formatTime(detailData.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="简介" :span="2">{{ detailData.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="章程" :span="2">{{ detailData.charter || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="创建社团" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="社团名称" prop="clubName">
          <el-input v-model="createForm.clubName" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="createForm.category" style="width: 100%">
            <el-option label="学术" value="学术" />
            <el-option label="体育" value="体育" />
            <el-option label="文艺" value="文艺" />
            <el-option label="志愿" value="志愿" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="章程">
          <el-input v-model="createForm.charter" type="textarea" :rows="3" />
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
import { listClubs, createClub, approveClub, getClubDetail } from '@/api/club'
import { formatTime } from '@/utils/time'
import { useUserStore } from '@/store'

const router = useRouter()
const userStore = useUserStore()
const canCreate = userStore.roles.includes('ADMIN')
const loading = ref(false)
const clubList = ref([])
const total = ref(0)
const createDialogVisible = ref(false)
const detailLoaded = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  clubName: '',
  category: '',
  approvalStatus: null
})

const createForm = reactive({
  clubName: '',
  category: '',
  description: '',
  charter: ''
})

const createRules = {
  clubName: [{ required: true, message: '请输入社团名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }]
}

const fetchClubs = async () => {
  loading.value = true
  try {
    const res = await listClubs(queryForm)
    clubList.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  queryForm.clubName = ''
  queryForm.category = ''
  queryForm.approvalStatus = null
  queryForm.pageNum = 1
  fetchClubs()
}

const searchClubs = () => {
  queryForm.pageNum = 1
  fetchClubs()
}

const showCreateDialog = () => {
  createDialogVisible.value = true
}

const submitCreate = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  createLoading.value = true
  try {
    await createClub(createForm)
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    fetchClubs()
  } finally {
    createLoading.value = false
  }
}

const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref({})

const viewDetail = async (id) => {
  detailDialogVisible.value = true
  detailLoaded.value = false
  try {
    detailLoaded.value = true
    const res = await getClubDetail(id)
    detailData.value = res.data
  } finally {
    detailLoaded.value = false
  }
}

const getApprovalStatusText = (s) => s === 1 ? '已通过' : s === 2 ? '已驳回' : '待审批'
const getApprovalStatusType = (s) => s === 1 ? 'success' : s === 2 ? 'danger' : 'warning'

const handleApprove = async (id, approved) => {
  const action = approved ? '通过' : '驳回'
  await ElMessageBox.confirm(`确定要${action}该社团吗？`, '提示', { type: 'warning' })
  try {
    await approveClub(id, { approved, rejectReason: approved ? '' : '不通过' })
    ElMessage.success(`已${action}`)
    fetchClubs()
  } catch (e) {
    // 忽略
  }
}

onMounted(fetchClubs)
</script>
