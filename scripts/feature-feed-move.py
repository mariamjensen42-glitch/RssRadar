#!/usr/bin/env python3
"""把 ui/feed 的资源按归属搬进 feature:feed —— 生成 feature res，并从 app res 删除独占项。

判据（SKILL「被依赖 >=2 次即非私有物」的落地）：
  一条 key 被「feed 侧」文件引用  -> feature res 必须要副本（feature 摸不到 app 的 R）
  搬迁后 app 侧（仍留在 app 的代码，含 app 测试）无引用 -> 从 app res 删除

feed 侧 = feature/feed/src/ 下的全部 Kotlin（含随域搬入的 FeedTexts.kt）。
`back` 由 core:ui 提供（player 模块已立此规矩），代码改用别名 UiR，故不进 feature res。
脚本在搬迁**之后**跑：此时 ui/feed 已不在 app，app/src/test 的守门测试算 app 侧。

用法：python scripts/feature-feed-move.py [--apply]
      不加 --apply 只打印计划，不落盘。
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
FEED_PREFIX = 'feature/feed/src/'
EXCLUDE_KEYS = {'back'}

SCAN_DIRS = ['app/src', 'core', 'feature']
REF = re.compile(r'\bR\.(string|plurals|array|drawable|mipmap|raw|xml)\.([A-Za-z0-9_]+)')
XML_REF = re.compile(r'@string/([A-Za-z0-9_]+)')
STRING_LINE = re.compile(r'\s*<string name="([A-Za-z0-9_]+)"[^>]*>.*?</string>\s*$')

FEATURE_RES = ROOT / 'feature/feed/src/main/res'
APP_RES = ROOT / 'app/src/main/res'


def iter_kotlin():
    for base in SCAN_DIRS:
        d = ROOT / base
        if not d.is_dir():
            continue
        for p in d.rglob('*.kt'):
            if 'build' in p.parts:
                continue
            yield p


def collect_refs() -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    feed: dict[str, set[str]] = {}
    app: dict[str, set[str]] = {}
    for p in iter_kotlin():
        text = p.read_text(encoding='utf-8', errors='replace')
        keys = {m.group(2) for m in REF.finditer(text) if m.group(1) == 'string'}
        if not keys:
            continue
        rel = p.relative_to(ROOT).as_posix()
        bucket = feed if rel.startswith(FEED_PREFIX) else app
        for k in keys:
            bucket.setdefault(k, set()).add(rel)
    return feed, app


def read_res(path: pathlib.Path) -> list[str]:
    return path.read_text(encoding='utf-8').splitlines()


def build_feature_res(lang: str, keys: list[str]) -> list[str]:
    src = read_res(APP_RES / lang / 'strings.xml')
    by_key = {}
    for line in src:
        m = STRING_LINE.match(line)
        if m:
            by_key.setdefault(m.group(1), line)
    out = ['<resources>']
    missing = []
    for k in keys:
        if k in by_key:
            out.append(by_key[k])
        else:
            missing.append(k)
    out.append('</resources>')
    if missing:
        print(f'  !! {lang} 缺少 {len(missing)} 条原文：{sorted(missing)}')
    return out


def collect_xml_refs() -> set[str]:
    """非 strings.xml 的 xml（Manifest / 布局 / shortcuts）里 @string/xxx 也算 app 侧引用。"""
    keys: set[str] = set()
    for p in (ROOT / 'app/src').rglob('*.xml'):
        if p.name == 'strings.xml':
            continue
        keys |= set(XML_REF.findall(p.read_text(encoding='utf-8', errors='replace')))
    return keys


def main() -> int:
    apply_ = '--apply' in sys.argv
    feed, app = collect_refs()
    xml_refs = collect_xml_refs()

    feature_keys = sorted(k for k in feed if k not in EXCLUDE_KEYS)
    delete_keys = sorted(k for k in feature_keys if k not in app and k not in xml_refs)
    keep_in_app = sorted(k for k in feature_keys if k in app or k in xml_refs)

    print(f'feed 侧引用 string：{len(feed)} 项')
    print(f'  -> feature res 需覆盖：{len(feature_keys)} 项（排除 {sorted(EXCLUDE_KEYS & set(feed))}）')
    print(f'  -> 其中 app 侧仍在用（app res 留副本，feature 也留）：{keep_in_app}')
    print(f'app res 可删（仅在 feed 侧被引用）：{len(delete_keys)} 项')
    print('     ' + ', '.join(delete_keys))

    if not apply_:
        print('\n[dry-run] 加 --apply 落盘')
        return 0

    for lang in ('values', 'values-en'):
        d = FEATURE_RES / lang
        d.mkdir(parents=True, exist_ok=True)
        lines = build_feature_res(lang, feature_keys)
        (d / 'strings.xml').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        print(f'写入 {d / "strings.xml"}（{len(feature_keys)} 条）')

    for lang in ('values', 'values-en'):
        p = APP_RES / lang / 'strings.xml'
        drop = set(delete_keys)
        kept = [l for l in read_res(p) if not (STRING_LINE.match(l) and STRING_LINE.match(l).group(1) in drop)]
        p.write_text('\n'.join(kept) + '\n', encoding='utf-8')
        print(f'从 {p} 删除 {len(delete_keys)} 条')
    return 0


if __name__ == '__main__':
    sys.exit(main())
