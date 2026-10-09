# PermKeeper

[简体中文](README.md) | [English](README_EN.md)

一个 Magisk / KernelSU / SukiSU 的 Zygisk 模块。应用执行“清除数据”后，自动把它清除前的设置恢复回来；另支持导出配置，导入后批量恢复全部应用的权限与设置。

## 恢复内容

- 运行时权限
- AppOps（含厂商自定义 op，如小米 `MIUIOP`：自启动、联网、后台弹出等）
- 通知（总开关与各通知渠道）
- 省电策略（Doze 白名单）
- 自启动

按每个应用**自身已有的状态**记录与恢复，不是固定清单，所以不同应用互不影响。

## 原理

模块注入 `system_server`，监听系统的 `PACKAGE_DATA_CLEARED` 广播；在系统完成权限重置后，按此前记录的状态回补。部分系统（如 HyperOS）会先发广播、后重置权限，模块会等权限存储稳定后再恢复，并校验重试。

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
4. 导出 / 导入在 WebUI 内：
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

## 已测试范围

- 系统：一加 ColorOS 16（Android 16）、红米 HyperOS 4（Android 17）
- 恢复内容：运行时权限、特殊权限（如“显示悬浮窗”）、AppOps（含小米 `MIUIOP`：自启动 / 联网 / 后台弹出等）、通知（总开关与渠道）、省电（Doze 白名单）、自启动

## 已知限制

- 上述范围**之外**（其它厂商 / 系统版本、未列出的 OEM 设置）**未经验证，可能不生效或表现不同**，请以实际设备为准。
- 无障碍服务、默认应用、通知使用权等**非 AppOps** 项暂不覆盖。
- Doze 白名单、厂商自启动的实现随系统而异（小米走 AppOps，一加走 XML 文件）。

## 第三方

- `zygisk/zygisk.hpp` 为 Zygisk 模块 API 头文件，版权归 John "topjohnwu" Wu，采用宽松许可（见文件头），原始版权声明予以保留。

## 许可证

本项目采用 [GPL-3.0](LICENSE) 许可证。


