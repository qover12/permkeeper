# PermKeeper 视角申报表 · 端专属域（PERM / INST / ENV / COMPAT）

- 生成日期：2026-10-30　|　设备：一加 PLU110 / ColorOS 16 / Android 16（SDK 36）
- 对应用例集：`cases_end.md`（54 条：PERM 24 / INST 13 / ENV 8 / COMPAT 10）
- 行数校验：**53 + 11 + 11 + 12 + 15 = 102**
- 状态取值只有三种：`已覆盖(TC-xxx)` / `不适用(原因)` / `未覆盖(原因+建议)`；**无空格子、无「基本覆盖」**

---

## 第一部分 · 基线 53 条视角（整个任务只申报一次）

| 视角 | 状态 | 备注 |
|---|---|---|
| 功能性 | 已覆盖(TC-APP-PERM-010) | 核心功能=清数据后五类设置回补；本域只认「真实系统状态 == 基线」 |
| 功能完备性 | 已覆盖(TC-APP-PERM-010,TC-APP-PERM-012) | 权限/AppOps 已覆盖；通知见 PERM-015/016；Doze/自启动在本机 `pm clear` 不清，只能做防御性再确认（PERM-014、COMPAT-004） |
| 功能正确性 | 已覆盖(TC-APP-PERM-011,TC-APP-PERM-021) | 用「集合严格相等」而非「包含」判定，能抓出多授予 |
| 功能适合性 | 未覆盖(需真实使用场景) | 建议：装模块后连续使用 1 周，统计「清数据后仍需手动重设」的次数，>0 即说明「保护」没达成用户目标 |
| 性能效率 | 已覆盖(TC-APP-ENV-007,TC-APP-INST-003) | 恢复耗时上限 + 导出进度单调 |
| 时间特性 | 已覆盖(TC-APP-ENV-004,TC-APP-ENV-007) | 2.5s 防抖区间、恢复总耗时 5s/15s 上限 |
| 资源利用率 | 已覆盖(TC-APP-ENV-003,TC-APP-ENV-008) | 熄屏 10 分钟 CPU 增量 <2%、单次恢复日志 ≤40 行 |
| 兼容性 | 已覆盖(TC-APP-COMPAT-002,TC-APP-COMPAT-009,TC-APP-COMPAT-010) | ROM 抽象（一加 XML）、0 渠道应用、uid 变化三类兼容面 |
| 易用性 | 已覆盖(TC-APP-COMPAT-008) | 仅覆盖「深色/1.3x 字体下能完成一次配置写入」，交互流畅度属前端域 |
| 可辨识性 | 不适用(无独立产品入口) | 模块只在管理器（Magisk/KernelSU）里出现，没有「自我介绍/适用机型」页面可辨识 |
| 易学性 | 未覆盖(WebUI 无引导) | 建议：前端域评估首次打开是否有「卡片的勾选=保护」这一关键语义提示；本域无可执行用例 |
| 易操作性 | 已覆盖(TC-APP-INST-008,TC-APP-COMPAT-007) | 导入默认项、开关自动落盘（无保存按钮） |
| 用户差错防御 | 已覆盖(TC-APP-INST-011,TC-APP-PERM-009,TC-APP-INST-012) | 注入输入被跳过、坏基线留档、导入可取消 |
| 可靠性 | 已覆盖(TC-APP-PERM-020,TC-APP-PERM-021) | 连续两次清数据幂等、基线不回退 |
| 成熟性 | 已覆盖(TC-APP-PERM-022,TC-APP-ENV-008) | 3 轮兜底 + 具体缺项前缀 + 日志不刷屏 |
| 容错性 | 已覆盖(TC-APP-PERM-009,TC-APP-COMPAT-003) | 配置/基线/系统 XML 任一损坏都不静默降级 |
| 易恢复性 | 已覆盖(TC-APP-PERM-016,TC-APP-PERM-020) | 渠道被删可重建、失败可重试、结果幂等 |
| 安全性 | 已覆盖(TC-APP-INST-011,TC-APP-PERM-017) | 命令注入防护、渠道不得落 uid 1000 |
| 保密性 | 未覆盖(导出快照落 sdcard) | 建议：`/sdcard/Download/permkeeper/*.json` 含全量应用的权限/AppOps 快照，sdcard 可被任意应用读；应评估加密或改落私有目录，并补一条「文件权限 600 + 内容脱敏」用例 |
| 完整性 | 已覆盖(TC-APP-PERM-011,TC-APP-PERM-021) | 不多授予、不丢基线 |
| 真实性 | 不适用(无身份/认证概念) | 模块在 root 下运行，无登录态，不存在「证明自己是自己」的语义 |
| 适应性 | 已覆盖(TC-APP-COMPAT-002,TC-APP-COMPAT-006) | ROM 差异、`includeSystem` 差异 |
| 功能测试 | 已覆盖(TC-APP-PERM-010) | 黑盒行为：清数据→设置回来 |
| 非功能测试 | 已覆盖(TC-APP-ENV-003,TC-APP-ENV-007) | 开销与耗时 |
| 回归测试 | 已覆盖(TC-APP-PERM-017,TC-APP-PERM-021,TC-APP-INST-002) | 三条均对应已修缺陷（uid 1000、基线退化、重复导出） |
| 确认测试/复测 | 已覆盖(TC-APP-PERM-017,TC-APP-INST-002) | 定向复测「重建渠道落 uid」与「一次请求两次采集」 |
| 冒烟测试 | 已覆盖(TC-APP-COMPAT-001,TC-APP-PERM-010) | 冷启后 ready + 一次清数据恢复，10 分钟可跑完 |
| 健全性测试 | 已覆盖(TC-APP-COMPAT-005) | 只换 `.so` 后的窄而深验证（maps `(deleted)` → 冷启生效） |
| 验收测试(UAT) | 未覆盖(无验收人/验收准则文档) | 建议：以「清数据后登录态与权限完全不用重设」为验收准则，由王大哥本人日常使用 3 天确认 |
| Alpha/Beta 测试 | 未覆盖(单机自用无内测用户群) | 建议：至少在一台非 ColorOS 设备（如小米 HyperOS4）再跑一遍 PERM-010/016 作为跨机 Beta，历史已验证过一次 |
| 探索式测试 | 已覆盖(TC-APP-PERM-022,TC-APP-COMPAT-003) | 两条都是「构造异常去撞边界」，执行时需要边看日志边加动作，属脚本化探索 |
| 基于风险的测试 | 已覆盖(TC-APP-PERM-011,TC-APP-INST-011) | 红线优先：多授予权限、root 命令注入 |
| 文档测试 | 未覆盖(无用户手册) | 建议：补 README 说明「哪些设置不在保护范围」（无障碍/默认应用/通知使用权/分身），并与 WebUI 文案对齐，否则用户会误判为 bug |
| 配置测试 | 已覆盖(TC-APP-PERM-024,TC-APP-COMPAT-006) | 类别开关 × 每应用覆盖、`includeSystem` 组合 |
| 恢复测试 | 已覆盖(TC-APP-PERM-020) | 中断/重复后的状态一致 |
| 稳定性/耐久测试 | 已覆盖(TC-APP-ENV-003) | 仅 10 分钟窗口；**24h 长稳不在本表范围**，建议单列 |
| 安全测试 | 已覆盖(TC-APP-INST-011) | 覆盖命令注入面；**未做完整渗透**（见下方「渗透测试」） |
| 可靠性测试 | 已覆盖(TC-APP-PERM-020,TC-APP-ENV-008) | 幂等 + 日志健康 |
| Ad hoc 测试 | 未覆盖(非脚本化，无法预先编入) | 建议：执行期随手记「可疑观察」到执行记录，事后按 HICCUPPS 收敛 |
| 会话式测试 | 未覆盖(需单独排期) | 建议：90 分钟章程「导出→改配置→导入→清数据→比对」，专攻三条链路的交叉态 |
| 集成测试 | 已覆盖(TC-APP-COMPAT-007,TC-APP-INST-002) | WebUI↔`permkeeper.sh`↔模块的文件桥、并发触发 |
| 系统测试 | 已覆盖(TC-APP-PERM-010,TC-APP-INST-001) | 整机视角：装机 472 应用的采集/恢复 |
| 端到端测试(E2E) | 已覆盖(TC-APP-INST-013,TC-APP-PERM-010) | 导出→导入→清数据→恢复的串联 |
| 验收测试 | 已覆盖(TC-APP-COMPAT-001,TC-APP-PERM-010) | 以「重启后就绪 + 清数据后与基线一致」为可验收准则（与 UAT 的差别：本条是技术准则，不依赖人） |
| 手工测试 | 已覆盖(TC-APP-PERM-015) | 全部 54 条均手工执行；本条最依赖人眼比对渠道名/描述 |
| 自动化测试 | 未覆盖(仓库无测试框架) | 建议：把 `check_baseline.py` + 一批断言扩成 `verify.py`，让 PERM-001～009、021、022 可一键复跑；清数据类仍需手工 |
| 数据驱动测试 | 已覆盖(TC-APP-PERM-002,TC-APP-COMPAT-009) | 同一判定脚本参数化跑 3 个样本应用（kuaibao/posthouse/yunda） |
| 关键字驱动测试 | 未覆盖(无关键字表) | 建议：把 adb 步骤抽成 `{起流｜清数据｜拉基线｜断言}` 四类关键字，后续新应用只换参数 |
| 黑盒测试 | 已覆盖(TC-APP-INST-001,TC-APP-PERM-010) | 只用 `dumpsys`/`appops get`/文件 md5 等外部可见面判定 |
| 正交/组合测试 | 已覆盖(TC-APP-COMPAT-006,TC-APP-PERM-024) | `all` × `includeSystem` × 类别开关 × 每应用覆盖 |
| 特性开关测试 | 已覆盖(TC-APP-PERM-024,TC-APP-COMPAT-006) | 关闭通知类别、开关 `includeSystem` 的行为差异 |
| 渗透测试 | 未覆盖(只覆盖了命令注入一个面) | 建议：补「WebUI 任意文件读」（导出/导入路径拼接，`../` 已测包名侧，未测文件名侧）、「`export.req` 被任意应用触发」两个面 |
| 软件组成分析(SCA) | 未覆盖(无依赖清单) | 建议：模块只有 `.so` + 内嵌 dex，跑 `readelf -d` 确认无第三方动态库，并在 CI 里锁 NDK 版本；无 SBOM 可扫 |

---

## 第二部分 · PERM 系统权限 · 专属 11 条

| 视角 | 状态 | 备注 |
|---|---|---|
| 可访问性 | 已覆盖(TC-APP-PERM-001) | 只通过 adb/su + `/data/system/permkeeper/*.json` 观察，不依赖任何 UI；无图形界面依赖 |
| 可分析性 | 已覆盖(TC-APP-PERM-022,TC-APP-PERM-009) | 失败必须给类别前缀（`perm:`/`op:`/`chan:`/`doze`/`autostart`）；坏文件必须留 `.bad` |
| 易测试性 | 已覆盖(TC-APP-PERM-012) | AppOps 可用 `appops set/get` 独立构造与断言，不依赖应用 UI；权限同样可用 `pm grant/revoke` 构造 |
| 兼容性测试 | 已覆盖(TC-APP-PERM-007,TC-APP-PERM-024) | ColorOS 的 `mundo@` 代理渠道、每应用类别覆盖在本机的实际行为 |
| 易用性测试 | 已覆盖(TC-APP-PERM-024) | 关闭某类别后用户预期「别再动它」，行为与预期一致；WebUI 交互流畅度属前端域 |
| 无障碍测试 | 不适用(模块无独立可访问界面) | 唯一 UI 是 KsuWebUI 里的 WebUI 页，其无障碍（TalkBack/焦点顺序）归前端域；模块本体无界面 |
| 国际化测试(i18n) | 不适用(日志与文件均为英文/ASCII) | 模块日志无本地化文案；WebUI 中文文案的 i18n 属前端域 |
| 数据迁移测试 | 已覆盖(TC-APP-COMPAT-010) | uid 变化（卸载重装）时旧 key（含旧 uid）不得被写入；基线/渠道归属的迁移风险点在这条 |
| 安全配置错误 | 已覆盖(TC-APP-PERM-017) | 渠道不得落 uid 1000（系统身份滥用）；`/data/system/permkeeper/` 600/700 权限在 COMPAT-001 步骤中顺带核对 |
| 组件与依赖漏洞 | 不适用(模块无第三方依赖) | 只用系统反射 API（`IPackageManager`/`INotificationManager`/`deviceidle`），无三方库；风险是「反射签名随版本变」，已由 COMPAT-001/005 的冷启回归覆盖 |
| 权限受限 | 已覆盖(TC-APP-PERM-018,TC-APP-PERM-019) | 两类越权边界：非主用户（分身）不得按主 uid 操作、`exclude` 应用不得被写入 |

### PERM 模块规则覆盖情况（`rules.md` 第二章 二/三/四/五章 + 禁止规则）

| 规则ID | 覆盖用例 | 说明 |
|---|---|---|
| R-PERM-01 | TC-APP-PERM-001 | 基线只收受保护应用 |
| R-PERM-02 | TC-APP-PERM-002,TC-APP-PERM-003 | 有效类 + 无效类（已撤销）各一条 |
| R-PERM-03 | TC-APP-PERM-004 | 数字 op id |
| R-PERM-04 | TC-APP-PERM-005 | 排除 mode=3 |
| **R-PERM-05** | **未覆盖** | **不适用**：本机 ColorOS 不存在 MIUIOP（`10000..10200`），`appops get` 无该区间 op；该规则只能在小米/HyperOS 上验证（历史已在 Redmi K80/小米 17 上验过） |
| R-PERM-06 | TC-APP-PERM-006,TC-APP-COMPAT-010 | key 三段式；uid 段在重装后变化的风险 |
| R-PERM-07 | TC-APP-PERM-007 | mundo 代理渠道归属 |
| R-PERM-08 | TC-APP-PERM-008,TC-APP-ENV-004 | 合并次数 + 延迟区间 |
| R-PERM-09 | TC-APP-PERM-009 | 损坏留档 |
| R-PERM-10 | TC-APP-PERM-023 | 不轮询 |
| **R-PERM-11** | **未覆盖** | **不适用**：该规则是 HyperOS「先广播后重置」的时序补偿，只在小米机型暴露；本机为「先重置后广播」，1.8s 等待在本机无法产生可区分的结果（历史已在红米上验证过修复） |
| R-PERM-12 | TC-APP-PERM-010,TC-APP-PERM-022,TC-APP-ENV-007 | 3 轮上限、rounds=1 成功路径、耗时上限 |
| R-PERM-13 | TC-APP-PERM-010,TC-APP-PERM-022,TC-APP-COMPAT-009 | 逐类别校验、缺项前缀、0 渠道不误报 |
| R-PERM-14 | TC-APP-PERM-021 | 恢复后基线不回退 |
| R-PERM-15 | TC-APP-PERM-018 | 分身跳过 |
| R-PERM-16 | TC-APP-PERM-019 | 未受保护应用直接返回 |
| R-PERM-17 | TC-APP-PERM-020 | 幂等 |
| R-PERM-18 | TC-APP-PERM-010,TC-APP-PERM-011 | 只授快照内（含反向断言） |
| R-PERM-19 | TC-APP-PERM-012 | 逐项设回 |
| R-PERM-20 | TC-APP-PERM-013 | 只影响目标包 |
| R-PERM-21 | TC-APP-PERM-014 | Doze（本机 `pm clear` 不清，只能防御性再确认） |
| **R-PERM-22** | **未覆盖** | 原因：`deviceidle` binder 在 `system_server` 内，root 下无法单独 stop 该服务构造「接口不可用」；建议：加一个调试开关（`config` 里 `debug.disableDoze`）或在 CI 用 mock 反编译件验证，再补一条 |
| R-PERM-23 | TC-APP-PERM-015 | 渠道存在时只改 importance |
| R-PERM-24 | TC-APP-PERM-016 | 重建用 chanName |
| R-PERM-25 | TC-APP-PERM-017,TC-APP-COMPAT-010 | 落应用 uid；uid 变化后仍不得落错 |
| R-PERM-26 | TC-APP-PERM-024 | 通知类别关闭时跳过且不计失败 |
| R-PERM-27 | TC-APP-PERM-016,TC-APP-PERM-022 | 名字缺失回退 id 并打日志（022 的构造会打出该日志或 `chan:` 缺项） |
| R-PERM-28 | TC-APP-PERM-007 | 代理渠道按 owner 恢复 |
| R-X-01 | TC-APP-PERM-001,TC-APP-PERM-019 | 禁止对未受保护应用写入 |
| R-X-02 | TC-APP-PERM-011 | 禁止授予快照外权限 |
| R-X-03 | TC-APP-PERM-017 | 禁止把渠道创建到 uid 1000 |
| R-X-06 | TC-APP-PERM-018 | 禁止在非主用户按主 uid 操作 |
| R-X-08 | TC-APP-PERM-009 | 禁止损坏时静默降级 |

> 说明：R-SET-03/04 属 SET 模块规则，本域只在 TC-APP-PERM-024 做交叉验证（类别开关关闭 → 不恢复且不报失败）。

---

## 第三部分 · INST 安装升级 · 专属 11 条

| 视角 | 状态 | 备注 |
|---|---|---|
| 可访问性 | 已覆盖(TC-APP-INST-006) | 导出产物落在 `/sdcard/Download/permkeeper/`，可用 adb/文件管理器直接访问与校验 |
| 可分析性 | 已覆盖(TC-APP-INST-003,TC-APP-INST-005) | `export.progress`/`export.done` 可实时采样；产物四字段自带版本号与时间戳，可追溯 |
| 易测试性 | 已覆盖(TC-APP-INST-002) | 可用 `: > export.req` 直接触发导出，绕过 UI 连点，便于构造并发 |
| 兼容性测试 | 已覆盖(TC-APP-INST-001) | 导出对 472 个应用（含系统、targetSdk 混杂）全量采集不崩 |
| 易用性测试 | 已覆盖(TC-APP-INST-008,TC-APP-INST-012) | 导入默认「全部应用」、可取消且提示为中性灰（不让用户误以为失败） |
| 无障碍测试 | 不适用(导入/导出面板属 WebUI) | 进度层、确认弹窗的无障碍归前端域；模块侧只产出文件 |
| 国际化测试(i18n) | 不适用(产物字段与日志为 ASCII) | JSON 字段名、`export.progress` 格式不含文案；toast 文案属前端域 |
| 数据迁移测试 | 已覆盖(TC-APP-INST-005,TC-APP-INST-010) | `permkeeper:2` 版本号字段存在；旧快照/含未安装包的快照导入不崩 |
| 安全配置错误 | 已覆盖(TC-APP-INST-011) | 包名/权限名白名单，防 root 命令注入（已修 F3 的回写） |
| 组件与依赖漏洞 | 不适用(无三方组件) | 导入执行靠系统 `pm`/`appops` 二进制，无第三方解析库；JSON 解析在 WebView 内，风险归前端域 |
| 回滚 | 未覆盖(设计上不提供) | 原因：导入是「严格覆盖 + 未安装跳过」，**没有回滚入口**，取消也不回滚已下发命令。建议：补「导入前自动导出当前状态」的隐性快照，或至少在确认弹窗里提示「不可撤销」 |

### INST 模块规则覆盖情况（`rules.md` 第六/七章 + 相关禁止规则）

| 规则ID | 覆盖用例 | 说明 |
|---|---|---|
| R-INST-01 | TC-APP-INST-001 | 全量采集，与 exclude 无关 |
| R-INST-02 | TC-APP-INST-002 | 一次请求一次采集 |
| R-INST-03 | TC-APP-INST-003,TC-APP-ENV-007 | 进度单调 |
| R-INST-04 | TC-APP-INST-004 | 导出中无取消按钮 |
| R-INST-05 | TC-APP-INST-005 | 四字段 + 版本号 |
| R-INST-06 | TC-APP-INST-006 | 落 `/sdcard/Download/permkeeper/` |
| R-INST-07 | TC-APP-INST-006 | `export.*` 清零 |
| R-INST-08 | TC-APP-INST-007 | 绿/红 toast |
| R-INST-09 | TC-APP-INST-008 | 默认「全部应用」 |
| R-INST-10 | TC-APP-INST-009 | 比对真实状态 |
| R-INST-11 | TC-APP-INST-010 | 未安装跳过不计失败 |
| R-INST-12 | TC-APP-INST-011 | 非法包名/权限名跳过 |
| R-INST-13 | TC-APP-INST-012 | 阶段二可取消，中性提示 |
| R-INST-14 | TC-APP-INST-013 | 完成后写回 baseline |
| R-INST-15 | TC-APP-INST-013 | 完成后关闭弹层 |
| R-X-04 | TC-APP-INST-002 | 禁止一次请求并发多次采集 |
| R-X-05 | TC-APP-INST-003 | 禁止进度回退 |
| R-X-09 | TC-APP-INST-010 | 禁止把未安装跳过当失败 |
| R-X-10 | TC-APP-INST-011 | 禁止未转义拼接 root 命令 |

---

## 第四部分 · ENV 环境与性能 · 专属 12 条

| 视角 | 状态 | 备注 |
|---|---|---|
| 可访问性 | 已覆盖(TC-APP-ENV-002) | 证明「普通 shell 进不去、必须 su」——环境可达性是所有用例的前提 |
| 可分析性 | 已覆盖(TC-APP-ENV-001,TC-APP-ENV-008) | 实时抓流 vs 事后；单次日志行数 ≤40，保证 64KiB buffer 内可诊断 |
| 易测试性 | 已覆盖(TC-APP-ENV-004) | 用 `stat -c %Y` 观测 mtime 变化即可测扫描时序，不需要改代码插桩 |
| 兼容性测试 | 已覆盖(TC-APP-COMPAT-001) | 冷启后注入完成是本机（ColorOS16/Android16）的兼容基线；跨 ROM 见 COMPAT-002 |
| 易用性测试 | 已覆盖(TC-APP-ENV-005) | 首屏 ≤2s 直接决定「打开就能用」的体感 |
| 无障碍测试 | 不适用(本域用例不触达 UI 语义) | 唯一涉及的 UI 是首屏/滚动，其 TalkBack 可读性归前端域 |
| 国际化测试(i18n) | 不适用(本域观测的是耗时/CPU/日志) | 无界面文案参与 |
| 数据迁移测试 | 已覆盖(TC-APP-ENV-005) | 冷启后 `apps.json` 必须被重写（否则列表回退 `pm list packages`）——这是文件级迁移正确性 |
| 安全配置错误 | 已覆盖(TC-APP-ENV-002) | 非 su 执行 `pm clear` 必须失败，防止脚本误用降权路径 |
| 组件与依赖漏洞 | 不适用(无可枚举依赖) | 同 PERM：只有系统 binder 调用，无三方库 |
| 冷启动/首屏(TTID/TTFD) | 已覆盖(TC-APP-ENV-005,TC-APP-COMPAT-001) | TTID ≤2.0s；模块侧「冷启后 ready」是首屏的前置条件 |
| 渲染帧率与卡顿(Jank/ANR) | 已覆盖(TC-APP-ENV-006) | 472 应用列表连续滑动 10s，janky ≤5%、无 ANR |

### ENV 模块规则覆盖情况（`rules.md` 第八章 ENV 部分）

| 规则ID | 覆盖用例 | 说明 |
|---|---|---|
| R-ENV-01 | TC-APP-ENV-001,TC-APP-ENV-008,TC-APP-COMPAT-001 | 实时抓取必要性 + 日志量控制 |
| R-ENV-02 | TC-APP-ENV-002 | `pm clear` 必须经 su（只写了无效等价类，有效类由所有清数据用例隐含） |
| R-ENV-03 | TC-APP-ENV-003,TC-APP-PERM-023 | 常驻开销低、无定时任务 |

---

## 第五部分 · COMPAT 兼容性 · 专属 15 条

| 视角 | 状态 | 备注 |
|---|---|---|
| 可访问性 | 已覆盖(TC-APP-COMPAT-001) | 冷启后 `apps.json` 可读、WebUI 列表非空——模块产物本身的可访问性 |
| 可分析性 | 已覆盖(TC-APP-COMPAT-005) | 用 `maps` 里的 `(deleted)` 标记判断「内存中跑的是哪个版本的 .so」，是升级排障的关键手段 |
| 易测试性 | 已覆盖(TC-APP-COMPAT-004) | XML 结构可用 `xml.etree` 静态断言，无需起应用 |
| 兼容性测试 | 已覆盖(TC-APP-COMPAT-002,TC-APP-COMPAT-009,TC-APP-COMPAT-010) | ROM 抽象、0 渠道应用、uid 变化 |
| 易用性测试 | 已覆盖(TC-APP-COMPAT-008) | 深色/1.3x 字体/高 DPI 下控件不越界、可完成写入 |
| 无障碍测试 | 未覆盖(本域无 TalkBack 执行条件) | 原因：harness 无法读 PNG 截图、无 TalkBack 自动化；建议前端域用 `browser.snapshot` 的无障碍树 + 手工 TalkBack 各跑一次 WebUI |
| 国际化测试(i18n) | 不适用(模块无多语言资源) | `webroot` 只有中文文案，无 `values-*` 资源；多语言属产品决策，非缺陷 |
| 数据迁移测试 | 已覆盖(TC-APP-COMPAT-005,TC-APP-COMPAT-010) | 模块升级（`.so` 换代）与应用重装（uid 变）两类迁移 |
| 安全配置错误 | 已覆盖(TC-APP-COMPAT-003) | 系统 XML 写前自检，非法即放弃；`/data/system/permkeeper/` 权限在 COMPAT-001 中顺带核对 |
| 组件与依赖漏洞 | 不适用(无三方动态库) | 模块只依赖 libc/liblog 与系统 API；建议 CI 加 `readelf -d` 白名单校验（见基线 SCA 行） |
| TLS 配置与协议强度 | 不适用(模块不发任何网络请求) | 无 socket/HTTP 调用；WebView 只加载本地 `file://`/相对资源，无 TLS 面 |
| 证书校验与中间人 | 不适用(同上，无网络栈) | 无证书校验逻辑可测 |
| 冷启动/首屏(TTID/TTFD) | 已覆盖(TC-APP-COMPAT-001,TC-APP-ENV-005) | 模块侧 `ready (after ~Nms)`；WebUI 侧 TTID ≤2s |
| 渲染帧率与卡顿(Jank/ANR) | 已覆盖(TC-APP-ENV-006) | 列表滚动 janky ≤5%、无 ANR |
| 前端核心指标(LCP/INP/CLS) | 未覆盖(无浏览器性能采集条件) | 原因：WebUI 跑在 KsuWebUI 的 WebView 里，`file://` 页面 + 无 DevTools 连接，取不到 LCP/INP/CLS；建议：用 Chrome `webview_devtools`（需可调试 WebView）或退化为「首屏 ≤2s + 滑动 janky ≤5%」代理指标（已由 ENV-005/006 覆盖） |

### COMPAT 模块规则覆盖情况（`rules.md` 第八章 COMPAT 部分 + 相关禁止规则）

| 规则ID | 覆盖用例 | 说明 |
|---|---|---|
| R-COMPAT-01 | TC-APP-COMPAT-001,TC-APP-COMPAT-005,TC-APP-ENV-005,TC-APP-ENV-006 | 冷启注入就绪；升级必须冷启；首屏/滚动是就绪的下游表现 |
| R-COMPAT-02 | TC-APP-COMPAT-002 | 一加走 XML，不进小米路径 |
| R-COMPAT-03 | TC-APP-COMPAT-003 | 非法/不可解析即放弃写入 |
| R-COMPAT-04 | TC-APP-COMPAT-004 | 追加位置与单根元素 |
| R-X-07 | TC-APP-COMPAT-003,TC-APP-COMPAT-004 | 禁止写坏系统 XML |

---

## 第六部分 · 汇总

| 项 | 数值 |
|---|---|
| 基线视角行 | 53 |
| PERM 专属行 | 11 |
| INST 专属行 | 11 |
| ENV 专属行 | 12 |
| COMPAT 专属行 | 15 |
| **合计** | **102** |

本域 `rules.md` 规则未覆盖清单：

| 规则ID | 归类 | 原因 |
|---|---|---|
| R-PERM-05 | 不适用 | 小米 MIUIOP 专用，本机 ColorOS 无该区间 op |
| R-PERM-11 | 不适用 | HyperOS「先广播后重置」专用时序补偿，本机为「先重置后广播」 |
| R-PERM-22 | 未覆盖 | 无法在 root 下构造 `deviceidle` 不可用；建议加调试开关后补 |
