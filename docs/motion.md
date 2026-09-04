# 动效规范（Motion Spec）

全 app 动画的单一事实来源。新增动画必须引用本规范的 token，禁止散落 `tween(300)` 之类的魔法数。

## Token

定义于 `ui/theme/Motion.kt`：

| Token | 值 | 用途 |
|---|---|---|
| `DurationMicro` | 120ms | 按压缩放 |
| `DurationShort` | 200ms | 图片 crossfade、item 删除淡出 |
| `DurationMedium` | 280ms | 页面转场 |
| `EasingStandard` | `FastOutSlowInEasing` | 通用 |
| `EasingEmphasized` | `CubicBezier(0.2f, 0f, 0f, 1f)` | 页面转场（M3 emphasized） |

## 动效清单

### 1. 页面切换（NavHost 转场）

按导航语义分两层，不搞一刀切：

- **层级导航**（列表→详情、进设置二级页这类）：前进「新页自右滑入 1/12 + fade in」，280ms + `EasingEmphasized`；旧页退场 200ms（滑出 1/12 + fade out）——**退场必须比进场快**，双向同速转场必然显拖；返回取镜像。
- **顶层 tab 互切**（Feed / 订阅 / 搜索 / 我的）：同级关系没有方向语义，统一 200ms crossfade，不做滑移假动作。
- **范围**：`MainActivity` 的 `NavHost` 统一设置，单目的地不覆盖。
- **降级**：reduce-motion 时全部退化为瞬时切换。

### 2. 按压反馈

- **触发**：列表卡片、主操作按钮按下/抬起。
- **行为**：`Modifier.pressScale()`——按压缩放至 0.97，抬起回弹；M3 ripple 保留。
- **规格**：120ms + `EasingStandard`。
- **范围**：列表卡片、主操作按钮。普通文字按钮/图标按钮只用系统 ripple，不加缩放；hover 由系统 ripple 承担，不重复实现。
- **降级**：reduce-motion 时跳过缩放，仅保留 ripple。

### 3. 内容加载

- **图片**：Coil crossfade 200ms。**降级**：reduce-motion 时 `crossfade(false)`。
- **加载指示**：现有 `CircularProgressIndicator` 保持，不加骨架屏（数据源为本地 Room，加载快，骨架屏是负资产）。

### 4. 列表 item

- **订阅列表**：`animateItem()` 全量（增删 + placement）。
- **文章列表**：仅删除淡出（200ms），不加 placement——数万条列表的 placement 动画在低端机是帧率杀手。
- **降级**：reduce-motion 时跳过动画，直接增删。

### 5. 弹层

- `ModalBottomSheet` / `DropdownMenu` / `AlertDialog` 沿用 M3 自带转场，**不自绘**（系统自动响应 reduce-motion 与动画时长缩放）。
- 应用内自制的显隐（分组对话框等）：`AnimatedVisibility` + 200ms + `EasingStandard`。

## reduce-motion 检测

- 信号：`Settings.Global.ANIMATOR_DURATION_SCALE == 0`（系统"移除动画"开启时置 0）。
- 实现：`rememberReducedMotion()` 单一函数封装 + `LocalReducedMotion` CompositionLocal 下发；未来若加应用内开关只改此处。
- 原则：降级 = 瞬时状态切换，不是去掉反馈。

## 红线

- 不做无限循环动画（loading 指示器除外）。
- 不做布局跳变动画：容器尺寸变化只在明确需要处用 `animateContentSize()`。
- 数万条列表（文章列表）禁止 placement 动画。
- 任何动画不得阻塞输入（无 `graphicsLayer` 全屏滥用、无转场期间点击穿透禁用）。
