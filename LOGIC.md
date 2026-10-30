# PermKeeper 逻辑说明

> 依据源码逐条核对（2026-10-30），行号对应当前工作区状态。
> 文件：`zsrc/com/permkeeper/z/*.java`、`zygisk/zygisk_module.cpp`、`module/*.sh`、`webroot/index.html`

---

## 1. 一句话定位

**没有常驻轮询**。靠 Zygisk 注入 `system_server`，平时被动监听文件变化刷新基线；
只在收到系统「应用数据被清除」广播时，把基线里的设置重新写回该应用。

---

## 2. 运行形态

```
Magisk/KernelSU
  └─ zygisk/arm64-v8a.so
       └─ postServerSpecialize()            ← 只在 system_server 里跑
            └─ InMemoryDexClassLoader 加载内嵌 dex（dex_data.h）
                 └─ SystemHook.install()
                      └─ initLoop：每 500ms 试一次，最多 600 次（≈5 分钟）
```

- 只注入 `system_server`，**不注入普通应用**（`zygisk_module.cpp:79`）。
  Android 里 `grantRuntimePermission` / `setMode` / 通知渠道这些接口，只有系统身份调得动。
- 为什么要 Zygisk 而不是 LSPosed：该机 LSPosed 无法注入 system_server。
- 初始化顺序（`SystemHook.java:88` `trySetup`）：
  1. `scan()` 首轮建基线
  2. 挂 `FileObserver`（权限存储、ROM 额外路径、`/data/app`、通知配置）
  3. 挂导出请求观察者
  4. 注册清数据广播接收器

---

## 3. 数据文件

全部在 `/data/system/permkeeper/`，属主 `system:system`，权限 600/700。

| 文件 | 谁写 | 内容 |
|---|---|---|
| `config.json` | WebUI（`sh put`）/ 模块首次创建 | 保护范围 + 类别开关 + 每应用覆盖 |
| `baseline.json` | 模块 | **受保护应用**的最近一次状态快照 |
| `apps.json` | 模块 `Meta.write()` | 应用列表：包名 + 名称（不含图标，保持 ~30KB） |
| `labels.json` | 模块 `Meta.write()` | 包名 → 显示名缓存，**只增不删**（已卸载的还能显示名字） |
| `icon/<pkg>.png` | 模块 `Meta.write()` | 逐应用图标，每次重建先清空 |
| `export.req` | WebUI | 空文件，写一下 = 请求导出 |
| `export.config` | WebUI | 导出时把当前配置交给模块 |
| `export.progress` | 模块 | `已完成/总数`，导出中实时刷 |
| `export.done` | 模块 | `已完成|总数|耗时ms` |
| `export.bundle.json` | 模块 | 最终导出包（含配置 + 全部应用快照） |
| `export.threads` | 手工 | 并行线程数，缺省 8 |

---

## 4. 配置模型（`Config.java`）

```jsonc
{
  "all": true,              // true = 默认保护全部，靠 exclude 挖洞
  "includeSystem": false,   // 是否把有启动入口的系统应用也算进来
  "apps": [],               // 老的 opt-in 白名单（all=false 时才用）
  "exclude": [ "…" ],       // 例外名单（受保护的反面）
  "categories": { permissions, notifications, appops, autostart },
  "overrides": { "<pkg>": { "cats": { "notifications": false } } }
}
```

判定链：

- `covers(pkg)`：`all ? !exclude.contains(pkg) : apps.contains(pkg)`（`Config.java:120`）
- `catFor(pkg, name)`：**每应用覆盖优先**，没有就落到全局类别开关（`Config.java:124`）

WebUI 侧还做了旧配置迁移：读到 `all=false` 的 opt-in 老格式时，自动转成 `all=true` + 其余全进 `exclude`（`index.html:351`）。

---

## 5. 采集：五类状态怎么读（`State.capture`，`State.java:47`）

| 类别 | 手段 | 说明 |
|---|---|---|
| **运行时权限** | `pm.checkPermission` | 只收 `protectionLevel == DANGEROUS` 且已授予的；同时记 `req`（清单里声明的） |
| **AppOps** | 反射 `checkOpNoThrow(op, uid, pkg)` | op 用**数字 id** 枚举，不用名字（Android 17 上名字重载已失效） |
| **通知渠道** | 反射 `INotificationManager` | key 形如 `ownerPkg\|uid\|channelId`；另存 `chanName` 用于重建 |
| **省电/Doze** | `PowerManager.isIgnoringBatteryOptimizations` | 布尔 |
| **自启动** | `Rom.readAutostart` | 一加读 XML；小米走 MIUIOP AppOp |

### AppOps 的两段扫描

```
op  0..200      → 特殊权限（SYSTEM_ALERT_WINDOW=24、WRITE_SETTINGS…）
                  但跳过「背后是危险运行时权限」的 op（用 opToPermission + protectionLevel 判定）
op 10000..10200 → 小米 MIUIOP（自启动 / 联网 / 后台弹出…）
```
只记**不等于 `MODE_DEFAULT`** 的项——默认值本来就不需要恢复，记了只会白写。

### 通知渠道的两个坑

1. **`ParceledListSlice.getList()` 必须用 `getDeclaredMethod` + `setAccessible`**。
   走公开的 `getMethod` 会返回 0 条（`Notif.java:42`）。
2. **OEM 代理渠道**：ColorOS 会把三方通知挂在 `mundo@<目标包>@<id>` 这类渠道下。
   `buildProxyIndex()` 扫描所有三方应用的渠道 id，把含目标包名的归到目标包名下（`SystemHook.java:572`）。
   HyperOS 的 `mipush|pkg|id` 走同一套逻辑。

---

## 6. 基线：什么时候写、写谁

`scan()`（`SystemHook.java:504`）在两个情况下被触发：

1. 启动时一次
2. `FileObserver` 看到变化 → 防抖 **2.5 秒** → 扫一次（`onChange`，`:487`）

**只记录「受保护」的应用**（`covered` 集合），不是全部。
这样周期扫描的代价跟受保护应用数量成正比，而不是跟装机量成正比。

导出的全量采集是另一条路（`exportAll()`），与保护范围无关。
另：清数据恢复完不会重新 `scan()`，而是把该包的**已知好快照直接写回** `baseline.json`，
防止期间有并发 `scan` 把半恢复状态记成基线。

---

## 7. 清数据恢复主流程（核心）

```
系统发 ACTION_PACKAGE_DATA_CLEARED
   ↓
onReceive （SystemHook.java:140）
   ├─ 取 EXTRA_UID 反推 userId
   ├─ userId != 0 → 直接跳过（分身/工作资料不支持，绝不拿主空间 uid 去动同名包）
   └─ 从 baseline 取该包快照 → onPackageCleared()
   ↓
onPackageCleared （:189）
   ├─ cfg.covers(pkg)?  不在保护范围就退出
   ├─ waitPermStoreSettled()       ← 等权限存储安静下来
   └─ 最多 3 轮：
        restoreAll()  →  capture()  →  verify()
        通过就 break，不通过等 900ms 再来
```

### 为什么有 `waitPermStoreSettled()`

HyperOS 的清数据**先发广播、后完成运行时权限重置**。
模块如果收到广播立刻恢复，恢复结果会被紧随其后的重置抹掉。
（一加是「先重置后广播」，所以没这个问题。）

判据（`:279`）：权限存储目录 `/data/misc_de/0/apexdata/com.android.permission`
的文件 mtime 连续 **1 秒不变**、且总耗时 ≥ **1.8 秒** 才算稳定；最长等 9 秒。

### 为什么要 3 轮校验

`verify()`（`State.java:520`）**逐类别**比对，不是只看权限：

```
perm:xxx   权限没回来
op:NNN     AppOp 不一致
chan:KEY   渠道 importance 不一致
doze       省电白名单没进去
autostart  自启动没恢复
```

以前只校验 `after.perms ⊇ snap.perms`，AppOps / 通知 / Doze 全失败也报 ok，故障被完全掩盖。
现在任何一项不达标都会重试，最终日志给出具体缺项：

```
clear-data com.xxx restored rounds=1 ok          ← 一轮就过
clear-data com.xxx restored rounds=3 incomplete: op:24 chan:…   ← 3 轮仍缺
```

恢复完还会把 `baseline` 再写回一次已知好快照（防止期间有 `scan` 把半恢复状态记成基线）。

---

## 8. 三个恢复语义（注意只有一个在用）

| 方法 | 语义 | 调用者 |
|---|---|---|
| `restoreAll` | 无条件把快照里的全部写一遍（幂等） | **清数据恢复**（唯一在用） |
| `restoreMissing` | 只补当前缺失的 | 无（死代码，源码注释已标注） |
| `enforceExact` | 双向对齐：快照有的授予、快照没有的**撤销** | 无（WebUI 的导入走 shell 命令，没调它） |

`restoreAll` 里对通知渠道有个关键细节（`State.java:484`）：

- **渠道还在** → 只改 `importance`，把**原对象**回传（`updateNotificationChannelForPackage`）。
  如果新建一个 `NotificationChannel` 再写回，名字/描述/声音/震动会全被打回默认，渠道名变成渠道 id。
- **渠道已被清数据删掉** → 用快照里的 `chanName` 重建；名字缺失才退回用 id，并打日志。

---

## 9. 各类别的具体写回方式

| 类别 | 写回 | 备注 |
|---|---|---|
| 权限 | 反射 `AppGlobals.getPackageManager()` 或 `ServiceManager.getService("permission")` 上的 `grantRuntimePermission` | 3 参数 / 4 参数两种签名都兼容（`:562`） |
| AppOps | 反射 `AppOpsManager.setMode(op, uid, pkg, mode)` | op 支持数字 id（已验证） |
| 通知 | `updateNotificationChannelForPackage`（改）/ `createNotificationChannels`（重建） | 重建要包 `ParceledListSlice` |
| Doze | `ServiceManager.getService("deviceidle")` → `addPowerSaveWhitelistApp` | 绕开取不到的 `com.android.server.*` |
| 自启动 | 一加：改 `/data/oplus/os/startup/startup_dynamic_list.xml` | 写前做 XML 合法性自检，产物非法就放弃写入（宁可这次不恢复，也不写坏系统文件） |

`doze` 一旦发现接口不可用会把 `sDozeDisabled` 置真，后续不再重试、也不计入失败——避免永远报 incomplete。

---

## 10. 导出（`exportAll` + WebUI）

```
WebUI:
  1. sh put 当前配置 → export.config
  2. 写 export.req（触发）
  3. 每 250ms 轮询 export.progress / export.done
  4. 完成后 cp export.bundle.json /sdcard/Download/permkeeper/permkeeper-<时间戳>.json
  5. 清理全部 export.* 临时文件
```

模块侧（`SystemHook.java:335`）：

- **采集全部已安装应用**（`getInstalledPackages`），跟 `exclude` / 保护范围**无关**
- 并行 **8 线程**（可被 `export.threads` 覆盖；≤1 走串行路径）
- 最终包在**设备端**合成 `{permkeeper:2, exportedAt, config, apps}`，
  WebView 只负责 `cp`——避免 1MB JSON 在 WebView 桥上来回搬

### 已修的两个并发问题（2026-10-23）

1. **一次请求跑两遍**：`FileObserver` 原先监听 `CREATE|MOVED_TO|CLOSE_WRITE`，
   一次写入触发多个事件 → 并发跑两遍 `exportAll`。
   现在只监听 `CLOSE_WRITE` + `AtomicBoolean EXPORTING` 互斥。
2. **进度条倒退**：每个采集线程各自持有一个 `done` 快照，慢线程会把大值覆盖成小值。
   现在 `writeProgress` 加单调递进守卫（`sProgressHigh` / `sProgressTotal`）。

实测：706 个应用 / 8.3 秒 / 进度严格递增 / 单次请求只触发一次。

---

## 11. 导入（两条路径）

### 11.1 导入配置（不动应用状态）

只把 `config` 写回 `config.json`，覆盖当前配置。

### 11.2 导入并恢复权限（`index.html:542`）

```
1. 若含 config → 先写回配置
2. 确定目标范围
     「全部应用」     = 忽略 exclude（默认选中）
     「仅受保护」     = 遵守 exclude
   未安装的跳过；包名不合法（防 root 命令注入）的跳过
3. 【阶段一】读取真实系统状态
     dumpsys package <pkg>  → 已授予的运行时权限
     appops get <pkg>       → AppOp（只取带数字 id 的，如 MIUIOP(10001)）
     每 20 个包一条命令批量跑，可并行
4. 逐应用算差集，生成命令
     权限：快照有当前无 → pm grant；当前有快照无 → pm revoke
     AppOps：值不同才下发；当前有但快照没有且非 DEFAULT → appops set default
5. 【阶段二】执行（16 路并行、按批），可取消
6. 写回 baseline
```

**关键设计：比对基准是真实系统状态，不是 baseline。**
拿 baseline 当基准的话，如果快照恰好等于 baseline，永远算出 0 差异，什么都不做。

---

## 12. WebUI（`webroot/index.html`，654 行）

- 桥接：`window.ksu.exec`（KernelSU）或 `window.magisk.exec`（Magisk），优先 KSU
- **没有「保存」按钮**：任何开关/勾选改动 → `scheduleSave()` 防抖 500ms → 自动写回
- 应用列表数据源：优先 `apps.json`（含名称），缺失才回退 `pm list packages`（无名称、慢）
- 图标：模块写到 `/data/system/permkeeper/icon/`，WebUI 用**一条 root 命令**同步到 `webroot/icon/`，
  再用 `<img src="icon/<pkg>.png?v=<ver>">` 原生加载（不走 exec 传数据）
- 导出/导入都走同一个进度层，语义：

| 场景 | 颜色 | 文案 |
|---|---|---|
| 成功 | 绿 | 导出成功 / 导入成功 |
| 真失败（有命令批次报错） | 红 | 导入完成，但有 N 批失败 |
| 用户主动取消 | 灰 | 已取消导入 |
| 未安装 / 不在范围跳过 | 不算失败 | 写进摘要 |

统一 2.5 秒消失。导出**不可取消**（按钮隐藏）；导入第二阶段可取消。

---

## 13. 已验证范围

| 设备 | 系统 | 结果 |
|---|---|---|
| Xiaomi 17 Pro Max (`2509FPN0BC`) | HyperOS 4 / Android 17 | 权限 4/4 恢复；京东 7 个通知子渠道逐项一致；A/B 对照确认渠道是模块恢复的 |
| Redmi K80 至尊版 (`25060RK16C`) | HyperOS 4 / Android 17 | 历史验证 |
| 一加 PLU110 | ColorOS 16 / Android 16 | 权限/通知/省电/自启动/联网均恢复 |

**A/B 对照方法**（唯一能定罪的方式）：自建测试 APK 建 3 个渠道 →
A 组移入 `exclude` 清数据后渠道变空（未恢复）；B 组受保护清数据后渠道全在（恢复）。
证明 `pm clear` 确实会清渠道，B 组是模块的功劳。

---

## 14. 已知限制

1. **多用户/分身不支持**：`userId != 0` 直接跳过，避免拿主空间 uid 误伤同名包。
2. **无障碍、默认应用、通知使用权**不覆盖（非 AppOps）。
3. **系统应用**默认不算，除非勾选「包含系统应用」且它有启动入口。
4. **自启动**：一加靠改 XML，小米靠 AppOps；其他 ROM 未适配。
5. 通知渠道**重建**时只能恢复 id + 显示名 + importance，声音/震动等属性回默认
   （原对象还在时不受影响，只改 importance）。

---

## 15. 发现但没动

核对时看到、但本次没改的：

1. **`restoreMissing()` 和 `enforceExact()` 是死代码**（无任何调用者，`State.java:233` / `:375`）。
   `enforceExact` 看起来是给导入准备的，但 WebUI 实际用 shell 命令实现，没调它。
   删掉能少 200 行，但涉及「导入的双向语义」，是否删除值得单独决定。
2. **`permkeeper.sh` 的 `export` / `list_exports` / `import` 三个子命令已废**：
   WebUI 只用到 `get` / `put` / `apps` / `labels`。
3. **`export.threads` 控制文件**（设备上当前值 `8`）没有 UI 入口，靠手工改。
   是做成用户可配、还是干脆去掉固定 8，待定。
