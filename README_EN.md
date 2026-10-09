# PermKeeper

[简体中文](README.md) | [English](README_EN.md)

A Zygisk module for Magisk / KernelSU / SukiSU. After an app's data is cleared, PermKeeper restores its previous settings.

## What it restores

- Runtime permissions
- AppOps (including vendor ops such as Xiaomi `MIUIOP`: autostart, network, background launch)
- Notifications (master switch and channels)
- Battery / Doze whitelist
- Autostart

State is recorded and restored per app, based on what each app actually has, so apps don't affect one another.

## How it works

The module injects into `system_server` and listens for `PACKAGE_DATA_CLEARED`. After the system finishes resetting permissions, it restores the previously recorded state. Some ROMs (e.g. HyperOS) broadcast before the reset completes, so the module waits for the permission store to settle, then verifies and retries.

## Requirements

- Root: Magisk / KernelSU / SukiSU with Zygisk enabled
- Tested: OnePlus ColorOS 16, Redmi HyperOS 4 (Android 17)

## Install

1. Install `permkeeper-zygisk.zip` in your root manager
2. Reboot

## Usage

1. Open the module WebUI (KsuWebUI, or the WebUI entry in your manager)
2. Select apps to protect. All are protected by default; unchecking an app excludes it
3. Changes apply and save automatically
4. Export / import are inside the WebUI:
   - **Export**: writes to `/sdcard/Download/permkeeper/`
   - **Import & restore**: overwrites each app's permissions from the snapshot (apps whose permission set changed are skipped)

## Uninstall

Remove the module in your manager and reboot. Generated caches (icons) are removed; `config.json` and exported files are kept.

## Layout

```
zsrc/         Java sources injected into system_server
zygisk/       Zygisk entry (C++) and API header
webroot/      WebUI (index.html)
module/       module.prop, post-fs-data.sh, uninstall.sh, permkeeper.sh
build_zygisk.py   offline build script
```

## Build

```
python build_zygisk.py
```

Requires JDK, Android SDK build-tools and NDK (paths at the top of `build_zygisk.py`). Output: `out/permkeeper-zygisk.zip`.

## Limitations

- Doze whitelist and vendor autostart handling differ per ROM (Xiaomi via AppOps, OnePlus via an XML file).
- Non-AppOps items such as accessibility services, default apps and notification access are not covered yet.
