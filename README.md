# PermKeeper

简体中文 · [English](#english)

一个 Magisk / KernelSU / SukiSU 的 Zygisk 模块。应用执行“清除数据”后，自动把它清除前的设置恢复回来。

## 恢复内容

- 运行时权限
- AppOps（含厂商自定义 op，如小米 `MIUIOP`：自启动、联网、后台弹出等）
- 通知（总开关与各通知渠道）
- 省电策略（Doze 白名单）
- 自启动

按每个应用**自身已有的状态**记录与恢复，不是固定清单，所以不同应用互不影响。

## 工作原理

模块注入 `system_server`，监听系统的 `PACKAGE_DATA_CLEARED` 广播；在系统完成权限重置后，按此前记录的状态回补。因为部分系统（如 HyperOS）会先发广播、后重置权限，模块会等权限存储稳定后再恢复，并校验重试。

## 环境要求

- 已 Root：Magisk / KernelSU / SukiSU，且启用 Zygisk
- 实测：一加 ColorOS 16、红米 HyperOS 4（Android 17）

## 安装

1. 在 Root 管理器中安装 `permkeeper-zygisk.zip`
2. 重启设备

## 使用

1. 打开模块的 WebUI（KsuWebUI，或管理器自带的 WebUI 入口）
2. 在“选择应用”里勾选需要保护的应用。默认全部保护，取消勾选即为“排除”
3. 改动即时生效并自动保存
4. 导出 / 导入配置在 WebUI 内：
   - **导出配置**：导出到 `/sdcard/Download/permkeeper/`
   - **导入并恢复权限设置**：按导出的快照覆盖各应用权限（权限集有变化的应用会跳过）

## 卸载

在管理器中卸载模块并重启。卸载会清除模块生成的缓存（图标等），保留 `config.json` 与导出的配置文件。

## 目录结构

```
zsrc/         注入 system_server 的 Java 源码
zygisk/       Zygisk 入口（C++）与 API 头
webroot/      WebUI（index.html）
module/       module.prop、post-fs-data.sh、uninstall.sh、permkeeper.sh
build_zygisk.py   离线构建脚本
```

## 构建

```
python build_zygisk.py
```

依赖：JDK、Android SDK build-tools、NDK（路径见 `build_zygisk.py` 顶部）。产物输出到 `out/permkeeper-zygisk.zip`。

## 已知限制

- Doze 白名单、厂商自启动的实现随系统而异（小米走 AppOps，一加走 XML 文件）。
- 无障碍服务、默认应用、通知使用权等非 AppOps 项暂不覆盖。

---

## English

A Zygisk module for Magisk / KernelSU / SukiSU. After an app's data is cleared, PermKeeper restores its previous settings.

### What it restores

- Runtime permissions
- AppOps (including vendor ops such as Xiaomi `MIUIOP`: autostart, network, background launch)
- Notifications (master switch and channels)
- Battery / Doze whitelist
- Autostart

State is recorded and restored per app, based on what each app actually has, so apps don't affect one another.

### How it works

The module injects into `system_server` and listens for `PACKAGE_DATA_CLEARED`. After the system finishes resetting permissions, it restores the previously recorded state. Some ROMs (e.g. HyperOS) broadcast before the reset completes, so the module waits for the permission store to settle, then verifies and retries.

### Requirements

- Root: Magisk / KernelSU / SukiSU with Zygisk enabled
- Tested: OnePlus ColorOS 16, Redmi HyperOS 4 (Android 17)

### Install

1. Install `permkeeper-zygisk.zip` in your root manager
2. Reboot

### Usage

1. Open the module WebUI (KsuWebUI, or the WebUI entry in your manager)
2. Select apps to protect. All are protected by default; unchecking an app excludes it
3. Changes apply and save automatically
4. Export / import are inside the WebUI:
   - **Export**: writes to `/sdcard/Download/permkeeper/`
   - **Import & restore**: overwrites each app's permissions from the snapshot (apps whose permission set changed are skipped)

### Uninstall

Remove the module in your manager and reboot. Generated caches (icons) are removed; `config.json` and exported files are kept.

### Layout

```
zsrc/         Java sources injected into system_server
zygisk/       Zygisk entry (C++) and API header
webroot/      WebUI (index.html)
module/       module.prop, post-fs-data.sh, uninstall.sh, permkeeper.sh
build_zygisk.py   offline build script
```

### Build

```
python build_zygisk.py
```

Requires JDK, Android SDK build-tools and NDK (paths at the top of `build_zygisk.py`). Output: `out/permkeeper-zygisk.zip`.

### Limitations

- Doze whitelist and vendor autostart handling differ per ROM (Xiaomi via AppOps, OnePlus via an XML file).
- Non-AppOps items such as accessibility services, default apps and notification access are not covered yet.
