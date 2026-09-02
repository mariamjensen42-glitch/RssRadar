# RSSHub 公共实例实测记录

`RssHubInstanceStore.BUILTIN_INSTANCES` 的取值依据。实例状态随时在变，这份记录是**快照**，不是承诺。

## 结论先行

- 官方文档两处「公共实例」页合计 29 个候选，实测**只有 8–9 个能真返回 feed**（两次实测分别是 8 和 9）。
- 约一半（13 个）DNS 直接失败（`getaddrinfo failed`）—— 域名已经没了，不是临时故障。
- 官方主站 `rsshub.app` 在本机**完全不可达**（连接超时，24s 无响应），但它对部分网络是通的，所以仍保留在列表首位。
- **实例快慢会漂移，写死的名次没有意义。** 隔一小时复测，同一批实例的排序几乎全变了：

  | 实例 | 第 1 次 | 第 2 次 |
  |---|---|---|
  | rss.injahow.cn | 0.9s | 0.1s |
  | hub.slarker.me | 0.7s | 0.7s |
  | rsshub-balancer.virworks.moe | 1.9s | 0.6s |
  | rsshub.rssforever.com | **读超时** | 1.0s |

  所以 `detectFirstAvailable()` 改成按**当次探测耗时取最快者**，而不是按列表顺序取第一个可达的。列表顺序只用于设置页展示。

- **`/healthz` 200 不代表能出 feed**：`rsshub.rssforever.com` 第 1 次 `/zhihu/daily` 读超时，第 2 次 1.0s 正常。探活只是"还活着"的必要条件。

## 实测方法

对每个候选并发跑两个请求：

1. `GET /healthz` —— 探活，6s 超时
2. `GET /zhihu/daily` —— 真抓一条路由，15s 超时，看响应体是不是 `<?xml` / `<rss` / `<feed`

只认第 2 步真出 XML 的。`zhihu/daily` 需要实例去抓上游，所以它同时也粗略反映了实例的出口能力；纯本地路由（如 `/rsshub/routes`）会高估实例可用度，不采用。

## 结果（2026-09-02，本机网络）

### 第 1 次（GMT+8 09:20）—— 8 个能出 feed

| 实例 | /healthz | /zhihu/daily | 备注 |
|---|---|---|---|
| hub.slarker.me | 200 (0.8s) | 200 (0.7s) | 官方列表 |
| rss.injahow.cn | 404 (0.2s) | 200 (0.9s) | 不在官方列表；**/healthz 是 404** |
| rsshub.liumingye.cn | 200 (1.0s) | 200 (1.1s) | 旧文档列表 |
| rsshub.ktachibana.party | 200 (2.0s) | 200 (1.3s) | 官方列表 |
| rsshub.isrss.com | 200 (2.4s) | 200 (1.7s) | 官方列表 |
| rsshub.woodland.cafe | 200 (1.0s) | 200 (1.7s) | 旧文档列表 |
| rsshub.umzzz.com | 200 (1.8s) | 200 (1.8s) | 官方列表 |
| rsshub-balancer.virworks.moe | 200 (1.0s) | 200 (1.9s) | 官方列表；名字带 balancer，实际是单实例 |

### 第 2 次（GMT+8 10:30）—— 9 个，排序几乎全变

| 实例 | /healthz | /zhihu/daily |
|---|---|---|
| rss.injahow.cn | 0.1s | 0.1s |
| rsshub-balancer.virworks.moe | 0.6s | 0.6s |
| hub.slarker.me | 0.5s | 0.7s |
| rsshub.liumingye.cn | 1.3s | 0.7s |
| rsshub.ktachibana.party | 0.7s | 0.7s |
| rsshub.woodland.cafe | 0.7s | 1.0s |
| rsshub.rssforever.com | 0.9s | 1.0s |
| rsshub.umzzz.com | 0.9s | 1.3s |
| rsshub.isrss.com | 1.3s | 1.6s |

两次都不可用的 20 个见下一节。`rsshub.app` 两次都超时，但仍保留在内置列表首位——本机不可达不代表所有用户都不可达。

### 不可用（21 个）

| 现象 | 实例 |
|---|---|
| DNS 失败（域名已废） | rsshub.feeded.xyz、rss.fatpandac.com、rsshub.friesport.ac.cn、rsshub.atgw.io、rsshub.mubibai.com、rsshub.aierliz.xyz、rss.littlebaby.life、rsshub.henry.wang、rsshub.email-once.com |
| 连接/读超时 | rsshub.app、rsshub.pseudoyu.com |
| 502 Bad Gateway | rsshub.rss.tips、rss.owo.nz |
| 503（抓不到上游） | rss.wudifeixue.com |
| 523（源站不可达） | rss.spriple.org |
| 403 Cloudflare 拦截 | rss.datuan.dev |
| TLS 握手失败 | holoxx.f5.si |
| 返回 HTML 不是 feed | rsshub-instance.zeabur.app、rss.4040940.xyz、rsshub.cups.moe |

`rsshub.cups.moe` 和 `rss.4040940.xyz` 返回 200 但响应体是 HTML——可能是新版 RSSHub 的 Web UI，或反代把路由吞了。没有进一步确认，直接排除。

## 设计取舍

**为什么选中按探测耗时而不是列表名次？** 见上面两次实测对比——不到两小时，排序全变，还有一个实例从"读超时"恢复正常。写死的名次是过期信息，探测耗时是当次真实信号。列表顺序只用于设置页展示。

**为什么列表里的顺序还是按实测速度写？** 纯粹是展示可读性：设置页「内置镜像」列表从上往下大致由快到慢，用户手动挑时有个参考。

**为什么不探测真实路由？** 每个实例抓一次 `/zhihu/daily` 要 1–2s，10 个实例串行就是十几秒，还白白给公共实例加负载。`/healthz` 是 RSSHub 内置端���，便宜且够用——代价是会放过 `rss.wudifeixue.com` 这种 healthz 404 但 feed 503 的实例，可接受（它本来就不在列表里）。

**自建实例仍是最终出路。** 实测两个镜像对 `bilibili/user/video/:uid` 这类需要抓上游站点的路由都返回 503，瞎填参数也是 503——公共镜像对"抓不到上游"统一用 503 兜着，这类路由换实例也未必行。

## 复测

    python scripts/probe-rsshub-instances.py

并发 GET 每个候选的 `/healthz`（6s）与 `/zhihu/daily`（15s），只保留响应体以 `<?xml` / `<rss` / `<feed` 开头的，按 feed 耗时排序输出。候选清单写死在脚本顶部，来源是 RSSHub 官方文档：

- <https://docs.rsshub.app/guide/instances>（现行）
- <https://rsshub.netlify.app/instances>（旧）

官方列表会增删条目，隔一段时间照着官方文档更新一次 `CANDIDATES` 再跑。
