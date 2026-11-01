# -*- coding: utf-8 -*-
"""清数据前的强制守卫：备份必须存在且非空，否则拒绝执行。

用法：
    import pkguard
    pkguard.require_backup("com.kuaibao.skuaidi")     # 没有合格备份就抛异常
    pkguard.clear("com.kuaibao.skuaidi")              # 只有前面过了才会清
"""
import subprocess

DEV = "3B663C00LY900000"
BK_ROOT = "/data/local/tmp/pk-guard"


def _sh(cmd, timeout=600):
    full = "su 0 sh -c " + "'" + cmd.replace("'", "'\\''") + "'"
    r = subprocess.run(["adb", "-s", DEV, "shell", full],
                       capture_output=True, text=True, encoding="utf-8",
                       errors="replace", timeout=timeout)
    return (r.stdout or "") + (r.stderr or "")


def backup(pkg, tag):
    """真备份：内部 data + user_de + 外部数据，并校验文件数。"""
    d = f"{BK_ROOT}/{tag}"
    _sh(f"rm -rf {d}; mkdir -p {d}; chmod 700 {d}")
    _sh(f"am force-stop {pkg}")
    n = 0
    for k, src in [("data", f"/data/data/{pkg}"),
                   ("de", f"/data/user_de/0/{pkg}"),
                   ("ext", f"/sdcard/Android/data/{pkg}")]:
        _sh(f"cp -a {src} {d}/{k} 2>/dev/null")
        n += int(_sh(f"find {d}/{k} -type f 2>/dev/null | wc -l").strip() or 0)
    _sh(f"sh -c 'echo {n} > {d}/.filecount'")
    return d, n


def require_backup(pkg, tag=None):
    """清数据前的硬闸门：备份目录必须存在、且文件数 > 0，否则抛异常。"""
    tag = tag or pkg
    d = f"{BK_ROOT}/{tag}"
    cnt = _sh(f"cat {d}/.filecount 2>/dev/null").strip()
    real = _sh(f"find {d} -type f 2>/dev/null | wc -l").strip()
    if not cnt or not real or int(real) <= 1:      # 只有 .filecount 一个文件 = 空备份
        raise RuntimeError(
            f"拒绝清数据：{pkg} 没有有效备份（{d}，文件数={real}）。"
            f"先调用 pkguard.backup()。")
    return d, int(real)


def clear(pkg, tag=None):
    """带守卫的 pm clear。"""
    require_backup(pkg, tag)
    out = _sh(f"pm clear {pkg}")
    return out.strip()
