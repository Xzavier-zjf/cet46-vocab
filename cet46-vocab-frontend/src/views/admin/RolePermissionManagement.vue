<template>
  <section class="admin-page">
    <section class="admin-hero">
      <div>
        <h1>权限与角色管理</h1>
        <p>可视化配置 USER/ADMIN 的私有云端模型权限（创建、编辑、删除、启停）。</p>
      </div>
      <div class="hero-metrics">
        <article class="metric-item">
          <span>角色数量</span>
          <strong>{{ rows.length }}</strong>
        </article>
        <article class="metric-item">
          <span>审计记录</span>
          <strong>{{ auditTotal }}</strong>
        </article>
      </div>
    </section>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-title">角色权限配置</div>
      </template>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="role" label="角色" width="120" />
        <el-table-column label="权限配置" min-width="600">
          <template #default="{ row }">
            <el-checkbox-group v-model="row.permissions">
              <el-checkbox
                v-for="item in permissionOptions"
                :key="item.value"
                :label="item.value"
              >
                {{ item.label }}
              </el-checkbox>
            </el-checkbox-group>
          </template>
        </el-table-column>
      </el-table>

      <div class="actions">
        <BtnSecondary :loading="loading" @click="refreshAll">刷新</BtnSecondary>
        <BtnPrimary :loading="saving" @click="saveRolePermissions">保存配置</BtnPrimary>
      </div>
    </el-card>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-title">权限变更审计日志</div>
      </template>

      <el-table :data="auditRows" v-loading="auditLoading" size="small">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="actorUserId" label="操作人ID" width="110" />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column label="变更前权限" min-width="260">
          <template #default="{ row }">{{ formatPermissions(row.beforePermissions) }}</template>
        </el-table-column>
        <el-table-column label="变更后权限" min-width="260">
          <template #default="{ row }">{{ formatPermissions(row.afterPermissions) }}</template>
        </el-table-column>
        <el-table-column prop="changedAt" label="变更时间" min-width="180" />
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next, sizes"
          :total="auditTotal"
          :current-page="auditPage"
          :page-size="auditSize"
          :page-sizes="[10, 20, 50]"
          @current-change="handleAuditPageChange"
          @size-change="handleAuditSizeChange"
        />
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import BtnSecondary from '@/components/common/BtnSecondary.vue'

const permissionOptions = [
  { value: 'PRIVATE_CLOUD_MODEL_CREATE', label: '创建私有模型' },
  { value: 'PRIVATE_CLOUD_MODEL_EDIT', label: '编辑私有模型' },
  { value: 'PRIVATE_CLOUD_MODEL_DELETE', label: '删除私有模型' },
  { value: 'PRIVATE_CLOUD_MODEL_TOGGLE', label: '启停私有模型' }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref([])

const auditLoading = ref(false)
const auditRows = ref([])
const auditTotal = ref(0)
const auditPage = ref(1)
const auditSize = ref(10)

const normalizeRows = (items) => {
  const map = new Map(Array.isArray(items) ? items.map((item) => [String(item?.role || '').toUpperCase(), item]) : [])
  return ['ADMIN', 'USER'].map((role) => {
    const row = map.get(role)
    const permissions = Array.isArray(row?.permissions) ? row.permissions.filter(Boolean) : []
    return {
      role,
      permissions
    }
  })
}

const formatPermissions = (permissions) => {
  if (!Array.isArray(permissions) || permissions.length === 0) {
    return '无'
  }
  return permissions.join(', ')
}

const loadRolePermissions = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/users/role-permissions')
    rows.value = normalizeRows(res?.data)
  } finally {
    loading.value = false
  }
}

const loadAudits = async () => {
  auditLoading.value = true
  try {
    const res = await request.get('/admin/users/role-permissions/audits', {
      params: {
        page: auditPage.value,
        size: auditSize.value
      }
    })
    const data = res?.data || {}
    auditRows.value = Array.isArray(data.list) ? data.list : []
    auditTotal.value = Number(data.total || 0)
  } finally {
    auditLoading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([loadRolePermissions(), loadAudits()])
}

const saveRolePermissions = async () => {
  saving.value = true
  try {
    const payload = {
      items: rows.value.map((row) => ({
        role: row.role,
        permissions: Array.from(new Set(row.permissions || []))
      }))
    }
    await request.put('/admin/users/role-permissions', payload)
    ElMessage.success('权限配置已保存')
    auditPage.value = 1
    await refreshAll()
  } finally {
    saving.value = false
  }
}

const handleAuditPageChange = async (page) => {
  auditPage.value = page
  await loadAudits()
}

const handleAuditSizeChange = async (size) => {
  auditSize.value = size
  auditPage.value = 1
  await loadAudits()
}

onMounted(async () => {
  await refreshAll()
})
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-hero {
  background: linear-gradient(120deg, #1a2b4a 0%, #243f68 100%);
  border: 1px solid rgba(201, 168, 76, 0.45);
  border-radius: var(--radius-card);
  padding: 18px 20px;
  box-shadow: var(--shadow-card);
  color: #ebf1fb;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
}

.admin-hero h1 {
  margin: 0;
  color: #fff;
}

.admin-hero p {
  margin: 8px 0 0;
  color: #cfd9ea;
  font-size: 13px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.metric-item {
  min-width: 100px;
  border: 1px solid rgba(201, 168, 76, 0.35);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 10px;
  text-align: center;
}

.metric-item span {
  font-size: 12px;
  color: #dce5f4;
}

.metric-item strong {
  display: block;
  margin-top: 4px;
  color: #fff4d2;
  font-size: 20px;
}

.panel-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.panel-title {
  font-weight: 700;
  color: #1a2b4a;
}

.actions {
  margin-top: 14px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .admin-hero {
    grid-template-columns: 1fr;
  }
}
</style>
