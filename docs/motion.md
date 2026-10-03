# 动效规范（Motion Spec）

全 app 动画的单一事实来源。**动效规格一律取 M3 Expressive 官方 `MotionScheme`，禁止自定时长/曲线**，
也禁止散落 `tween(300)` 之类的魔法数。

## 规格来源（2026-10-03 起）

定义于 `core/ui/theme/Motion.kt`，包装官方 `MaterialTheme.motionScheme` 的四档：

| 入口 | 官方档位 | 用途 |
|---|---|---|
| `effectsSpec()` | `defaultEffectsSpec()` | 效果变化：按压缩放、淡入淡出、颜色 |
| `fastEffectsSpec()` | `fastEffectsSpec()` | 退场淡出（比进场快才跟手） |
| `spatialSpec()` | `defaultSpatialSpec()` | 空间位移：页面转场、列表 placement |
| `fastSpatialSpec()` | `fastSpatialSpec()` | 退场位移 |

原先的自定 token（`DurationMicro` 120 / `DurationShort` 200 / `DurationMedium` 280 +
`FastOutSlowInEasing` 与 `CubicBezier(0.2,0,0,1)` 两条曲线）**已全部删除**。

**为什么要分 spatial / effects 两族**：M3 Expressive 把动效分成「元素在空间里移动」与
「元素在原地改变外观」两族，用错会让转场显得生硬。对应关系：

- 空间位移 → 页面转场的滑入滑出、列表 item placement
- 效果变化 → 缩放、淡入淡出

唯一保留的自定值是 `crossfadeMotion` 里的 200ms——Coil 的 `crossfade()` 只接受毫秒 Int、
不认 `AnimationSpec`，无法接官方 scheme。该值与官方 effects 档同量级。

## 动效清单

### 1. 页面切换（NavHost 转场）

按导航语义分两层，不搞一刀切：

- **层级导航**（列表→详情、进设置二级页这类）：前进「新页自右滑入 1/12 + fade in」用 `spatialSpec()`；旧页退场用 `fastSpatialSpec()` + `fastEffectsSpec()`——**退场必须比进场快**，双向同速转场必然显拖；返回取镜像。
- **顶层 tab 互切**（Feed / 订阅 / 搜索 / 我的）：同级关系没有方向语义，统一淡入淡出，不做滑移假动作。
- **范围**：`MainActivity` 的 `NavHost` 统一设置，单目的地不覆盖。
- **降级**：reduce-motion 时全部退化为瞬时切换。

### 2. 按压反馈

- **触发**：列表卡片、主操作按钮按下/抬起。
- **行为**：`Modifier.pressScale()`——按压缩放至 0.97，抬起回弹；M3 ripple 保留。
- **规格**：`effectsSpec()`。
- **范围**：列表卡片、主操作按钮。普通文字按钮/图标按钮只用系统 ripple，不加缩放；hover 由系统 ripple 承担，不重复实现。
- **降级**：reduce-motion 时跳过缩放，仅保留 ripple。

### 3. 内容加载

- **图片**：Coil crossfade 走 `crossfadeMotion()`。**降级**：reduce-motion 时 `crossfade(false)`。
- **加载指示**：官方 `CircularProgressIndicator` / `LoadingIndicator` 保持（`docs/ui-resources.md` 组件准则），不自绘。

### 4. 列表 item

- **订阅列表**：`animateItem()` 全量（增删 + placement），规格取 `effectsSpec()` / `spatialSpec()`。
- **文章列表**：仅删除淡出（`effectsSpec()`），不加 placement——数万条列表的 placement 动画在低端机是帧率杀手。
- **降级**：reduce-motion 时跳过动画，直接增删。

### 5. 弹层

- `ModalBottomSheet` / `DropdownMenu` / `AlertDialog` 沿用 M3 自带转场，**不自绘**（系统自动响应 reduce-motion 与动画时长缩放）。
- 应用内自制的显隐（分组对话框等）：`AnimatedVisibility` + `effectsSpec()`。

## reduce-motion 检测

**这一层不是「自定义动效」而是无障碍，必须自己留**：官方 scheme 只管动效长什么样，
不管用户是否要求关掉。

- 信号：`Settings.Global.ANIMATOR_DURATION_SCALE == 0`（系统"移除动画"开启时置 0）。
- 实现：`rememberReducedMotion()` 单一函数封装 + `LocalReducedMotion` CompositionLocal 下发；未来若加应用内开关只改此处。

- 原则：降级 = 瞬时状态切换，不是去掉反馈。

## 红线

- 不做无限循环动画（loading 指示器除外）。
- 不做布局跳变动画：容器尺寸变化只在明确需要处用 `animateContentSize()`。
- 数万条列表（文章列表）禁止 placement 动画。
- 任何动画不得阻塞输入（无 `graphicsLayer` 全屏滥用、无转场期间点击穿透禁用）。
