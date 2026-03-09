<template>
  <div class="ring-chart">
    <div ref="chartRef" class="chart" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

const props = defineProps({
  mastered: { type: Number, default: 0 },
  learning: { type: Number, default: 0 },
  total: { type: Number, default: 0 }
})

const chartRef = ref(null)
const themeStore = useThemeStore()
let chartInstance = null

const unlearned = computed(() => {
  const value = props.total - props.mastered - props.learning
  return value > 0 ? value : 0
})

const percent = computed(() => {
  if (!props.total || props.total <= 0) return 0
  return Math.round((props.mastered / props.total) * 100)
})

const renderChart = () => {
  if (!chartInstance) return
  const isDark = themeStore.isDark
  const titleColor = isDark ? '#dbe8ff' : '#1A2B4A'
  const subTitleColor = isDark ? '#8da4c8' : '#8896A8'
  const colors = isDark ? ['#9FB7E4', '#D9BD73', '#23344e'] : ['#1A2B4A', '#C9A84C', '#E8ECF2']

  chartInstance.setOption({
    animation: true,
    color: colors,
    title: {
      text: `${percent.value}%`,
      subtext: '掌握进度',
      left: 'center',
      top: '44%',
      textStyle: {
        color: titleColor,
        fontSize: 30,
        fontWeight: 700
      },
      subtextStyle: {
        color: subTitleColor,
        fontSize: 13
      }
    },
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['62%', '82%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: [
          { value: props.mastered, name: '已掌握' },
          { value: props.learning, name: '学习中' },
          { value: unlearned.value, name: '未学' }
        ]
      }
    ]
  })
}

const initChart = () => {
  if (!chartRef.value) return
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  chartInstance = echarts.init(chartRef.value, themeStore.isDark ? 'dark' : undefined)
  renderChart()
}

onMounted(() => {
  initChart()
})

watch(
  () => [props.mastered, props.learning, props.total],
  () => renderChart()
)

watch(
  () => themeStore.isDark,
  () => initChart()
)

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.ring-chart {
  width: 300px;
  height: 300px;
}

.chart {
  width: 300px;
  height: 300px;
}
</style>
