<template>
  <div ref="chartRef" class="line-chart" />
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const chartRef = ref(null)
let chart = null

const render = () => {
  if (!chart) return
  const source = Array.isArray(props.data) ? props.data : []
  chart.setOption({
    grid: { left: 42, right: 18, top: 24, bottom: 36 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: source.map((i) => i.date),
      axisLine: { lineStyle: { color: '#D7DFEA' } },
      axisLabel: { color: '#6D7E94' }
    },
    yAxis: {
      type: 'value',
      name: '复习词数',
      nameTextStyle: { color: '#6D7E94' },
      splitLine: { lineStyle: { color: '#EEF2F8' } },
      axisLabel: { color: '#6D7E94' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: source.map((i) => i.count),
        symbolSize: 6,
        lineStyle: { width: 3, color: '#1A2B4A' },
        itemStyle: { color: '#1A2B4A' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(26,43,74,0.28)' },
            { offset: 1, color: 'rgba(26,43,74,0)' }
          ])
        }
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
.line-chart {
  width: 100%;
  height: 320px;
}
</style>
