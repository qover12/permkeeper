# PermKeeper 健壮性 / 安全网复查

- 日期：2026-10-14
- 范围：**全部代码**（`zsrc` 7 个 Java + `zygisk` C++ 入口 + `module/*.sh` + `webroot/index.html` + `build_zygisk.py` + CI）
- 方法：静态审查为主。所有条目**逐条回到源码核对过**（下方每条的 `文件:行` 都是我本人看过的原文，不是转述）。
- 关注点：**健壮性 + 安全网**（异常路径 / 失败回滚 / 边界条件 / 误操作风险）。**不含**功能缺口与功能 backlog（见附录 A）。
- **状态：必修 6 项已修复**（2026-10-14 同日，见各条下的「✅ 已修」）；建议 / 可选未动。
- 本文件为新增文档，**未 commit**；修复本身也**未 commit**。
- 设备：本轮在线的是 `f2e7e0b0`（小米 24129PN74C / Android 17 / HyperOS OS4.0），与 `STATUS.md` 记录的测试机（一加 PLU110、红米 25060RK16C）**不是同一台**，故本轮**未做真机复现**，需要真机的项已在附录 B 标明。

## 严重度定义

| 级别 | 判据 |
|---|---|
| **必修** | 会造成数据丢失 / 核心功能静默失效 / 写坏系统文件 |
| **建议** | 不会立刻坏，但会掩盖故障、降低可诊断性、或在特定条件下退化 |
| **可选** | 卫生问题、死代码、构建/分发层面的改进 |

---

# 必修

## P0-1 恢复通知渠道时，把渠道**名字/描述/声音/震动等全部打回默认**

> **✅ 已修（2026-10-14）** — `State.java:32` 新增 `chanName`；`:118-131` capture 存显示名（含 proxy 渠道第 5 元素）；`:165-172` toJson；`:214-224` fromJson（老快照兼容）；`:484-505` `applyChannels` —— 渠道还在就回传**原对象**只改 importance，不再重建。快照 schema 新增可选字段，**向后兼容**。
> 未验：真机 `pm clear` 后设置界面里的渠道名是否真的保持中文。

**位置**
```java
State.java:447   Notif.update(svc, ownerPkg, ownerUid, new NotificationChannel(cid, cid, imp));
State.java:449   boolean ok = Notif.create(svc, ownerPkg, ownerUid, new NotificationChannel(cid, cid, imp));
```

**问题**：重建渠道时只带了 `id / name(=id) / importance` 三个字段，其余字段全是 `NotificationChannel` 的默认值。
而系统侧 `PreferencesHelper.updateNotificationChannel()` 是**整体替换**：

```java
r.channels.put(updatedChannel.getId(), updatedChannel);   // 不是字段合并
```

**触发**：任意一次清数据恢复，只要该应用快照里有渠道且 `sNotifReady`（`State.java:294` 的条件成立）。

**后果**
- 渠道**显示名变成渠道 id**（例：`mipush|com.taobao.idlefish|msa_notification_ad_com.taobao.idlefish`），设置界面里就是这么显示的
- 描述被清空、自定义声音 / 震动 / 灯 / 角标全部回默认
- 渠道仍然存在（所以用户不会「发现渠道没了」，只会发现名字变成一串乱码）
- 快照里**只存了 importance**（`State.java:113`：`s.channels.put(pkg+"|"+uid+"|"+ch.getId(), ch.getImportance())`），所以现在这份数据**不足以**还原名字

**证据**：本会话我在 Android 17 上实测过 `updateNotificationChannelForPackage` 的写入路径，并读过 `PreferencesHelper.updateNotificationChannel` 源码；`r.channels.put(...)` 整对象替换是确认的。
**未验证**：没有在真机走完整 `pm clear → 恢复 → 看设置界面` 的流程目视确认（需要真机 + 冷重启，见附录 B）。

**修法方向**：`applyChannels` 里对「已存在」的渠道，**先 `getNotificationChannelForPackage` 取回真实对象，只改 importance 再回写**（和 Settings 的做法一致）；对「已删除」的渠道，快照必须额外存下 `name`（以及需要保留的 sound/vibration/lights 等），否则无法还原。

---

## P0-2 状态文件全是非原子写；损坏后静默降级（基线损坏连日志都没有）

> **✅ 已修（2026-10-14）** — `SystemHook.java:435-462` 新增 `writeAtomic`（`.tmp` + `fsync` + `rename`）；`:469-481` 新增 `quarantine`（损坏文件改名 `.corrupt-<ts>`）；`:411-424` `loadBaseline` 补日志 + 留档；`Config.java:66-69`/`:106`；`Meta.java:90-96`（icon 也改 `.tmp`+rename）/`:106`/`:128`。所有状态写入已无裸 `FileOutputStream` 覆盖。

**位置**
```java
SystemHook.java:382  try (FileOutputStream fos = new FileOutputStream(BASE_FILE)) {   // baseline.json
Config.java:102      try (FileOutputStream fos = new FileOutputStream(FILE)) {        // config.json
Meta.java:101        try (FileOutputStream fos = new FileOutputStream(FILE)) {        // apps.json
Meta.java:127        try (FileOutputStream fos = new FileOutputStream(LABELS)) {      // labels.json
Meta.java:90         new FileOutputStream(new File(iconDir, pi.packageName + ".png")) // icon/*.png
```
直接 `FileOutputStream` 覆盖原文件，**没有临时文件 + rename**。

**触发**：写入过程中 `system_server` 被杀 / 设备掉电 / 存储写满。

**后果**
- 文件被截断 → 下次读取失败
- `baseline.json` 损坏时：
```java
SystemHook.java:374  } catch (Throwable t) {
SystemHook.java:375      return new JSONObject();     // ← 连日志都不打
```
  → **静默丢失全部基线**，此后所有应用清数据都不再恢复，用户零感知
- `config.json` 损坏时：
```java
Config.java:65  } catch (Throwable t) {
Config.java:66      SystemHook.log("config load failed: " + t);
```
  → 有 logcat 日志，但回退成 `all=false`（`Config.java:22`）＝**不保护任何应用**，用户侧没有任何提示

**修法方向**：写临时文件 → `fsync` → `rename`（同目录原子替换）；`loadBaseline` 失败时至少 `log()`，并把损坏文件改名备份（`.corrupt`）而不是静默返回空。

---

## P0-3 恢复结果只校验「权限」一项，其它类别失败也会报 `ok`

> **✅ 已修（2026-10-14）** — `State.java:520-552` 新增 `verify()`：permissions / appops / channels / doze / autostart **逐类别断言**；`SystemHook.java:225` 改用 `verify()`；`:236` 日志按类别输出未达标项。`sNotifReady==false` 与 `sDozeDisabled` 时标 `skipped`，避免永远报 incomplete。
> 未验：新判定在真机上会不会误报（需要跑一次真实清数据看日志）。

**位置**
```java
SystemHook.java:185      if (after.perms.containsAll(snap.perms)) {
SystemHook.java:186          ok = true;
SystemHook.java:187          break;
SystemHook.java:197      log("clear-data " + pkg + " restored rounds=" + rounds + (ok ? " ok" : " incomplete"));
```
校验只看 `after.perms ⊇ snap.perms`。而 `restoreAll` 里 AppOps / 通知 / Doze / 自启动都只是「尽力而为」：

```java
State.java:428  } catch (Throwable t) {
State.java:429      SystemHook.log("setMode " + e.getKey() + " failed: " + t);   // 只记日志
State.java:455  return true;   // applyChannels：只要 chans 非空就返回 true
```

**触发**：通知/省电/自启动恢复失败但权限恰好恢复成功。

**后果**：日志打出 `restored rounds=1 ok`，**用户和开发者都会以为整体恢复成功**，实际通知渠道/Doze/自启动没恢复。这正是「安全网」失效的典型形态。

**修法方向**：把校验扩展成「逐类别」断言（权限 + appops 抽样 + 渠道 importance + doze 布尔），`ok` 只有在全部类别达标时才置位；日志按类别分别输出。

---

## P0-4 多用户 / 应用分身 / XSpace 完全未处理，且可能误伤主空间

> **✅ 已修（2026-10-14，按确认只消除误伤）** — `SystemHook.java:143-164`：读 `EXTRA_UID` → `userId != 0` 直接**跳过并 log**，绝不拿主空间 uid 去操作同名包。
> 踩坑：`UserHandle.getUserId(int)` 是 **@hide**（公开 `android.jar` 里没有），改走反射；**兜底用 `uid / 100000` 算术**而不是"失败当 0" —— 否则反射一挂就会把分身当主用户，正好是要防的误伤。
> **未做**：完整多用户支持（baseline 键带 userId、scan 遍历多用户、恢复路径全改），属 backlog。
> 未验：真机没抓过广播，`EXTRA_UID` 只在 AOSP `Intent.java:2944` javadoc 里确认。

**位置**
```java
SystemHook.java:126      String d = i.getDataString();
SystemHook.java:130      String pkg = d.startsWith("package:") ? d.substring(8) : d;   // ← 不含 userId
SystemHook.java:161      pi = ctx.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);  // ← 默认用户
State.java:61            int uid = pi.applicationInfo != null ? pi.applicationInfo.uid : -1;
State.java:417           int uid = pi.applicationInfo != null ? pi.applicationInfo.uid : -1;
```

**问题**：`ACTION_PACKAGE_DATA_CLEARED` 的 `dataString` 只带包名，没有 userId；后续一律按**默认用户**取 `PackageInfo` / `uid`。

**触发**：工作资料 / 应用分身 / XSpace 里的应用清数据（这类空间在小米/一加上都常见）。

**后果**
- 分身空间的应用**完全不受保护**（要么广播收不到，要么按主空间 uid 去操作，恢复不生效）
- 更糟的是**可能误伤**：拿到同名包后按主空间 uid 执行 grant/setMode，改的是主空间那份的状态

**修注**：本条为静态推断，**未在真机验证**（需要分身空间 + 清数据，见附录 B）。

**修法方向**：从广播 Intent 取 `EXTRA_UID`（`Intent.EXTRA_UID` 存在）或 `UserHandle`，按 userId 取 `PackageInfo`/uid，并把 userId 纳入 baseline 键与恢复路径。

---

## P0-5 单线程 `EXEC` 会被最长 150 秒的任务占住，恢复被推迟

> **✅ 已修（2026-10-14）** — `SystemHook.java:49-52` 新增 `META_EXEC` 与 `DEBOUNCE`；`:337` `Meta.write` 改由 `META_EXEC` 执行（`waitBoot` 随之离开恢复线程）；`:301-308` `onChange` 的 2.5s 防抖改由 `ScheduledExecutorService` 调度，不再 `Thread.sleep` 占住 `EXEC`。`scan` 仍是 `synchronized`，行为不变。

**位置**
```java
SystemHook.java:43   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
SystemHook.java:293  EXEC.submit(() -> Meta.write(c2));      // scan 里提交
Meta.java:142        for (int i = 0; i < 300; i++) {
Meta.java:143            if ("1".equals(get.invoke(null, "sys.boot_completed"))) {
Meta.java:146            Thread.sleep(500);                    // ← 最多 300×500ms = 150s
```
同一个单线程池承担三类任务：`scan`（`SystemHook.java:263`）、清数据恢复（`:149`）、`Meta.write`（`:293`）。
另外 `onChange` 还在这条线程上先 `Thread.sleep(2500)`：
```java
SystemHook.java:256  EXEC.submit(() -> {
SystemHook.java:258      Thread.sleep(2500);
SystemHook.java:263      scan(systemContext());
```

**触发**：开机早期（`sys.boot_completed` 还没置 1）就触发了一次 scan → `Meta.write` 排队 → 此后任何清数据恢复都要排在它后面。

**后果**：恢复最长被推迟约 150 秒；用户在这段时间里清数据，会认为「模块没生效」，甚至可能再清一次。

**修法方向**：`Meta.write` 用独立线程/独立 executor；`waitBoot` 不要占用恢复线程；恢复任务优先级与 scan 解耦。

---

## P0-6 `writeAutostart` 的追加分支可能写出非法 XML

> **✅ 已修（2026-10-14）** — `Rom.java:33-44` 新增 `isWellFormed`；`:94-113`：未命中时插到根元素闭合标签**之前**（找不到闭合标签则拒绝追加并 log）；写入前自检 XML，**不合法就放弃写入**；写入改走 `writeAtomic`。
> 未验：一加 `startup_dynamic_list.xml` 的真实结构（手上没有一加机器）。

**位置**
```java
Rom.java:76   Matcher m = Pattern.compile("<dynamic\\s+[^>]*pkgName=\"" + Pattern.quote(pkg) + "\"[^>]*/>").matcher(xml);
Rom.java:78   String out = m.find()
Rom.java:79           ? xml.substring(0, m.start()) + entry + xml.substring(m.end())
Rom.java:80           : xml + entry;                       // ← 未命中时直接追加到文件末尾
Rom.java:81   try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(out.getBytes(...)); }
```

**触发**：目标包在 `/data/oplus/os/startup/startup_dynamic_list.xml` 里**没有**条目（比如新装应用第一次恢复自启动）。

**后果**：若该文件以 `</dynamic-list>`（或任何闭合标签）结尾，追加的 `<dynamic .../>` 会落到**根元素之外** → XML 非法。轻则被 OEM 忽略，重则被系统重置，且这是一份**系统文件**、写入还是非原子的（同 P0-2）。

**修法方向**：追加前定位根元素闭合标签，插到它之前；写入走「临时文件 + rename」；写前做一次 XML 合法性自检（解析一遍再落盘）。

---

# 建议

## P1-1 Doze 一次失败就整轮开机永久放弃
```java
State.java:568   if (sDozeDisabled) { return false; }
State.java:593   sDozeDisabled = true;        // 一旦置位，永不复位（且非 volatile）
```
只要**任何一次** `addPowerSaveWhitelistApp` 失败（例如服务还没就绪），本次开机内所有应用的 Doze 恢复全部跳过。
（`STATUS.md` 已把「`com.android.server.*` 不可见」记为已知限制，但**「一次失败=永久放弃」是额外的**。）
**修法方向**：区分「服务不存在」（可永久标记）与「本次调用失败」（应重试）；加失败次数上限而不是布尔开关。

## P1-2 默认配置语义前后端相反
```java
Config.java:22    public boolean all = false;                  // 后端默认：不保护任何应用
index.html:286    const DEF={version:1,all:true,...}          // 前端默认：全部保护
index.html:289    CFG=Object.assign({},DEF,parsed);           // parsed 为空时 → 全部保护
```
`config.json` 缺失或读不到时，界面显示「全部保护」，后端实际「谁都不保护」。
**修法方向**：两边用同一个默认；或前端在后端读不到配置时显式提示「配置未就绪」。

## P1-3 基线里没有该包时，清数据静默空转
```java
SystemHook.java:133   snap = loadBaseline().optJSONObject(pkg);   // 可能为 null
SystemHook.java:168   State snap = State.fromJson(snapJson);      // null → 空 State
```
`State.fromJson(null)` 返回空 State（`State.java:162-164`），`restoreAll` 什么也不做、也不报错。用户看到的现象就是「模块没反应」。
**修法方向**：`snapJson == null` 时打一条明确日志（"no baseline for pkg"），并可选择立即补一次 capture。

## P1-4 日志只进 logcat，无落盘
```java
SystemHook.java:403   public static void log(String m) { Log.i(TAG, m); }
```
logcat 会被环形缓冲冲掉，事后无法复盘「那次清数据到底恢复成功了没有」。
**修法方向**：关键事件（clear-data / restored / 失败）追加写一个小文件，带大小上限。

## P1-5 导出/导入走命令行，15 秒静默超时，失败也报成功
```javascript
index.html:198   setTimeout(()=>fin(""),15000);                    // 超时静默 resolve 空串
index.html:381   await runIn(`cat > ${path}`, JSON.stringify(bundle,null,2));   // 整个 bundle 走 echo <base64> | base64 -d
index.html:382   log("已导出: "+path);                              // ← 无条件打印成功
```
bundle 里含**完整 baseline**（`index.html:376`），应用多时 payload 很大；命令行长度/超时都可能失败，但日志照样说「已导出」。
**修法方向**：导出后回读校验（比大小或 `md5sum`）再报成功；`run()` 的超时不要静默 resolve 成空串，要能被调用方区分。

## P1-6 配置/基线无版本校验与迁移
`post-fs-data.sh` 与 `index.html:321` 都写 `version:1`，但 `Config.load()`（`Config.java:44-64`）**完全不读 `version`**。
未来 schema 变更时，新旧格式会互相覆盖，无检测、无迁移、无拒绝。
**修法方向**：读取时校验版本，未知版本走明确的降级/迁移分支。

## P1-7 capture 成本偏高且由文件变化防抖触发
```java
State.java:70    for (int op = 0; op <= 200; op++) {          // 每个 op 还要 opToPermission + getPermissionInfo
State.java:96    for (int op = 10000; op <= 10200; op++) {    // 再 201 次
```
每个包每轮 ≈ 400 次反射调用，外加逐权限 `getPermissionInfo`（`State.java:48`）。
`/data/app`、权限目录、`/data/system`(过滤 `notification`) 都有 FileObserver（`SystemHook.java:84-89`），任何一次写入都会防抖触发全量 scan。
**修法方向**：op 枚举结果缓存（`opToPermission` 的映射基本不变）；scan 做增量（只重扫受影响的包）。

## P1-8 `Meta.write` 每次全量删光并重建全部图标
```java
Meta.java:41   File[] old = iconDir.listFiles();
Meta.java:44       f.delete();
Meta.java:89   Bitmap scaled = Bitmap.createScaledBitmap(bmp, 96, 96, true);
```
任一应用安装/卸载/更新都会触发一次全量重建（几十~上百个 PNG）。
**修法方向**：按包增量更新；只删不再存在的包。

## P1-9 死代码与文档漂移
以下定义**全仓无调用者**（已 grep 确认）：
- `Config.save()`（`Config.java:83`）—— 配置实际只由 `permkeeper.sh` 的 `save_cfg` 写
- `State.restoreMissing()`（`State.java:200`）
- `State.enforceExact()`（`State.java:339`）—— 连带 `revoke()`（`State.java:462`）也只在它里面用
- `Utils` 整个类
- `uninstall.sh:7-8` 删除的 `apply.json` / `permkeeper_apply.json` —— 全仓没有任何代码生成这两个文件

**风险**：维护时会误以为这些路径生效；Java 侧与 shell 侧的配置写入行为可能悄悄漂移。

---

# 可选

| # | 位置 | 问题 |
|---|---|---|
| P2-1 | `index.html:408` | `safeId=/^[A-Za-z0-9_.]+$/` 允许 `..`。当前只作 `pm`/`appops` 参数、无路径用途，**现状无害**；但语义偏松，日后复用成路径拼接就是穿越 |
| P2-2 | `permkeeper.sh:41` | `import)` 直接读 `$2` 路径，无参数校验（调用方是 root）。注意 WebUI 现在走 `<input type=file>` 在 JS 里读，这条可能是遗留路径 |
| P2-3 | `build_zygisk.py:16-40` | 工具缺失时 `FileNotFoundError` 未捕获，直接 traceback；SDK/NDK/build-tools 路径与版本有多个来源（本地默认 `r27c` vs CI `27.2.12479018`） |
| P2-4 | `release.yml:25,29` | `SDKM` 可能为空导致安装步骤失败（无兜底）；流水线**无任何测试/校验步骤** |
| P2-5 | `build_zygisk.py:101` vs `AndroidManifest.xml` | `module.prop` 写 `versionCode=1`，manifest 是 `versionCode=2`，不一致 |
| P2-6 | 仓库根 | 161KB 的 `permkeeper-zygisk.zip` 被 git 跟踪（交接文档 backlog 第 4 项） |

---

# 已知 / 文档已记录（不重复计入上方）

来自 `STATUS.md`「已知限制」与 `FINDINGS.md`「待办」，列在这里只为让你看到全貌：

- **Doze 白名单恢复**：`com.android.server.*` 对注入 classloader 不可见，已优雅跳过（一加本就保留 Doze）
- **保守模式的基线语义**：baseline 是「曾授予并集」——⚠️ **文档自相矛盾**：`STATUS.md:19/20` 描述了保守模式，但 `STATUS.md:84` 又写了「**移除"保守恢复"**（全局+每应用+`Config.mode`）」。现在的代码里确实没有 `mode`（`index.html:294` 还在 `delete CFG.mode`），但「已知限制」那节没同步删。建议顺手对齐文档
- **本机 `pm clear` 保留 `POST_NOTIFICATIONS`**：故清数据实际缺失项可能少于预期
- **WebUI 宿主差异**：Kitsune 需手动允许一次 su；KsuWebUI 的 `exec(cmd)` 只返回最后一行，须用回调形式
- **无测试框架**：`STATUS.md` 多处记录「无可自动化的正确 seam」，真机回路是唯一回归手段
- **卸载保留 `config.json` 与 `/sdcard/Download/permkeeper`**：有意为之

---

# 附录 A：功能 backlog（交接文档 4 项，**本轮未评估**）

1. 非 AppOps 项：无障碍服务、默认应用/关联打开、通知使用权（存在系统 settings，需「逆向存储 → 抓取/恢复」）
2. 更多机型/系统适配：`Rom` 抽象目前只有 oneplus / xiaomi / aosp，其它厂商未验证
3. WebUI 多语言：中英切换
4. 从仓库移除 `permkeeper-zygisk.zip`，改用 Release 分发

---

# 附录 B：本轮**未验证**的项（老实交代）

1. **没有真机复现**。本轮在线设备（`f2e7e0b0` / Android 17 / HyperOS OS4.0）与 `STATUS.md` 记录的测试机不是同一台，且复查约定为「静态为主 + 只读验证」，故**没有安装模块、没有冷重启、没有执行 `pm clear`**。
2. **P0-1（渠道名被清）** 依赖两个已确认的事实（`State.java:447/449` 的构造方式 + 系统侧 `r.channels.put()` 整对象替换），但**没有在真机走完整流程目视确认**设置界面里的渠道名。
3. **P0-4（多用户/分身误伤）** 是静态推断，未在分身空间实测。
4. **P1-5（导出静默成功）** 是代码推断，未在真机构造大 payload 复现。
5. `build_zygisk.py` 在 Windows 下 `subprocess.run(["...d8.bat"])` 能否直接执行，**我没有运行构建验证**。
