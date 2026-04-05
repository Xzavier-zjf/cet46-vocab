import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ModelUsage from '@/views/ModelUsage.vue'

const { requestGet, userStoreState } = vi.hoisted(() => ({
  requestGet: vi.fn(),
  userStoreState: {
    role: 'USER',
    fetchUserInfo: vi.fn(async () => {})
  }
}))

vi.mock('@/api/request', () => ({
  default: {
    get: requestGet
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => userStoreState
}))

const flush = async () => {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
}

const globalStubs = {
  BtnSecondary: {
    template: '<button class="btn-secondary-stub" @click="$emit(\'click\')"><slot /></button>'
  },
  LineChart: {
    props: ['data'],
    template: '<div class="line-chart-stub">{{ Array.isArray(data) ? data.length : 0 }}</div>'
  },
  'el-empty': {
    template: '<div class="el-empty-stub"></div>'
  },
  'el-table': {
    template: '<div class="el-table-stub"></div>'
  },
  'el-table-column': {
    template: '<div class="el-table-column-stub"></div>'
  },
  'el-tag': {
    template: '<span class="el-tag-stub"><slot /></span>'
  }
}

describe('ModelUsage.vue', () => {
  beforeEach(() => {
    requestGet.mockReset()
    userStoreState.fetchUserInfo.mockClear()
  })

  it('loads user usage stats and supports refresh', async () => {
    userStoreState.role = 'USER'
    requestGet
      .mockResolvedValueOnce({
        data: {
          viewRole: 'USER',
          summary: {
            totalCallsToday: 2,
            totalCalls7d: 5,
            totalCalls30d: 9,
            publicCalls30d: 6,
            privateCalls30d: 3,
            activeModels: 2
          },
          trend: [{ date: '20260405', calls: 2 }],
          models: [{ modelKey: 'my-private', scope: 'private', freeTier: true }],
          users: []
        }
      })
      .mockResolvedValueOnce({
        data: {
          viewRole: 'USER',
          summary: { totalCallsToday: 3, totalCalls7d: 6, totalCalls30d: 10, publicCalls30d: 6, privateCalls30d: 4, activeModels: 2 },
          trend: [{ date: '20260405', calls: 3 }],
          models: [{ modelKey: 'my-private', scope: 'private', freeTier: true }],
          users: []
        }
      })
      .mockResolvedValue({
        data: {
          viewRole: 'USER',
          summary: { totalCallsToday: 3, totalCalls7d: 6, totalCalls30d: 10, publicCalls30d: 6, privateCalls30d: 4, activeModels: 2 },
          trend: [{ date: '20260405', calls: 3 }],
          models: [{ modelKey: 'my-private', scope: 'private', freeTier: true }],
          users: []
        }
      })

    const wrapper = mount(ModelUsage, { global: { stubs: globalStubs } })
    await flush()

    expect(requestGet).toHaveBeenCalledWith('/user/llm/usage', { timeout: 15000 })
    expect(wrapper.vm.summary.privateCalls30d).toBe(3)
    expect(wrapper.vm.models).toHaveLength(1)
    expect(wrapper.vm.summaryCards.some((item) => item.label === '私有模型调用')).toBe(true)

    await wrapper.find('.btn-secondary-stub').trigger('click')
    await flush()

    expect(requestGet.mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(wrapper.vm.summary.totalCallsToday).toBe(3)
  })

  it('loads admin usage stats and exposes user breakdown', async () => {
    userStoreState.role = 'ADMIN'
    requestGet.mockResolvedValueOnce({
      data: {
        viewRole: 'ADMIN',
        summary: {
          totalCallsToday: 7,
          totalCalls7d: 18,
          totalCalls30d: 30,
          publicCalls30d: 30,
          privateCalls30d: 0,
          activeModels: 3,
          activeUsers: 4
        },
        trend: [{ date: '20260405', calls: 7 }],
        models: [{ modelKey: 'qwen-plus', scope: 'public', freeTier: false }],
        users: [{ userId: 1, modelKey: 'qwen-plus' }]
      }
    })

    const wrapper = mount(ModelUsage, { global: { stubs: globalStubs } })
    await flush()

    expect(requestGet).toHaveBeenCalledWith('/admin/llm/usage', { timeout: 15000 })
    expect(wrapper.vm.users).toHaveLength(1)
    expect(wrapper.vm.summary.activeUsers).toBe(4)
    expect(wrapper.vm.summaryCards.some((item) => item.label === '活跃用户')).toBe(true)
  })
  it('keeps trend chart visible when 7-day trend has no calls', async () => {
    userStoreState.role = 'USER'
    requestGet.mockResolvedValueOnce({
      data: {
        viewRole: 'USER',
        summary: {
          totalCallsToday: 0,
          totalCalls7d: 0,
          totalCalls30d: 0,
          publicCalls30d: 0,
          privateCalls30d: 0,
          activeModels: 0
        },
        trend: [
          { date: '20260330', calls: 0 },
          { date: '20260331', calls: 0 },
          { date: '20260401', calls: 0 },
          { date: '20260402', calls: 0 },
          { date: '20260403', calls: 0 },
          { date: '20260404', calls: 0 },
          { date: '20260405', calls: 0 }
        ],
        models: [],
        users: []
      }
    })

    const wrapper = mount(ModelUsage, { global: { stubs: globalStubs } })
    await flush()

    expect(wrapper.find('.line-chart-stub').exists()).toBe(true)
    expect(wrapper.find('.el-empty-stub').exists()).toBe(false)
  })
})

