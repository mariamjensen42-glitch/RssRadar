#!/usr/bin/env python3
"""跑 JVM 单元测试（不经过 gradle）。

check-kotlin.py 只做编译诊断，本脚本负责把编译产物真正跑起来：
先调 check-kotlin.py 全量编译（main + test），再用 JUnitCore 跑所有 *Test 类。

用法：
    python scripts/run-tests.py                      # 编译并跑全部测试
    python scripts/run-tests.py ReadingImagesTest    # 只跑类名含这些关键字的测试
    python scripts/run-tests.py --no-build           # 跳过编译，直接跑已有产物

说明：
- 依赖 jar 复用 check-kotlin.py 收集好的 build/kotlinc/cp/，android.jar 从 SDK 现取。
- android.jar 里的类都是 stub（调用就抛 RuntimeException），所以测试必须走
  Fake* 假实现，不能真的碰 Android 运行时。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WORK = ROOT / "build/kotlinc"
OUT = WORK / "out"
CP_DIR = WORK / "cp"
SDK = pathlib.Path(r"E:\SoftWare\SDK")


def android_jar() -> pathlib.Path:
    jars = sorted(SDK.glob("platforms/android-3*/android.jar"), key=lambda p: p.parent.name)
    if not jars:
        sys.exit(f"找不到 android.jar：{SDK}/platforms/")
    return jars[-1]


def classpath() -> str:
    entries = [str(OUT)]
    # 编译需要 coroutines-android（main 源码用 Dispatchers.Main），但它与 core-jvm 有重复类，
    # 运行时留着会让 runBlocking 解析到旧签名 → NoSuchMethodError。运行时只留 core-jvm。
    entries += [
        str(p) for p in pick_newest(
            [p for p in sorted(CP_DIR.glob("*.jar")) if "coroutines-android" not in p.name]
        )
    ]
    entries.append(str(android_jar()))
    return ";".join(entries)


VERSIONED = re.compile(r"^(.+)-(\d+(?:\.\d+)*)\.jar$")


def pick_newest(jars: list[pathlib.Path]) -> list[pathlib.Path]:
    """同一 artifact 只留版本最高的那个 jar。

    cp 目录里可能同时躺着 1.10.2 与 1.11.0（gradle 缓存同一 artifact 有多个 hash 目录），
    两份 BuildersKt 一起进 classpath 时，编译期按新版生成的调用会在运行时落到旧类上，
    报 NoSuchMethodError——看上去像代码坏了，实际是重复依赖。
    """
    best: dict[str, tuple[tuple[int, ...], pathlib.Path]] = {}
    for path in jars:
        m = VERSIONED.match(path.name)
        key = m.group(1) if m else path.stem
        version = tuple(int(x) for x in m.group(2).split(".")) if m else (0,)
        if key not in best or version > best[key][0]:
            best[key] = (version, path)
    return [p for _, p in best.values()]


def test_classes(keywords: list[str]) -> list[str]:
    names = []
    for path in OUT.rglob("*Test.class"):
        rel = path.relative_to(OUT).with_suffix("")
        fqcn = ".".join(rel.parts)
        if not keywords or any(k in fqcn for k in keywords):
            names.append(fqcn)
    return sorted(names)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("keywords", nargs="*", help="只跑类名含这些关键字的测试")
    parser.add_argument("--no-build", action="store_true", help="跳过编译，直接跑已有产物")
    args = parser.parse_args()

    if not args.no_build:
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts/check-kotlin.py")],
            cwd=str(ROOT),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        output = (result.stdout or "") + (result.stderr or "")
        if re.search(r"^  app.*\berror\b", output, flags=re.M):
            print("编译有错，先修编译再跑测试：")
            print("\n".join(l for l in output.splitlines() if "error" in l))
            return 1

    classes = test_classes(args.keywords)
    if not classes:
        print("没有匹配的测试类")
        return 1

    cmd = ["java", "-cp", classpath(), "org.junit.runner.JUnitCore", *classes]
    result = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True, encoding="utf-8", errors="replace")
    print((result.stdout or "") + (result.stderr or ""))
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
