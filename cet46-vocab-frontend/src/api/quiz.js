import request from './request'

export const generateQuiz = (data) => request.post('/quiz/generate', data)

export const submitQuiz = (data) => request.post('/quiz/submit', data)
