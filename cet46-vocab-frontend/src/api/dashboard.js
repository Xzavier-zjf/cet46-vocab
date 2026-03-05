import request from './request'

export const getOverview = () => request.get('/dashboard/overview')

export const getStats = (days) => request.get('/dashboard/stats', { params: { days } })
