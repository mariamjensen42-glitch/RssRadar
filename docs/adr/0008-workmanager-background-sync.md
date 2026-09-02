# 后台自动同步：WorkManager 唯一周期任务，不自建调度

自动同步（按间隔后台刷新订阅源，完成后按保留天数归档）建立在 WorkManager 的 `enqueueUniquePeriodicWork` 之上：一个名为 `rssradar-auto-sync` 的周期任务，间隔与约束（仅 WiFi / 仅充电）由 SyncStore 驱动，设置变更即用 UPDATE 策略原地重建。

## Status

accepted

## Context

引入自动同步前刷新全靠手动（下拉 / 单源 / 导入补齐），项目零调度基建。规模现实：订阅源 1000+，一轮全量刷新（8 路并发）可能持续数十分钟——这直接否决了短间隔档位，也决定了调度器必须能在进程死亡、Doze、设备重启后继续可靠执行。

## Considered Options

- **AlarmManager（否决）**：精确闹钟在 Android 12+ 需要用户授权，非精确闹钟仍要自己处理 Doze 白名单、重启广播、网络可用性判断——全是 WorkManager 免费提供的东西的手工复刻。
- **前台协程循环 / 手写 Handler 调度（否决）**：进程被杀即失效，且后台长时间运行受前台服务限制约束；为"几十分钟一轮"的需求维护一套存活机制不成比例。
- **WorkManager 周期任务（选）**：Doze/进程死亡/设备重启后自动恢复；Constraints（`UNMETERED` 网络、充电）由系统调度器原生执行；`CoroutineWorker` + Hilt EntryPoint 取依赖，不引 hilt-work 注解处理器。

## Consequences

- **间隔档位是承诺的下限而非精确值**：WorkManager 周期任务最小 15 分钟且受系统批调度影响有抖动，因此只提供 1 小时及以上档位（手动 / 1h / 3h / 6h / 12h / 1 天）——15/30 分钟档对 1000+ 源是虚假承诺。
- **约束只作用于周期任务**：仅 WiFi = `NetworkType.UNMETERED`，仅充电 = `setRequiresCharging`。手动刷新永不受任何约束限制。
- **启动时同步与周期任务共用执行体**（`SyncRunner.runAutoSync`）：刷新（过滤 `syncEnabled = 0` 的屏蔽源）→ 归档清理（NonCancellable，不允许删一半留一半）；`lastAutoSyncAt` 时间戳做 30 分钟去抖，防止反复进出应用重复刷 1000+ 源。
- 周期任务在应用每次启动时用最新偏好重建一次（UPDATE 策略），兜底系统重启后任务丢失的场景。
- Worker 失败返回 `retry()` 交给 WorkManager 退避，不打断周期。
