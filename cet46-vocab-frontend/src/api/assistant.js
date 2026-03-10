import request from '@/api/request'

const isTimeoutError = (error) => {
  const message = String(error?.message || '').toLowerCase()
  return error?.code === 'ECONNABORTED' || message.includes('timeout')
}

export const assistantChat = async (payload, options = {}) => {
  const requestOptions = {
    timeout: 120000,
    silentError: true,
    ...options
  }
  try {
    return await request.post('/assistant/chat', payload, requestOptions)
  } catch (error) {
    if (!isTimeoutError(error)) {
      throw error
    }
    return request.post('/assistant/chat', payload, {
      ...requestOptions,
      timeout: 180000
    })
  }
}
