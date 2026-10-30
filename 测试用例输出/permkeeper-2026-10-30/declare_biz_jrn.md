# 视角申报表 · 业务功能域 SET ＋ 端到端旅程域 JRN

- 项目：PermKeeper / `6e2ca13` / 2026-10-30
- 申报范围：`TC-APP-SET-001..015`（15 条）、`TC-APP-JRN-001..008`（8 条）
- 状态取值只有三种：`已覆盖(TC-xxx)` / `不适用(原因)` / `未覆盖(原因+建议)`，**无空白行**
- 本表行数：**53（基线）+ 17（SET 专属）+ 0（JRN 专属）= 70**

---

## 一、基线层（53 条，整个任务只申报一次）

| 视角 | 状态 | 备注 |
|---|---|---|
| 功能性 | 已覆盖(TC-APP-SET-001) | 拨开关即落盘是本模块的主功能形态，无「保存」按钮 |
| 功能完备性 | 已覆盖(TC-APP-SET-003) | 保护范围的两个极端（全保护/全排除）都覆盖到，`includeSystem` 见 TC-APP-SET-011 |
| 功能正确性 | 已覆盖(TC-APP-JRN-001) | 以「granted 集合与基线完全相等」这类可计算判据验收，不靠肉眼 |
| 功能适合性 | 已覆盖(TC-APP-JRN-003) | 导出→清数据→导入是用户真实目的（换机/重置保设置） |
| 性能效率 | 未覆盖(原因：本批次只设计功能与健壮性，未给耗时基线；建议补「706 应用导出 ≤ 15 秒、导入 200 包 ≤ 60 秒」两条，属 ENV/PERF 域) | — |
| 时间特性 | 已覆盖(TC-APP-JRN-001) | 断言「首次恢复日志时刻 − pm clear 时刻 ≥ 1.8 秒」（R-PERM-11），且自动保存 1.5 秒内落盘（TC-APP-SET-001） |
| 资源利用率 | 未覆盖(原因：无轮询/CPU/内存采样用例；建议 ENV 域补「空闲 10 分钟 CPU 占用 ≈ 0」（R-ENV-03、R-PERM-10）) | — |
| 兼容性 | 未覆盖(原因：只有一加 PLU110 一台真机，HyperOS/小米路径（MIUIOP）与 `mipush` 代理渠道未验；建议 COMPAT 域补交叉机型，参考 R-COMPAT-02) | — |
| 易用性 | 已覆盖(TC-APP-SET-004) | 全选/全不选 + 摘要文案，不用逐个点 |
| 可辨识性 | 已覆盖(TC-APP-SET-011) | `selSummary` 的「（含系统）」后缀让用户知道自己看的是哪一份列表 |
| 易学性 | 不适用(单页 4 开关 + 4 个入口 + 1 个列表，无引导/教程形态，学不会的风险来自平台而非产品) | — |
| 易操作性 | 已覆盖(TC-APP-SET-001) | 改动即生效，无「改完忘记保存」的操作负担 |
| 用户差错防御 | 已覆盖(TC-APP-SET-015) | 导入有 `cdlgYes` 二次确认、第二阶段可取消、取消不计失败 |
| 可靠性 | 已覆盖(TC-APP-SET-002) | 200 次 50ms 采样证明写入原子（`.tmp`+rename） |
| 成熟性 | 未覆盖(原因：需长时间运行才可观察；建议 ENV 域补「连续运行 24h 无异常退出 / 无新崩溃日志」) | — |
| 容错性 | 已覆盖(TC-APP-SET-007) | 坏 JSON 必须留档 + 打日志，不得静默降级（R-X-08） |
| 易恢复性 | 已覆盖(TC-APP-JRN-007) | 恢复中途断电重启后基线与状态的可恢复性 |
| 安全性 | 已覆盖(TC-APP-SET-013) | root 命令注入是 root 模块的头号红线（R-X-10） |
| 保密性 | 未覆盖(原因：`/data/system/permkeeper/` 的 700/600 与属主 system 未做断言校验；建议补「`ls -lZ` 断言 config/baseline 为 600、目录 700，非 system 进程不可读」) | — |
| 完整性 | 已覆盖(TC-APP-SET-002) | 半截配置 = 完整性被破坏，本条即针对它 |
| 真实性 | 不适用(无身份认证模型：WebUI 由 KsuWebUI/Magisk 以 root 会话加载本地页，不存在「谁登录了」的概念) | — |
| 适应性 | 未覆盖(原因：深色模式、横竖屏、分屏、不同 DPI 均未验；建议 COMPAT 域补「深色模式下 4 个开关与 toast 对比度可辨」) | — |
| 功能测试 | 已覆盖(TC-APP-JRN-001) | — |
| 非功能测试 | 未覆盖(原因：非功能只覆盖了安全一面（SET-013），性能与资源为零；建议随第 5、7 行一起补) | — |
| 回归测试 | 已覆盖(TC-APP-JRN-005) | 重启后配置与保护范围不变，可作为每轮回归的固定位 |
| 确认测试/复测 | 未覆盖(原因：本批次为设计阶段，无待复测的缺陷单；建议 R-PERM-25（渠道 uid 修复）上线后补一条定向复测「`dumpsys notification` 中渠道在应用 uid 下、不在 1000 下」) | — |
| 冒烟测试 | 已覆盖(TC-APP-JRN-001) | 一条闭环即可判「模块是否基本可用」 |
| 健全性测试 | 已覆盖(TC-APP-SET-001) | 小改动（改 1 个开关）的窄而深验证 |
| 验收测试(UAT) | 未覆盖(原因：需真实用户按自己的应用清单试用；建议发版前请 1 名真实用户按 TC-APP-JRN-001 走一遍并记录主观结论) | — |
| Alpha/Beta 测试 | 不适用(内部 root 模块，无公测分发渠道) | — |
| 探索式测试 | 已覆盖(TC-APP-SET-006) | 配置里塞非法包名/恶意名称属于探索发现的异常数据面 |
| 基于风险的测试 | 已覆盖(TC-APP-JRN-007) | 按风险排序：恢复中断+基线污染被定为最高风险并单列 P0 |
| 文档测试 | 未覆盖(原因：仓内无用户手册，只有 README/LOGIC；建议核对界面文案（如「按快照覆盖各应用权限（不一致的应用跳过）」）与实际行为是否一致) | — |
| 配置测试 | 已覆盖(TC-APP-SET-008) | 老格式/新格式两套配置形态的读入行为 |
| 恢复测试 | 已覆盖(TC-APP-JRN-007) | — |
| 稳定性/耐久测试 | 未覆盖(原因：同「成熟性」，需 24h 与重复导出 10 次的耐久脚本；建议 ENV 域承接) | — |
| 安全测试 | 已覆盖(TC-APP-SET-013) | — |
| 可靠性测试 | 已覆盖(TC-APP-SET-002) | — |
| Ad hoc 测试 | 不适用(本批次为脚本化设计，ad hoc 由执行人现场补，不在此申报) | — |
| 会话式测试 | 不适用(需限时章程与记录，本批次不组织) | — |
| 集成测试 | 已覆盖(TC-APP-JRN-003) | WebUI ↔ `permkeeper.sh` ↔ 模块 ↔ 系统接口 四方联调 |
| 系统测试 | 已覆盖(TC-APP-JRN-001) | 整机整体行为（含 broadcast、权限存储、通知服务） |
| 端到端测试(E2E) | 已覆盖(TC-APP-JRN-001) | — |
| 验收测试 | 已覆盖(TC-APP-JRN-003) | 以「导出的快照能被完整导回」为正式验收准则 |
| 手工测试 | 已覆盖(TC-APP-JRN-001) | 全部用例为手工执行（除 SET-002 的采样脚本外） |
| 自动化测试 | 未覆盖(原因：模块无自动化框架，本批次全手工；建议把 SET-002/003/005 的 shell 断言固化成脚本，先自动化「原子写 + 保护范围落盘」这两处) | — |
| 数据驱动测试 | 已覆盖(TC-APP-SET-006) | 4 组非法包名/名称用同一脚本跑 |
| 关键字驱动测试 | 不适用(无关键字表基建，本项目不投入) | — |
| 黑盒测试 | 已覆盖(TC-APP-SET-003) | 只凭 WebUI 文案与 config.json 判定，不读实现 |
| 正交/组合测试 | 已覆盖(TC-APP-SET-009) | 全局类别开关 × 单应用覆盖 = 2×2 判定表的核心行 |
| 特性开关测试 | 已覆盖(TC-APP-SET-010) | 4 个类别开关分别关掉后的行为 |
| 渗透测试 | 未覆盖(原因：只做了注入类输入校验（SET-013），未做主动渗透（提权、WebView 桥劫持、临时文件符号链接攻击）；建议 SEC 域承接) | — |
| 软件组成分析(SCA) | 不适用(无依赖清单可扫：WebUI 为单文件零依赖，模块为预编译 `.so`+内嵌 dex，无包管理器元数据) | — |

---

## 二、SET 模块专属视角（17 条，逐条申报）

| 视角 | 状态 | 备注 |
|---|---|---|
| 可访问性 | 未覆盖(原因：4 个开关是 `opacity:0` 的原生 checkbox（靠 `<label class="sw">` 包裹），TalkBack 读到的名称来自相邻文本节点的关联，未验证；建议真机开 TalkBack 走一遍「4 开关 + 应用列表勾选 + 完成」，确认可聚焦、可播报、可切换) | 高风险：复选框隐藏 + 完全无 `<label for>`，很可能只在视觉上可用 |
| 可分析性 | 已覆盖(TC-APP-JRN-001) | 判定全部依赖可分析的现场证据：`restored rounds=N ok/incomplete: op:/chan:/doze`、`#log` 的「已保存」、config.json 字段 —— 不需要猜 |
| 兼容性测试 | 未覆盖(原因：仅一加 ColorOS 16 一台机；KsuWebUI 与 Magisk App 两种宿主、HyperOS 的 MIUIOP 与 `mipush` 代理渠道都未验；建议 COMPAT 域补，参考 R-COMPAT-02) | 与基线「兼容性」同源，同一缺口 |
| 故障恢复性 | 已覆盖(TC-APP-JRN-007) | 恢复中断、坏配置（SET-007）、取消导入（SET-015）三条故障路径都有出口 |
| 国际化测试(i18n) | 未覆盖(原因：文案全部硬编码中文（`<html lang="zh-CN">`，无 strings 资源），系统语言切英文后界面仍为中文；属「已知不支持」而非缺陷，但需产品确认并在 README 写明；建议补一条「系统语言切 English 后界面文案与布局不溢出」的渲染检查) | 需产品决策，不宜直接判缺陷 |
| 接口/API 测试 | 已覆盖(TC-APP-SET-001) | `permkeeper.sh get/put` 的契约（stdin 传 JSON、stdout 回 JSON）与 config.json schema 双键校验已写入断言 |
| 输入校验 | 已覆盖(TC-APP-SET-006) | 非法包名、路径穿越形态、空串、恶意应用名四组数据；导入侧由 TC-APP-SET-013 的 `safeId` 白名单兜底 |
| 存储加密 | 不适用(配置与基线是给 `system_server` 读的明文 JSON，加密后模块无法直接解析；且设备已 root，密钥无处安放 —— 本产品不设此需求) | 若产品要求，应改为「最小化 + 权限收紧」而非加密 |
| 访问控制 | 未覆盖(原因：未断言 `/data/system/permkeeper/` 目录 700、`config.json`/`baseline.json` 600、属主 `system:system`、SELinux 上下文 `system_data_file`；建议补一条 `su 0 ls -lZ` 断言，并验证普通应用（非 root）`cat` 该目录被拒) | 与基线「保密性」同源 |
| 密钥管理 | 不适用(无密钥、无证书、无 token：WebUI 靠宿主 App 的 root 会话授权，不存在密钥生命周期) | — |
| 日志脱敏 | 未覆盖(原因：`logcat` 直接打印完整包名与通知渠道 key（形如 `ownerPkg\|uid\|channelId`，含 uid），未做任何脱敏；是否算敏感需产品确认。建议先取一条真实日志评审，再决定「保留/截断/仅 debug 构建输出」) | 渠道 key 含 uid，属可识别信息 |
| XSS（存储/反射/DOM） | 已覆盖(TC-APP-SET-006) | 存储型：`labels.json` 的应用名注入 `<img onerror>`，断言 `.label` 走 `textContent` 原样显示、`document.title` 不变、无弹窗；反射型不适用（无 URL 参数入口）；DOM 型由同一断言覆盖（渲染路径只有 `textContent`/`createElement`） |
| CSRF | 不适用(WebUI 是 `file://` 本地单页，由 KsuWebUI 以 root 加载，无 HTTP 服务、无 Cookie/会话、无跨站可触发的写操作入口；外部页面无法调用 `ksu.exec`) | 若将来加本地 HTTP 端口，此结论必须推翻重判 |
| 文件上传/路径穿越 | 已覆盖(TC-APP-SET-013) | 上传通道=「选择 JSON 文件」(`fileInput`)→`readAsText`→`JSON.parse`→`safeId` 白名单；路径穿越形态另由 TC-APP-SET-006 覆盖（`../../etc/passwd` 作包名不产生任何文件读写） |
| 敏感数据存储 | 未覆盖(原因：`baseline.json` 明文含全部权限/AppOp/渠道/uid，导出包又把**全部已安装应用**（含系统）快照写到 `/sdcard/Download/permkeeper/`，该目录对其他应用可读；建议产品决策后补「导出文件权限 600 + 是否只导受保护应用」用例) | 导出物是全机权限画像，泄露面比配置文件大 |
| 个人信息保护 | 未覆盖(原因：导出包的应用清单可反推用户装机画像，属个人信息；当前无告知、无删除/撤回机制（DSAR）。建议 README 增加数据流向说明，并补「导出后删除文件 → 模块侧无副本残留」用例) | 与上一条同批处理 |
| 权限滥用/越权 | 已覆盖(TC-APP-SET-013) | 导入路径必须无法越权执行 root 命令（`;`、`rm -rf`、`id` 三种载荷），且跳过不计失败；「谁能改配置」的越权面见上表「访问控制」 |

---

## 三、JRN 模块专属视角

**JRN 无专属视角，只需基线申报**（jrn.md：必备专属视角 0 条；本表第一部分 53 行即其全部申报）。

---

## 四、本模块规则覆盖情况

### 4.1 R-SET 组（设置与配置，9 条）

| 规则ID | 覆盖用例 | 状态 |
|---|---|---|
| R-SET-01 | TC-APP-SET-003、TC-APP-SET-004、TC-APP-SET-005、TC-APP-SET-006、TC-APP-JRN-001、TC-APP-JRN-002 | 已覆盖 |
| R-SET-02 | TC-APP-SET-008、TC-APP-JRN-006 | 已覆盖（老格式下的 opt-in 语义） |
| R-SET-03 | TC-APP-SET-009 | 已覆盖（判定表核心行） |
| R-SET-04 | TC-APP-SET-010 | 已覆盖（含「关了还报 incomplete」反向探针） |
| R-SET-05 | TC-APP-SET-011、TC-APP-JRN-005（baseline 不含被排除包） | 已覆盖 |
| R-SET-06 | TC-APP-SET-001、TC-APP-JRN-008（无桥时不得假保存） | 已覆盖 |
| R-SET-07 | TC-APP-SET-002、TC-APP-SET-006、TC-APP-SET-012 | 已覆盖 |
| R-SET-08 | TC-APP-SET-007 | 已覆盖 |
| R-SET-09 | TC-APP-SET-008、TC-APP-JRN-006 | 已覆盖 |

### 4.2 JRN 旅程触达的规则（跨 PERM / INST / COMPAT / ENV）

| 规则ID | 覆盖用例 | 状态 |
|---|---|---|
| R-PERM-01 | TC-APP-SET-003、TC-APP-JRN-002、TC-APP-JRN-005 | 已覆盖 |
| R-PERM-02 | — | 未覆盖（属 PERM 域基线采集，由 PERM 子智能体承接） |
| R-PERM-03 | — | 未覆盖（属 PERM 域：AppOp 用数字 op id，需查 `baseline.appops` 的 key 形态） |
| R-PERM-04 | — | 未覆盖（属 PERM 域：基线不得出现值为 3 的 default 项） |
| R-PERM-05 | — | 未覆盖（属 PERM 域：小米机 `10001+` 厂商 op，本机为 ColorOS 无法验） |
| R-PERM-06 | — | 未覆盖（属 PERM 域：渠道 key 形如 `ownerPkg\|uid\|channelId`，属基线采集断言） |
| R-PERM-07 | — | 未覆盖（属 PERM 域：ColorOS 代理渠道 `mundo@` 归属，本域只在 JRN-001 旁观 importance） |
| R-PERM-08 | — | 未覆盖（原因：需在模块侧统计「连续改动触发的 scan 次数」，属 ENV 域；建议补「5 秒内改 10 次配置，scan 次数 ≤ 2」） |
| R-PERM-09 | — | 未覆盖（原因：属 PERM 域；建议补「写坏 baseline.json 后出现 baseline load failed + baseline.bad」） |
| R-PERM-10 | — | 未覆盖（原因：需空闲 CPU 采样，属 ENV 域） |
| R-PERM-11 | TC-APP-JRN-001 | 已覆盖（断言 ≥1.8 秒后才出现恢复） |
| R-PERM-12 | TC-APP-JRN-001、TC-APP-JRN-007 | 已覆盖（断言 `rounds=1..3`，不无限重试） |
| R-PERM-13 | TC-APP-JRN-001 | 已覆盖（缺项必须带 `op:`/`chan:`/`doze` 前缀；逐类别深挖由 PERM 域做） |
| R-PERM-14 | TC-APP-JRN-007 | 已覆盖（中断后基线不得退化） |
| R-PERM-15 | — | 未覆盖（原因：需分身/工作资料环境，本机未建；建议 COMPAT/ENV 域补「分身清数据后日志 ignored (multi-user unsupported) 且主空间应用不变」） |
| R-PERM-16 | TC-APP-JRN-002 | 已覆盖 |
| R-PERM-17 | TC-APP-JRN-007 | 已覆盖（二次清数据结果一致） |
| R-PERM-18 | TC-APP-JRN-001 | 已覆盖（部分：断言 granted 集合与基线相等；「不得授予快照外权限」的完整矩阵由 PERM 域做） |
| R-PERM-19 | TC-APP-JRN-001 | 已覆盖（AppOp 生效值与基线一致） |
| R-PERM-20 | TC-APP-JRN-001 | 已覆盖（断言另两个应用状态不变，未被同 uid 操作波及） |
| R-PERM-21 | TC-APP-JRN-001 | 已覆盖（断言在 deviceidle 白名单内） |
| R-PERM-22 | — | 未覆盖（原因：需构造 `deviceidle` 服务不可用，属 ENV 域；建议用 `stop` 系统服务或打桩验证置位后不再重试） |
| R-PERM-23 | TC-APP-JRN-001 | 已覆盖（部分：恢复后渠道名/描述不变，只 importance 变——本域旁观断言；渠道对象语义属 PERM 域） |
| R-PERM-24 | — | 未覆盖（原因：需先删渠道再清数据才触发重建，属 PERM 域；建议补「重建后渠道名 == 基线 chanName」） |
| R-PERM-25 | — | 未覆盖（原因：属 PERM 域（2026-10-30 修复项）；建议定向复测「`dumpsys notification` 中渠道在应用 uid 下、uid 1000 下为 0」） |
| R-PERM-26 | — | 未覆盖（原因：需构造通知服务不可用，属 ENV/PERM 域） |
| R-PERM-27 | — | 未覆盖（原因：需构造缺 `chanName` 的快照，属 PERM 域；建议断言日志 `recreate channel without stored name`） |
| R-PERM-28 | — | 未覆盖（原因：代理渠道（owner 为别包）要按 owner 包名+uid 操作，属 PERM 域；TC-APP-SET-009 只旁观目标包自身渠道，不判代理归属） |
| R-INST-01 | TC-APP-JRN-003 | 已覆盖（apps 键数 == `pm list packages`） |
| R-INST-02 | TC-APP-JRN-004 | 已覆盖（中断后二次导出不得跳过采集） |
| R-INST-03 | TC-APP-JRN-004 | 已覆盖（进度采样单调不减） |
| R-INST-04 | TC-APP-JRN-004 | 已覆盖（`#pCancel` 带 `hidden`） |
| R-INST-05 | TC-APP-JRN-003 | 已覆盖（四顶层字段） |
| R-INST-06 | TC-APP-JRN-003、TC-APP-JRN-004 | 已覆盖（落 `/sdcard/Download/permkeeper/`） |
| R-INST-07 | TC-APP-JRN-004 | 已覆盖（成功/超时后无 `export.*` 残留） |
| R-INST-08 | TC-APP-JRN-003、TC-APP-JRN-004 | 已覆盖（绿/红/灰三色与文案） |
| R-INST-09 | TC-APP-JRN-003 | 已覆盖（`scopeAll` 默认 `pri`） |
| R-INST-10 | TC-APP-JRN-003 | 已覆盖（差异按真实状态算，M>0） |
| R-INST-11 | TC-APP-SET-014 | 已覆盖 |
| R-INST-12 | TC-APP-SET-013 | 已覆盖 |
| R-INST-13 | TC-APP-SET-015 | 已覆盖 |
| R-INST-14 | TC-APP-JRN-003 | 已覆盖 |
| R-INST-15 | TC-APP-JRN-003 | 已覆盖 |
| R-COMPAT-01 | TC-APP-JRN-005、TC-APP-JRN-007 | 已覆盖（重启后 `ready (after ~Nms)`） |
| R-COMPAT-02 | TC-APP-JRN-005 | 已覆盖（日志 `rom=oneplus`；小米侧未验，见基线「兼容性」） |
| R-COMPAT-03 | — | 未覆盖（属 COMPAT 域：XML 合法性自检失败须放弃写入；建议断言日志 `result would not be well-formed XML, aborting` 且文件未被改坏） |
| R-COMPAT-04 | — | 未覆盖（属 COMPAT 域：追加必须落在根元素闭合标签前；建议补「写入后 XML 解析成功且只有一个根元素」） |
| R-ENV-01 | TC-APP-JRN-001 前置「LOG 实时抓取」 | 已覆盖（作为所有时序断言的执行约束） |
| R-ENV-02 | TC-APP-JRN-001、TC-APP-JRN-002、TC-APP-SET-009、TC-APP-SET-010、TC-APP-JRN-006、TC-APP-JRN-007 的 CLEAR 步骤 | 已覆盖 |
| R-ENV-03 | — | 未覆盖（原因：同 R-PERM-10，需空闲采样，属 ENV 域） |
| R-X-01 | TC-APP-JRN-002、TC-APP-JRN-006 | 已覆盖 |
| R-X-02 | TC-APP-JRN-001 | 已覆盖（部分：断言「不多授予」，矩阵化由 PERM 域做） |
| R-X-03 | — | 未覆盖（属 PERM 域，断言需 `dumpsys notification` 下 uid 归属，建议并入 R-PERM-25 复测） |
| R-X-04 | TC-APP-JRN-004 | 已覆盖 |
| R-X-05 | TC-APP-JRN-004 | 已覆盖 |
| R-X-06 | — | 未覆盖（同 R-PERM-15，缺分身环境） |
| R-X-07 | — | 未覆盖（属 COMPAT 域） |
| R-X-08 | TC-APP-SET-007 | 已覆盖 |
| R-X-09 | TC-APP-SET-014 | 已覆盖 |
| R-X-10 | TC-APP-SET-013 | 已覆盖 |

### 4.3 未覆盖规则汇总（本域结论）

- **本域应覆盖且已全部覆盖**：R-SET-01 ~ R-SET-09（9/9）。
- **不属本域、留给对应域**：R-PERM-02/03/04/05/06/07/09/13(深)/18(矩阵)/23~28、R-COMPAT-03/04、R-X-03/07。
- **本域该补但本批次没写**（3 条，均已在上表给出建议）：R-PERM-08（防抖重扫次数）、R-PERM-10 / R-ENV-03（空闲资源）、R-PERM-15 / R-X-06（多用户跳过，缺环境）；另有 R-PERM-22（`deviceidle` 不可用降级，需构造故障）。
