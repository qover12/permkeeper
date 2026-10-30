# PermKeeper 测试用例 · 端专属域（PERM / INST / ENV / COMPAT）

- 项目：PermKeeper（Zygisk 模块，清数据后恢复应用设置）
- 工作区版本：`6e2ca13`（2026-10-30）
- 设备：一加 PLU110，序列号 `3B663C00LY900000`，ColorOS 16 / Android 16（SDK 36）
- 规则来源：`rules.md`（同目录，v2026-10-30）。**第 4 列规则ID 全部取自该表**，未自造。
- 用例总数：**54 条**（PERM 24 / INST 13 / ENV 8 / COMPAT 10）
- 生成日期：2026-10-30　|　生成阶段第 12、13 列留空，执行期回填

> ⚠ 本文件只覆盖「端专属域」四个模块，配置模型（SET）、WebUI 交互与页面栈（NAV）等由其他子域负责。

---

## 0. 通用前置与执行约定（所有用例共用）

### 0.1 命令约定

下文所有命令省略 `-s 3B663C00LY900000`，实际执行为：

```powershell
adb -s 3B663C00LY900000 shell ...
```

root 命令一律 `adb shell su 0 ...`（普通 shell = uid 2000，无 `CLEAR_APP_USER_DATA`）。

### 0.2 实时抓模块日志（**每条用例都必须先起流**）

该机 logcat ring buffer 仅 **64 KiB**，事后 `logcat -d` 常为 0 条（`R-ENV-01`）。

```powershell
# 后台起流，落主机文件；触发动作后才能看到 restored 行
adb -s 3B663C00LY900000 shell "su 0 sh -c 'logcat -c; logcat -v time -s PermKeeperZ:I'" > D:\pk\pk.log
```

### 0.3 基线/导出产物拉取与断言

```powershell
adb -s 3B663C00LY900000 shell su 0 cat /data/system/permkeeper/baseline.json > D:\pk\baseline.json
python D:\pk\check_baseline.py D:\pk\baseline.json com.kuaibao.skuaidi
```

`D:\pk\check_baseline.py`（主机侧，执行前先 `python -m json.tool D:\pk\baseline.json` 看一眼实际字段名，字段名不符只改取字段那一行，**不改判定口径**）：

```python
import json, sys
b = json.load(open(sys.argv[1], encoding='utf-8'))
apps = b.get('apps', b)
pkg = sys.argv[2]
inApps = pkg in apps
e = apps.get(pkg, {})
perms = e.get('perms', {})
ops = e.get('appops', {})
chans = e.get('channels', {})
print('inApps =', inApps, '| perms =', len(perms), '| appops =', len(ops), '| channels =', len(chans))
print('permalink perms =', list(perms)[:20])
print('nonNumericAppops =', [k for k in ops if not str(k).isdigit()])
print('defaultMode3Appops =', [k for k, v in ops.items() if str(v) == '3'])
print('badChannelKeys =', [k for k in chans if str(k).count('|') != 2])
print('channelNoName =', [k for k, v in chans.items() if not (v.get('chanName') or v.get('name'))])
print('doze =', e.get('doze'), '| autostart =', e.get('autostart'))
```

### 0.4 数据备份与还原（标「需备份」的用例**必须先做**）

`pm clear` 丢登录态，不可回滚。三个样本应用一律先备份：

```powershell
adb -s 3B663C00LY900000 shell su 0 sh -c 'mkdir -p /data/local/tmp/pkbak && cp -a /data/data/com.kuaibao.skuaidi /data/local/tmp/pkbak/data && cp -a /data/user_de/0/com.kuaibao.skuaidi /data/local/tmp/pkbak/user_de'
# 还原：先 pm clear 再回拷，最后 restorecon
adb -s 3B663C00LY900000 shell su 0 sh -c 'pm clear com.kuaibao.skuaidi; rm -rf /data/data/com.kuaibao.skuaidi /data/user_de/0/com.kuaibao.skuaidi; cp -a /data/local/tmp/pkbak/data /data/data/com.kuaibao.skuaidi; cp -a /data/local/tmp/pkbak/user_de /data/user_de/0/com.kuaibao.skuaidi; restorecon -R /data/data/com.kuaibao.skuaidi /data/user_de/0/com.kuaibao.skuaidi'
```

### 0.5 样本应用（本机实测基线）

| 包名 | 说明 | 运行时权限 | AppOps | 通知渠道 | 本文件中的角色 |
|---|---|---|---|---|---|
| `com.kuaibao.skuaidi` | 快宝快递员，uid **10372** | 16 | 82 | 9 | 主被测（受保护） |
| `com.kuaidihelp.posthouse` | 快宝驿站 | 14 | — | 有 | 旁证（不受影响 / 0 权限场景对照） |
| `com.yunda.bmapp` | 快递员揽派 | 14 | — | **0** | 排除名单 / 无渠道对照 |

### 0.6 判定口径

- 「恢复准确」= 恢复后真实系统状态与**清数据前基线**逐项相等（不是「非空」「有值」）。
- 日志文案以实际输出为准；本文件给出的 `restored rounds=N ok` / `incomplete: op:` 等为 `LOGIC.md`、`STATUS.md` 记录的既有文案，若实际不同，按「同一语义」判定并在执行结果里抄原文。
- 任何用例的预期都**不得**预先填 PASS；第 12、13 列留空。

---

## 1. PERM · 系统权限（24 条）

| 用例编号 | 模块 | 需求编号 | 规则ID | 用例标题 | 优先级 | 设计方法 | 前置条件 | 操作步骤 | 预期结果 | 备注 | 执行结果 | 缺陷ID |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-APP-PERM-001 | 系统权限 |  | R-PERM-01,R-X-01 | 判定表：`all=true` 时基线只收受保护应用，`exclude` 内应用不进基线 | P0 | 判定表 | 模块已就绪；`config.json` 中 `all:true`、`exclude` 含 `com.yunda.bmapp`、不含 `com.kuaibao.skuaidi`；实时日志已起流 | 1. `adb shell su 0 cat /data/system/permkeeper/config.json` 确认 exclude 名单<br>2. 触发重扫：`adb shell su 0 pm revoke com.kuaibao.skuaidi android.permission.READ_CONTACTS`，等 5s<br>3. `adb shell su 0 cat /data/system/permkeeper/baseline.json > D:\pk\baseline.json`<br>4. `python D:\pk\check_baseline.py D:\pk\baseline.json com.kuaibao.skuaidi`<br>5. 同脚本把第 2 参数换成 `com.yunda.bmapp` 再跑一次<br>6. `adb shell pm list packages \| wc -l` 取装机包数 | `check_baseline.py` 输出 kuaibao 的 `inApps = True`、yunda 的 `inApps = False`；`len(baseline.apps) == 装机包数 − len(config.exclude)`；实时日志中**没有**任何 `com.yunda.bmapp` 的 capture 记录 | 与 TC-APP-PERM-019 互补（那条测「清数据后不恢复」）。revoke 改真实状态，测完 `pm grant` 还原 |  |  |
| TC-APP-PERM-002 | 系统权限 |  | R-PERM-02 | 等价类：运行时权限只采 `protectionLevel==DANGEROUS` 且已授予的 | P0 | 等价类 | 实时日志已起流；`com.kuaibao.skuaidi` 已授予全部危险权限 | 1. `adb shell dumpsys package com.kuaibao.skuaidi` 落 `D:\pk\dumpsys.txt`<br>2. 从 dumpsys 统计 `granted=true` 且 `protectionLevel` 含 `dangerous` 的权限条数（本机基线为 16）<br>3. 触发重扫（`pm revoke` 再 `pm grant` 同一权限）后拉 `baseline.json`<br>4. `python D:\pk\check_baseline.py D:\pk\baseline.json com.kuaibao.skuaidi` | `perms` 条数 == 步骤 2 统计值（本机 16）；`perms` 中不含 `android.permission.INTERNET`、`android.permission.ACCESS_NETWORK_STATE`、`android.permission.RECEIVE_BOOT_COMPLETED` 等普通权限；`perms` 全部出现在 dumpsys 的 `dangerous` 清单内 | 注意 `req` 字段可含非危险权限，**不要把 `req` 当 `perms` 断言** |  |  |
| TC-APP-PERM-003 | 系统权限 |  | R-PERM-02 | 等价类（无效）：已撤销的危险权限不得进基线 | P1 | 等价类 | 实时日志已起流 | 1. `adb shell su 0 pm revoke com.kuaibao.skuaidi android.permission.READ_CONTACTS`<br>2. 等 5s（防抖 2.5s）<br>3. 拉 `baseline.json` 并跑 `check_baseline.py`<br>4. `adb shell dumpsys package com.kuaibao.skuaidi \| findstr READ_CONTACTS` | 基线 `perms` 中**不含** `android.permission.READ_CONTACTS`；其余 15 条权限逐条仍在；`perms` 总数比 TC-APP-PERM-002 少 1；日志无异常/无 FATAL | 无效等价类单独成条；测完 `pm grant` 还原 |  |  |
| TC-APP-PERM-004 | 系统权限 |  | R-PERM-03 | 等价类：AppOps 只用数字 op id 作 key，不出现名字 | P0 | 等价类 | 实时日志已起流；`com.kuaibao.skuaidi` 有非默认 AppOp | 1. 拉 `baseline.json`<br>2. `python D:\pk\check_baseline.py D:\pk\baseline.json com.kuaibao.skuaidi`（看 `nonNumericAppops`）<br>3. `adb shell su 0 appops get com.kuaibao.skuaidi` 落 `D:\pk\appops.txt`，统计非 `default` 的 op 条数<br>4. 断言 `"24"`（SYSTEM_ALERT_WINDOW）是否存在 | `nonNumericAppops == []`（无 `SYSTEM_ALERT_WINDOW`、无 `OPSTR_*` 之类名字 key）；`appops` 的 key 条数 == 步骤 3 统计的非 default 条数；key 全匹配 `^\d+$` | 数字 id 是 Android 17 上 name 重载失效后的唯一口径（`LOGIC.md` §5） |  |  |
| TC-APP-PERM-005 | 系统权限 |  | R-PERM-04 | 边界值：AppOps 不记录 `MODE_DEFAULT(=3)` 的项（上边界 mode=4 保留） | P1 | 边界值 | 同上 | 1. 拉 `baseline.json` 跑 `check_baseline.py`<br>2. 取 `defaultMode3Appops` 列表<br>3. 取 `appops get` 输出中值为 `default` 的 op 名/id 列表<br>4. 交叉比对两个列表 | `defaultMode3Appops == []`（没有任何值为 `"3"` 的项）；`appops` 所有 value ∈ {`0`,`1`,`2`,`4`}；`appops get` 中判为 `default` 的 op 一个都不出现在基线里 | 边界语义：mode 0/1/2/4 是「有效值」，3 是「默认值」。**不是**把 4 当异常值 |  |  |
| TC-APP-PERM-006 | 系统权限 |  | R-PERM-06 | 边界值：渠道 key 必须三段式 `ownerPkg\|uid\|channelId` 且带 `chanName` | P0 | 边界值 | `com.kuaibao.skuaidi` 有 9 个通知渠道 | 1. 拉 `baseline.json` 跑 `check_baseline.py`<br>2. `adb shell su 0 dumpsys notification --noredact > D:\pk\notif.txt`，抽出 kuaibao 的渠道 id→name 映射<br>3. 比对 key 的 `\|` 数量与 `chanName` | `badChannelKeys == []`（每个 key 恰好两个 `\|`）；`channels` 条数 == 9；每条 key 的 uid 段 == `10372`；`chanName` 非空且与 dumpsys 中同 `channelId` 的 name 一致；`channelNoName` 只允许为空 | 三段式是恢复时定位渠道的唯一依据，缺一段就无法落对 uid |  |  |
| TC-APP-PERM-007 | 系统权限 |  | R-PERM-07,R-PERM-28 | 灰盒：ColorOS `mundo@` 代理渠道归属到目标应用，并按 owner 包名+uid 恢复 | P1 | 灰盒 | ColorOS 16；`com.kuaibao.skuaidi` 至少有 1 个 owner≠自身包的代理渠道（若为 0 个，本条记 N/A） | 1. 拉 `baseline.json`，用 python 列出 `channels` 中 key 首段 ≠ `com.kuaibao.skuaidi` 的项，记 owner 包名与 channelId<br>2. 记录这些 owner 包下该渠道的当前 importance<br>3. 备份数据后 `adb shell su 0 pm clear com.kuaibao.skuaidi`，等 15s<br>4. `dumpsys notification --noredact` 查 owner 包下同 channelId 的 importance | 步骤 1 能找到 owner 为代理包（形如 `mundo@com.kuaibao.skuaidi@<id>`）的条目，且这些条目出现在 kuaibao 的 `channels` 下；步骤 4 中 owner 包下该渠道 importance == 步骤 2 记录值；日志无 `chan:` 缺项 | 若设备/该应用无代理渠道则记 N/A 并说明「本机该应用无 mundo 代理渠道」；需备份 |  |  |
| TC-APP-PERM-008 | 系统权限 |  | R-PERM-08 | 性能：2.5s 防抖——5 次连续权限变动只触发 1 次扫描 | P2 | 性能 | 实时日志已起流 | 1. `adb shell su 0 sh -c 'for i in 1 2 3 4 5; do pm revoke com.kuaibao.skuaidi android.permission.READ_CONTACTS; pm grant com.kuaibao.skuaidi android.permission.READ_CONTACTS; sleep 0.2; done'`<br>2. 等 15s 后统计 `D:\pk\pk.log` 中的扫描/基线写入次数<br>3. `adb shell top -n 1 -b \| findstr system_server` | 10 秒窗口内基线扫描触发次数 **≤ 2**（正常为 1），不是 5 次；`baseline.json` 的 mtime 只更新 1 次；system_server CPU 无持续尖峰（`top` 单次采样 < 20%，且 5s 后回落） | 判据是「合并」，不是「更快」。若实现每写一次就全量扫，本条 FAIL |  |  |
| TC-APP-PERM-009 | 系统权限 |  | R-PERM-09,R-X-08 | 异常场景：`baseline.json` 损坏必须留档 + 打日志，不得静默清空 | P0 | 异常场景 | 实时日志已起流；已备份 `baseline.json` 到 `D:\pk\baseline.bak` | 1. `adb shell su 0 sh -c 'printf "{\"apps\":" > /data/system/permkeeper/baseline.json'`<br>2. 触发重扫（`pm revoke` 再 `pm grant` READ_CONTACTS），等 6s<br>3. `adb shell su 0 ls -l /data/system/permkeeper/`<br>4. 查 `D:\pk\pk.log` 中 `baseline` 相关行 | 目录下出现 `baseline.*.bad`（或同义留档文件），其内容 == 步骤 1 写入的坏内容；日志出现 `baseline load failed`；坏文件**未被直接删除**；随后日志出现新基线写入动作；`config.json` / `apps.json` 的 md5 不变 | 测完把 `D:\pk\baseline.bak` 推回 |  |  |
| TC-APP-PERM-010 | 系统权限 |  | R-PERM-12,R-PERM-13,R-PERM-18 | 场景法：清数据后运行时权限全量恢复（16/16） | P0 | 场景法 | **需备份**；实时日志已起流；kuaibao 16 条危险权限全部 granted | 1. `adb shell dumpsys package com.kuaibao.skuaidi > D:\pk\before.txt`<br>2. `adb shell su 0 pm clear com.kuaibao.skuaidi`<br>3. 等 15s<br>4. `adb shell dumpsys package com.kuaibao.skuaidi > D:\pk\after.txt`<br>5. `python` 比对两次 `granted=true` 的 dangerous 权限集合<br>6. 查日志 | `after` 的授予集合 **== `before` 的授予集合（16/16，严格相等）**；日志出现 `clear-data com.kuaibao.skuaidi restored rounds=1 ok`；从广播到 restored 行的时间差 1.8s–8s | 需备份（丢登录态）。若出现 `rounds=2/3` 记 FAIL 并附缺项前缀 |  |  |
| TC-APP-PERM-011 | 系统权限 |  | R-PERM-18,R-X-02 | 安全（无效等价类）：不得授予快照之外的权限 | P0 | 安全 | **需备份**；实时日志已起流 | 1. `adb shell su 0 pm revoke com.kuaibao.skuaidi android.permission.CAMERA`<br>2. 等 6s，拉基线确认 `perms` 不含 CAMERA<br>3. `adb shell su 0 pm clear com.kuaibao.skuaidi`，等 15s<br>4. `adb shell dumpsys package com.kuaibao.skuaidi \| findstr CAMERA`<br>5. 全量比对授予集合 | `CAMERA` 仍为 `granted=false`；恢复后授予集合与基线**严格相等**（不是「⊇ 基线」）；日志 `restored rounds=1 ok` | 这是「多授予」的唯一判定方式：必须用相等，不能用包含 |  |  |
| TC-APP-PERM-012 | 系统权限 |  | R-PERM-19 | 场景法：AppOps 恢复逐项设回原值 | P0 | 场景法 | **需备份**；实时日志已起流 | 1. `adb shell su 0 appops set com.kuaibao.skuaidi SYSTEM_ALERT_WINDOW ignore`<br>2. `adb shell su 0 appops set com.kuaibao.skuaidi RUN_IN_BACKGROUND allow`<br>3. 等 6s 让基线记录<br>4. `adb shell su 0 appops get com.kuaibao.skuaidi > D:\pk\appops_before.txt`<br>5. `pm clear` 后等 15s，再 `appops get > D:\pk\appops_after.txt`<br>6. `python` 逐项比对基线 `appops` | `appops get` 输出含 `SYSTEM_ALERT_WINDOW: ignore`、`RUN_IN_BACKGROUND: allow`（与步骤 1/2 一致）；`appops_after` 与 `appops_before` 在基线条目上逐项一致；日志无 `op:` 缺项 | 需备份；构造「非默认值」才能证明是恢复而不是碰巧 |  |  |
| TC-APP-PERM-013 | 系统权限 |  | R-PERM-20 | 安全：AppOps 写入只影响目标包 | P2 | 安全 | 实时日志已起流 | 1. `adb shell su 0 appops get com.kuaidihelp.posthouse > D:\pk\ph_before.txt`<br>2. 对 kuaibao 执行 `pm clear`，等 15s<br>3. `adb shell su 0 appops get com.kuaidihelp.posthouse > D:\pk\ph_after.txt`<br>4. `python` 做行级 diff | `ph_before.txt` 与 `ph_after.txt` **逐行一致**（diff 为空）；kuaibao 的 appops 已按下条 TC-APP-PERM-012 口径恢复 | 用旁证应用证明「没有误伤同机其他应用」 |  |  |
| TC-APP-PERM-014 | 系统权限 |  | R-PERM-21 | 场景法：Doze 白名单在清数据前后保持（防御性再确认） | P2 | 场景法 | **需备份**；实时日志已起流 | 1. `adb shell su 0 cmd deviceidle whitelist +com.kuaibao.skuaidi`<br>2. `adb shell dumpsys deviceidle whitelist > D:\pk\doze_before.txt`<br>3. `pm clear` 后等 15s<br>4. `adb shell dumpsys deviceidle whitelist > D:\pk\doze_after.txt`<br>5. diff 两文件 | 两个文件中都含 `com.kuaibao.skuaidi`；diff 中该行无差异；日志无 `doze` 缺项 | **本机 `pm clear` 不清 Doze**（FINDINGS §2 实测），此条只能证明「未被破坏」，不能证明恢复逻辑；要证明恢复需先手工删白名单再清数据 |  |  |
| TC-APP-PERM-015 | 系统权限 |  | R-PERM-23 | 灰盒：渠道仍存在时只改 importance，渠道名/描述不被打回默认 | P0 | 灰盒 | **需备份**；先确认 `pm clear` 后 kuaibao 的 9 个渠道是否仍在（`dumpsys notification --noredact` 前后对比）；若已全删则记 N/A 转 TC-APP-PERM-016 | 1. `dumpsys notification --noredact > D:\pk\notif_before.txt`，记录 9 个渠道的 id→(name, desc, importance)<br>2. `pm clear`，等 15s<br>3. `dumpsys notification --noredact > D:\pk\notif_after.txt`<br>4. python 比对同 channelId 的 name/desc | 渠道仍在的场景下：`name` 与清前**逐字相同**，且 `name != channelId`；`desc` 不变；`importance` == 清前值；日志 `restored rounds=1 ok` | 若渠道被删则该走重建路径，本用例记 N/A 并说明；这是「新建 NotificationChannel 会把名字打回 id」的守门用例 |  |  |
| TC-APP-PERM-016 | 系统权限 |  | R-PERM-24,R-PERM-27 | 场景法：渠道被删除后重建，名字取快照 `chanName` 而非 id | P0 | 场景法 | **需备份**；`pm clear` 会删掉 kuaibao 渠道（小米已 A/B 证明，本机需实测确认） | 1. 拉 `baseline.json`，记录 9 条 `channels` 的 channelId→`chanName`<br>2. `pm clear`，等 15s<br>3. `dumpsys notification --noredact`，取重建后 9 条渠道的 name<br>4. python 比对 name 与 `chanName` | 重建后渠道条数 == 9；每个渠道 `name == 基线 chanName`（**不得等于 channelId**）；日志不出现 `recreate channel without stored name`；importance 逐项与基线一致 | 需备份；名字缺失回退路径由 TC-APP-PERM-022 单独验证 |  |  |
| TC-APP-PERM-017 | 系统权限 |  | R-PERM-25,R-X-03 | 灰盒（缺陷回写）：重建渠道必须落在应用自己的 uid，禁止落 1000 | P0 | 灰盒 | **需备份**；实时日志已起流 | 1. `dumpsys notification --noredact > D:\pk\n_before.txt`，数 `AppSettings: com.kuaibao.skuaidi (10372)` 段与 `AppSettings: android (1000)` 段里 kuaibao 渠道数<br>2. `pm clear`，等 15s<br>3. `dumpsys notification --noredact > D:\pk\n_after.txt`，重复计数 | `AppSettings: com.kuaibao.skuaidi (10372)` 段渠道数 == 基线 9；`AppSettings: android (1000)` 段内 kuaibao 渠道数**不增加**（历史遗留 4 个不计）；日志无 `incomplete: chan:` | 已知缺陷（`STATUS.md`：两参数重载用调用方 uid=1000）。本机 uid 1000 仍有 4 个修复前遗留，判定以「增量」为准 |  |  |
| TC-APP-PERM-018 | 系统权限 |  | R-PERM-15,R-X-06 | 异常场景：非主用户（分身/工作资料）清数据必须跳过，不得拿主空间 uid 操作 | P1 | 异常场景 | 主空间 `com.kuaibao.skuaidi` 已受保护并已建基线；实时日志已起流 | 1. `adb shell su 0 pm create-user --profileOf 0 --managed PerTest`，记返回的 userId（下称 N）<br>2. `adb shell su 0 cmd package install-existing --user N com.kuaibao.skuaidi`<br>3. 记录主空间该包 granted 集合与渠道数<br>4. `adb shell su 0 pm clear --user N com.kuaibao.skuaidi`，等 10s<br>5. 查日志；重查主空间状态<br>6. `adb shell su 0 pm remove-user N` | 日志出现 `ignored (multi-user unsupported)`（或等义：跳过非 0 用户），**不出现该包在该 user 下的 restore 记录**；主空间该包的权限集合与渠道数与步骤 3 完全一致；分身内该包权限保持清空状态（未被恢复） | 创建/删除用户会重启 UI，属可逆操作。若 `pm create-user` 被系统拒绝 → 记 BLOCKED 并说明 |  |  |
| TC-APP-PERM-019 | 系统权限 |  | R-PERM-16,R-X-01 | 判定表：未受保护应用（`exclude` 内）清数据后不做任何写入 | P0 | 判定表 | **需备份**；`com.yunda.bmapp` 在 `exclude` 中；实时日志已起流 | 1. `adb shell dumpsys package com.yunda.bmapp > D:\pk\yd_before.txt`<br>2. `adb shell su 0 pm clear com.yunda.bmapp`，等 10s<br>3. `adb shell dumpsys package com.yunda.bmapp > D:\pk\yd_after.txt`<br>4. 查 `D:\pk\pk.log` 中 `yunda` / `clear-data` 相关行 | 日志**没有** `clear-data com.yunda.bmapp restored`，也没有任何 `op:`/`chan:` 写入动作；`yd_after` 中危险权限为未授予（清空后未被补回）；基线中该包仍不存在 | 需备份；与 TC-APP-PERM-001 互补（那条测采集，这条测恢复） |  |  |
| TC-APP-PERM-020 | 系统权限 |  | R-PERM-17 | 可靠性：连续两次清数据，结果与基线一致（幂等） | P1 | 可靠性 | **需备份**；实时日志已起流 | 1. 第一次 `pm clear com.kuaibao.skuaidi`，等 15s<br>2. 抓快照 A：`dumpsys package` + `appops get` + `dumpsys notification --noredact`<br>3. 第二次 `pm clear`，等 15s<br>4. 抓快照 B（同三项）<br>5. python 三项各做 diff | A 与 B **三项 diff 全为空**（权限集合、appops 逐项、渠道 id/name/importance）；两次日志都是 `restored rounds=1 ok`，第二次不得劣化为 `rounds=3 incomplete` | 测完从备份还原；幂等是「第二次不掉项」，不是「第二次也 ok」 |  |  |
| TC-APP-PERM-021 | 系统权限 |  | R-PERM-14 | 灰盒：恢复后基线仍为完整快照，不退化成残缺 | P1 | 灰盒 | **需备份**；实时日志已起流 | 1. `adb shell su 0 cat /data/system/permkeeper/baseline.json > D:\pk\bl_before.json`<br>2. `pm clear`，等 15s<br>3. `adb shell su 0 cat ... > D:\pk\bl_after.json`<br>4. python 只比 kuaibao 这一个包的全部字段 | `bl_after` 中 kuaibao 的 `perms`/`appops`/`channels`/`doze`/`autostart` 与 `bl_before` **逐字段相同**；perms 条数仍 16、channels 仍 9、appops 条数不变 | 这是「半恢复被记成基线」的守门用例（曾导致渠道从基线永久丢失） |  |  |
| TC-APP-PERM-022 | 系统权限 |  | R-PERM-12,R-PERM-13 | 边界值：重试上限 3 轮，且失败日志必须带类别前缀 | P1 | 边界值 | **需备份**；已备份 `baseline.json`；实时日志已起流 | 1. 拉 `baseline.json`，把 kuaibao 一条渠道 key 的首段（owner）改成不存在的包名 `com.per.missing`，推回<br>2. `pm clear`，等 20s<br>3. 在 `D:\pk\pk.log` 里找 `restored` 行<br>4. `adb shell su 0 cat` 回基线，确认已按 TC-APP-PERM-021 还原 | 日志形如 `... restored rounds=3 incomplete: chan:...`：`rounds=3` 出现、`rounds=4` **不出现**；`incomplete` 后紧跟类别前缀（`perm:`/`op:`/`chan:`/`doze`/`autostart` 之一），不是裸 `ok`；总耗时 < 20s | 构造的坏基线会污染该包，**必须**用备份还原；同时覆盖「max=3 / max+1 不出现」边界 |  |  |
| TC-APP-PERM-023 | 系统权限 |  | R-PERM-10,R-ENV-03 | 性能：无清数据动作时不恢复、无轮询、无定时器 | P1 | 性能 | 实时日志已起流；不做任何清数据/权限操作 | 1. 静置 10 分钟<br>2. 统计 `D:\pk\pk.log` 中 `clear-data` / `restore` 行数<br>3. `adb shell top -n 1 -b \| findstr system_server` 采两次（间隔 5 分钟）<br>4. `adb shell su 0 dumpsys alarm \| findstr permkeeper` | 10 分钟内 `clear-data`/`restore` 行数 == 0；system_server CPU% 两次采样增量 < 2%（无周期性尖峰）；`dumpsys alarm` 中无 permkeeper 相关 alarm | 与 TC-APP-ENV-003 互补：这条判「有没有动作」，ENV-003 判「资源占用」 |  |  |
| TC-APP-PERM-024 | 系统权限 |  | R-SET-04,R-PERM-26 | 判定表：关闭通知类别后清数据不恢复渠道，且不报失败 | P1 | 判定表 | **需备份**；实时日志已起流 | 1. 打开 WebUI → 应用详情 → 关闭 `com.kuaibao.skuaidi` 的「通知」类别<br>2. 1s 后 `adb shell su 0 cat /data/system/permkeeper/config.json` 确认 `overrides` 中该应用 `notifications:false`<br>3. `pm clear`，等 15s<br>4. 查渠道与日志 | 渠道保持清空（未被重建）；日志 `restored rounds=1 ok`，**不含 `chan:` 缺项**、不进入 `incomplete`；权限类别仍正常恢复（perms 16/16） | 规则 R-SET-04 属 SET 模块，本条作跨模块交叉验证；同时覆盖 R-PERM-26 的「跳过且不计失败」语义 |  |  |

---

## 2. INST · 安装升级（导出 / 导入 / 模块安装升级）（13 条）

| 用例编号 | 模块 | 需求编号 | 规则ID | 用例标题 | 优先级 | 设计方法 | 前置条件 | 操作步骤 | 预期结果 | 备注 | 执行结果 | 缺陷ID |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-APP-INST-001 | 安装升级 |  | R-INST-01 | 等价类：导出采集全部已安装应用，与 `exclude`/保护范围无关 | P0 | 等价类 | 模块就绪；`exclude` 中含多个应用（含 `com.yunda.bmapp`）；实时日志已起流 | 1. WebUI 点「导出配置」→ 页面内确认弹窗点「确定」<br>2. 等 toast「导出成功」<br>3. `adb shell pm list packages \| wc -l` 取装机包数<br>4. `python` 读产物：`len(json['apps'])`<br>5. 断言 `com.yunda.bmapp`、`com.android.settings` 是否在 `apps` 内 | `len(apps)` == 步骤 3 的装机包数（本机实测 **472**，含系统应用）；`apps` 中**包含** `exclude` 内的 `com.yunda.bmapp`；`apps` 包含系统包（如 `com.android.settings`） | 导出路径与 `scan` 的「只收受保护」是两条路，别混。判据是「与 exclude 无关」 |  |  |
| TC-APP-INST-002 | 安装升级 |  | R-INST-02,R-X-04 | 状态迁移：一次导出请求只触发一次采集（并发写 `export.req`） | P0 | 状态迁移 | 模块就绪；`/data/system/permkeeper/` 下无 `export.*` 残留；实时日志已起流 | 1. `adb shell su 0 sh -c 'cd /data/system/permkeeper && : > export.req && sleep 0.05 && : > export.req'`<br>2. 等 `export.done` 出现（`ls export.done`），最多等 30s<br>3. 统计 `D:\pk\pk.log` 中导出启动类日志条数（`export request received`）<br>4. `adb shell su 0 cat /data/system/permkeeper/export.done` | 导出启动日志**只出现 1 次**；`export.done` 只有一份且形如 `<done>\|<total>\|<ms>`，`done == total`；日志中不出现两次采集并行（无两次「开始采集」且进度不出现两段从 0 重来） | 若 `: > export.req` 不产生 `CLOSE_WRITE`，改用 WebUI 连点两次导出（第二次被确认弹窗挡住属 UI 层保护，Bash 层并发仍需本条验证） |  |  |
| TC-APP-INST-003 | 安装升级 |  | R-INST-03,R-X-05 | 边界值：导出进度 `done/total` 单调递增，禁止回退 | P1 | 边界值 | 同 TC-APP-INST-002 | 1. 触发导出<br>2. 每 100ms `adb shell su 0 cat /data/system/permkeeper/export.progress`，采样 40 次，落 `D:\pk\progress.txt`<br>3. python 断言序列单调不减且末值 == total | 采样序列**单调不减**（任意相邻两点满足 `next >= prev`）；无任何一次从大值掉回小值（历史缺陷：649 掉回 48）；末值 == `export.done` 的 `total`（472） | 必须边导边采，导出后 `export.progress` 会被清理 |  |  |
| TC-APP-INST-004 | 安装升级 |  | R-INST-04 | UI检查：导出进行中进度层无「取消」按钮 | P2 | UI检查 | 模块就绪；导出耗时 ≥ 3s（472 应用实测 4.07s） | 1. 触发导出<br>2. 导出中 `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml D:\pk\ui.xml`<br>3. 在 dump 中检索文本「取消」 | 导出进行中的 UI 树**不含**「取消」节点（`text="取消"` 计数 == 0）；进度层显示 `X / 472（N%）`；导出结束后进度层自动消失 | 导入第二阶段是「可取消」的，两者行为不同，别互相套用 |  |  |
| TC-APP-INST-005 | 安装升级 |  | R-INST-05 | 等价类：导出产物含四个顶层字段且版本号为 2 | P0 | 等价类 | 已有一次成功导出（产物在 `/sdcard/Download/permkeeper/`） | 1. `adb shell ls /sdcard/Download/permkeeper/` 取最新文件名<br>2. `adb pull` 到 `D:\pk\exp.json`<br>3. `python -c "import json;d=json.load(open(r'D:\pk\exp.json',encoding='utf-8'));print(sorted(d))"`<br>4. 打印 `d['permkeeper']`、`d['exportedAt']` | 顶层 key 集合 **== `['apps','config','exportedAt','permkeeper']`**（四个，不多不少）；`permkeeper == 2`；`exportedAt` 可被 `datetime.fromisoformat` 解析且与当前时间差 < 10 分钟；`config` 内 `all`/`categories` 字段存在 | 字段齐全才可被导入端消费；少字段会在导入时静默走默认 |  |  |
| TC-APP-INST-006 | 安装升级 |  | R-INST-06,R-INST-07 | 场景法：产物落到 `/sdcard/Download/permkeeper/`，导出结束清理全部 `export.*` | P1 | 场景法 | 同 TC-APP-INST-005 | 1. 导出前 `adb shell ls /sdcard/Download/permkeeper/` 记文件列表<br>2. 导出成功后 `adb shell ls -l /sdcard/Download/permkeeper/`<br>3. `adb shell su 0 ls -l /data/system/permkeeper/ \| findstr export`<br>4. python 校验新文件名正则 `^permkeeper-\d{8}[-_]\d{6}\.json$` 且大小 > 100KB | 目录下新增 **1 个** `permkeeper-<时间戳>.json`，文件名匹配 `^permkeeper-\d{8}[-_]\d{6}\.json$`，大小 > 100KB；步骤 3 输出**为空**（`export.req/progress/done/config/bundle` 全部被清理） | 若步骤 3 有残留即为临时文件泄漏，会污染下次导出 |  |  |
| TC-APP-INST-007 | 安装升级 |  | R-INST-08 | 判定表：导出成功给绿色 toast，失败给红色 toast | P2 | 判定表 | 模块就绪 | 1. 正常导出，记录 toast 文案与颜色<br>2. `adb shell su 0 sh -c 'mkdir -p /sdcard/Download/permkeeper.bak && chmod 000 /sdcard/Download/permkeeper'`<br>3. 再导出一次，记录 toast<br>4. 还原：`chmod 777 /sdcard/Download/permkeeper` | 成功：绿色 toast 文案 `导出成功：<N> 个应用`，N == 472；失败：红色 toast 文案含「导出失败」，**不得**报成功；两种情况 toast 均在 2.5s 内消失 | 失败构造用目录不可写；测完务必 `chmod 777` 还原 |  |  |
| TC-APP-INST-008 | 安装升级 |  | R-INST-09 | UI检查：导入并恢复默认选中「全部应用」 | P1 | UI检查 | 模块就绪；已有一份可导入的 JSON | 1. WebUI → 「导入配置」→ 选择 JSON 文件<br>2. `adb shell uiautomator dump` 抓导入面板<br>3. 查两个选项的 `selected`/高亮状态 | 面板默认高亮/选中「**全部应用**」，「仅受保护」未选中；摘要文案不出现「仅受保护 N 个应用」 | 默认值错会导致用户以为只恢复受保护的，实际全改 |  |  |
| TC-APP-INST-009 | 安装升级 |  | R-INST-10 | 灰盒：比对基准是真实系统状态，不是 baseline（差异为 0 才无操作） | P0 | 灰盒 | 已有一份快照（其中 READ_CONTACTS=true）；baseline 中该权限也为 true；**时序敏感** | 1. `adb shell su 0 pm revoke com.kuaibao.skuaidi android.permission.READ_CONTACTS`（此时真实=false、baseline 仍=true，2.5s 内）<br>2. 立即（< 2.5s）在 WebUI 导入该快照 → 「仅受保护」→ 执行<br>3. 等导入完成，`adb shell dumpsys package com.kuaibao.skuaidi \| findstr READ_CONTACTS`<br>4. 查导入阶段二进度计数 | 导入后 `READ_CONTACTS: granted=true`；阶段二进度计数 **≥ 1**（证明下发了命令）；若进度为 `0/0` 且 toast 显示无差异 → FAIL（说明拿 baseline/快照自比） | 两步必须脚本连跑；手工点会超过防抖窗口，届时 baseline 已变 false，用例失效 |  |  |
| TC-APP-INST-010 | 安装升级 |  | R-INST-11,R-X-09 | 等价类（无效）：未安装的应用跳过且不计为失败 | P1 | 等价类 | 模块就绪；手工准备 JSON：在 `apps` 中加 `com.pk.not.exist`（含 1 条权限与 1 条 appop） | 1. WebUI 导入该 JSON → 执行<br>2. 记录完成 toast 文案与颜色<br>3. `adb shell pm list packages \| findstr pk.not.exist` | 完成 toast **绿色**（不是红）；摘要文案含「跳过」且数量 ≥ 1（形如 `跳过（未安装 1）`）；`pm list packages` 无该包；日志无对应包的 `pm`/`appops` 报错行 | 与 TC-APP-INST-011 的区别：这条是「合法但不存在」，那条是「非法字符」 |  |  |
| TC-APP-INST-011 | 安装升级 |  | R-INST-12,R-X-10 | 安全：包名/权限名含 shell 元字符时必须跳过，零命令执行 | P0 | 安全 | 模块就绪；设备无 `/data/local/tmp/pwned` | 1. 构造 JSON，`apps` 键与 `perms` 键分别取：`com.evil;touch /data/local/tmp/pwned;`、`com.evil2$(touch /data/local/tmp/pwned)`、`android.permission.CAMERA\`id>/data/local/tmp/pwned\``、`../../system/bin/rm`<br>2. WebUI 导入并执行<br>3. `adb shell ls /data/local/tmp/pwned`<br>4. `adb shell getprop sys.boot_completed`，确认设备未重启<br>5. 查日志有无 shell 语法错误/`sh: ... not found` | 步骤 3 返回 `No such file or directory`；设备未重启（`sys.boot_completed=1` 且 uptime 连续）；日志无 `sh: syntax error`/`inaccessible`；摘要显示这些条目「跳过」，toast 不为红 | 白名单正则 `^[A-Za-z0-9_.]+$`（WEBUI 已修 F3）。**必须**用 `;`、`$()`、反引号、`../` 四类向量各一次 |  |  |
| TC-APP-INST-012 | 安装升级 |  | R-INST-13 | 状态迁移：导入第二阶段取消 → 中性灰提示，不算失败 | P2 | 状态迁移 | 有一份覆盖 472 应用的快照（阶段二耗时足够长） | 1. WebUI 导入 → 执行<br>2. 阶段二（「正在恢复权限…」）进行中点击「取消」<br>3. 记录 toast 文案与颜色<br>4. 查已下发部分的真实状态 | toast 为**灰色**、文案 `已取消导入`；不出现红色「N 批失败」；已执行部分**不回滚**（属设计，记录实际状态即可）；进度层 2.5s 内消失 | 取消不回滚是既定设计，别当缺陷报；判据只看「提示颜色/文案 + 不算失败」 |  |  |
| TC-APP-INST-013 | 安装升级 |  | R-INST-14,R-INST-15 | 场景法：导入完成后写回 baseline 且关闭弹层 | P1 | 场景法 | 有一份 `com.kuaidihelp.posthouse` 的快照（含明确 perms/appops） | 1. 记录导入前 `baseline.json` 中该包内容<br>2. WebUI 导入 → 「仅受保护」→ 执行完成<br>3. 拉 `baseline.json` 对比该包<br>4. `adb shell uiautomator dump` 查是否还有导入弹层节点 | `baseline.json` 中该包内容 == 导入快照（perms/appops/渠道逐字段）；UI dump 中**无**导入弹层的可见节点（`visibility` 非 visible/节点不存在）；页面回到主列表 | 用 posthouse 而非 kuaibao，避免与清数据用例抢同一包的基线 |  |  |

---

## 3. ENV · 环境与性能（8 条）

| 用例编号 | 模块 | 需求编号 | 规则ID | 用例标题 | 优先级 | 设计方法 | 前置条件 | 操作步骤 | 预期结果 | 备注 | 执行结果 | 缺陷ID |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-APP-ENV-001 | 环境与性能 |  | R-ENV-01 | 可靠性：实时抓取 vs 事后 `logcat -d`——后者为空不代表模块没工作 | P1 | 可靠性 | 模块就绪；logcat 未被 `-c` 清过 | 1. 起实时流（§0.2）<br>2. `adb shell su 0 pm clear com.kuaibao.skuaidi`，等 30s<br>3. `adb shell "su 0 logcat -d -s PermKeeperZ:I \| wc -l"`<br>4. `wc -l D:\pk\pk.log` | 实时流中 `restored` 行 **≥ 1**；步骤 3 的条数 **0 或远小于** 步骤 4；判定必须以实时流为准（事后为空不构成「模块未工作」证据） | 该机 buffer 仅 64KiB（实测 3 分钟约 4400 行即冲掉）。本条本身是「诊断方法有效性」验证，顺带产出 1 条 restored 证据 |  |  |
| TC-APP-ENV-002 | 环境与性能 |  | R-ENV-02 | 等价类（无效）：非 su 身份执行 `pm clear` 必须失败 | P0 | 等价类 | `com.yunda.bmapp` 处于可清状态；已记其数据目录 | 1. `adb shell pm clear com.yunda.bmapp`<br>2. 记录输出与 `$LASTEXITCODE`<br>3. `adb shell ls /data/data/com.yunda.bmapp` | 输出含 `Error` 与 `CLEAR_APP_USER_DATA`（或 `SecurityException`）；退出码非 0；`/data/data/com.yunda.bmapp` **仍存在**（数据未被清） | 有效等价类（`su 0 pm clear` 返回 `Success`）由其余所有清数据用例隐含覆盖，本表不单列 |  |  |
| TC-APP-ENV-003 | 环境与性能 |  | R-ENV-03,R-PERM-10 | 性能：熄屏空闲 10 分钟无周期唤醒、CPU 无持续占用 | P1 | 性能 | 实时日志已起流；期间不做任何操作 | 1. `adb shell input keyevent 26` 熄屏<br>2. `adb shell top -n 1 -b > D:\pk\top1.txt`<br>3. 等 10 分钟（期间不动设备）<br>4. `adb shell top -n 1 -b > D:\pk\top2.txt`<br>5. `adb shell dumpsys batterystats --charged > D:\pk\bs.txt`，检索 `permkeeper`<br>6. `adb shell dumpsys deviceidle` 查是否进入 IDLE | system_server 的 CPU% 增量 < 2%，且无周期性峰值（`top` 两次采样差异小）；`batterystats` 中无 permkeeper 相关 wakelock/alarm；10 分钟内日志无周期输出（相邻时间戳间隔无固定节律） | 与 TC-APP-PERM-023 互补：那条判「无动作」，这条判「无资源占用」 |  |  |
| TC-APP-ENV-004 | 环境与性能 |  | R-PERM-08 | 边界值：单次权限变动的扫描延迟落在 [2.5s, 4s] 区间 | P2 | 边界值 | 实时日志已起流；已记录 `baseline.json` 的 mtime | 1. `adb shell stat -c %Y /data/system/permkeeper/baseline.json`<br>2. 记当前时间 T0，执行 `adb shell su 0 pm revoke com.kuaibao.skuaidi android.permission.READ_CONTACTS`<br>3. 每 200ms 轮询 `stat -c %Y`，直到 mtime 变化，记 T1<br>4. 计算 T1−T0 | `T1−T0` ∈ **[2.5s, 4.0s]**：不得 < 2.5s（防抖未生效），也不得 > 4.0s（扫描过慢）；期间只有 1 次 mtime 变化（不是连续多次） | 上下两个边界都要判：太小=没防抖，太大=扫描卡；`min-1` 等价于「无防抖」即本条的 <2.5s 分支 |  |  |
| TC-APP-ENV-005 | 环境与性能 |  | R-COMPAT-01 | 性能：冷启后 WebUI 首屏 TTID ≤ 2.0s（列表非空、不空白） | P1 | 性能 | 设备已冷启（重启完成 ≥ 60s），`/data/system/permkeeper/apps.json` 存在且 mtime > 开机时间 | 1. `adb shell screenrecord --time-limit 20 /sdcard/ttid.mp4` 后台录制<br>2. 在管理器里点击模块 WebUI 图标（或 `am start` KsuWebUI 的 WebUIActivity）<br>3. 停止录制，`adb pull /sdcard/ttid.mp4 D:\pk\`<br>4. `adb shell uiautomator dump` 确认列表节点与首行应用名<br>5. 逐帧数到首行应用可见 | 从点击到列表**首行应用可见** ≤ **2.0s**；首屏应用数 > 0（不是空白/骨架屏）；「加载中」停留 < 1.0s；dump 中可见真实应用名（非「未知应用」） | 首屏依赖 `apps.json`，若模块未就绪会回退 `pm list packages`（无名称、更慢）——回退即视为 FAIL |  |  |
| TC-APP-ENV-006 | 环境与性能 |  | R-COMPAT-01 | 性能：472 应用列表连续滑动 10s 无 ANR、无明显掉帧 | P2 | 性能 | WebUI 已打开且列表为全量（≈472 项） | 1. `adb shell dumpsys gfxinfo <WebUI 进程包名> reset`<br>2. `adb shell input swipe` 连续上滑 10 次（间隔 300ms）<br>3. `adb shell dumpsys gfxinfo <包名> > D:\pk\gfx.txt`<br>4. `adb logcat -d \| findstr "ANR in"` | `gfxinfo` 中 janky frames 占比 ≤ **5%**；日志无 `ANR in`；滑动过程中列表不出现 > 500ms 的空白图标块；无应用崩溃弹窗 | 若 `gfxinfo` 取不到 WebView 帧数据，退回录屏人工数帧：判据改为「连续掉帧 ≥3 帧的窗口 ≤ 2 次」 |  |  |
| TC-APP-ENV-007 | 环境与性能 |  | R-PERM-12 | 性能：清数据恢复总耗时上限（1 轮 ≤5s，最坏 3 轮 ≤15s）且不卡 system_server | P1 | 性能 | **需备份**；实时日志已起流（带 `-v time` 时间戳） | 1. 记录 `pm clear` 命令发出时刻 T0<br>2. 等日志出现 `restored` 行，记 T1<br>3. 期间每秒 `adb shell echo ok` 并计时响应<br>4. 计算 T1−T0 与最大响应耗时 | `T1−T0` ≤ **5.0s**（`rounds=1 ok` 情形）；若为 `rounds=3`，`T1−T0` ≤ **15.0s** 且中间 3 轮间隔 ≈ 900ms；恢复窗口内 `echo ok` 响应每次 < 500ms（system_server 未卡死） | 需备份；耗时超限虽不一定是缺陷，但要记录以便后续定位（导出 472 应用 4.07s 是同类基线） |  |  |
| TC-APP-ENV-008 | 环境与性能 |  | R-ENV-01 | 可分析性：单次清数据全程日志行数 ≤ 40 行，不刷屏 | P2 | 可靠性 | **需备份**；实时日志已起流；buffer 已 `logcat -c` 清空 | 1. `logcat -c`<br>2. `pm clear com.kuaibao.skuaidi`，等 20s<br>3. `wc -l D:\pk\pk.log`<br>4. 用 python 统计连续重复行最大长度 | 本次全过程 PermKeeperZ 日志行数 ≤ **40**；无同一行连续重复 > 3 次；关键行（`clear-data`、`restored`）各出现 1 次 | 日志过密会挤掉 64KiB buffer 里的诊断信息，属可分析性缺陷 |  |  |

---

## 4. COMPAT · 兼容性（10 条）

| 用例编号 | 模块 | 需求编号 | 规则ID | 用例标题 | 优先级 | 设计方法 | 前置条件 | 操作步骤 | 预期结果 | 备注 | 执行结果 | 缺陷ID |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-APP-COMPAT-001 | 兼容性 |  | R-COMPAT-01 | 状态迁移：冷启动后模块完成注入，`apps.json` 被重写 | P0 | 状态迁移 | 模块已安装；实时日志已起流（重启后重新起流） | 1. `adb shell su 0 sh -c 'date +%s > /data/local/tmp/boot.ts'`<br>2. `adb reboot`，等 `sys.boot_completed=1`<br>3. 重启后立刻起实时流<br>4. `adb shell su 0 ls -l /data/system/permkeeper/apps.json`<br>5. 查日志 | 日志出现 `ready (after ~Nms)`（N 为该机实际值，≤ 300000ms）；`apps.json` 的 mtime **晚于** 开机时间且大小 > 10KB；日志 `rom=oneplus`；WebUI 应用列表非空 | 冷启是模块唯一生效方式（`.so` 改动必须重启），也是冒烟起点 |  |  |
| TC-APP-COMPAT-002 | 兼容性 |  | R-COMPAT-02 | 兼容性：ROM 识别为一加并走 XML 自启动路径，不误走 MIUIOP | P1 | 兼容性 | 模块就绪 | 1. 重启后查日志中 `rom=` 字段<br>2. `adb shell su 0 sh -c 'dumpsys meminfo \| head -1'` 记录机型<br>3. 查日志是否出现 MIUIOP/`10001` 相关写入<br>4. 查 `/data/oplus/os/startup/startup_dynamic_list.xml` 是否存在 | 日志 `rom=oneplus`；自启动读写指向 `/data/oplus/os/startup/startup_dynamic_list.xml`；日志中**无** MIUIOP 写入动作（`10000..10200` 区间在本机即便枚举到也不得作为自启动主路径） | 与 R-PERM-05 的「不适用」一致：本机没有 MIUIOP，不该出现小米路径 |  |  |
| TC-APP-COMPAT-003 | 兼容性 |  | R-COMPAT-03,R-X-07 | 异常场景：系统 XML 非法/不可解析时放弃写入，不得写坏 | P0 | 异常场景 | **需备份**；已备份 `/data/oplus/os/startup/startup_dynamic_list.xml` 到 `/data/local/tmp/startup.bak` | 1. `adb shell su 0 sh -c 'md5sum /data/oplus/os/startup/startup_dynamic_list.xml'` 记 M0<br>2. `adb shell su 0 sh -c 'printf "</broken>" >> /data/oplus/os/startup/startup_dynamic_list.xml'`，记 M1<br>3. 起实时流，`pm clear com.kuaibao.skuaidi`，等 15s<br>4. 再 `md5sum` 记 M2<br>5. `adb shell su 0 sh -c 'cp -a /data/local/tmp/startup.bak /data/oplus/os/startup/startup_dynamic_list.xml; restorecon ...'` | **M2 == M1**（模块未追加、未重写、未进一步破坏）；日志出现写前自检/解析失败（`well-formed`、`abort`、`load failed` 之一）；日志**不出现** autostart 写入成功条目 | 判定核心是「M2==M1」：宁可这次不恢复，也不写坏系统文件。测完必须还原备份 |  |  |
| TC-APP-COMPAT-004 | 兼容性 |  | R-COMPAT-04,R-X-07 | 边界值：自启动 XML 追加落在根元素闭合标签之前，只有 1 个根元素 | P0 | 边界值 | **需备份**；已执行过一次触发自启动写入的清数据（如 TC-APP-PERM-014 之后） | 1. `adb shell su 0 cat /data/oplus/os/startup/startup_dynamic_list.xml > D:\pk\startup.xml`<br>2. `python -c "import xml.etree.ElementTree as ET;r=ET.parse(r'D:\pk\startup.xml').getroot();print(r.tag,len(list(r)))"`<br>3. python 统计 `<dynamic pkgName=...>` 条数并与 UI 上该应用自启动开关状态比对 | `ET.parse` 解析成功（不抛 `ParseError`）；根元素**只有 1 个**；所有 `dynamic` 节点均为根的子节点（不在根外）；文件末尾为根闭合标签；`com.kuaibao.skuaidi` 的 `switch` 值与系统 UI 显示的开关状态一致 | 若本次未发生写入（md5 未变），断言改为「结构未变」并记 N/A（清数据不清自启动） |  |  |
| TC-APP-COMPAT-005 | 兼容性 |  | R-COMPAT-01 | 状态迁移：模块升级后必须冷启才生效（内存中旧映像带 `(deleted)`） | P1 | 状态迁移 | 已备份当前模块 zip；设备可重启 | 1. `adb shell su 0 sh -c 'md5sum /data/adb/modules/permkeeper/zygisk/arm64-v8a.so'` 记 M0<br>2. 安装新 zip（`adb push` + `adb shell su 0 magisk --install-module /data/local/tmp/new.zip`），**不重启**<br>3. `adb shell su 0 sh -c 'grep permkeeper /proc/$(pidof system_server)/maps'`<br>4. `adb reboot`，等 boot_completed<br>5. 重复步骤 3，并起流看 `ready` 日志 | 步骤 3：system_server 的 maps 中该 `.so` 路径**带 `(deleted)`** 标记（旧映像仍在内存）；步骤 5：**无** `(deleted)` 标记，且日志出现 `ready (after ~Nms)`、`apps.json` mtime 更新 | 这条解释「为什么改逻辑必须冷启」（zygiskd 缓存 `.so`）；步骤 2 若失败记 BLOCKED 并保留原模块 |  |  |
| TC-APP-COMPAT-006 | 兼容性 |  | R-SET-05 | 判定表：`includeSystem` 开关在 Android 16 上的纳入行为 | P2 | 判定表 | 模块就绪；实时日志已起流 | 1. WebUI 关闭「包含系统应用」，等 5s，拉基线并数 `len(apps)`<br>2. 打开「包含系统应用」，等 5s，再数 `len(apps)`<br>3. 检查 `com.android.settings` 是否进基线 | 关闭时系统中无启动入口的应用不在基线；打开后 `len(apps)` 增加（STATUS 记录 68 → 87 同量级），且 `com.android.settings` 出现；两次切换的 `config.json` 都在 1s 内写入（无需点保存） | R-SET-05/R-SET-06 属 SET 模块，本条作跨模块交叉验证 |  |  |
| TC-APP-COMPAT-007 | 兼容性 |  | R-SET-06 | 集成：KsuWebUI 桥接（`ksu.exec` 回调形式）下配置改动能落盘 | P1 | 集成 | KsuWebUI 已获一次 su 授权；实时日志已起流 | 1. 打开 WebUI，拨动「包含系统应用」开关<br>2. 等 2s，`adb shell su 0 cat /data/system/permkeeper/config.json`<br>3. `adb shell su 0 stat -c %Y /data/system/permkeeper/config.json` 取 mtime 并再拨回开关后再取一次<br>4. `adb shell uiautomator dump` 抓界面状态 | 两次拨动后 `config.json` 的 `includeSystem` 分别变为 `true`/`false`；mtime 每次都在 1s 内更新（证明 `ksu.exec(cmd,"{}",cb)` 回调生效）；界面无「保存」按钮且不出现控制台报错 | Kitsune 需手动允许一次 KsuWebUI 的 su；`exec(cmd)` 只返回最后一行是已知坑 |  |  |
| TC-APP-COMPAT-008 | 兼容性 |  | R-SET-06 | 兼容性：深色模式 + 1.3x 字体 + 高 DPI 下仍能完成一次配置写入 | P2 | 兼容性 | 模块就绪 | 1. `adb shell settings put system font_scale 1.3`<br>2. `adb shell cmd uimode night yes`<br>3. 打开 WebUI，滚动到「包含系统应用」并拨动<br>4. 等 2s，查 `config.json`<br>5. `adb shell uiautomator dump` 查有无控件越界（bounds 超出屏幕） | `config.json` 中 `includeSystem` 按拨动结果变化（证明控件可点到、写入链路通）；UI dump 中所有可交互节点 `bounds` 在 `[0,0][1080,2772]` 内（不越界）；无文字截断到不可辨识 | 还原：`font_scale 1.0`、`cmd uimode night no`。本条只判「可操作性+落盘」，不判视觉美观（外观属前端域） |  |  |
| TC-APP-COMPAT-009 | 兼容性 |  | R-PERM-13 | 兼容性：0 通知渠道的应用清数据不产生 `chan:` 缺项 | P1 | 兼容性 | **需备份**；`com.yunda.bmapp` 受保护且基线 `channels` 为空；实时日志已起流 | 1. 拉基线确认 `com.yunda.bmapp` 的 `channels == {}`、`perms == 14`<br>2. `pm clear com.yunda.bmapp`，等 15s<br>3. 查日志 `restored` 行<br>4. `dumpsys package` 比对权限集合 | 日志 `restored rounds=1 ok`；**不含** `chan:` 缺项、不进入 `incomplete`；权限 14/14 恢复；渠道数仍为 0（不凭空造渠道） | 无渠道应用是最容易被 verify 误判成失败的一类，必须单独成条 |  |  |
| TC-APP-COMPAT-010 | 兼容性 |  | R-PERM-25,R-PERM-06 | 风险探测：应用卸载重装后 uid 变化，恢复不得写到旧 uid 或误伤其他包 | P1 | 错误推测 | **需备份 + 需 APK**；已 `adb pull` 出 kuaibao 的 APK；已备份数据目录 | 1. `adb shell su 0 sh -c 'pm path com.kuaibao.skuaidi'` → `adb pull` APK 到 `D:\pk\kb.apk`<br>2. 备份 `/data/data` + `/data/user_de/0`（§0.4）<br>3. 记录基线中该包 channels key 里的旧 uid（10372）<br>4. `adb shell su 0 pm uninstall com.kuaibao.skuaidi` → `adb shell su 0 pm install -r /data/local/tmp/kb.apk`，记新 uid<br>5. 等 8s（scan 重建基线）→ `pm clear`，等 15s<br>6. 查 `dumpsys notification --noredact` 与日志 | 新 uid 名下出现（或明确未出现）渠道，**旧 uid 10372 段渠道数不再被写入**；无 FATAL、无 ANR；其他应用（posthouse）的渠道与权限不受影响；若基线仍带旧 uid 导致失败，日志必须给出 `incomplete: chan:...10372...` 而不是静默失败 | 这条是「uid 敏感」的兼容性探测（渠道归属 uid 敏感的实测结论）。重装会丢登录态，需备份 |  |  |

---

## 5. 依赖真实清数据的用例（执行前必须备份）

`pm clear` 会丢登录态、且不可回滚。以下用例在**备注**列已标「需备份」，执行清单：

| 用例 | 清的是哪个包 | 丢什么 |
|---|---|---|
| TC-APP-PERM-010 / 011 / 012 / 013 / 015 / 016 / 017 / 020 / 021 / 022 / 024 | `com.kuaibao.skuaidi` | 登录态、本地数据、缓存、通知渠道 |
| TC-APP-PERM-014 | `com.kuaibao.skuaidi` | 同上（本条只为对齐 Doze 检查时点） |
| TC-APP-PERM-019 / ENV-002（失败路径不清）/ COMPAT-009 | `com.yunda.bmapp` | 登录态、本地数据 |
| TC-APP-ENV-007 / ENV-008 | `com.kuaibao.skuaidi` | 同上 |
| TC-APP-COMPAT-003 / COMPAT-004 | `com.kuaibao.skuaidi` + 改系统 XML | 同上 + 自启动配置需从备份还原 |
| TC-APP-COMPAT-010 | 卸载重装 `com.kuaibao.skuaidi` | 全部数据 + uid 变化 |

**执行顺序建议**：先跑全部「只读/采集类」（PERM-001～009、INST-001～008、ENV-001～006、COMPAT-001～005），最后集中跑清数据类，每跑完一条立刻从备份还原，再跑下一条。

---

## 6. 发散引擎落点（说明「多出来的用例从哪来」）

| 层 | 落点 |
|---|---|
| L1 SFDIPOT | Structure=Zygisk 注入/文件监听/五类写入；Data=baseline 结构、含 `\|` 的渠道 key；Integrations=`deviceidle` binder、`INotificationManager`、ColorOS 启动 XML；Platform=ColorOS16/Android16；Operations=单次清数据/连续两次/分身；Time=2.5s 防抖、1.8s 稳定窗口、3 轮重试、64KiB 日志缓冲 |
| L2 约束反演 | 限制「权限名/包名合法」→ `;`/`$()`/`../` 注入（INST-011）；限制「渠道 key 三段」→ 缺 `chanName`、owner 为不存在包（PERM-022）；限制「uid 固定」→ 卸载重装换 uid（COMPAT-010）；限制「只清主用户」→ 建分身（PERM-018）；限制「XML 必须合法」→ 注入 `</broken>`（COMPAT-003）；限制「日志只在内存」→ 实时 vs 事后（ENV-001） |
| L3 致病值表 | `--`/空 → 0 渠道应用（COMPAT-009）；`max+1` → 重试第 4 轮不得出现（PERM-022）；`0/1/多个` → 导出 2 次请求只跑 1 次（INST-002）；边界 2.5s 两侧（ENV-004） |
| L4 变异三招 | 换常量：把 mode 3 改成 2（PERM-005 能抓到）；换算子：`⊇` 改 `==`（PERM-011 能抓到「多授予」）；删步骤：删掉 verify 逐类别（PERM-022 能抓到裸 ok） |
| L5 HICCUPPS | 「渠道名变成 id」→ 与 Claims（`R-PERM-23/24`）不一致 → 真缺陷；「后来日志为空」→ 与 History（曾功能正常）不一致 → 先怀疑抓取方式（ENV-001） |
| L6 HTSM | 补 Risk（uid 变化、注入）、Stress（472 应用列表滑动/导出）、Automatic（baseline 断言脚本化进 §0.3） |
