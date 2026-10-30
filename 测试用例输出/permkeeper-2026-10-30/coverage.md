# PermKeeper 覆盖度自查（横向视角 + 纵向规则追溯）

生成依据：`cases.md`（78 条）、`declare_end.md` + `declare_biz_jrn.md`、`rules.md`（69 条）

> 注：两个子智能体各自申报了一遍基线 53 条，本表只计一次（技能要求基线层整个任务申报一次）。

## 一、横向：视角覆盖

| 分组 | 必过视角数 | 已覆盖 | 不适用 | 未覆盖 |
|---|---|---|---|---|
| 基线（全模块共用） | 53 | 39 | 2 | 12 |
| PERM | 11 | 8 | 3 | 0 |
| INST | 11 | 7 | 3 | 1 |
| ENV | 12 | 9 | 3 | 0 |
| COMPAT | 15 | 9 | 4 | 2 |
| SET | 17 | 7 | 3 | 7 |
| **合计** | **119** | **79** | **18** | **22** |

> 行数核验：期望 119 行（53+11+11+12+15+17+0），实际申报 **119** 行 —— **通过**

### 1.1 未覆盖视角与建议

| 分组 | 视角 | 原因与建议 |
|---|---|---|
| 基线 | 功能适合性 | 需真实使用场景 |
| 基线 | 易学性 | WebUI 无引导 |
| 基线 | 保密性 | 导出快照落 sdcard |
| 基线 | 验收测试(UAT) | 无验收人/验收准则文档 |
| 基线 | Alpha/Beta 测试 | 单机自用无内测用户群 |
| 基线 | 文档测试 | 无用户手册 |
| 基线 | Ad hoc 测试 | 非脚本化，无法预先编入 |
| 基线 | 会话式测试 | 需单独排期 |
| 基线 | 自动化测试 | 仓库无测试框架 |
| 基线 | 关键字驱动测试 | 无关键字表 |
| 基线 | 渗透测试 | 只覆盖了命令注入一个面 |
| 基线 | 软件组成分析(SCA) | 无依赖清单 |
| INST | 回滚 | 设计上不提供 |
| COMPAT | 无障碍测试 | 本域无 TalkBack 执行条件 |
| COMPAT | 前端核心指标(LCP/INP/CLS) | 无浏览器性能采集条件 |
| SET | 可访问性 | 原因：4 个开关是 `opacity:0` 的原生 checkbox（靠 `<label class="sw">` 包裹），TalkBack 读到的名称来自相邻文本节点的关联，未验证；建议真机开 TalkBack 走一遍「4 开关 + 应用列表勾选 + 完成」，确认可聚焦、可播报、可切换 |
| SET | 兼容性测试 | 原因：仅一加 ColorOS 16 一台机；KsuWebUI 与 Magisk App 两种宿主、HyperOS 的 MIUIOP 与 `mipush` 代理渠道都未验；建议 COMPAT 域补，参考 R-COMPAT-02 |
| SET | 国际化测试(i18n) | 原因：文案全部硬编码中文（`<html lang="zh-CN">`，无 strings 资源），系统语言切英文后界面仍为中文；属「已知不支持」而非缺陷，但需产品确认并在 README 写明；建议补一条「系统语言切 English 后界面文案与布局不溢出」的渲染检查 |
| SET | 访问控制 | 原因：未断言 `/data/system/permkeeper/` 目录 700、`config.json`/`baseline.json` 600、属主 `system:system`、SELinux 上下文 `system_data_file`；建议补一条 `su 0 ls -lZ` 断言，并验证普通应用（非 root）`cat` 该目录被拒 |
| SET | 日志脱敏 | 原因：`logcat` 直接打印完整包名与通知渠道 key（形如 `ownerPkg|uid|channelId`，含 uid），未做任何脱敏；是否算敏感需产品确认。建议先取一条真实日志评审，再决定「保留/截断/仅 debug 构建输出」 |
| SET | 敏感数据存储 | 原因：`baseline.json` 明文含全部权限/AppOp/渠道/uid，导出包又把**全部已安装应用**（含系统）快照写到 `/sdcard/Download/permkeeper/`，该目录对其他应用可读；建议产品决策后补「导出文件权限 600 + 是否只导受保护应用」用例 |
| SET | 个人信息保护 | 原因：导出包的应用清单可反推用户装机画像，属个人信息；当前无告知、无删除/撤回机制（DSAR）。建议 README 增加数据流向说明，并补「导出后删除文件 → 模块侧无副本残留」用例 |

未覆盖合计 **22** 条，不适用 **18** 条。

## 二、纵向：规则追溯

| 规则ID | 覆盖用例 | 条数 |
|---|---|---|
| R-COMPAT-01 | TC-APP-COMPAT-001, TC-APP-COMPAT-005, TC-APP-ENV-005, TC-APP-ENV-006, TC-APP-JRN-005, TC-APP-JRN-007 | 6 |
| R-COMPAT-02 | TC-APP-COMPAT-002, TC-APP-JRN-005 | 2 |
| R-COMPAT-03 | TC-APP-COMPAT-003 | 1 |
| R-COMPAT-04 | TC-APP-COMPAT-004 | 1 |
| R-ENV-01 | TC-APP-ENV-001, TC-APP-ENV-008, TC-APP-JRN-001 | 3 |
| R-ENV-02 | TC-APP-ENV-002, TC-APP-JRN-001 | 2 |
| R-ENV-03 | TC-APP-ENV-003, TC-APP-PERM-023 | 2 |
| R-INST-01 | TC-APP-INST-001, TC-APP-JRN-003 | 2 |
| R-INST-02 | TC-APP-INST-002, TC-APP-JRN-004 | 2 |
| R-INST-03 | TC-APP-INST-003, TC-APP-JRN-004 | 2 |
| R-INST-04 | TC-APP-INST-004, TC-APP-JRN-004 | 2 |
| R-INST-05 | TC-APP-INST-005, TC-APP-JRN-003 | 2 |
| R-INST-06 | TC-APP-INST-006, TC-APP-JRN-003, TC-APP-JRN-004 | 3 |
| R-INST-07 | TC-APP-INST-006, TC-APP-JRN-004 | 2 |
| R-INST-08 | TC-APP-INST-007, TC-APP-JRN-003, TC-APP-JRN-004 | 3 |
| R-INST-09 | TC-APP-INST-008, TC-APP-JRN-003 | 2 |
| R-INST-10 | TC-APP-INST-009, TC-APP-JRN-003 | 2 |
| R-INST-11 | TC-APP-INST-010, TC-APP-SET-014 | 2 |
| R-INST-12 | TC-APP-INST-011, TC-APP-SET-013 | 2 |
| R-INST-13 | TC-APP-INST-012, TC-APP-SET-015 | 2 |
| R-INST-14 | TC-APP-INST-013, TC-APP-JRN-003 | 2 |
| R-INST-15 | TC-APP-INST-013, TC-APP-JRN-003 | 2 |
| R-PERM-01 | TC-APP-JRN-002, TC-APP-JRN-005, TC-APP-PERM-001, TC-APP-SET-003 | 4 |
| R-PERM-02 | TC-APP-PERM-002, TC-APP-PERM-003 | 2 |
| R-PERM-03 | TC-APP-PERM-004 | 1 |
| R-PERM-04 | TC-APP-PERM-005 | 1 |
| R-PERM-05 | **未覆盖** | 0 |
| R-PERM-06 | TC-APP-COMPAT-010, TC-APP-PERM-006 | 2 |
| R-PERM-07 | TC-APP-PERM-007 | 1 |
| R-PERM-08 | TC-APP-ENV-004, TC-APP-PERM-008 | 2 |
| R-PERM-09 | TC-APP-PERM-009 | 1 |
| R-PERM-10 | TC-APP-ENV-003, TC-APP-PERM-023 | 2 |
| R-PERM-11 | TC-APP-JRN-001 | 1 |
| R-PERM-12 | TC-APP-ENV-007, TC-APP-JRN-001, TC-APP-JRN-007, TC-APP-PERM-010, TC-APP-PERM-022 | 5 |
| R-PERM-13 | TC-APP-COMPAT-009, TC-APP-JRN-001, TC-APP-PERM-010, TC-APP-PERM-022 | 4 |
| R-PERM-14 | TC-APP-JRN-007, TC-APP-PERM-021 | 2 |
| R-PERM-15 | TC-APP-PERM-018 | 1 |
| R-PERM-16 | TC-APP-JRN-002, TC-APP-PERM-019 | 2 |
| R-PERM-17 | TC-APP-JRN-007, TC-APP-PERM-020 | 2 |
| R-PERM-18 | TC-APP-JRN-001, TC-APP-PERM-010, TC-APP-PERM-011 | 3 |
| R-PERM-19 | TC-APP-JRN-001, TC-APP-PERM-012 | 2 |
| R-PERM-20 | TC-APP-JRN-001, TC-APP-PERM-013 | 2 |
| R-PERM-21 | TC-APP-JRN-001, TC-APP-PERM-014 | 2 |
| R-PERM-22 | **未覆盖** | 0 |
| R-PERM-23 | TC-APP-PERM-015 | 1 |
| R-PERM-24 | TC-APP-PERM-016 | 1 |
| R-PERM-25 | TC-APP-COMPAT-010, TC-APP-PERM-017 | 2 |
| R-PERM-26 | TC-APP-PERM-024 | 1 |
| R-PERM-27 | TC-APP-PERM-016 | 1 |
| R-PERM-28 | TC-APP-PERM-007 | 1 |
| R-SET-01 | TC-APP-JRN-001, TC-APP-JRN-002, TC-APP-SET-003, TC-APP-SET-004, TC-APP-SET-005, TC-APP-SET-006 | 6 |
| R-SET-02 | TC-APP-JRN-006, TC-APP-SET-008 | 2 |
| R-SET-03 | TC-APP-SET-009 | 1 |
| R-SET-04 | TC-APP-PERM-024, TC-APP-SET-010 | 2 |
| R-SET-05 | TC-APP-COMPAT-006, TC-APP-JRN-005, TC-APP-SET-011 | 3 |
| R-SET-06 | TC-APP-COMPAT-007, TC-APP-COMPAT-008, TC-APP-JRN-005, TC-APP-JRN-008, TC-APP-SET-001, TC-APP-SET-005 | 6 |
| R-SET-07 | TC-APP-SET-002, TC-APP-SET-006, TC-APP-SET-012 | 3 |
| R-SET-08 | TC-APP-SET-007 | 1 |
| R-SET-09 | TC-APP-JRN-006, TC-APP-SET-008 | 2 |
| R-X-01 | TC-APP-JRN-002, TC-APP-JRN-006, TC-APP-PERM-001, TC-APP-PERM-019 | 4 |
| R-X-02 | TC-APP-PERM-011 | 1 |
| R-X-03 | TC-APP-PERM-017 | 1 |
| R-X-04 | TC-APP-INST-002 | 1 |
| R-X-05 | TC-APP-INST-003 | 1 |
| R-X-06 | TC-APP-PERM-018 | 1 |
| R-X-07 | TC-APP-COMPAT-003, TC-APP-COMPAT-004 | 2 |
| R-X-08 | TC-APP-PERM-009, TC-APP-SET-007 | 2 |
| R-X-09 | TC-APP-INST-010, TC-APP-SET-014 | 2 |
| R-X-10 | TC-APP-INST-011, TC-APP-SET-013 | 2 |

### 2.1 未覆盖规则与原因

| 规则ID | 原因 | 建议 |
|---|---|---|
| R-PERM-05 | 厂商 AppOp（MIUIOP 10000..10200）只在 HyperOS 上存在；本轮为 ColorOS，**不适用** | 待小米真机回归时补测 |
| R-PERM-22 | 需构造 `deviceidle` 服务不可用的故障，无法在不改系统服务的前提下可靠复现 | 用代码审阅 + 注入故障替代，或标为不适用 |





## 三、执行状态汇总

| 项 | 值 |
|---|---|
| 用例总数 | 78 |
| 已执行 | 35 |
| PASS | 34 |
| FAIL | 0 |
| BLOCKED / 未执行 | 43 |

### 3.1 按模块

| 模块 | 总数 | 已执行 | PASS | FAIL | 未执行 |
|---|---|---|---|---|---|
| 系统权限 | 24 | 16 | 15 | 0 | 8 |
| 设置与配置界面 | 15 | 5 | 5 | 0 | 10 |
| 安装升级 | 13 | 5 | 5 | 0 | 8 |
| 兼容性 | 10 | 3 | 3 | 0 | 7 |
| 环境与性能 | 8 | 4 | 4 | 0 | 4 |
| 端到端旅程 | 8 | 2 | 2 | 0 | 6 |

## 四、缺陷清单

| 缺陷ID | 用例 | 严重度 | 现象 | 根因 | 状态 |
|---|---|---|---|---|---|
| BUG-01 | TC-APP-PERM-001<br>TC-APP-PERM-006 | P2 | `baseline.json` 保留已移入 `exclude` 的应用快照：70 条中 67 条属于排除名单；其 149 条通知渠道里 147 条缺 `chanName`（旧格式遗留） | `SystemHook.scan()` 只对 `covered` 应用调用 `base.put()`，**从不删除**已不再受保护的条目 | **已修复并复测通过** |

**影响**：① 违反「基线只记录受保护应用」；② 基线随使用时间膨胀，携带过期格式；③ 应用重新纳入保护时，在其被下一次 `scan` 刷新前会短暂使用过期快照（含缺 `chanName` 的渠道，重建时只能退回渠道 id 作名字）。

**功能影响面**：因 `Config.covers()` 在清数据入口处有闸门，排除名单内的应用不会被恢复，所以**不会误恢复**；属数据卫生与潜在降级问题。

**修法（已实施）**：`scan()` 保存前剔除 `base` 中 `key ∉ covered` 的条目。复测：基线由 70 条收缩到 3 条，缺 `chanName` 归零。

## 五、未执行用例与原因

| 模块 | 条数 | 用例 |
|---|---|---|
| 设置与配置界面 | 10 | TC-APP-SET-001, TC-APP-SET-002, TC-APP-SET-006, TC-APP-SET-008, TC-APP-SET-009, TC-APP-SET-010, TC-APP-SET-012, TC-APP-SET-013… |
| 系统权限 | 8 | TC-APP-PERM-008, TC-APP-PERM-014, TC-APP-PERM-015, TC-APP-PERM-016, TC-APP-PERM-018, TC-APP-PERM-020, TC-APP-PERM-022, TC-APP-PERM-024 |
| 安装升级 | 8 | TC-APP-INST-004, TC-APP-INST-007, TC-APP-INST-008, TC-APP-INST-009, TC-APP-INST-010, TC-APP-INST-011, TC-APP-INST-012, TC-APP-INST-013 |
| 兼容性 | 7 | TC-APP-COMPAT-003, TC-APP-COMPAT-004, TC-APP-COMPAT-005, TC-APP-COMPAT-006, TC-APP-COMPAT-007, TC-APP-COMPAT-008, TC-APP-COMPAT-010 |
| 端到端旅程 | 6 | TC-APP-JRN-002, TC-APP-JRN-003, TC-APP-JRN-004, TC-APP-JRN-006, TC-APP-JRN-007, TC-APP-JRN-008 |
| 环境与性能 | 4 | TC-APP-ENV-003, TC-APP-ENV-005, TC-APP-ENV-006, TC-APP-ENV-007 |

主要阻塞原因：
1. **需要 WebUI 交互**（点开关、看 toast/进度层、导入面板）：需在 KsuWebUI 中人工操作，或改用桌面假桥做逻辑验证（与真机渲染有差异）。
2. **需要真实清数据**：会丢登录态，执行前须先备份（本轮已完成 27 条，含 3 次真实清数据）。
3. **本机不可构造**：多用户/分身、`deviceidle` 服务不可用、HyperOS 时序。
