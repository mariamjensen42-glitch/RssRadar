"""第二批：抓取常规（非 RSSHub）中英文 RSS/Atom 源。

其中包含两个“上游等价源”，可用来反推对应 RSSHub 路由的行为：
- github.com/JetBrains/kotlin/releases.atom  -> 对应 /github/repos/JetBrains/kotlin/releases
- hnrss.org/frontpage                        -> 对应 /hackernews/front
"""

import gzip
import json
import os
import ssl
import time
import urllib.error
import urllib.request
import zlib
from concurrent.futures import ThreadPoolExecutor

BASE = os.path.dirname(os.path.abspath(__file__))
TIMEOUT = 20

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)

TARGETS = [
    # 与 RSSHub 路由同源的上游
    ("upstream_github_kotlin_releases.atom", "https://github.com/JetBrains/kotlin/releases.atom"),
    ("upstream_hackernews_frontpage.xml", "https://hnrss.org/frontpage"),
    # 中文科技资讯
    ("cn_36kr.xml", "https://36kr.com/feed"),
    ("cn_huxiu.xml", "https://www.huxiu.com/rss/0.xml"),
    ("cn_ithome.xml", "https://www.ithome.com/rss/"),
    ("cn_ifanr.xml", "https://www.ifanr.com/feed"),
    ("cn_solidot.xml", "https://www.solidot.org/index.rss"),
    ("cn_linuxcn.xml", "https://linux.cn/feed/"),
    ("cn_infoq.xml", "https://www.infoq.cn/feed"),
    ("cn_meituan_tech.xml", "https://tech.meituan.com/feed/"),
    # 中文个人博客
    ("blog_coolshell.xml", "https://coolshell.cn/feed"),
    ("blog_yinwang.xml", "https://www.yinwang.org/atom.xml"),
    ("blog_codingnow.xml", "https://blog.codingnow.com/atom.xml"),
    ("blog_appinn.xml", "https://feeds.appinn.com/appinns/"),
    # 中文社区 / 论坛
    ("cn_geekpark.xml", "https://www.geekpark.net/rss"),
    # 英文对照
    ("en_rustlang_blog.xml", "https://blog.rust-lang.org/feed.xml"),
    ("en_androiddev_reddit.xml", "https://www.reddit.com/r/androiddev/.rss"),
]


def decompress(body: bytes, encoding: str) -> bytes:
    enc = (encoding or "").lower()
    if "gzip" in enc:
        try:
            return gzip.decompress(body)
        except Exception:
            return body
    if "deflate" in enc:
        try:
            return zlib.decompress(body, -zlib.MAX_WBITS)
        except Exception:
            return body
    return body


def fetch(item):
    name, url = item
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
            "Accept-Encoding": "gzip, deflate",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        },
    )
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT, context=ctx) as resp:
            body = decompress(resp.read(), resp.headers.get("Content-Encoding", ""))
            status, ctype, final = resp.status, resp.headers.get("Content-Type", ""), resp.geturl()
    except urllib.error.HTTPError as e:
        return {"name": name, "url": url, "ok": False, "error": f"HTTP {e.code} {e.reason}",
                "elapsed": round(time.time() - t0, 2), "bytes": 0}
    except Exception as e:
        return {"name": name, "url": url, "ok": False, "error": f"{type(e).__name__}: {e}",
                "elapsed": round(time.time() - t0, 2), "bytes": 0}

    with open(os.path.join(BASE, name), "wb") as f:
        f.write(body)
    return {"name": name, "url": url, "ok": True, "status": status, "content_type": ctype,
            "final_url": final, "bytes": len(body), "elapsed": round(time.time() - t0, 2), "error": None}


with ThreadPoolExecutor(max_workers=6) as ex:
    results = list(ex.map(fetch, TARGETS))

for r in results:
    if r["ok"]:
        print(f"[OK  ] {r['name']:38s} {r['bytes']:>8,}B {r['elapsed']}s")
    else:
        print(f"[FAIL] {r['name']:38s} {r['error']}")

with open(os.path.join(BASE, "fetch_results_2.json"), "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)
