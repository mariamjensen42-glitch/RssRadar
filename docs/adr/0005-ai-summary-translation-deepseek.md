# AI 摘要持久化、AI 翻译会话级缓存；DeepSeek 走手写 HTTP client

详情页引入 AI 加持功能（摘要 + 翻译），由用户自备 DeepSeek API Key。三个相互独立的取舍：

## Status

accepted

## Considered Options

**结果存储：摘要持久化、翻译不落盘**

- 都持久化：整篇译文体积可达正文数倍，DB 膨胀风险大。否决。
- 都不持久化：每次打开文章重新花钱，违背成本敏感。否决。
- 摘要存 DB（articles 加 `aiSummary` 列，DB v5）、翻译仅会话内内存缓存（选）：摘要短（数百字）值得持久化且离线可见；译文随时可重译，重复成本由「会话内缓存 + 明确的重译按钮」控制。`aiSummary` 属于生成物，语义同用户状态——**刷新订阅源永不覆盖**。

**API 接入：手写 client，不引 SDK/Retrofit**

- 引 okhttp + retrofit / DeepSeek SDK：为一两个接口引 3+ 新依赖不值，SDK 的 Android 适配未知。否决。
- `HttpURLConnection` + 已有的 `kotlinx-serialization` 直调 OpenAI 兼容 `/chat/completions`（选）：与 RssParser 的手写网络风格一致，零新依赖。模型固定 `deepseek-chat`（语言组织任务不需要 reasoner 的长思考），不进设置页；设置页只管 API Key（SharedPreferences，对标 RssHubInstanceStore）。

**呈现：替换式翻译 + 常驻摘要卡片**

- 双语对照（段落级交替）：要求 LLM 按段落对齐输出，长文漏段/并段不可靠，token 翻倍。另开 issue，接受瑕疵。否决出 MVP。
- 替换式（选）：译文整体替换正文，顶部一键切回原文；目标语言固定简体中文，本地启发式（中文字符占比 >30%）预检命中则不调 API。
- 摘要卡片常驻（选）：无摘要显示生成按钮、生成中 loading、有摘要显示内容——空态不藏功能。
- 交互均为一次性调用（非流式）：3–10s 的等待用 loading 态覆盖足够，readTimeout 取 60s（LLM 生成慢，不能沿用抓取的 10s）；错误区分网络失败与 API 报错（401 引导检查 Key）。

## Consequences

- 长文输入超约 3 万字符截断，摘要尾部注明「基于前 N 字」——AI 不捏造原则的延伸：不假装读了全文。
- 后续扩展（双语对照、流式输出、文章问答）复用同一 client 与错误处理，各自另开 issue。
