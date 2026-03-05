import request from './request'

export const getTodayList = () => request.get('/review/today')

export const submitReview = (data) => request.post('/review/submit', data)

export const getSessionProgress = () => request.get('/review/session/progress')
