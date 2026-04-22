# SMSForwarder 开发协作文档

本目录用于存放与开发流程相关的协作资料，供人类开发者与 AI agent 共用。

## 文档索引

- `ARCHITECTURE.md`：系统架构与模块边界
- `GOALS.md`：项目目标、阶段里程碑、验收标准
- `PROGRESS.md`：当前进度、已完成事项、待办事项
- `CONSTRAINTS.md`：技术约束、安全约束、运行约束
- `WORKFLOW.md`：推荐开发流程与任务执行规范

## 使用建议

1. 需求变化时，先更新 `GOALS.md` 与 `CONSTRAINTS.md`。
2. 每次完成开发任务后更新 `PROGRESS.md`。
3. 涉及核心行为变更时，优先更新 `ARCHITECTURE.md`。
4. 开发与评审流程统一参考 `WORKFLOW.md`。

## 当前项目定位

- 平台：Android（Kotlin）
- 核心能力：监听短信 -> 规则匹配 -> 内容提取 -> 网络推送
- 配置策略：以 YAML 文件为主，便于审查、备份、迁移
- 推送扩展策略：适配器在代码中静态注册，不向最终用户暴露插件系统
