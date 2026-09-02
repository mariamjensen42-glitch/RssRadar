"""抓取 RSS/Atom 原始样本到本地 samples 目录。

不做重试，失败即在结果里标注原因。
"""

import gzip
import io
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
import zlib

BASE = os.path.dirname(os.path.abspath(__file__))
TIMEOUT = 20

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)

TARGETS = [
    ("rsshub_zhihu_daily.xml", "https://rsshub.app/zhihu/daily"),
    ("rsshub_sspai_index.xml", "https://rsshub.app/sspai/index"),
    ("rsshub_github_kotlin_releases.xml", "https://rsshub.app/github/repos/JetBrains/kotlin/releases"),
    ("rsshub_hackernews_front.xml", "https://rsshub.app/hackernews/front"),
    ("plain_ruanyifeng_atom.xml", "http://www.ruanyifeng.com/blog/atom.xml"),
    ("plain_sspai_feed.xml", "https://sspai.com/feed"),
    ("plain_oschina_news.xml", "https://www.oschina.net/news/rss"),
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


def fetch(name: str, url: str) -> dict:
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
            raw = resp.read()
            body = decompress(raw, resp.headers.get("Content-Encoding", ""))
            status = resp.status
            ctype = resp.headers.get("Content-Type", "")
            final_url = resp.geturl()
    except urllib.error.HTTPError as e:
        return {
            "name": name, "url": url, "ok": False,
            "error": f"HTTP {e.code} {e.reason}",
            "elapsed": round(time.time() - t0, 2), "bytes": 0,
        }
    except Exception as e:
        return {
            "name": name, "url": url, "ok": False,
            "error": f"{type(e).__name__}: {e}",
            "elapsed": round(time.time() - t0, 2), "bytes": 0,
        }

    elapsed = round(time.time() - t0, 2)
    path = os.path.join(BASE, name)
    with open(path, "wb") as f:
        f.write(body)
    return {
        "name": name, "url": url, "ok": True, "status": status,
        "content_type": ctype, "final_url": final_url,
        "bytes": len(body), "elapsed": elapsed, "error": None,
    }


def main():
    results = []
    for name, url in TARGETS:
        r = fetch(name, url)
        results.append(r)
        flag = "OK  " if r["ok"] else "FAIL"
        extra = f"{r.get('bytes', 0)}B {r['elapsed']}s" if r["ok"] else r["error"]
        print(f"[{flag}] {name:38s} {extra}", flush=True)

    with open(os.path.join(BASE, "fetch_results.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
