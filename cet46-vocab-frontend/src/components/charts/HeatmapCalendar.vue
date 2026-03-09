<template>
  <div ref="chartRef" class="heatmap-calendar" />
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const chartRef = ref(null)
const themeStore = useThemeStore()
let chart = null

const today = new Date()
const startDate = new Date(today)
startDate.setDate(startDate.getDate() - 364)

const maxValue = computed(() => {
  const source = Array.isArray(props.data) ? props.data : []
  const max = source.reduce((m, item) => Math.max(m, Number(item.value || 0)), 0)
  return max <= 0 ? 1 : max
})

const render = () => {
  if (!chart) return
  const source = Array.isArray(props.data) ? props.data : []
  const isDark = themeStore.isDark
  const textColor = isDark ? '#8da4c8' : '#6D7E94'
  const borderColor = isDark ? '#1a2740' : '#ffffff'
  const heatColors = isDark ? ['#132038', '#3e5f95', '#9FB7E4'] : ['#E8ECF2', '#1A2B4A']
  chart.setOption({
    tooltip: {
      formatter: (params) => `${params.value[0]}: ${params.value[1]}`
    },
    visualMap: {
      min: 0,
      max: maxValue.value,
      orient: 'horizontal',
      left: 'center',
      top: 4,
      inRange: {
        color: heatColors
      },
      textStyle: { color: textColor }
    },
    calendar: {
      top: 52,
      left: 18,
      right: 18,
      cellSize: ['auto', 14],
      range: [
        startDate.toISOString().slice(0, 10),
        today.toISOString().slice(0, 10)
      ],
      splitLine: { lineStyle: { color: borderColor, width: 1 } },
      itemStyle: {
        borderWidth: 1,
        borderColor
      },
      yearLabel: { show: false },
      monthLabel: { color: textColor },
      dayLabel: { color: textColor }
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: source.map((i) => [i.date, i.value])
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
.heatmap-calendar {
  width: 100%;
  height: 320px;
}
</style>
