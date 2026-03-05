<template>
  <div ref="chartRef" class="heatmap-calendar" />
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const chartRef = ref(null)
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
        color: ['#E8ECF2', '#1A2B4A']
      },
      textStyle: { color: '#6D7E94' }
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
      splitLine: { lineStyle: { color: '#fff', width: 1 } },
      itemStyle: {
        borderWidth: 1,
        borderColor: '#fff'
      },
      yearLabel: { show: false },
      monthLabel: { color: '#6D7E94' },
      dayLabel: { color: '#6D7E94' }
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

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  render()
})

watch(
  () => props.data,
  () => render(),
  { deep: true }
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
