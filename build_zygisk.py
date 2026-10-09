#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build the PermKeeper Zygisk (native) module.

javac -> d8 -> dex_data.h -> NDK clang++ .so -> Magisk module zip
"""
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get("ANDROID_HOME") or r"C:\Users\Administrator\AppData\Local\Android\Sdk"
BT = os.path.join(SDK, "build-tools", "36.1.0")
PLATFORM = os.path.join(SDK, "platforms", "android-36", "android.jar")
D8 = os.path.join(BT, "d8.bat")

TOOLCHAIN = os.path.join(HERE, "toolchain")
NDK = os.path.join(TOOLCHAIN, "android-ndk-r27c")
CLANG = os.path.join(NDK, "toolchains", "llvm", "prebuilt", "windows-x86_64", "bin",
                     "aarch64-linux-android29-clang++.cmd")

BUILD = os.path.join(HERE, "build_z")
OUT = os.path.join(HERE, "out")
MOD = os.path.join(OUT, "permkeeper-zygisk")


def run(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd))
    p = subprocess.run(cmd, **kw)
    if p.returncode != 0:
        print("!! failed:", " ".join(str(c) for c in cmd))
        sys.exit(p.returncode)


def gen_dex_header(dex_path, header_path):
    data = open(dex_path, "rb").read()
    with open(header_path, "w") as f:
        f.write("// auto-generated, do not edit\n")
        f.write("static const unsigned char dex_data[] = {\n")
        for i in range(0, len(data), 20):
            f.write(",".join(str(b) for b in data[i:i + 20]) + ",\n")
        f.write("};\n")
        f.write("static const unsigned int dex_data_len = %dU;\n" % len(data))


def main():
    if not os.path.exists(CLANG):
        print("NDK clang not found:", CLANG)
        sys.exit(1)
    shutil.rmtree(BUILD, ignore_errors=True)
    shutil.rmtree(MOD, ignore_errors=True)
    os.makedirs(os.path.join(BUILD, "classes"))
    os.makedirs(os.path.join(BUILD, "dex"))
    os.makedirs(os.path.join(MOD, "zygisk"))
    os.makedirs(OUT, exist_ok=True)

    # 1. compile java
    srcs = []
    for root, _, files in os.walk(os.path.join(HERE, "zsrc")):
        srcs += [os.path.join(root, f) for f in files if f.endswith(".java")]
    with open(os.path.join(BUILD, "sources.txt"), "w") as f:
        f.write("\n".join(srcs))
    run(["javac", "-nowarn", "-source", "11", "-target", "11", "-encoding", "UTF-8",
         "-classpath", PLATFORM, "-d", os.path.join(BUILD, "classes"),
         "@" + os.path.join(BUILD, "sources.txt")])

    # 2. dex
    classes_jar = os.path.join(BUILD, "classes.jar")
    run(["jar", "cf", classes_jar, "-C", os.path.join(BUILD, "classes"), "."])
    run([D8, "--lib", PLATFORM, "--min-api", "29",
         "--output", os.path.join(BUILD, "dex"), classes_jar])
    dex = os.path.join(BUILD, "dex", "classes.dex")
    if not os.path.exists(dex):
        print("no classes.dex")
        sys.exit(1)

    # 3. dex -> header
    header = os.path.join(HERE, "zygisk", "dex_data.h")
    gen_dex_header(dex, header)

    # 4. native .so
    so = os.path.join(MOD, "zygisk", "arm64-v8a.so")
    run([CLANG, "-shared", "-fPIC", "-O2", "-std=c++17", "-static-libstdc++",
         "-I", os.path.join(HERE, "zygisk"),
         "-o", so, os.path.join(HERE, "zygisk", "zygisk_module.cpp"),
         "-llog"])
    print("so size:", os.path.getsize(so))

    # 5. module.prop
    with open(os.path.join(MOD, "module.prop"), "w", newline="\n", encoding="utf-8") as f:
        f.write("id=permkeeper\n")
        f.write("name=PermKeeper\n")
        f.write("version=0.1-zygisk\n")
        f.write("versionCode=1\n")
        f.write("author=permkeeper\n")
        f.write("description=Clear-data 后保留应用运行时权限/通知开关（Zygisk, 注入 system_server）\n")

    # 5b. scripts + webui
    for sub, dest in (("module", ""), ("webroot", "webroot")):
        src = os.path.join(HERE, sub)
        if not os.path.isdir(src):
            continue
        for root, _, files in os.walk(src):
            for fn in files:
                s = os.path.join(root, fn)
                rel = os.path.relpath(s, src)
                dst = os.path.join(MOD, dest, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(s, dst)

    # 6. zip
    zp = os.path.join(OUT, "permkeeper-zygisk.zip")
    if os.path.exists(zp):
        os.remove(zp)
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(MOD):
            for fn in files:
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, MOD))
    print("BUILT zip:", zp, os.path.getsize(zp))


if __name__ == "__main__":
    main()
