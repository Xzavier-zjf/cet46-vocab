<template>
  <div class="ring-chart">
    <div ref="chartRef" class="chart" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  mastered: { type: Number, default: 0 },
  learning: { type: Number, default: 0 },
  total: { type: Number, default: 0 }
})

const chartRef = ref(null)
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
  chartInstance.setOption({
    animation: true,
    color: ['#1A2B4A', '#C9A84C', '#E8ECF2'],
    title: {
      text: `${percent.value}%`,
      subtext: '掌握进度',
      left: 'center',
      top: '44%',
      textStyle: {
        color: '#1A2B4A',
        fontSize: 30,
        fontWeight: 700
      },
      subtextStyle: {
        color: '#8896A8',
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

onMounted(() => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  renderChart()
})

watch(
  () => [props.mastered, props.learning, props.total],
  () => renderChart()
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
