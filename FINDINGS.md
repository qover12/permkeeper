# permkeeper — 一加(OnePlus/ColorOS) 权限保留模块 侦察结论

设备：OnePlus PLU110，Android 16 (SDK 36)，ColorOS `PLU110_16.0.9.401(CN01)`
Root：Magisk (Kitsune 27001) + 已装 LSPosed (`/data/adb/lspd`, `zygisk_lsposed`)
测试应用：`com.kuaibao.skuaidi` (快宝快递员, uid 10372)

## 1. 各类设置的权威存储位置（真机确认）

| 类别 | 存储 | 说明 |
|---|---|---|
| 运行时权限 | `/data/misc_de/0/apexdata/com.android.permission/runtime-permissions.xml` | 普通 XML；读写走 `pm grant/revoke` |
| AppOps | `/data/system/appops/`（目录：`discrete/`+`history/`，Android 14+ 新布局） | 读写走 `appops get/set`；无 `appops.xml` |
| 通知（含类别） | `/data/system/notification_policy.xml` | **ABX 二进制** + 一加私有字段(`app_banner`/`opush`/`fold`/`max_messages`…)，不能文本解析，必须走框架 API |
| 省电-Doze | `/data/system/deviceidle.xml`（普通 XML `<wl n=.../>`） | `dumpsys deviceidle whitelist +/-` |
| 省电-待机分组 | `/data/system/users/0/app_idle_stats.xml`（`appLimitBucket`） | `am get/set-standby-bucket` |
| **一加自启动(用户)** | `/data/oplus/os/startup/startup_dynamic_list.xml` | `<dynamic pkgName=.. type=1 source=1 switch=1/0/>`；`source=1`=用户, `source=8`=系统默认 |
| 一加后台/省电策略 | `/data/oplus/os/battery/*.xml`（`not_restrict`/`screenoff_restrict`/`power_consume_opt`…） | 需进一步确认哪些是用户态 |
| 一加通知拦截 | `/data/oplus/os/notification/sys_nms_intercept_blacklist.xml` | — |

定位方法：`touch /data/local/tmp/ref` → 拨动开关 → `find ... -newer ref`。

## 2. `pm clear` 实测（清数据重置了什么）

对同一 App 配置好后 `pm clear`，归一化状态 diff：

| 项 | 结果 |
|---|---|
| 运行时权限 (通知/定位/联系人/相机…) | **被重置** granted=true → false |
| 通知 App 级开关 | **被重置** importance=DEFAULT → NONE |
| 包级 AppOps（悬浮窗/后台运行/全文件访问…） | 保留 |
| 待机分组 | 保留 |
| Doze 白名单 | 保留 |
| 一加自启动 switch | 保留 |
| 通知 channels 本身 | 保留（但用户从未改过，locked=0） |

结论：**这台一加上，清数据主要重置"运行时权限 + 通知总开关"**；自启动/省电/显式 AppOps 本就保留（可作为防御性再确认项）。

## 3. 闭环恢复验证

1. 配置：授予 POST_NOTIFICATIONS / READ_CONTACTS / ACCESS_FINE_LOCATION，`SYSTEM_ALERT_WINDOW=allow`，Doze 白名单，自启动开。
2. `save` → 快照。
3. `pm clear`。
4. `restore`（`pm grant` 缺失权限 + 重新 apply appops/doze/standby）。
5. 状态 diff：**运行时权限、通知 App 级开关、Doze、自启动、待机 全部一致**。（diff 仅多出若干 AppOps 行，属于"授予权限后系统同步 appops"的表现，无害。）

## 4. 待办 / 未验证

- [ ] 从**设置界面**点"清除数据"是否与 `pm clear` 一致（本次按钮因数据为 0 而置灰，未测到）。
- [ ] **通知类别细分**：需先让 App 触发通知产生可见类别；CLI 无法改类别设置，留待 LSPosed 进程内用 `NotificationManagerService` API 验证。
- [ ] 小米/HyperOS：自启动是否走 AppOps 自定义 op(10001-10053)，待小米真机。
- [ ] 通用 AOSP/Pixel：无 OEM 层，仅权限/AppOps/Doze/待机/通知。
- [ ] 一加 `/data/oplus/os/battery/*` 用户态策略归属确认。

## 5. 工具

- `probe.py`：`snapshot/diff`（文件级）、`state/statediff`（归一化状态）、`save/restore`（跑通闭环）。
- `ui.py`：`list/find/tap/toggle`（UI 自动化，用于拨动无法用 CLI 改的 OEM 开关）。
