import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Assistant from '@/views/Assistant.vue'

const { assistantGetState, assistantSyncState } = vi.hoisted(() => ({
  assistantGetState: vi.fn(),
  assistantSyncState: vi.fn()
}))

const { pushMock } = vi.hoisted(() => ({
  pushMock: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: pushMock })
}))

vi.mock('@/api/assistant', () => ({
  assistantChat: vi.fn(),
  assistantGetState,
  assistantSyncState
}))

vi.mock('@/api/request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn()
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    error: vi.fn()
  },
  ElMessageBox: {
    prompt: vi.fn(() => Promise.reject(new Error('cancel')))
  }
}))

const flush = async () => {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
}

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const stubs = {
  BtnPrimary: {
    template: '<button class="btn-primary-stub" @click="$emit(\'click\')"><slot /></button>'
  },
  BtnSecondary: {
    template: '<button class="btn-secondary-stub" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'keydown'],
    template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keydown="$emit(\'keydown\', $event)"></textarea>'
  },
  'el-drawer': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-select': { props: ['modelValue'], emits: ['update:modelValue', 'change'], template: '<div><slot /></div>' },
  'el-option': { template: '<div></div>' },
  'el-dropdown': { emits: ['command'], template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-checkbox': { template: '<input type="checkbox" />' },
  'el-dialog': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /><slot name="footer" /></div>' }
}

describe('Assistant sync queue integration', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    assistantGetState.mockReset()
    assistantSyncState.mockReset()
    pushMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('sends 2 sync requests (first + queued replay) when second action occurs during in-flight sync', async () => {
    const now = Date.now()
    assistantGetState.mockResolvedValue({
      data: {
        sessions: [
          {
            id: 's_existing',
            title: '已有会话',
            updatedAt: now,
            hasInteraction: true,
            pinned: false,
            groupId: null,
            context: {},
            messages: [{ id: 'm1', role: 'assistant', content: 'hi' }]
          }
        ],
        groups: []
      }
    })

    const slowFirst = deferred()
    assistantSyncState
      .mockImplementationOnce(() => slowFirst.promise)
      .mockResolvedValue({ data: {} })

    const wrapper = mount(Assistant, {
      global: {
        stubs
      }
    })

    await flush()
    await flush()

    const newSessionBtn = wrapper
      .findAll('button')
      .find((btn) => btn.text().includes('新建对话'))

    expect(newSessionBtn).toBeTruthy()

    await newSessionBtn.trigger('click')
    vi.advanceTimersByTime(500)
    await flush()
    expect(assistantSyncState).toHaveBeenCalledTimes(1)

    await newSessionBtn.trigger('click')
    vi.advanceTimersByTime(500)
    await flush()
    expect(assistantSyncState).toHaveBeenCalledTimes(1)

    slowFirst.resolve({ data: {} })
    await flush()

    vi.advanceTimersByTime(500)
    await flush()

    expect(assistantSyncState).toHaveBeenCalledTimes(2)
  })
})
