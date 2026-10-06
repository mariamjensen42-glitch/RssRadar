#!/usr/bin/env python3
"""把某个 feature 的资源按归属搬进它的 res，并从 app res 删除独占项。

判据（SKILL「被依赖 >=2 次即非私有物」的落地）：
  被该 feature 侧文件引用  -> feature res 必须要副本（feature 摸不到 app 的 R）
  搬迁后 app 侧无引用        -> 从 app res 删除
  已在 core:ui/res 提供     -> 不搬，代码里改用别名 `import ...core.ui.R as UiR`（脚本只报告，不改代码）

feature 侧 = `<prefix>` 下的全部 Kotlin。**必须在 git mv 之后跑**：此时 ui/<x> 已不在 app，
app/src/test 的守门测试自然算 app 侧。

本脚本由 feature-feed-move.py 推广而来（feed 是第一站，硬编码了前缀与排除项）。
用法：
  python scripts/feature-res-move.py --prefix feature/article/src/ --res feature/article/src/main/res \
      [--exclude key1,key2] [--apply]
不加 --apply 只打印计划。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CORE_UI_RES = ROOT / 'core/ui/src/main/res'
APP_RES = ROOT / 'app/src/main/res'
SCAN_DIRS = ['app/src', 'core', 'feature']

REF = re.compile(r'\bR\.(string|plurals|array|drawable|mipmap|raw|xml)\.([A-Za-z0-9_]+)')
XML_REF = re.compile(r'@string/([A-Za-z0-9_]+)')
STRING_LINE = re.compile(r'\s*<(string|plurals)\s+name="([A-Za-z0-9_]+)"[^>]*>.*?</\1>\s*$')


def iter_kotlin():
    for base in SCAN_DIRS:
        d = ROOT / base
        if not d.is_dir():
            continue
        for p in d.rglob('*.kt'):
            if 'build' in p.parts:
                continue
            yield p


def collect_refs(prefix: str) -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    feature: dict[str, set[str]] = {}
    app: dict[str, set[str]] = {}
    for p in iter_kotlin():
        text = p.read_text(encoding='utf-8', errors='replace')
        keys = {m.group(2) for m in REF.finditer(text) if m.group(1) == 'string'}
        if not keys:
            continue
        rel = p.relative_to(ROOT).as_posix()
        bucket = feature if rel.startswith(prefix) else app
        for k in keys:
            bucket.setdefault(k, set()).add(rel)
    return feature, app


def collect_xml_refs() -> set[str]:
    keys: set[str] = set()
    for p in (ROOT / 'app/src').rglob('*.xml'):
        if p.name == 'strings.xml':
            continue
        keys |= set(XML_REF.findall(p.read_text(encoding='utf-8', errors='replace')))
    return keys


def res_keys(res_dir: pathlib.Path, lang: str) -> set[str]:
    f = res_dir / lang / 'strings.xml'
    if not f.is_file():
        return set()
    text = f.read_text(encoding='utf-8', errors='replace')
    return set(re.findall(r'<(?:string|plurals)\s+name="([A-Za-z0-9_]+)"', text))


def read_lines(p: pathlib.Path) -> list[str]:
    return p.read_text(encoding='utf-8').splitlines()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--prefix', required=True, help='feature 侧源码前缀，如 feature/article/src/')
    ap.add_argument('--res', required=True, help='feature 的 src/main/res 相对路径')
    ap.add_argument('--exclude', default='', help='额外排除的 key（逗号分隔，由 core:ui 或别处提供）')
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    prefix = args.prefix if args.prefix.endswith('/') else args.prefix + '/'
    feature_res = ROOT / args.res
    exclude = {k.strip() for k in args.exclude.split(',') if k.strip()}

    feature, app = collect_refs(prefix)
    xml_refs = collect_xml_refs()
    core_ui_keys = res_keys(CORE_UI_RES, 'values')

    feature_keys = sorted(
        k for k in feature
        if k not in core_ui_keys and k not in exclude
    )
    ui_alias_keys = sorted(k for k in feature if k in core_ui_keys or k in exclude)
    delete_keys = sorted(k for k in feature_keys if k not in app and k not in xml_refs)
    keep_keys = sorted(k for k in feature_keys if k in app or k in xml_refs)

    print(f'feature 侧（{prefix}）引用 string：{len(feature)} 项')
    print(f'  core:ui 已提供 ⇒ 代码改用 UiR（不搬）：{ui_alias_keys}')
    print(f'  feature res 需覆盖：{len(feature_keys)} 项')
    print(f'  其中 app 侧仍引用（两边都留副本）：{keep_keys}')
    print(f'app res 可删（仅 feature 侧引用）：{len(delete_keys)} 项')

    if not args.apply:
        print('\n[dry-run] 加 --apply 落盘')
        return 0

    for lang in ('values', 'values-en'):
        src = APP_RES / lang / 'strings.xml'
        by_key = {}
        for line in read_lines(src):
            m = STRING_LINE.match(line)
            if m:
                by_key.setdefault(m.group(2), line)
        missing = [k for k in feature_keys if k not in by_key]
        if missing:
            print(f'  !! {lang} 缺原文，跳过：{missing}')
            continue
        out = ['<resources>'] + [by_key[k] for k in feature_keys] + ['</resources>']
        d = feature_res / lang
        d.mkdir(parents=True, exist_ok=True)
        (d / 'strings.xml').write_text('\n'.join(out) + '\n', encoding='utf-8')
        print(f'写入 {d / "strings.xml"}（{len(feature_keys)} 条）')

    for lang in ('values', 'values-en'):
        p = APP_RES / lang / 'strings.xml'
        drop = set(delete_keys)
        kept = []
        for line in read_lines(p):
            m = STRING_LINE.match(line)
            if m and m.group(2) in drop:
                continue
            kept.append(line)
        p.write_text('\n'.join(kept) + '\n', encoding='utf-8')
        print(f'从 {p} 删除 {len(delete_keys)} 条')
    return 0


if __name__ == '__main__':
    sys.exit(main())
