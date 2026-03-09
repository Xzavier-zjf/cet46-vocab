<template>
  <div ref="chartRef" class="line-chart" />
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const chartRef = ref(null)
const themeStore = useThemeStore()
let chart = null

const render = () => {
  if (!chart) return
  const source = Array.isArray(props.data) ? props.data : []
  const isDark = themeStore.isDark
  const axisColor = isDark ? '#7f96b8' : '#6D7E94'
  const splitColor = isDark ? '#20324f' : '#EEF2F8'
  const lineColor = isDark ? '#9FB7E4' : '#1A2B4A'
  const areaFrom = isDark ? 'rgba(159,183,228,0.32)' : 'rgba(26,43,74,0.28)'
  const areaTo = isDark ? 'rgba(159,183,228,0.02)' : 'rgba(26,43,74,0)'
  chart.setOption({
    grid: { left: 42, right: 18, top: 24, bottom: 36 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: source.map((i) => i.date),
      axisLine: { lineStyle: { color: splitColor } },
      axisLabel: { color: axisColor }
    },
    yAxis: {
      type: 'value',
      name: '复习词数',
      nameTextStyle: { color: axisColor },
      splitLine: { lineStyle: { color: splitColor } },
      axisLabel: { color: axisColor }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: source.map((i) => i.count),
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

onMounted(() => {
  initChart()
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
