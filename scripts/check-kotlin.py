#!/usr/bin/env python3
"""用 kotlinc 直接编译校验（不经过 gradle）。

背景：本项目禁止用 gradle 编译，只能靠静态脚本交叉检查。但脚本抓不到类型错误，
于是在 gradle 缓存里翻出 kotlin-compiler-embeddable，配上 android.jar 与各依赖的
jar/aar，直接对源码做真正的编译诊断——能抓到 unresolved reference、类型不匹配、
重载选错这类静态脚本无能为力的问题。

用法：
    python scripts/check-kotlin.py                  # 编译 app/src/main + test 源码
    python scripts/check-kotlin.py --main-only      # 只编译主源码
    python scripts/check-kotlin.py --files a.kt b.kt

说明：
- 依赖版本从 gradle 缓存里按「版本号最大」挑，不读 libs.versions.toml 的解析结果；
  新增依赖时需要往 DEPS 里补一行。
- aar 会被解开，其中的 classes.jar 复制进 build/kotlinc/cp/。
- 只报 error 行；warning 忽略。
"""
from __future__ import annotations

import argparse
import glob
import os
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
# 真实 GRADLE_USER_HOME：gradle.properties / 环境变量指向 E:\SoftWare\GradleCache，
# 不是默认的 ~/.gradle，也不是别的盘上留下的旧副本（写错路径 = 依赖全丢，
# 报出上百条 unresolved reference 的假错误）。注意 files-2.1 下的 group 目录名
# 保留点号（io.coil-kt.coil3），不转成斜杠。
CACHE = pathlib.Path(r"E:\SoftWare\GradleCache\caches\modules-2\files-2.1")
SDK = pathlib.Path(r"E:\SoftWare\SDK")
WORK = ROOT / "build/kotlinc"

KOTLIN_VERSION = "2.2.10"

# 编译期需要的依赖（group, artifact, 版本前缀）。版本前缀为空时取缓存里最大的版本；
# 项目锁定的版本必须显式写出，否则会挑到缓存里别的项目留下的更高版本。
DEPS = [
    ("org.jetbrains.kotlin", "kotlin-stdlib", KOTLIN_VERSION),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", ""),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", ""),
    ("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.7"),
    ("org.jdom", "jdom2", ""),  # rome 的传递依赖，运行时也要，否则 SyndFeedInput 直接 NoClassDefFoundError
    ("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.7"),
    ("androidx.annotation", "annotation-jvm", ""),
    ("androidx.annotation", "annotation-experimental", ""),
    ("androidx.core", "core-ktx", ""),
    ("androidx.core", "core", ""),
    ("androidx.collection", "collection-jvm", ""),
    ("androidx.collection", "collection-ktx", ""),
    ("androidx.lifecycle", "lifecycle-runtime-ktx-android", ""),
    ("androidx.lifecycle", "lifecycle-runtime-android", ""),
    ("androidx.lifecycle", "lifecycle-viewmodel-android", ""),
    ("androidx.lifecycle", "lifecycle-viewmodel-compose-android", ""),
    ("androidx.lifecycle", "lifecycle-viewmodel-savedstate-android", ""),
    ("androidx.lifecycle", "lifecycle-common", ""),
    ("androidx.lifecycle", "lifecycle-common-jvm", ""),
    ("androidx.savedstate", "savedstate-ktx", ""),
    ("androidx.savedstate", "savedstate-android", ""),
    ("androidx.activity", "activity-compose", ""),
    ("androidx.activity", "activity", ""),
    ("androidx.compose.runtime", "runtime-android", ""),
    ("androidx.compose.runtime", "runtime-saveable-android", ""),
    ("androidx.compose.ui", "ui-android", ""),
    ("androidx.compose.ui", "ui-geometry-android", ""),
    ("androidx.compose.ui", "ui-graphics-android", ""),
    ("androidx.compose.ui", "ui-text-android", ""),
    ("androidx.compose.ui", "ui-unit-android", ""),
    ("androidx.compose.ui", "ui-tooling-preview-android", ""),
    ("androidx.compose.foundation", "foundation-android", ""),
    ("androidx.compose.foundation", "foundation-layout-android", ""),
    ("androidx.compose.material3", "material3-android", ""),
    ("androidx.compose.material", "material-icons-core-android", ""),
    ("androidx.compose.animation", "animation-android", ""),
    ("androidx.navigation", "navigation-compose-android", ""),
    ("androidx.navigation", "navigation-runtime-android", ""),
    ("androidx.navigation", "navigation-runtime-ktx", ""),
    ("androidx.navigation", "navigation-common-android", ""),
    ("androidx.navigationevent", "navigationevent-android", ""),
    ("androidx.room", "room-runtime-android", "2.7"),
    ("androidx.room", "room-common-jvm", "2.7"),
    ("androidx.room", "room-ktx", "2.7"),
    ("androidx.room", "room-ktx", ""),
    ("androidx.sqlite", "sqlite-android", ""),
    ("androidx.sqlite", "sqlite-framework-android", ""),
    ("androidx.work", "work-runtime-ktx", ""),
    ("androidx.work", "work-runtime", ""),
    ("androidx.hilt", "hilt-navigation-compose", ""),
    ("com.google.dagger", "dagger", "2.60"),
    ("com.google.dagger", "hilt-android", ""),
    ("com.google.dagger", "hilt-core", ""),
    ("javax.inject", "javax.inject", ""),
    ("jakarta.inject", "jakarta.inject-api", ""),
    # 只有 -android-debug 的 aar 里才带 Kotlin 类（Lucide object）；icons-lucide-android 那个 aar 纯资源。
    ("com.composables", "icons-lucide-cmp-android-debug", ""),
    ("com.rometools", "rome", ""),
    ("com.rometools", "rome-modules", ""),
    ("com.rometools", "rome-utils", ""),
    ("org.jsoup", "jsoup", ""),
    # rome 运行时要 slf4j：LoggerFactory 出现在 RSS092Parser 的静态初始化里。
    # 少了它，RssParserTest 会报 NoClassDefFoundError: org/slf4j/LoggerFactory——
    # 看起来像解析代码坏了，其实只是运行时 classpath 缺一个 jar（gradle 打包会自动带上）。
    ("org.slf4j", "slf4j-api", ""),
    ("com.squareup.okhttp3", "okhttp", ""),
    ("com.squareup.okio", "okio-jvm", ""),
    ("io.coil-kt.coil3", "coil-compose-android", "3.3"),
    ("io.coil-kt.coil3", "coil-compose-core-android", "3.3"),
    ("io.coil-kt.coil3", "coil-core-android", "3.3"),
    ("io.coil-kt.coil3", "coil-network-okhttp", "3.3"),
    ("net.dankito.readability4j", "readability4j", ""),
    ("org.jetbrains", "annotations", ""),
    ("junit", "junit", "4"),
    ("org.hamcrest", "hamcrest-core", "1.3"),
]

VERSION_DIR = re.compile(r"^\d+(\.\d+)*$")


def version_key(name: str) -> tuple:
    parts = name.split(".")
    return tuple(int(p) if p.isdigit() else 0 for p in parts)


def newest_version_dir(artifact_dir: pathlib.Path, version_prefix: str = "") -> pathlib.Path | None:
    candidates = [
        d for d in artifact_dir.iterdir()
        if d.is_dir() and VERSION_DIR.match(d.name) and d.name.startswith(version_prefix)
    ]
    if not candidates:
        return None
    return max(candidates, key=lambda d: version_key(d.name))


def collect_classpath(out_dir: pathlib.Path) -> list[str]:
    """把依赖 jar / aar 的 classes.jar 收集到 out_dir，返回可拼接的 classpath。"""
    out_dir.mkdir(parents=True, exist_ok=True)
    entries: list[str] = []

    android_jar = sorted(SDK.glob("platforms/android-3*/android.jar"), key=lambda p: p.parent.name)
    if not android_jar:
        sys.exit(f"找不到 android.jar：{SDK}/platforms/")
    entries.append(str(android_jar[-1]))

    for group, artifact, version_prefix in DEPS:
        # 缓存目录是 files-2.1/<group>/<artifact>/<version>/<hash>/，group 里的点不转成斜杠
        artifact_dir = CACHE / group / artifact
        if not artifact_dir.is_dir():
            print(f"  [warn] 缓存里没有 {group}:{artifact}")
            continue
        version_dir = newest_version_dir(artifact_dir, version_prefix)
        if version_dir is None:
            print(f"  [warn] {group}:{artifact} 没有版本目录")
            continue
        hash_dirs = [d for d in version_dir.iterdir() if d.is_dir()]
        found: list[pathlib.Path] = []
        for hash_dir in hash_dirs:
            found.extend(p for p in hash_dir.glob("*.jar") if "-sources" not in p.name and "-javadoc" not in p.name)
            found.extend(hash_dir.glob("*.aar"))
        if not found:
            print(f"  [warn] {group}:{artifact} 没有 jar/aar")
            continue
        for path in found:
            if path.suffix == ".jar":
                target = out_dir / f"{artifact}-{version_dir.name}.jar"
                if not target.exists():
                    shutil.copyfile(path, target)
                entries.append(str(target))
            else:
                target = out_dir / f"{artifact}-{version_dir.name}-classes.jar"
                if not target.exists():
                    with zipfile.ZipFile(path) as zf, open(target, "wb") as out:
                        out.write(zf.read("classes.jar"))
                entries.append(str(target))
    return entries


def find_compiler() -> pathlib.Path:
    pattern = (
        CACHE / "org.jetbrains.kotlin" / "kotlin-compiler-embeddable" / KOTLIN_VERSION / "*"
        / f"kotlin-compiler-embeddable-{KOTLIN_VERSION}.jar"
    )
    matches = glob.glob(str(pattern))
    if not matches:
        sys.exit(f"找不到 kotlin-compiler-embeddable {KOTLIN_VERSION}")
    return pathlib.Path(matches[0])


def find_compose_plugin() -> pathlib.Path | None:
    pattern = (
        CACHE / "org.jetbrains.kotlin" / "kotlin-compose-compiler-plugin-embeddable" / KOTLIN_VERSION / "*"
        / f"kotlin-compose-compiler-plugin-embeddable-{KOTLIN_VERSION}.jar"
    )
    matches = glob.glob(str(pattern))
    return pathlib.Path(matches[0]) if matches else None


def find_serialization_plugin() -> pathlib.Path | None:
    pattern = (
        CACHE / "org.jetbrains.kotlin" / "kotlin-serialization-compiler-plugin-embeddable" / KOTLIN_VERSION / "*"
        / f"kotlin-serialization-compiler-plugin-embeddable-{KOTLIN_VERSION}.jar"
    )
    matches = glob.glob(str(pattern))
    return pathlib.Path(matches[0]) if matches else None


def sources(root: pathlib.Path) -> list[str]:
    return [str(p) for p in sorted(root.rglob("*.kt"))]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--main-only", action="store_true", help="只编译主源码，跳过 test 源码")
    parser.add_argument("--files", nargs="*", help="只编译指定文件（相对仓库根）")
    args = parser.parse_args()

    print("收集依赖 …")
    classpath = collect_classpath(WORK / "cp")
    compiler = find_compiler()
    # 两个 Kotlin 编译器插件都得挂上：compose 管 @Composable，serialization 生成 @Serializable 的 serializer
    plugins = [p for p in (find_compose_plugin(), find_serialization_plugin()) if p]
    classpath.append(str(compiler))

    print(f"编译器 {compiler.name} · 插件 {len(plugins)} 个 · 依赖 {len(classpath)} 项")

    if args.files:
        files = [str(ROOT / f) for f in args.files]
    else:
        files = sources(ROOT / "app/src/main/java")
        if not args.main_only:
            files += sources(ROOT / "app/src/test/java")

    out = WORK / "out"
    out.mkdir(parents=True, exist_ok=True)

    cmd = [
        "java",
        "-Xmx3g",
        "-cp", os.pathsep.join(classpath),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib",
        "-jvm-target", "17",
        "-d", str(out),
    ]
    for plugin in plugins:
        cmd += [f"-Xplugin={plugin}"]
    cmd += ["-classpath", os.pathsep.join(classpath)]
    cmd += files

    print(f"编译 {len(files)} 个文件 …")
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    output = (result.stdout or "") + (result.stderr or "")
    errors = [line for line in output.splitlines() if "error:" in line or "warning:" in line and "never used" not in line]
    errors = [line for line in output.splitlines() if re.search(r"\berror\b|\bwarning\b", line)]

    if result.returncode == 0 and not errors:
        print("编译通过，0 error")
        return 0

    print(f"退出码 {result.returncode} · {len(errors)} 条诊断：")
    for line in errors[:120]:
        print("  " + line.strip())
    if len(errors) > 120:
        print(f"  … 还有 {len(errors) - 120} 条")
    return 1


if __name__ == "__main__":
    sys.exit(main())
