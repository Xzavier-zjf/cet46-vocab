<template>
  <div ref="chartRef" class="bar-chart" />
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

const palette = ['#1A2B4A', '#365B8C', '#4A6FA5', '#6B8DBE', '#8BAFD4', '#A9C1DE', '#C3D3E8']
const darkPalette = ['#9FB7E4', '#7FA2DA', '#638ECC', '#4776B9', '#3563A4', '#2D578F', '#234B7A']

const render = () => {
  if (!chart) return
  const source = Array.isArray(props.data) ? props.data : []
  const isDark = themeStore.isDark
  const axisColor = isDark ? '#7f96b8' : '#6D7E94'
  const splitColor = isDark ? '#20324f' : '#EEF2F8'
  const activePalette = isDark ? darkPalette : palette
  chart.setOption({
    grid: { left: 50, right: 20, top: 20, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: splitColor } },
      axisLabel: { color: axisColor }
    },
    yAxis: {
      type: 'category',
      data: source.map((i) => i.pos),
      axisLabel: { color: axisColor }
    },
    series: [
      {
        type: 'bar',
        data: source.map((i, idx) => ({
          value: i.count,
          itemStyle: { color: activePalette[idx % activePalette.length], borderRadius: 6 }
        })),
        barWidth: 18
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
.bar-chart {
  width: 100%;
  height: 320px;
}
</style>
