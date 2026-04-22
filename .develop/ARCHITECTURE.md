# 架构说明

## 1. 系统目标

SMSForwarder 用于在 Android 设备上稳定监听短信，按用户定义的规则筛选内容，并将结果推送到网络端点。

## 2. 总体流程

1. 短信到达：系统广播触发接收组件。
2. 事件标准化：将原始短信转换为统一事件模型。
3. 工作流调度：遍历已启用工作流并执行。
4. 匹配阶段：按号码、关键词、正则等规则进行命中判断。
5. 提取阶段：对命中短信执行提取规则（如验证码）。
6. 推送阶段：调用已配置推送适配器发送结果。
7. 记录阶段：写入日志与执行结果，便于回溯。

## 3. 分层设计

### 3.1 接入层

- `SmsReceiver`：接收短信广播。
- `BootReceiver`：开机后触发服务恢复。
- `MonitorService`：前台服务，保证长期运行能力。

### 3.2 领域层

- `WorkflowEngine`：工作流执行器。
- `RuleMatcher`：匹配规则执行器。
- `Extractor`：内容提取器。
- `PushDispatcher`：推送调度器。

### 3.3 适配层

- `SmsAgentAdapter`：对接自定义 sms_agent 接口。
- `CurlAdapter`：按配置构造 HTTP 请求进行推送。
- `PushAdapterRegistry`：代码内静态注册适配器。

### 3.4 配置与存储层

- `YamlConfigRepository`：读取与解析 YAML 配置。
- `ConfigValidator`：配置校验（字段、正则、URL、必填项）。
- `ExecutionLogStore`：轻量日志存储（可使用本地文件或 Room）。

## 4. 核心数据模型

- `SmsEvent`
  - `sender`
  - `body`
  - `receivedAt`
  - `simSlot`（可选）
- `Workflow`
  - `id`
  - `name`
  - `enabled`
  - `matchRules`
  - `extractRules`
  - `pushTargets`
- `PushTarget`
  - `type`（sms_agent/curl）
  - `enabled`
  - `config`（平铺 KV）

## 5. YAML 配置结构建议

- `config/app.yaml`
  - 全局开关、日志级别、重试策略
- `config/workflows/*.yaml`
  - 每个工作流独立文件，便于拆分管理

示意：

```yaml
workflow:
  id: login_code
  name: 登录验证码
  enabled: true

matching:
  mode: all
  rules:
    - type: sender_regex
      pattern: "^1069"
      enabled: true
    - type: body_regex
      pattern: "(验证码|校验码)"
      enabled: true

extraction:
  rules:
    - key: code
      type: regex
      pattern: "\\b\\d{4,8}\\b"

push:
  targets:
    - type: sms_agent
      enabled: true
      config:
        endpoint: "https://example/api/push"
        tag: "otp"
    - type: curl
      enabled: false
      config:
        url: "https://example/webhook"
        method: "POST"
        headers_json: "{\"Authorization\":\"Bearer xxx\"}"
```

## 6. 架构约束

1. 推送适配器仅允许代码内注册，不支持运行时动态加载。
2. 业务规则以 YAML 配置驱动，避免把规则写死在代码中。
3. 单条短信的工作流执行必须具备超时控制，防止阻塞后续处理。
4. 推送请求必须支持重试和退避策略。
5. 日志不得明文长期保存完整敏感短信内容。

## 7. 后续演进建议

1. 增加配置热重载与版本号校验。
2. 增加工作流模拟调试模式（输入样例短信查看命中与提取结果）。
3. 增加失败告警通道（例如本地通知或统一错误上报）。
