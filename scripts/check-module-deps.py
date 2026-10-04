#!/usr/bin/env python3
"""模块依赖自检：从源码 import 静态推断 Gradle 模块依赖图，提前拦住 CI 才暴露的错误。

为什么需要它：check-kotlin.py 是**扁平 classpath** 编译 —— 所有源码挂在同一个 classpath 上，
它**看不见模块边界**。因此 `core:model` 反向 import `core:data`（构成 Gradle 循环）这类问题
它一条都不报，只有真实 Gradle 构建（CI）才失败。本脚本补上这段盲区。

检查项：
1. 循环依赖（Gradle 会直接构建失败）
2. core:model 必须自包含（纯数据模型，不得依赖任何其他模块）
3. core:ui 铁律：不得依赖 core:data / core:domain（主题与组件必须参数化）

用法：python scripts/check-module-deps.py
"""
from __future__ import annotations

import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

MODULES = {
    'core:model': 'core/model/src/main',
    'core:domain': 'core/domain/src/main',
    'core:data': 'core/data/src/main',
    'core:ui': 'core/ui/src/main',
    'core:navigation': 'core/navigation/src/main',
    'core:playback': 'core/playback/src/main',
    'feature:addsubscription': 'feature/addsubscription/src/main',
    'feature:annotations': 'feature/annotations/src/main',
    'feature:player': 'feature/player/src/main',
    'feature:library': 'feature/library/src/main',
    'feature:search': 'feature/search/src/main',
    'feature:settings': 'feature/settings/src/main',
    'feature:subscriptions': 'feature/subscriptions/src/main',
    'app': 'app/src/main',
}

# import 前缀 -> 所属模块。顺序敏感：长前缀在前，app 兜底放最后。
PREFIXES = [
    ('com.cycling.rssradar.core.model', 'core:model'),
    ('com.cycling.rssradar.core.domain', 'core:domain'),
    ('com.cycling.rssradar.core.data', 'core:data'),
    ('com.cycling.rssradar.core.ui', 'core:ui'),
    ('com.cycling.rssradar.core.navigation', 'core:navigation'),
    ('com.cycling.rssradar.core.playback', 'core:playback'),
    # feature 模块暂时保留 ui.* 包名（package 与模块名解耦是有意为之）。
    ('com.cycling.rssradar.ui.addsubscription', 'feature:addsubscription'),
    ('com.cycling.rssradar.ui.annotations', 'feature:annotations'),
    ('com.cycling.rssradar.ui.player', 'feature:player'),
    ('com.cycling.rssradar.ui.library', 'feature:library'),
    ('com.cycling.rssradar.ui.search', 'feature:search'),
    ('com.cycling.rssradar.ui.settings', 'feature:settings'),
    ('com.cycling.rssradar.ui.subscriptions', 'feature:subscriptions'),
    ('com.cycling.rssradar', 'app'),
]


def target_module(import_path: str) -> str | None:
    for prefix, mod in PREFIXES:
        if import_path == prefix or import_path.startswith(prefix + '.'):
            return mod
    return None


def build_graph() -> dict[str, set[str]]:
    graph: dict[str, set[str]] = {m: set() for m in MODULES}
    for mod, rel in MODULES.items():
        base = ROOT / rel
        if not base.is_dir():
            continue
        for root, _dirs, files in os.walk(base):
            for name in files:
                if not name.endswith('.kt'):
                    continue
                path = pathlib.Path(root) / name
                text = path.read_text(encoding='utf-8', errors='ignore')
                for line in text.splitlines():
                    m = re.match(r'\s*import\s+(com\.cycling\.rssradar[\w.]*)', line)
                    if not m:
                        continue
                    tgt = target_module(m.group(1))
                    if tgt and tgt != mod:
                        graph[mod].add(tgt)
    return graph


def main() -> int:
    graph = build_graph()

    print('模块依赖图（源码 import 实测）：')
    for mod in MODULES:
        deps = sorted(graph[mod])
        print(f'  {mod:24s} -> {", ".join(deps) if deps else "(无)"}')

    problems: list[str] = []
    for a in sorted(graph):
        for b in sorted(graph[a]):
            if a in graph.get(b, ()) and a < b:
                problems.append(f'循环依赖：{a} <-> {b}')

    if graph['core:model']:
        problems.append(f'core:model 必须自包含，却依赖 {sorted(graph["core:model"])}')

    bad_ui = graph['core:ui'] & {'core:data', 'core:domain'}
    if bad_ui:
        problems.append(f'core:ui 铁律违规：不得依赖 {sorted(bad_ui)}')

    print()
    if problems:
        print('发现问题：')
        for p in problems:
            print('  x ' + p)
        return 1
    print('OK：无循环依赖；core:model 自包含；core:ui 铁律合规')
    return 0


if __name__ == '__main__':
    sys.exit(main())
