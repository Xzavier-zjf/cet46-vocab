<template>
  <section class="admin-page">
    <h1>用户管理</h1>

    <el-card class="card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="输入用户名或昵称搜索"
          clearable
          class="keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="id" label="用户ID" min-width="180" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              style="width: 100px"
              @change="(value) => changeRole(row, value)"
            >
              <el-option label="用户" value="USER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="llmProvider" label="模型来源" width="120" />
        <el-table-column prop="dailyTarget" label="日目标" width="100" />
        <el-table-column prop="createdAt" label="注册时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openResetPassword(row)">重置密码</el-button>
            <el-button size="small" type="danger" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next, sizes"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const keyword = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/users', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value?.trim() || undefined
      }
    })
    const data = res?.data || {}
    rows.value = Array.isArray(data.list) ? data.list : []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  page.value = 1
  await loadUsers()
}

const handlePageChange = async (newPage) => {
  page.value = newPage
  await loadUsers()
}

const handleSizeChange = async (newSize) => {
  size.value = newSize
  page.value = 1
  await loadUsers()
}

const changeRole = async (row, role) => {
  const prev = row.role
  if (role === prev) {
    return
  }
  try {
    await request.put(`/admin/users/${row.id}/role`, { role })
    row.role = role
    ElMessage.success('角色更新成功')
  } catch {
    row.role = prev
  }
}

const openResetPassword = async (row) => {
  const { value, action } = await ElMessageBox.prompt(`请输入 ${row.username} 的新密码`, '重置密码', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputType: 'password',
    inputPlaceholder: '至少 6 位',
    inputValidator: (val) => (val && val.trim().length >= 6 ? true : '密码至少 6 位')
  })
  if (action !== 'confirm') {
    return
  }
  await request.put(`/admin/users/${row.id}/password/reset`, {
    newPassword: value
  })
  ElMessage.success('密码重置成功')
}

const removeUser = async (row) => {
  await ElMessageBox.confirm(`确认删除用户 ${row.username} 吗？该操作不可恢复。`, '删除确认', {
    type: 'warning'
  })
  await request.delete(`/admin/users/${row.id}`)
  ElMessage.success('删除成功')
  await loadUsers()
}

onMounted(async () => {
  await loadUsers()
})
</script>

<style scoped>
.admin-page {
  padding: 20px;
}

.card {
  border-radius: 12px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.keyword {
  width: 300px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
