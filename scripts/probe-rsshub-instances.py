"""
实测 RSSHub 公共实例，筛出真能出 feed 的，用来更新
RssHubInstanceStore.BUILTIN_INSTANCES。

用法：
    python scripts/probe-rsshub-instances.py

判据：只认 /zhihu/daily 返回 `<?xml` / `<rss` / `<feed` 开头的实例。
探活（/healthz）单独看没用——实测 rsshub.rssforever.com healthz 200，
但同一时刻 /zhihu/daily 直接读超时；反过来 rss.injahow.cn 的 healthz
是 404，feed 却正常。

用 /zhihu/daily 而不是纯本地路由，是因为它需要实例真去抓一次上游，
顺带反映出口能力；纯本地路由会高估实例可用度。

候选来源：
    https://docs.rsshub.app/guide/instances   （现行官方列表）
    https://rsshub.netlify.app/instances      （旧列表，新文档已移除的仍可能活着）

实测记录归档在 docs/rsshub-instances.md。
"""
import ssl
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"
PROBE_URL = "/zhihu/daily"  # 换成别的路由可以测不同出口
HEALTHZ_TIMEOUT = 6.0
FEED_TIMEOUT = 15.0

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

CANDIDATES = [
    # 官方现行列表（docs.rsshub.app）
    "https://rsshub.app",
    "https://rsshub.rssforever.com",
    "https://hub.slarker.me",
    "https://rsshub.pseudoyu.com",
    "https://rsshub.rss.tips",
    "https://rsshub.ktachibana.party",
    "https://rss.owo.nz",
    "https://rss.wudifeixue.com",
    "https://rss.littlebaby.life",
    "https://rsshub.henry.wang",
    "https://holoxx.f5.si",
    "https://rsshub.umzzz.com",
    "https://rsshub.isrss.com",
    "https://rsshub.email-once.com",
    "https://rss.datuan.dev",
    "https://rss.4040940.xyz",
    "https://rsshub.cups.moe",
    "https://rss.spriple.org",
    "https://rsshub-balancer.virworks.moe",
    # 旧列表（rsshub.netlify.app），新文档已移除，实测是否还活着
    "https://rsshub.feeded.xyz",
    "https://rsshub.liumingye.cn",
    "https://rsshub-instance.zeabur.app",
    "https://rss.fatpandac.com",
    "https://rsshub.friesport.ac.cn",
    "https://rsshub.atgw.io",
    "https://rsshub.mubibai.com",
    "https://rsshub.woodland.cafe",
    "https://rsshub.aierliz.xyz",
    # 不在官方列表里
    "https://rss.injahow.cn",
]


def get(url: str, timeout: float):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    started = time.time()
    try:
        r = urllib.request.urlopen(req, timeout=timeout, context=ctx)
        return r.status, r.read(4096), time.time() - started
    except urllib.error.HTTPError as e:
        return e.code, e.read(512), time.time() - started
    except Exception as e:
        return None, f"{type(e).__name__}: {e}".encode(), time.time() - started


def verdict_of(status, body: bytes) -> str:
    if status is None:
        return "unreachable"
    head = body[:400].decode("utf-8", "ignore").lstrip().lower()
    if head.startswith("<?xml") or "<rss" in head or "<feed" in head:
        return "feed"
    if status == 429:
        return "ratelimited"
    return f"http{status}"


def probe(host: str):
    hz_status, _, hz_t = get(host.rstrip("/") + "/healthz", HEALTHZ_TIMEOUT)
    f_status, f_body, f_t = get(host.rstrip("/") + PROBE_URL, FEED_TIMEOUT)
    return host, hz_status, hz_t, f_status, f_t, verdict_of(f_status, f_body), f_body[:90]


def main() -> None:
    with ThreadPoolExecutor(max_workers=10) as pool:
        results = list(pool.map(probe, CANDIDATES))

    print(f"{'host':40s} {'healthz':>8s} {PROBE_URL:>14s} {'verdict':>12s}  time")
    print("-" * 100)
    alive, dead = [], []
    for host, hz, hz_t, st, f_t, verdict, snip in results:
        (alive if verdict == "feed" else dead).append(host if verdict != "feed" else (host, hz_t, f_t))
        print(
            f"{host:40s} {str(hz):>8s} {str(st):>14s} {verdict:>12s}  "
            f"{hz_t:4.1f}s/{f_t:4.1f}s  {snip.decode('utf-8', 'ignore')[:60]!r}"
        )

    print(f"\n能出 feed：{len(alive)} / {len(CANDIDATES)}")
    for host, hz_t, f_t in sorted(alive, key=lambda x: x[2]):
        print(f"  {host:40s} healthz {hz_t:4.1f}s  feed {f_t:4.1f}s")
    print(f"不可用：{len(dead)}")
    for h in dead:
        print("  " + h)


if __name__ == "__main__":
    main()
