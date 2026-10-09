import { useTheme } from '../../theme'
import { useEffect, useRef } from 'react'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  AriaComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import type { TooltipComponentOption } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { MetricResult } from './model'
import { date, formatValue, seriesName } from './model'

echarts.use([
  LineChart,
  AriaComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

export default function MetricChart({ result, title }: { result: MetricResult; title: string }) {
  const theme = useTheme()
  const element = useRef<HTMLDivElement>(null)
  const instance = useRef<echarts.EChartsType | null>(null)
  useEffect(() => {
    if (!element.current) return
    const chart = echarts.init(element.current)
    instance.current = chart
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(element.current)
    return () => {
      observer.disconnect()
      chart.dispose()
      instance.current = null
    }
  }, [])
  useEffect(() => {
    const light = theme === 'light'
    const colors = {
      text: light ? '#435773' : '#aab8ce',
      surface: light ? '#ffffff' : '#132037',
      border: light ? '#c5d2e4' : '#344560',
      primary: light ? '#17263c' : '#e4edfa',
      grid: light ? '#e1e8f2' : '#253149',
    }
    const names = result.series.map((series, index) => {
      const labels = series.labels
      const label =
        labels.name ||
        labels.container_label_com_docker_compose_service ||
        labels.mountpoint ||
        labels.device ||
        labels.instance ||
        'Хост'
      return `${index + 1}. ${label.length > 48 ? label.slice(0, 45) + '…' : label}`
    })
    const formatter: TooltipComponentOption['formatter'] = (params) => {
      const rows = Array.isArray(params) ? params : [params]
      const box = document.createElement('div')
      box.className = 'monitoring-tooltip'
      const heading = document.createElement('div')
      const firstValue = rows[0]?.value
      heading.className = 'monitoring-tooltip-heading'
      heading.textContent =
        Array.isArray(firstValue) && typeof firstValue[0] === 'number' ? date(firstValue[0]) : title
      box.append(heading)
      for (const row of rows) {
        const line = document.createElement('div')
        line.className = 'monitoring-tooltip-row'
        const marker = document.createElement('span')
        marker.className = 'monitoring-tooltip-marker'
        if (typeof row.color === 'string') marker.style.backgroundColor = row.color
        const name = document.createElement('span')
        name.textContent = names[row.seriesIndex ?? 0]
        const value = document.createElement('strong')
        const sample = Array.isArray(row.value) ? row.value[1] : null
        value.textContent = formatValue(typeof sample === 'number' ? sample : null, result.unit)
        line.append(marker, name, value)
        box.append(line)
      }
      // Labels are untrusted source data: use textContent, never HTML interpolation.
      return box
    }
    instance.current?.setOption(
      {
        animation: false,
        aria: {
          enabled: true,
          label: {
            description: `${title}. ${result.series.length} рядов. Пропуски данных не соединяются.`,
          },
        },
        color: light
          ? ['#2563eb', '#14805c', '#a6650b', '#814ac7', '#c23d59', '#087c97']
          : ['#70adff', '#64d6b4', '#ffca73', '#ca9bff', '#fb8f9b', '#7cd7ed'],
        textStyle: { color: colors.text, fontFamily: 'system-ui' },
        tooltip: {
          trigger: 'axis',
          renderMode: 'html',
          confine: true,
          enterable: true,
          backgroundColor: colors.surface,
          borderColor: colors.border,
          textStyle: { color: colors.primary, fontSize: 12 },
          extraCssText: 'max-width:calc(100% - 16px);box-sizing:border-box;white-space:normal;',
          formatter,
        },
        legend: {
          type: 'scroll',
          top: 0,
          textStyle: { color: colors.text, width: 230, overflow: 'truncate' },
          tooltip: {
            show: true,
            renderMode: 'richText',
            backgroundColor: colors.surface,
            borderColor: colors.border,
            textStyle: { color: colors.primary },
          },
        },
        grid: { left: 12, right: 22, top: 50, bottom: 65, containLabel: true },
        xAxis: {
          type: 'time',
          min: result.start,
          max: result.end,
          axisLabel: { hideOverlap: true, color: colors.text },
          axisLine: { lineStyle: { color: colors.border } },
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            color: colors.text,
            formatter: (value: number) => formatValue(value, result.unit),
          },
          splitLine: { lineStyle: { color: colors.grid } },
        },
        dataZoom: [
          { type: 'inside' },
          {
            type: 'slider',
            bottom: 5,
            height: 22,
            borderColor: colors.border,
            textStyle: { color: colors.text },
          },
        ],
        series: result.series.map((series, index) => ({
          id: seriesName(series.labels),
          name: names[index],
          type: 'line',
          showSymbol: false,
          connectNulls: false,
          lineStyle: { width: 1.7 },
          data: series.points.map((point) => [point.timestamp, point.value]),
        })),
      },
      { notMerge: true },
    )
  }, [result, title, theme])
  return (
    <div
      className="monitoring-chart"
      ref={element}
      role="img"
      aria-label={`${title}: временные ряды; легенда позволяет скрывать ряды`}
    />
  )
}
