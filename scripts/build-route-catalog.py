#!/usr/bin/env python3
"""生成 RSSHub 路由目录的内置快照（app/src/main/assets/rsshub-routes.json）。

数据源：https://docs.rsshub.app/routes.json —— RSSHub 文档站的机器可读路由元数据
（1979 个命名空间 / 3800 条路由）。实例侧的 /api/routes 实测不可用（官方 403、
公共镜像 503/404），故以文档站 JSON 为唯一来源。

输出的是「slim schema」：只保留目录浏览与拼 URL 需要的字段，8.4MB → ~1MB。
schema 与 Kotlin 侧 app/src/main/java/com/cycling/rssradar/data/rsshub/RouteCatalogFile.kt
严格对应，改动 schema 必须两边同步，并同步 ADR-0010 里的 schema 表。

用法：
    python scripts/build-route-catalog.py                 # 下载并生成
    python scripts/build-route-catalog.py --from CACHE    # 用已下载的原始 JSON
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/rsshub-routes.json"
SOURCE_URL = "https://docs.rsshub.app/routes.json"
SCHEMA_VERSION = 1

# 截断上限：与 Kotlin 侧 RouteCatalogSlimmer 的常量保持一致
DESC_LIMIT = 160
PARAM_DESC_LIMIT = 100
TITLE_LIMIT = 60
TOP_FEEDS_LIMIT = 3
OPTIONS_LIMIT = 12

PARAM_TOKEN = re.compile(r":(\w+)")


def flatten(text: str, limit: int) -> str:
    """压平 markdown 噪声，截断到 limit。"""
    if not text:
        return ""
    s = text.replace("\\n", " ")
    s = re.sub(r"[\r\n]+", " ", s)
    s = re.sub(r"[`*_>#]+", "", s)
    # RSSHub 文档爱用 ::: warning / ::: tip 提示块，压平后只剩一行噪音
    s = re.sub(r":::\s*\w*", "", s)
    s = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", s)
    s = re.sub(r"\s{2,}", " ", s).strip()
    if len(s) > limit:
        s = s[:limit].rstrip() + "…"
    return s


def parse_param_keys(path: str) -> list[str]:
    """取 path 里的参数名，顺序即表单顺序。

    花括号里可能嵌套（如 /discuz/:ver{[7x]}/:cid{[0-9]{2}}），只在花括号外匹配 :name。
    """
    keys: list[str] = []
    depth = 0
    for i, ch in enumerate(path):
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth = max(0, depth - 1)
        elif ch == ":" and depth == 0:
            m = PARAM_TOKEN.match(path, i)
            if m:
                keys.append(m.group(1))
    # 去重保序
    seen: set[str] = set()
    return [k for k in keys if not (k in seen or seen.add(k))]


def param_meta(raw: dict, key: str) -> tuple[str, list[dict], str | None] | None:
    """把 parameters 的某项压成 (说明, 可选值, 默认值)。值可能是纯字符串或对象。"""
    value = raw.get(key)
    if value is None:
        return None
    if isinstance(value, str):
        return (flatten(value, PARAM_DESC_LIMIT), [], None)
    if not isinstance(value, dict):
        return None
    desc = flatten(value.get("description") or "", PARAM_DESC_LIMIT)
    options = []
    for opt in (value.get("options") or [])[:OPTIONS_LIMIT]:
        if not isinstance(opt, dict):
            continue
        v, label = opt.get("value"), opt.get("label")
        if v is None:
            continue
        options.append({"v": str(v), "l": flatten(str(label if label is not None else v), 40)})
    default = value.get("default")
    return (desc, options, None if default is None else str(default))


def build_examples(route: dict, path: str) -> list[dict]:
    """示例订阅：优先实例上真实被订阅的 topFeeds（带标题），否则退回官方 example。"""
    top = route.get("topFeeds")
    feeds = top[2] if isinstance(top, list) and len(top) > 2 and isinstance(top[2], list) else []
    healthy = []
    broken = []
    for item in feeds:
        if not isinstance(item, dict):
            continue
        url = item.get("url") or ""
        if not url.startswith("rsshub://"):
            continue
        entry = {"p": "/" + url[len("rsshub://"):].lstrip("/")}
        title = flatten(item.get("title") or "", TITLE_LIMIT)
        if title:
            entry["t"] = title
        # 抓取出错的示例仍是可参考的 path，但排在健康示例后面
        (broken if item.get("errorMessage") else healthy).append(entry)
    picked = (healthy + broken)[:TOP_FEEDS_LIMIT]
    if picked:
        return picked
    example = route.get("example") or ""
    if example.startswith("/"):
        return [{"p": example}]
    return []


def slim(raw: dict) -> dict:
    namespaces = {}
    route_count = 0
    for ns_key, ns in raw.items():
        routes = []
        for path, route in (ns.get("routes") or {}).items():
            raw_params = route.get("parameters") or {}
            pm, po, pd = {}, {}, {}
            for key in parse_param_keys(path):
                meta = param_meta(raw_params, key)
                if meta is None:
                    continue  # 元数据里没写这个参数：留空，不要在快照里塞空串
                desc, options, default = meta
                if desc:
                    pm[key] = desc
                if options:
                    po[key] = options
                if default:
                    pd[key] = default
            entry = {"p": path, "n": flatten(route.get("name") or path, 80)}
            # 只写有值的字段：Kotlin 侧（encodeDefaults 实测为省略）与这里必须字节级一致，
            # 否则「内置快照 vs 在线更新」两份数据无法直接 diff
            if route.get("heat"):
                entry["h"] = route["heat"]
            if route.get("categories"):
                entry["c"] = route["categories"]
            desc = flatten(route.get("description") or "", DESC_LIMIT)
            if desc:
                entry["d"] = desc
            if pm:
                entry["pm"] = pm
            if po:
                entry["po"] = po
            if pd:
                entry["pd"] = pd
            examples = build_examples(route, path)
            if examples:
                entry["e"] = examples
            routes.append(entry)
        routes.sort(key=lambda r: -r.get("h", 0))
        route_count += len(routes)
        namespace_entry = {"n": flatten(ns.get("name") or ns_key, 60)}
        if ns.get("url"):
            namespace_entry["u"] = ns["url"]
        if ns.get("categories"):
            namespace_entry["c"] = ns["categories"]
        if ns.get("heat"):
            namespace_entry["h"] = ns["heat"]
        namespace_entry["r"] = routes
        namespaces[ns_key] = namespace_entry
    return {
        "v": SCHEMA_VERSION,
        "generatedAt": int(time.time() * 1000),
        "namespaces": namespaces,
    }, route_count


def download(url: str) -> dict:
    print(f"下载 {url} …")
    req = urllib.request.Request(url, headers={"User-Agent": "RssRadar/1.0 (+route catalog build)"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from", dest="src", help="已下载的原始 routes.json，跳过网络")
    parser.add_argument("--out", dest="out", default=str(OUT))
    args = parser.parse_args()

    raw = json.loads(pathlib.Path(args.src).read_text(encoding="utf-8")) if args.src else download(SOURCE_URL)
    slimmed, route_count = slim(raw)

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(slimmed, ensure_ascii=False, separators=(",", ":"))
    out.write_text(text, encoding="utf-8")

    size_mb = len(text.encode("utf-8")) / 1048576
    print(f"命名空间 {len(slimmed['namespaces'])} · 路由 {route_count} · {size_mb:.2f} MB → {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
