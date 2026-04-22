# 开发进度

## 当前状态（2026-04-22）

**P0 阶段已完成，APK 可正常编译。**

## 已完成

### 基础架构
1. 项目目标与能力边界已形成初版。
2. 工作流驱动模型（匹配 -> 提取 -> 推送）已确定。
3. 协作开发文档框架已建立。
4. YAML 配置模型数据类（AppConfig / Workflow / MatchingRule / PushRule / VariableMapping）。

### P0 核心功能（本次完成）
5. YAML 配置加载：`YamlConfigLoader`（基于 kaml）、`AssetConfigLoader`（filesDir 优先，assets 回退）。
6. 配置管理：`ConfigManager`（带缓存）、`ConfigValidator`（正则/引用交叉校验）。
7. 规则引擎：`RuleMatcher`（PHONE_PREFIX / PHONE_REGEX / CONTENT_REGEX）、`VariableResolver`（`${var}` 模板替换）。
8. 推送适配器：`SmsAgentPusher`（HTTP Bearer JSON）、`CurlPusher`（可配置 HTTP 请求）。
9. 处理流水线：`SmsProcessingEngine`（逐工作流执行，30s 超时隔离）。
10. Android 组件：`SmsReceiver`（解析短信）、`SmsMonitorService`（前台服务）、`BootReceiver`（开机自启）。
11. 存储：`AppPreferences`（服务开关）、`EventLog`（StateFlow 日志，最多 100 条）。
12. UI：`MainActivity`（Compose，Status + Config 双 Tab；状态指示、日志列表、YAML 编辑器、校验与保存）。
13. 编译通过：解决 JDK 25 兼容问题（设置 `org.gradle.java.home` 为 AS JDK 21）；使用阿里云 Maven 镜像解决国内 TLS 下载失败；修复 `@OptIn(ExperimentalMaterial3Api::class)` 注解缺失。

## 进行中

无。

## 待办（按优先级）

1. 设备真机验证：安装 APK 并完成端到端短信转发测试。
2. 增加失败重试与退避逻辑。
3. 建立单元测试与样例回放测试。
4. P1 功能规划（参考 GOALS.md）。

## 风险与应对

1. Android 后台限制导致监听中断：使用前台服务 + 开机恢复 + 状态自检。
2. YAML 配置错误导致流程失效：加载前强校验并提供降级默认值。
3. 网络波动导致推送失败：重试、超时、退避和错误分类记录。
