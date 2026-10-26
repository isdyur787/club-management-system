<template>
  <div>
    <div class="page-header">
      <h2>资源管理</h2>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="场地管理" name="venues">
        <div class="table-card">
          <el-table :data="venueList" stripe v-loading="venueLoading">
            <el-table-column prop="venueName" label="场地名称" />
            <el-table-column prop="location" label="位置" />
            <el-table-column prop="capacity" label="容纳人数" width="100" />
            <el-table-column prop="facilities" label="设施" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '可用' : '不可用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="设备管理" name="equipment">
        <div class="table-card">
          <el-table :data="equipmentList" stripe v-loading="equipmentLoading">
            <el-table-column prop="equipmentName" label="设备名称" />
            <el-table-column prop="equipmentType" label="类型" />
            <el-table-column label="数量" width="120">
              <template #default="{ row }">{{ row.availableCount }}/{{ row.totalCount }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '可用' : '不可用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { listVenues, listEquipment } from '@/api/resource'

const activeTab = ref('venues')
const venueLoading = ref(false)
const equipmentLoading = ref(false)
const venueList = ref([])
const equipmentList = ref([])

const fetchVenues = async () => {
  venueLoading.value = true
  try {
    const res = await listVenues({ pageNum: 1, pageSize: 50 })
    venueList.value = res.data.list
  } finally {
    venueLoading.value = false
  }
}

const fetchEquipment = async () => {
  equipmentLoading.value = true
  try {
    const res = await listEquipment({ pageNum: 1, pageSize: 50 })
    equipmentList.value = res.data.list
  } finally {
    equipmentLoading.value = false
  }
}

watch(activeTab, (val) => {
  if (val === 'venues') fetchVenues()
  else if (val === 'equipment') fetchEquipment()
})

onMounted(fetchVenues)
</script>
