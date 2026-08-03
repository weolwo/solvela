/*
 * echarts 按需引入
 *
 * ⚠️ 不要改回 `import * as echarts from 'echarts'`：
 * 默认入口会把全部图表类型、组件与渲染器打进产物（约 1.1MB），而本项目只用到下面这几种。
 *
 * ⚠️ 新增图表时必须在这里补注册，否则**图表会渲染成空白且控制台不报错**——
 * 这是按需引入唯一的坑，漏注册没有任何显式信号。
 * 对照关系：
 *   series 的 type: 'bar'    -> BarChart
 *   series 的 type: 'line'   -> LineChart
 *   series 的 type: 'pie'    -> PieChart
 *   series 的 type: 'gauge'  -> GaugeChart
 *   series 的 type: 'funnel' -> FunnelChart
 *   option 里的 grid / xAxis / yAxis -> GridComponent
 *   option 里的 legend / title / tooltip -> 对应的 LegendComponent / TitleComponent / TooltipComponent
 */
import * as echarts from 'echarts/core';
import { BarChart, FunnelChart, GaugeChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  // 图表类型
  BarChart,
  LineChart,
  PieChart,
  GaugeChart,
  // 任务阶梯流失漏斗（首页营销大屏）在用
  FunnelChart,
  // 组件（直角坐标系 grid 同时提供 xAxis / yAxis）
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  // 渲染器
  CanvasRenderer,
]);

// echarts.graphic（LinearGradient 等）与 echarts.init 都在 core 里，随本对象一起导出
export default echarts;
