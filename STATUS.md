# PermKeeper 状态

## 已完成并验证
- Zygisk 模块注入 system_server（`postServerSpecialize`）；`rom=oneplus`。
- 运行时权限：清数据后自动回补 ✅（`restored com.kuaibao.skuaidi`，`POST_NOTIFICATIONS` 保持 granted）。
- 通知（总开关 + 类别）：总开关随 `POST_NOTIFICATIONS` ✅；类别捕获+恢复 ✅（清掉 `com.renxiong.pushsim` 后冷重启 dumpsys 显示渠道被重建）。ColorOS "mundo" 代理渠道按 `@<目标包>@` 归属；创建用 `createNotificationChannels(String, ParceledListSlice)`。
- AppOps / 一加自启动（`startup_dynamic_list.xml`）✅。
- 全部应用 / **包含系统应用** / 新装自动纳入 ✅。
  - `includeSystem` 勾选后：用户可见系统应用进入列表并纳入保护；实测 baseline 从 68 → 87（含 com.android.settings / com.android.contacts）。
- 多系统抽象 `Rom`（oneplus/xiaomi/aosp）✅。
- 应用图标与名称：由 system_server 生成 `apps.json`（**等 `sys.boot_completed` 后再生成**，否则 `getApplicationIcon` 返回默认图标）；图标已修复（各应用图标各不相同）✅。
- WebUI：仿 HyperOS 分组卡片 + iOS 开关、跟随系统深浅色；选择应用/导入为卡片→底部弹窗；弹窗内**搜索**、图标/名称/小字包名、**右侧勾选**；勾选为**居中 SVG 对勾**（弹性放大/对勾/行按压/弹窗弹性上滑/按钮缩放动画，尊重减弱动态效果）；导出/导入/保存二次确认。
  - 导入支持：粘贴 JSON、**选择 JSON 文件**（`<input type=file>`）、**扫描存储中的 JSON**（`find /sdcard/Download ...`，实测列出文件）✅。
- 稳定性回归：连开 6 应用 + 熄屏/亮屏，无 FATAL/ANR；模块日志 11–31 行；system_server 存活 ✅。
- **按应用 opt-in 细化** ✅（真机验证）：
  - **例外名单**：`all=true` 时勾选=保护、取消=例外（`exclude`）；被排除应用清数据后不恢复（无 `restored` 日志，READ_CONTACTS 保持 false）。
  - **每应用类别覆盖**：`overrides[pkg].cats` 覆盖全局类别；对 kuaibao 关闭 `permissions` 后清数据不恢复权限。
  - **每应用恢复时机**：`overrides[pkg].mode` 覆盖全局；全局 sticky 下把 kuaibao 设为 conservative，单项撤销被接受、不恢复。
  - **保守模式（多信号判定）**：缺失项 ≥2 视为清数据并恢复；≤1 视为用户手动改动并接受（更新基线）。实测：手动撤销单项不恢复；`pm clear` 同时丢多项（联系人+相机）后恢复。
  - WebUI：全局"保守恢复"开关；应用行点击进入详情弹窗（保护 / 类别 / 保守 / 清除覆盖）。
  - `permkeeper.sh get/put` 往返保留 `exclude`/`overrides`/`mode` ✅。

## 已知限制
- Doze 白名单恢复：`com.android.server.*` 对注入 classloader 不可见，已优雅跳过（一加本就保留 Doze）。
- 保守模式的基线语义：baseline 为"曾授予并集"，仅当缺失项瞬时 ≥2 才恢复；历史并集较大的应用（如 kuaibao）仍可能因累积差异触发恢复。清理 `baseline.json` 后会从当前状态重建。
- 本机 `pm clear` 保留 `POST_NOTIFICATIONS`（仅撤销联系人/相机等），故清数据实际缺失项可能少于预期。
- WebUI 宿主：Kitsune 需手动允许一次 KsuWebUI 的 su；KsuWebUI 的 `exec(cmd)` 只返回最后一行，须用回调形式 `exec(cmd,null,cb)`。

## 测试工作流
- 改逻辑需**冷重启**（zygiskd 缓存 `.so`）；改 WebUI 只需替换 `webroot/index.html`。
- 安装模块用 root：`pm install -r`。
- 设备坐标≈截图像素 ×1.42（用于自动化点击）。

## WebUI 走查（本会话）
- 环境限制：本 harness **无法读取 PNG 截图**（`read` 报 binary），`browser.screenshot` 需可见桌面窗口——故**真机视觉外观（HyperOS 风格/动画）未目视确认**，仅验证了前端逻辑与结构。
- 方法：本地静态服务器 (`python -m http.server` on 127.0.0.1:8137) 打开 `webroot/index.html`，用 `browser.snapshot`(无障碍树) + `browser.evaluate` 注入假 `ksu` 桥驱动。真机侧确认 KsuWebUI 能打开模块 `WebUIActivity`。
- 通过项：配置加载回显（all/includeSystem/mode/4 类别）、应用列表按 `includeSystem` 过滤、每应用详情回显覆盖（示例A: 权限=false/保守=false）、全选/全不选、搜索过滤、保守开关、`collect()` 结构、保存/导出/导入全链路（base64→put）、无控制台错误。
- 修复：①`包含系统应用` 切换后 `selSummary()` 未刷新（真 bug，已修）；②隐藏弹窗仅 `opacity:0` 仍在无障碍树（TalkBack 会读到），改为 `visibility` 切换（保留关闭动画）。
- 部署：`webroot/index.html` (20606B) 已推真机，md5 一致。

## 已卸载应用留存 + opt-out 列表（本会话）
- **留存（数据层）**：`Config` 的 `apps`/`exclude`/`overrides` 从不清理；`scan` 只遍历已安装包，已卸载包自然不匹配 → 配置留存且不报错。put/get 含不存在包实测保留、无 FATAL。
- **名称来源**：`Meta` 新增维护 `/data/system/permkeeper/labels.json`（包名→名称，**只增不减**缓存全部已安装应用标签）；后端 `permkeeper.sh labels` 输出该文件。
- **WebUI**：`load()` 读取 `labels.json`，把配置中引用的、不在已安装列表里的包补入列表，标 `installed:false`，显示 **名称 + 包名 + 「未安装」徽标**、首字占位图标（无需真实图标）；导入含未安装/未知包的配置**不异常**（实测无控制台错误）。
- **列表改为 opt-out**：去掉「全部应用」开关；列表**默认全部勾选**（保护），取消勾选=加入 `exclude`；点条目行（非勾选框）才进详情。`collect()` 恒写 `all=true`、`apps=[]`。旧的 opt-in 配置（`all=false`+`apps`）在加载时自动迁移为「全部保护 + 其余为例外」。
- 部署：`.so`(497360B，含 `labels.json` 生成) + `permkeeper.sh`(含 `labels` 命令) + `index.html` 已推真机，冷重启后 `apps.json written: 87 apps`、`labels.json` 23KB 生成。

## 搜索框键盘适配（本会话）
- 问题：搜索时软键盘弹出盖住应用列表。
- 方案：viewport 加 `interactive-widget=resizes-content`；`visualViewport` 的 `resize/scroll` 驱动 CSS 变量 `--kb`（键盘高度）与 `--sheet-max`（≈可视高度×0.92）——`.sheet` 底边抬到 `--kb` 之上，`.inner` 高度封顶，`#apps` 设 `flex:1 1 auto;min-height:0` 可滚动。
- 浏览器模拟验证：`--kb`=350 时弹窗底边精确=视口高−350，列表可滚动；无控制台错误。`index.html`(22494B) 已推真机，md5 一致。

### 键盘适配修正（真机根因）
- 真机实测：KsuWebUI 的 `WebUIActivity` 为 **边到边（EDGE_TO_EDGE_ENFORCED）**，键盘弹出时其窗口 `frame` **不变**（`mGivenVisibleInsets=[0,0][0,0]`）——KsuWebUI 未处理 IME insets，键盘**浮盖**在 WebView 上。故 `window.innerHeight` 与 `visualViewport` **都不反映键盘高度**，原 `visualViewport` 方案测到 0 → 无效。
- 修正：`fitViewport()` 在输入框聚焦时，若 embedder 未上报压缩，则按 **`baseH × 0.46`** 固定预留键盘高度（本机 IME 顶=1549px/2772 ≈ 44%，留余量）；若上报了真实差值仍用真实值。`focusin/focusout` 驱动。
- 真机验证：键盘弹出时 `@apps` 列表底部≈1565，恰停在键盘顶≈1549（未生效则≈2400+）；键盘收起恢复。

### 键盘适配：根因与最终方案（诊断循环）
- **真机实测根因**（页面内 `[DEBUG-kbd1]` 读数，经 `get_screen` 读取）：键盘弹出时 `ih=751 vv=752 off=0 rep=0` → **`window.innerHeight` 与 `visualViewport` 都不反映键盘**（该边到边 WebView 从不收到 IME inset），JS **无法测量键盘高度**。
- 由此定位两个症状：①缝隙——原预留 `0.46×751=345` > 实测键盘 `(2772−1549)/3.69≈331`，多出 ~14px 露出遮罩；②不还原——系统隐藏键盘（ESC/返回）时输入框**不失焦**，`focusout` 不触发，`act` 卡在 1。
- **修复**：正常高度固定（`--sheet-max`）；聚焦输入框时预留 `0.441×baseH≈331`（与实测键盘一致，缝隙≈0）；点/滚非输入区或按 Esc/Back 自动 `blur` → 还原；`closeAll()` 亦重置。`.inner` 增加 `padding-bottom:calc(var(--kb) + safe-area)` → sheet 背景向下填充到屏幕底，任何余量露出的是卡片底色而非遮罩。
- **回归测试**：本仓非 git、无测试框架、且需真机+IME，**无可自动化的正确 seam**（WebView 取不到 IME inset，逻辑只能在真机手动验证）。已用 `get_screen` 读数手工回归：键盘弹出 `kb=331 act=1`、点非输入区后 `kb=0 act=0`。调试代码 `[DEBUG-kbd1]` 已全部移除（grep=0）。

### 键盘适配最终定稿：不做自适应，固定高度（本会话，经 grilling 与用户确认）
- 需求共识：**无论聚焦/收起，sheet 高度与位置都不变**；高度固定 **80vh**（纯视口比例，天然适配大小屏）；键盘**浮盖**在列表下半部，搜索框在顶部始终可见，列表可滚动；保留"点/滚列表非输入区或 Esc/Back 自动收起键盘"。
- 实现：移除全部 `--kb`/`--sheet-max`/`visualViewport` 自适应与 `interactive-widget`；`.inner{max-height:80vh}`；仅保留 `_isField/_blurField` + `pointerdown/keydown(#apps scroll)` 收起键盘。
- 真机验证：`@apps` 在有无键盘时坐标完全一致（`y=1201,h=1571`）；sheet 顶≈554px（=屏高 20%，即 80vh）；搜索框 y=939 在键盘顶 1549 之上可见。浏览器模拟：聚焦前后 inner 均 560px（80vh）不变。

### WebUI 修复（本会话）
- **导出点击无效（根因）**：`保存/导出/导入` 的二次确认用原生 `confirm()`，KsuWebUI 的 WebView **不实现 JS 对话框** → `confirm()` 返回 false → 处理器 `return`，点击无反应（真机实测：点导出无对话框、日志无变化）。
- **修复**：新增**页面内确认弹窗** `askConfirm()`（`.cdlg`，任何 WebView 可用），替换三处 `confirm()`。真机实测：导出→弹「导出配置到 …」→确定→日志 `已导出: /sdcard/Download/permkeeper/config-….json`，文件已生成。
- **导入**：去掉「扫描存储中的 JSON」按钮与文件列表（保留「粘贴 / 选择 JSON 文件」），卡片描述同步更新。
- **保存**：把界面上的开关/类别/每应用覆盖/例外名单写成 `config.json`（`permkeeper.sh put`），模块据此生效；不点保存则界面改动不落盘。

### WebUI 交互改版（本会话）
- **去掉「保存」按钮 → 改动即自动保存**：任何改动（包含系统应用/保守/四类开关/例外勾选/全选全不选/每应用类别与恢复时机/清除覆盖）触发 `scheduleSave()`，**防抖 500ms** 后 `put`；写入期间若又有改动则排队再写（`_saveBusy/_savePending`）。日志限 12 行。
- **「导出」改为卡片**，样式与「导入配置」一致，名称 **「导出配置」**（`#cardExport`），点击弹页面内确认后执行 `permkeeper.sh export`。
- 真机验证：勾选「包含系统应用」→**未点保存**，`config.json` 即变为 `includeSystem:true`；关掉后自动还原。浏览器模拟：3 次连续改动合并为 1 次 `put`，内容正确。
- **点勾选框不再弹详情**：行勾选框是 `<label class="cbwrap">` 包隐藏 `<input>`，点 label 时 click 冒泡到整行 → 误触详情。在 label 上 `stopPropagation` 修复。真机验证：真实坐标点勾选框（1170,1315）**不弹详情**；点行内名称区（500,1315）仍正常打开详情。

### 本会话实现（implement 流程）
- **任务1 列表固定高度**：`#sheetSelect .inner{height:80vh;max-height:80vh}`，列表 `flex:1`。浏览器断言：全量 vs 过滤到 1 条，`.inner` 高均 675.19px（=80vh）。
- **任务2 摘要按实际**：`selSummary()` 现看 `excludeSet`：0→"全部保护"，N→"已排除 N 个应用"。真机显示"已排除 67 个应用"。
- **任务3 只认清数据**：模块注册 **`ACTION_PACKAGE_DATA_CLEARED`** 接收器，仅清数据时 `restoreAll` 整体恢复基线；**移除"保守恢复"**（全局+每应用+`Config.mode`）。基线=最近状态。真机：手动撤销不恢复；`pm clear` → `clear-data … restored`。
  - 竞态修复：广播 `onReceive` 里**立即捕获基线快照**再交给恢复任务（否则权限文件监听先触发的 scan 会把基线覆盖成"已清"）。
- **任务4 导入并恢复权限 + 导出快照**：导出=`{permkeeper:2,config,apps}`（apps=baseline 逐应用快照，含 `req`）。导入并恢复在 **WebUI 用 root shell** 执行 `pm grant/revoke` + `appops set`（模块无公开 revoke API，见下）；**严格覆盖 + req 不一致则跳过**。真机验证 `pm`/`appops`(root) 可用。
- **模块限制**：本版 Android 的 `IPackageManagerImpl` **没有公开/声明的 `revokeRuntimePermission`**（只有 `grantRuntimePermission`），故撤销走 root shell。
- **白盒测试子智能体**发现并已修 3 个缺陷：
  - **F3(高) 命令注入**：导入 JSON 的包名/权限名/op 名直接拼 root 命令 → 加白名单 `^[A-Za-z0-9_.]+$`，非法即跳过。复测：恶意包名/权限名**零命令发出**。
  - **F1(中)**：`collect()` 保留未知键（`mode`）→ 改为**白名单输出**。
  - **F2(中)**：`load()` 就地合并导致 `exclude/overrides/categories` 陈旧泄漏 → 改为**先重置默认再按字段合并**。
  - F4(低)：真机 baseline 对未覆盖(被排除)应用无 `req`（scan 只覆盖受保护应用）→ 这些应用在"导入并恢复"里本就会被跳过，暂不处理。

### 卸载残留检查（本会话）
- 真机扫描：模块目录 `/data/adb/modules/permkeeper/` 由 Magisk 卸载时删除；无 `modules_update`/`post-fs-data.d`/`service.d` 残留。
- **发现残留**：模块在模块目录外写了 `/data/system/permkeeper/`（config/baseline/labels/apps.json），**Magisk 不会删**。
- **修复**：新增 `module/uninstall.sh`（`rm -rf /data/system/permkeeper` + 清 `permkeeper_apply.json`），并入 zip。真机**安全测试**（移开真实目录→替身→跑脚本→验证→还原）：结果 `CLEANED`。
- 说明：`/sdcard/Download/permkeeper/` 里是**用户主动导出**的文件（非模块自动产物），卸载不删；模块对应用做过的权限/AppOps/Doze/自启动改动属于"应用当前状态"，卸载不回滚。

### 卸载清理定稿（按用户要求）
- 原则：**保留用户配置**（`config.json` 及 `/sdcard` 导出），**删除模块自动生成的被动产物**。
- `uninstall.sh` 最终内容：`rm -f /data/system/permkeeper/{baseline,apps,labels,apply}.json` + `permkeeper_apply.json`（**不再 `rm -rf` 整个目录**，config.json 保留）。
- 真机实测：跑 `uninstall.sh` 后目录只剩 `config.json`；重启后 `apps/baseline/labels` 自动重建、`config.json` 保留。
- zip 已更新（含新 `uninstall.sh`）并推送手机。

### 红米 K80 至尊版 / HyperOS4 适配 + 图标"无感"加载（本会话）
- 设备：Redmi 25060RK16C，Android 17(SDK37)，HyperOS OS4.0，KernelSU v3.2.5 + Zygisk（无 KsuWebUI）。
- **exec 桥**：KernelSU 的 `ksu.exec(cmd, "{}", 回调名)`（options 需 JSON 字符串）；原来传 `null` 在 KernelSU 上失败→列表空。改为 `"{}"`+超时兜底。
- **大包问题**：`apps.json` 曾内嵌图标=3.9MB，KernelSU WebView 传不动/很慢→回退到 `pm list packages`（无名称/图标、慢）。
- **定稿方案**：`apps.json` 只存名称（~29KB，秒开）；图标由模块生成到 `/data/system/permkeeper/icon/<pkg>.png`；WebUI 用**一条 root 命令**把它同步到 `webroot/icon/`（本地复制，**不过 exec 数据**），再用 **`<img src="icon/<pkg>.png?v=<ver>">` 原生相对加载**（白盒探测证实：`rel` 相对资源 OK、`file://` 绝对 FAIL）。
- 结果（用户确认）：**打开即完整、滚动零加载、全清晰**。
- **清理**：`uninstall.sh` 增加删除 `/data/system/permkeeper/icon` 与 `webroot/icon`；`config.json` 保留。

### 诊断：澎湃OS4 清数据后权限不恢复（本会话，diagnosing-bugs）
- **现象**：红米（HyperOS4 / Android17）清数据后权限完全不回补。
- **反馈回路**：`授权 CAMERA/READ_CONTACTS → 等待基线捕获 → pm clear → 查 dumpsys`（红=清后仍 false）。
- **根因（已证实）**：HyperOS 的清数据**先发 `PACKAGE_DATA_CLEARED` 广播、后完成运行时权限重置**。模块收到广播立刻恢复 → 被随后的重置抹掉。（一加是"先重置后广播"，故正常。）
  - 证据：插桩显示恢复瞬间模块自视 `after=[...CAMERA...]`（已授予），但外部 `dumpsys` 最终 false；**加 2s 延迟后清数据即成功**。
  - 权限存储：`/data/misc_de/0/apexdata/com.android.permission/access.abx` 在清数据后变化。
- **修复（方案③）**：`onPackageCleared` 先 `waitPermStoreSettled()`（权限存储"变化后静默 ≥1s 且至少 1.8s"才算稳定），再 `restoreAll`，并**校验 `after.perms ⊇ snap.perms`，最多重试 3 轮**兜底。
- **回归**：同一回路连续两次均 `granted=true`、日志 `clear-data … restored rounds=1 ok`。`[DEBUG-gr]` 插桩已移除（grep=0）。
- **无可单元化的正确 seam**：仅在真机+系统清数据路径暴露，无测试框架；以真机回路为回归手段。

### 澎湃OS4/HyperOS 五类设置适配（本会话）
- 侦察结论：`appops get` 显示该应用有大量 **MIUI 自定义 op：`MIUIOP(10001)…MIUIOP(10055)`**（自启动/后台/联网等在内）；省电白名单在 `/data/system/deviceidle.xml`；`cmd deviceidle` 支持 `whitelist`；`com.android.server.*` 对注入 classloader 不可见。
- 改动：
  1. **AppOps 全量抓取**：按 op **id 枚举**（`checkOpNoThrow(int,uid,pkg)`，因 Android17 上 name 重载已失效）；`0..130` 中**只跳过"危险(运行时)权限"对应的 op**（用 `opToPermission` + `PermissionInfo.protectionLevel` 判定），其余特殊 op（如 `SYSTEM_ALERT_WINDOW=24`、`WRITE_SETTINGS=23`）全部捕获；并枚举 `10000..10120` 的 MIUIOP。恢复用 `setMode(int, uid, pkg, mode)`。
  2. **省电（Doze）**：改走 `ServiceManager` 的 **`deviceidle` binder**（`addPowerSaveWhitelistApp`），绕开取不到的 `com.android.server.*`。
  3. 构建：`javac` 加 `-encoding UTF-8`（避免 GBK 编译非 ASCII 注释失败）。
- 真机结果（用户测+自测）：**权限管理（含"显示悬浮窗"）、通知、省电、自启动、联网均已恢复**；自测 `悬浮窗`：基线 `op24=0` → 清数据后 `SYSTEM_ALERT_WINDOW: allow`（`restored rounds=1 ok`）。
- **通用性**：按**每个应用自身的状态**抓取/恢复（非固定清单）——运行时权限全量、特殊 AppOp `0..200`、MIUI 自定义 op `10000..10200`（自启动/后台/联网等）、通知渠道、Doze、自启动。不同 app 的差异自动适配。真机 `com.kuaibao.skuaidi` 基线含 **143 个 appop**（含 op24 与 MIUIOP）。
















