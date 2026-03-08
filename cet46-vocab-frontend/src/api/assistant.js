import request from '@/api/request'

export const assistantChat = (payload) =>
  request.post('/assistant/chat', payload, {
    timeout: 90000
  })
