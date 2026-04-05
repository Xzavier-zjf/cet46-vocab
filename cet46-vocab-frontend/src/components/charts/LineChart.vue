<template>
  <div ref="chartRef" class="line-chart" />
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

const props = defineProps({
  data: { type: Array, default: () => [] },
  yAxisName: { type: String, default: '复习词数' }
})

const chartRef = ref(null)
const themeStore = useThemeStore()
let chart = null
let resizeObserver = null

const toSafeInteger = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return 0
  return Math.max(0, Math.round(num))
}

const render = () => {
  if (!chart) return
  const source = Array.isArray(props.data) ? props.data : []
  const isDark = themeStore.isDark
  const axisColor = isDark ? '#D9E6FF' : '#223453'
  const splitColor = isDark ? '#20324f' : '#EEF2F8'
  const lineColor = isDark ? '#9FB7E4' : '#1A2B4A'
  const areaFrom = isDark ? 'rgba(159,183,228,0.32)' : 'rgba(26,43,74,0.28)'
  const areaTo = isDark ? 'rgba(159,183,228,0.02)' : 'rgba(26,43,74,0)'

  chart.setOption({
    grid: { left: 74, right: 18, top: 24, bottom: 78, containLabel: true },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => `${toSafeInteger(value)}`
    },
    xAxis: {
      type: 'category',
      name: '日期',
      nameLocation: 'middle',
      nameGap: 40,
      nameTextStyle: { color: axisColor, fontSize: 12 },
      data: source.map((i) => i.date),
      axisLine: { show: true, lineStyle: { color: axisColor, width: 2 } },
      axisTick: { show: true, alignWithLabel: true },
      axisLabel: {
        show: true,
        color: axisColor,
        interval: 0,
        hideOverlap: false,
        showMinLabel: true,
        showMaxLabel: true,
        margin: 12,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: props.yAxisName || '复习词数',
      minInterval: 1,
      nameLocation: 'middle',
      nameRotate: 90,
      nameTextStyle: { color: axisColor, align: 'center', verticalAlign: 'middle' },
      nameGap: 56,
      splitLine: { lineStyle: { color: splitColor } },
      axisLabel: {
        color: axisColor,
        formatter: (value) => `${toSafeInteger(value)}`
      }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: source.map((i) => toSafeInteger(i.count)),
        symbolSize: 6,
        lineStyle: { width: 3, color: lineColor },
        itemStyle: { color: lineColor },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: areaFrom },
            { offset: 1, color: areaTo }
          ])
        }
      }
    ]
  })
}

const initChart = () => {
  if (!chartRef.value) return
  if (chart) {
    chart.dispose()
    chart = null
  }
  chart = echarts.init(chartRef.value, themeStore.isDark ? 'dark' : undefined)
  render()
}

const handleResize = () => {
  if (chart) {
    chart.resize()
  }
}

onMounted(() => {
  initChart()
  if (typeof ResizeObserver !== 'undefined' && chartRef.value) {
    resizeObserver = new ResizeObserver(() => handleResize())
    resizeObserver.observe(chartRef.value)
  }
  window.addEventListener('resize', handleResize)
})

watch(
  () => props.data,
  () => render(),
  { deep: true }
)

watch(
  () => themeStore.isDark,
  () => initChart()
)

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.line-chart {
  width: 100%;
  height: 320px;
}
</style>
