import request from '@/api/request'

const isTimeoutError = (error) => {
  const message = String(error?.message || '').toLowerCase()
  return error?.code === 'ECONNABORTED' || message.includes('timeout')
}

export const assistantChat = async (payload) => {
  try {
    return await request.post('/assistant/chat', payload, {
      timeout: 120000,
      silentError: true
    })
  } catch (error) {
    if (!isTimeoutError(error)) {
      throw error
    }
    return request.post('/assistant/chat', payload, {
      timeout: 180000,
      silentError: true
    })
  }
}
