<template>
  <div ref="chartRef" class="bar-chart" />
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const chartRef = ref(null)
let chart = null

const palette = ['#1A2B4A', '#365B8C', '#4A6FA5', '#6B8DBE', '#8BAFD4', '#A9C1DE', '#C3D3E8']

const render = () => {
  if (!chart) return
  const source = Array.isArray(props.data) ? props.data : []
  chart.setOption({
    grid: { left: 50, right: 20, top: 20, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#EEF2F8' } },
      axisLabel: { color: '#6D7E94' }
    },
    yAxis: {
      type: 'category',
      data: source.map((i) => i.pos),
      axisLabel: { color: '#6D7E94' }
    },
    series: [
      {
        type: 'bar',
        data: source.map((i, idx) => ({
          value: i.count,
          itemStyle: { color: palette[idx % palette.length], borderRadius: 6 }
        })),
        barWidth: 18
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
.bar-chart {
  width: 100%;
  height: 320px;
}
</style>
