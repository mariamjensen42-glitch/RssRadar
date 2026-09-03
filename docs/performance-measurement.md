# 真机性能测量手册

面向「上千订阅源 / 数万篇文章」的真实规模。这份文档只回答两件事：**怎么测**，以及**测出来多少算够**。

开发机禁跑 gradle，所以下面全是 `adb` 命令 + 系统自带工具，不依赖构建系统。

---

## 0. 测量前的准备

### 设备与数据规模

测量结论只对特定数据规模成立。**每次测之前先记录规模**，否则数字无法比较：

```bash
# 文章总数、未读数、订阅源数（走 App 自己的 DB，需要 debuggable 包）
adb shell run-as com.cycling.rssradar \
  sqlite3 databases/rssradar.db \
  "SELECT (SELECT COUNT(*) FROM feeds), (SELECT COUNT(*) FROM articles), (SELECT COUNT(*) FROM articles WHERE isRead = 0);"
```

目标规模：**feeds ≥ 1000、articles ≥ 30000**。低于这个量级，测出来的数字没有代表性。

### 固定变量

- 每次测量前**杀掉进程**（冷启动口径一致）：`adb shell am force-stop com.cycling.rssradar`
- 关掉省电模式、固定屏幕亮度、插着电测
- 每个场景**跑 3 次取中位数**。单次波动在真机上能到 ±30%，只跑一次的数字不可信。

---

## 1. 冷启动

```bash
# 完全冷启动（进程不存在 → 首帧绘制完成）
adb shell am force-stop com.cycling.rssradar
adb shell am start-activity -W -S com.cycling.rssradar/.MainActivity | grep TotalTime
```

`TotalTime` 是本次启动耗时（ms）。口径参考：

| 指标 | 合格 | 需要查 |
|---|---|---|
| 冷启动 TotalTime | < 1500 ms | > 3000 ms |

超过 3000 ms 时，先分清是**启动同步**还是**首屏渲染**。临时关掉启动同步（设置里关「启动时自动同步」）再测一次，差值就是同步占用的时间。

### 启动慢在哪

```bash
# 抓启动阶段的 trace，用 Android Studio 或 Perfetto 打开
adb shell am start -S --start-profiler /data/local/tmp/startup.trace \
  com.cycling.rssradar/.MainActivity
# 跑完后
adb pull /data/local/tmp/startup.trace
```

---

## 2. 滚动掉帧

滚动是 RSS 阅读器的主交互，掉帧比启动慢更影响体感。

```bash
# 打开信息流，滚到底，再导出帧统计
adb shell dumpsys gfxinfo com.cycling.rssradar reset
# （手动滚动列表 10 秒）
adb shell dumpsys gfxinfo com.cycling.rssradar
```

看 `Janky frames` 百分比：

| 指标 | 合格 | 需要查 |
|---|---|---|
| Janky frames | < 5% | > 15% |

`dumpsys gfxinfo` 的完整输出里还有 `Slow rendering` 段的逐帧耗时，超过 16.6 ms 的帧会列出来。

### 掉帧时先怀疑谁

按这个顺序排查，命中率从高到低：

1. **分页加载的下一页卡在主线程** —— 分页是 `suspend` + LaunchedEffect，正常应走 IO 线程。若某次滚动突然卡顿，典型症状是「滚到底部时顿一下」。
2. **文章卡片重组范围过大** —— 用 Layout Inspector 看单帧重组的 composable 数量。
3. **封面图加载** —— Coil 异步加载，理论上不阻塞。若图片源慢，会看到图片一张张跳出来（视觉问题，不是掉帧）。

---

## 3. 数据库查询

索引优化（#65）之后，主列表查询应该走索引、不再外排序。**这一步是唯一能直接验证索引生效的手段**：

```bash
adb shell run-as com.cycling.rssradar sqlite3 databases/rssradar.db \
  "EXPLAIN QUERY PLAN
   SELECT articles.id, articles.publishedAt FROM articles
   JOIN feeds ON articles.feedId = feeds.id
   ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
   LIMIT 30 OFFSET 300;"
```

**期望输出**（关键看后两行）：

```
SCAN articles
SEARCH feeds USING INTEGER PRIMARY KEY (rowid=?)
```

命中索引时会出现：

```
SCAN articles USING INDEX index_articles_publishedAt_fetchedAt
```

**绝不能出现**：

```
USE TEMP B-TREE FOR ORDER BY     ← 索引没生效，回到全表外排序
```

如果看到 `USE TEMP B-TREE FOR ORDER BY`，说明 ORDER BY 写法又退化了（最常见的退化方式是在排序里加表达式，比如 `publishedAt IS NULL`）。

### 直接量查询耗时

```bash
adb shell run-as com.cycling.rssradar sqlite3 databases/rssradar.db \
  ".timer on" \
  "SELECT COUNT(*) FROM (SELECT articles.id FROM articles
    JOIN feeds ON articles.feedId = feeds.id
    ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
    LIMIT 30 OFFSET 300);"
```

对比 `OFFSET 0` / `OFFSET 300` / `OFFSET 3000` 三档。有索引时三档应该在同一量级；无索引时耗时会随 OFFSET 线性增长。

---

## 4. 已知未优化项

这些问题**已经评估过，决定不修**。真机实测如果它们成为瓶颈，再回来处理。

| 查询 | 为什么索引无效 | 为什么不修 |
|---|---|---|
| `loadRecommendationCandidates` | `COALESCE(publishedAt, fetchedAt)` 是表达式 | 后台低频、LIMIT 有限，加索引要付出写入代价不划算 |
| `countByFeedSince` | 同上，且 `GROUP BY feedId` | 同上 |
| `loadEngagementSamples` | `COALESCE(lastOpenedAt, fetchedAt)` | 同上 |

要修它们需要 SQLite 表达式索引（Android 11+ 的 SQLite 3.32 支持），但 Room 的 `@Index` 声明不了表达式，只能在 migration 里手写 SQL，且会永久增加写入成本。**等真机数据证明它们慢到影响体验再动。**

---

## 5. 内存

历史 OOM 教训（ADR-0007）决定了这块必须持续盯。

```bash
adb shell dumpsys meminfo com.cycling.rssradar
```

重点看 `TOTAL PSS`。在信息流里连续滚动 5 分钟、进出详情页 20 次之后再看一次：

| 指标 | 合格 | 需要查 |
|---|---|---|
| 滚动 5 分钟后 TOTAL PSS | < 250 MB | 持续增长不回落 |

**关键看「不回落」而非绝对值**：进出详情页反复操作后，PSS 应该回到基线附近。持续攀升 = 泄漏。

分页加载（ADR-0006）的设计前提是「任何时刻只在内存里保留有限页」，如果 PSS 随滚动距离单调增长，说明分页快照没生效。

---

## 6. 记录模板

测完把结果填到这里，注明设备和规模。没有数字的性能结论等于没结论。

```
设备：
Android 版本：
feeds / articles 规模：
日期：

冷启动 TotalTime（3 次中位数）：     ms
滚动 Janky frames：                  %
OFFSET 0 / 300 / 3000 查询耗时：     /     /     ms
EXPLAIN QUERY PLAN 是否走索引：      是 / 否
滚动 5 分钟后 TOTAL PSS：            MB
```
