import request from './request'

export const generateQuiz = (data) => request.post('/quiz/generate', data)

export const submitQuiz = (data) => request.post('/quiz/submit', data)

export const getQuizHistory = (limit = 20) => request.get('/quiz/history', { params: { limit } })

export const getQuizHistoryDetail = (id) => request.get(`/quiz/history/${id}`)
