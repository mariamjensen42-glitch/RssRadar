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

# 测试源根：与 check-kotlin.py 收的 test 源码保持一致。
TEST_ROOTS = [
    ROOT / "app/src/test/java",
    ROOT / "core/data/src/test",
    ROOT / "core/model/src/test",
    ROOT / "core/domain/src/test",
]

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)", re.M)
CLASS_RE = re.compile(
    r"^(?:internal\s+)?(?:open\s+|abstract\s+|sealed\s+|data\s+)*class\s+([A-Za-z_][A-Za-z0-9_]*)", re.M
)


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
    # R 桩（check-kotlin 生成的 res → R.java → jar）：UI 层测试会在运行时读
    # R.string.*（i18n 枚举映射，ADR-0017 §3），缺了它直接 NoClassDefFoundError。
    r_stub = WORK / "rstub.jar"
    if r_stub.exists():
        entries.append(str(r_stub))
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
    """从**源文件**推导测试类，而不是扫 out 目录里的 .class。

    扫 .class 会把已改名/已删除的测试留下的陈旧产物一起跑起来——源码里早就没有它了，
    那种"全绿"与当前代码无关（XxxTest.kt 改名后，旧 XxxTest.class 仍在 out 里，
    测试照旧全部通过）。这里改为读源文件的 package 声明推导 FQCN，
    并要求编译产物真实存在：产物缺失就显式告警，而不是静默少跑一个类。
    """
    names: list[str] = []
    for root in TEST_ROOTS:
        if not root.exists():
            continue
        for path in sorted(root.rglob("*Test.kt")):
            text = path.read_text(encoding="utf-8", errors="replace")
            m = PACKAGE_RE.search(text)
            if not m:
                print(f"  [warn] {path.relative_to(ROOT).as_posix()} 没有 package 声明，跳过")
                continue
            # 类名取源文件的行首声明，**不用文件名**。反例：FeedListSnapshotTest.kt 里装的是
            # ScrollSlotsTest / DayGroupsTest / CalendarDayLabelTest 三个类——按 stem 找产物必然
            # 落空，那些测试就在「全量」里被静默漏跑（Gradle/CI 按真实类名跑，只有本地会漏）。
            declared = [c for c in CLASS_RE.findall(text) if c.endswith("Test")]
            for cls in declared or [path.stem]:
                fqcn = f"{m.group(1)}.{cls}"
                if not OUT.joinpath(*fqcn.split(".")).with_suffix(".class").exists():
                    print(f"  [warn] {fqcn} 没有编译产物——源已改名或编译未通过，跳过")
                    continue
                if not keywords or any(k in fqcn for k in keywords):
                    names.append(fqcn)
    return sorted(set(names))


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
        # 以退出码为准。原判定 `^  app.*\berror\b` 只认 app 模块，core:* 编不过时
        # 一律放行 —— 于是测试跑的是上一次的旧字节码，结果"全绿"却与源码无关。
        # check-kotlin.py 现已把 error 与 warning 分开，非零即真有编译错误。
        if result.returncode != 0:
            print("编译有错，先修编译再跑测试：")
            print("\n".join(l for l in output.splitlines() if re.search(r"\berror\b", l)))
            return 1
    else:
        print("[--no-build] 跳过编译：跑的是 out/ 里的既有产物，不代表当前源码状态")

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
