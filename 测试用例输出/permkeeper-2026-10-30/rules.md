# PermKeeper 业务规则表（rules.md）

- 项目：PermKeeper（Zygisk 模块，清数据后恢复应用设置）
- 版本：工作区 `6e2ca13`（2026-10-30）
- 设备口径：一加 PLU110 / ColorOS 16 / Android 16（SDK 36）
- 素材来源：`zsrc/com/permkeeper/z/*.java`、`webroot/index.html`、`module/*.sh`（L4）
  ＋ `LOGIC.md`、`STATUS.md`、`FINDINGS.md`、`REVIEW.md`（L2）
  ＋ 本仓已发生过的缺陷（L3，反写为禁止规则）
- 说明：`可观测判据` 一律指「用 adb/shell 或 WebUI 能直接看到的东西」，避免无法判定的规则。

## 一、保护范围与配置（SET）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-SET-01 | `all=true` 时，未列入 `exclude` 的应用都受保护 | `Config.java:120` | 目标应用清数据后模块有恢复日志 | 应用在 `exclude` 却被恢复 |
| R-SET-02 | `all=false` 时，只有 `apps` 白名单内的应用受保护 | `Config.java:120` | 白名单外应用清数据后无任何恢复动作 | 白名单外应用被恢复 |
| R-SET-03 | 每应用 `overrides.cats` 优先于全局 `categories` | `Config.java:124` | 全局关掉通知、单应用打开 → 该应用渠道仍被恢复 | 全局开关压过单应用覆盖 |
| R-SET-04 | 类别开关关闭时，该类别不做恢复 | `Config.java:124`、`State.java:318` | 关掉 `permissions` 后清数据，权限保持未授予 | 关了还去授权 |
| R-SET-05 | `includeSystem=false` 时系统应用不纳入保护（除有启动入口且勾选） | `SystemHook.java:538` | 系统应用不在 `baseline.json` | 系统应用进入基线 |
| R-SET-06 | 配置改动自动保存，无需点「保存」 | `index.html:421` | 拨动开关后 1 秒内 `config.json` mtime 变化 | 需要手动保存才生效 |
| R-SET-07 | 配置写入是原子的（`.tmp` + rename），不能出现半截文件 | `SystemHook.java:615`、`Config.java:106` | 连续快速改动后 `config.json` 仍可 `json.loads` | 文件被截断成非法 JSON |
| R-SET-08 | 配置解析失败不得静默降级为「不保护任何应用」 | `Config.java:63` | 写入坏 JSON 后日志出现 `config load failed`，且损坏文件被改名留存 | 坏配置被当成默认值且无日志 |
| R-SET-09 | 旧格式（`all=false` + `apps` 白名单）读入后自动迁移为 `all=true` + 其余进 `exclude` | `index.html:351` | 迁移后列表仍勾选原来的应用 | 迁移后保护范围变化 |

## 二、基线采集（PERM）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-PERM-01 | 基线只记录**受保护**应用 | `SystemHook.java:559` | `baseline.json` 里不出现 `exclude` 内应用 | 排除的应用也进基线 |
| R-PERM-02 | 运行时权限只采集 `protectionLevel == DANGEROUS` 且已授予的 | `State.java:55` | 普通权限不出现在 `perms` | 把普通权限记进快照 |
| R-PERM-03 | AppOps 用**数字 op id** 枚举，不用名字 | `State.java:94`、`State.java:104` | `baseline.appops` 的 key 全是数字 | key 出现 `SYSTEM_ALERT_WINDOW` 这类名字 |
| R-PERM-04 | AppOps 只记录**不等于 `MODE_DEFAULT`** 的项 | `State.java:95` | 值为 3(default) 的 op 不在基线里 | 基线里出现大量 `"3"` |
| R-PERM-05 | AppOps 要覆盖 AOSP 特殊 op（`0..200`）与厂商 op（`10000..10200`） | `State.java:76`、`:102` | 小米机上基线含 `10001+` 的 key | 厂商自启动/联网 op 缺失 |
| R-PERM-06 | 通知渠道 key 形如 `ownerPkg\|uid\|channelId`，并另存 `chanName` | `State.java:119` | 基线 `channels` 的 key 含两竖线；`chanName` 有对应值 | 只有 importance 没有名字 |
| R-PERM-07 | OEM 代理渠道（`mundo@<pkg>@<id>`）要归属到目标应用名下 | `SystemHook.java:572` | ColorOS 上目标应用基线里出现 `owner=其他包` 的渠道 | 代理渠道丢失 |
| R-PERM-08 | 文件变化触发重扫有防抖（约 2.5 秒），不能每写一次就全量扫 | `SystemHook.java:501` | 短时间连续改动只产生少量扫描 | 每次改动都全量扫 |
| R-PERM-09 | 基线文件损坏时不得静默清空，必须留档并打日志 | `SystemHook.java:630` | 写坏 `baseline.json` 后出现 `baseline load failed`，出现 `baseline.*.bad` 类文件 | 直接当空基线继续跑 |

## 三、清数据恢复（PERM / JRN）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-PERM-10 | 只在收到「应用数据被清除」广播时恢复，不常驻轮询 | `SystemHook.java:134` | 不动应用时 CPU 无持续活动；无清理动作时无恢复日志 | 定时轮询扫描 |
| R-PERM-11 | 恢复前先等权限存储稳定（变化后静默 ≥1s 且总 ≥1.8s，上限 9s） | `SystemHook.java:279` | 日志恢复发生在广播后约 2 秒以上 | 广播后立即恢复（HyperOS 会被抹掉） |
| R-PERM-12 | 恢复最多重试 3 轮，每轮 `restore` → `capture` → `verify` | `SystemHook.java:218` | 日志 `restored rounds=1..3` | 无限重试或只做 1 次不校验 |
| R-PERM-13 | `verify` 必须**逐类别**校验（权限/AppOps/通知/省电/自启动），任一缺项要写进日志 | `State.java:520` | 失败时日志出现 `op:` / `chan:` / `doze` 等前缀 | 只校验权限就报 ok |
| R-PERM-14 | 恢复后把该包的已知好快照写回基线，避免半恢复状态被记住 | `SystemHook.java:241` | 恢复后 `baseline` 中该包内容仍为完整快照 | 恢复后基线退化为残缺 |
| R-PERM-15 | 非主用户（分身/工作资料）一律跳过，不得用主空间 uid 操作同名包 | `SystemHook.java:167` | 分身清数据后日志 `ignored (multi-user unsupported)` | 误改主空间应用 |
| R-PERM-16 | 未受保护应用收到清数据广播时直接返回，不做任何写操作 | `SystemHook.java:197` | 排除名单应用清数据后无恢复日志、状态不变 | 排除的应用也被恢复 |
| R-PERM-17 | 单次恢复应为幂等（重复清数据结果一致） | `State.java:315` | 连续两次清数据，两次结果都与基线一致 | 第二次结果劣化 |

## 四、权限与 AppOps 恢复（PERM）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-PERM-18 | 恢复只授予快照内的权限，**不得**授予快照外的权限 | `State.java:318` | 清数据后已授予集合 == 基线 `perms` | 多授予了原本没有的权限 |
| R-PERM-19 | 恢复要把快照内的 AppOps 逐项设回原值 | `State.java:326`、`:448` | 生效值（`checkOpNoThrow`）与基线一致 | 部分 op 停留在默认值 |
| R-PERM-20 | AppOps 恢复用 `setMode(op, uid, pkg, mode)`，按 uid+包名定位 | `State.java:464` | 只影响目标包，其他包不变 | 影响同 uid 其他包 |
| R-PERM-21 | 省电（Doze）恢复走 `deviceidle` binder 的 `addPowerSaveWhitelistApp` | `State.java:674` | 清数据后应用仍在白名单 | 白名单未加回 |
| R-PERM-22 | `deviceidle` 接口不可用时要置位禁用，不再重试、也不计入失败 | `State.java:689` | 日志 `IDeviceIdleController unavailable` 后无反复报错 | 每轮都失败并报 incomplete |

## 五、通知渠道恢复（PERM / SET）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-PERM-23 | 渠道仍存在时**只改 importance**，必须回传原渠道对象，不得新建 | `State.java:484` | 恢复后渠道名/描述/声音不变，只有开关变 | 渠道名变成渠道 id |
| R-PERM-24 | 渠道已被删除时才重建，重建用快照里的 `chanName` | `State.java:494` | 重建后渠道名与基线 `chanName` 一致 | 名字变成 id |
| R-PERM-25 | 重建渠道必须落到**应用自己的 uid** 名下，不能用调用方 uid（system_server=1000） | `Notif.java:87`（2026-10-30 修复） | `dumpsys notification` 里 `AppSettings: <pkg> (<appUid>)` 下有渠道，uid 1000 下没有 | 渠道出现在 uid 1000 下、应用 uid 下 0 个 |
| R-PERM-26 | 通知服务不可用（`sNotifReady=false`）时跳过通知类别，且不计入失败 | `State.java:328`、`:537` | 日志无 chan 相关报错 | 永远报 incomplete |
| R-PERM-27 | 渠道名字缺失时退回用 id 重建，并打日志 | `State.java:497` | 日志 `recreate channel without stored name` | 静默用 id 且无日志 |
| R-PERM-28 | 代理渠道（owner 是别的包）恢复时，要按 owner 包名+uid 操作，不能按目标包 | `State.java:345` | 恢复后 owner 包下渠道 importance 正确 | 写到了错误的包 |

## 六、导出（INST）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-INST-01 | 导出采集**全部已安装应用**，与保护范围/`exclude` 无关 | `SystemHook.java:346` | 导出 `apps` 数 == `pm list packages` 数（含系统） | 只导出受保护的 |
| R-INST-02 | 一次导出请求只触发一次采集（不允许并发跑两遍） | `SystemHook.java:308`（2026-10-23 修复） | 日志 `export request received` 只出现 1 次 | 出现 2 次、进度倒退回小值 |
| R-INST-03 | 进度值必须单调递增，不允许倒退 | `SystemHook.java:468` | 连续采样 `done/total` 不回退 | 进度从 649 掉回 48 |
| R-INST-04 | 导出过程不可取消（取消按钮隐藏） | `index.html:444` | 导出中进度层无取消按钮 | 中途可取消导致半成品 |
| R-INST-05 | 导出结果在设备端合成 `{permkeeper,exportedAt,config,apps}` | `SystemHook.java:426` | 产物含四个顶层字段 | 产物只有 apps |
| R-INST-06 | 导出产物落在 `/sdcard/Download/permkeeper/` | `index.html:466` | 目录下出现 `permkeeper-<时间戳>.json` | 落在别处或没落地 |
| R-INST-07 | 导出成功后清理全部 `export.*` 临时文件 | `index.html:472` | `/data/system/permkeeper/` 下无 `export.req/progress/done/config` | 临时文件残留 |
| R-INST-08 | 导出成功给绿色 toast、失败给红色 toast | `index.html:475` | 文案 `导出成功：N 个应用` / `导出失败` | 失败也报成功 |

## 七、导入（INST）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-INST-09 | 导入并恢复默认选「全部应用」，另可选「仅受保护」 | `index.html:524`、`:530` | 打开面板默认高亮「全部应用」 | 默认只恢复受保护的 |
| R-INST-10 | 比对基准是**真实系统状态**，不是 baseline | `index.html:480`、`:551` | 快照与基线完全相同时也照样下发命令（差异为 0 才算无操作） | 拿基线比导致永远 0 差异 |
| R-INST-11 | 未安装的应用跳过，且不计为失败 | `index.html:567`、`:636` | 摘要里「跳过（未安装 N）」，toast 仍为绿色 | 未安装导致红色失败 |
| R-INST-12 | 包名/权限名不合法时跳过（防 root 命令注入） | `index.html:557`、`:566` | 导入含 `;` 的包名后无注入命令执行 | 拼进 shell 被执行 |
| R-INST-13 | 导入第二阶段可取消，取消给中性提示、不算失败 | `index.html:409`、`:627` | 取消后 toast 灰色「已取消导入」 | 取消后弹红色失败 |
| R-INST-14 | 导入完成后写回 baseline | `index.html:629` | 导入后 `baseline.json` 对应该包内容 == 导入快照 | 基线未更新 |
| R-INST-15 | 导入完成回到主页面（关闭弹层） | `index.html:634` | 完成后无弹层残留 | 停留在导入面板 |

## 八、兼容与安全（COMPAT / ENV）

| 规则ID | 规则描述 | 来源 | 可观测判据 | 反例 |
|---|---|---|---|---|
| R-COMPAT-01 | 冷启动后模块必须完成注入（`.so` 改动需重启生效） | `SystemHook.java:72` | 重启后日志 `ready (after ~Nms)` 且 `apps.json` 被重写 | 注入失败、无日志 |
| R-COMPAT-02 | ROM 差异要抽象：一加走 XML 自启动，小米走 MIUIOP | `Rom.java:136`、`STATUS.md` | 一加日志 `rom=oneplus`；小米 `rom=xiaomi` | 把一加路径写死在小米上 |
| R-COMPAT-03 | 写系统 XML 前必须做合法性自检，产物非法就放弃写入 | `Rom.java:108` | 日志 `result would not be well-formed XML, aborting`，文件未被改坏 | 写坏 `startup_dynamic_list.xml` |
| R-COMPAT-04 | 自启动 XML 追加必须落在根元素闭合标签之前 | `Rom.java:98` | 写入后 XML 仍只有一个根元素 | 产生两个根元素 |
| R-ENV-01 | 日志缓冲可能被冲掉，诊断必须实时抓取 | `STATUS.md`、本会话实测 | 事后 `logcat -d` 可能为 0 条 | 用事后日志判定模块没工作 |
| R-ENV-02 | `pm clear` 需经 `su` 执行，普通 shell 无权限 | 本会话实测 | `su 0 pm clear` 返回 Success | 报 `CLEAR_APP_USER_DATA` 拒绝 |
| R-ENV-03 | 常驻开销要低：无轮询、无定时任务 | `SystemHook.java:487` | 空闲时无周期性 CPU 占用 | 定时器周期性唤醒 |

## 九、禁止规则汇总（违反即 FAIL）

| 规则ID | 禁止事项 |
|---|---|
| R-X-01 | 禁止在未受保护（`exclude`）的应用上做任何恢复写入 |
| R-X-02 | 禁止授予快照之外的权限 |
| R-X-03 | 禁止把通知渠道创建到调用方 uid（1000）名下 |
| R-X-04 | 禁止一次导出请求并发执行多次采集 |
| R-X-05 | 禁止进度值回退 |
| R-X-06 | 禁止在非主用户上按主用户 uid 操作同名包 |
| R-X-07 | 禁止写入不合法的系统 XML |
| R-X-08 | 禁止在配置/基线损坏时静默降级且不留日志 |
| R-X-09 | 禁止把「未安装跳过」当作失败报红 |
| R-X-10 | 禁止用未转义的用户输入拼接 root shell 命令 |
