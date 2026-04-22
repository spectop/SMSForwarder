# SMSForwarder

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-33+-3DDC84.svg)](https://www.android.com/)

一个功能强大且灵活的 Android 短信转发应用。支持规则匹配、内容提取、多种推送方式，完全由 YAML 配置驱动。

## 功能特性

✨ **核心功能**
- 🔔 后台监听短信（Foreground Service）
- 🎯 支持正则表达式和电话号码的灵活匹配规则
- 📤 多种推送方式：[SMS Agent](#sms-agent-推送) 和 WebHook
- 🔄 自动变量提取和替换
- ⚙️ 完全的 YAML 配置驱动
- 🛡️ 系统级广播过滤绕过（数据库轮询机制）
- 📊 实时日志监控和配置热重载

## 系统要求

| 项目 | 要求 |
|------|------|
| Android 版本 | 13+ (API 33+) |
| Kotlin | 2.0.21 |
| Gradle | 8.13.1+ |
| Java/JDK | 11+ |

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/spectop/SMSForwarder.git
cd SMSForwarder
```

### 2. 编译构建

```bash
# Windows
./gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

### 3. 安装应用

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. 配置规则

在应用的"配置"选项卡中编辑 YAML 配置文件，或直接编辑 `config.yaml` 文件。

## 使用说明

### 应用界面

应用包含三个主要标签页：

1. **状态** - 服务运行状态和实时日志
2. **配置** - YAML 配置编辑和验证
3. **短信测试** - 直接读取本地短信库进行测试

### 基本配置示例

```yaml
matching_rules:
  - id: "login_code"
    name: "登录验证码"
    type: "content-regex"
    content: '(?:验证码|校验码)[^\d]{0,5}(\d{4,8})'
    variables:
      code: "$1"

push_rules:
  - id: "my_webhook"
    name: "WebHook 推送"
    enabled: true
    type: "CURL"
    config:
      url: "https://your-server.com/webhook"
      method: "POST"
      body_template: '{"code":"${code}"}'
      headers: "Content-Type: application/json"

workflows:
  - id: "workflow_1"
    name: "验证码转发"
    enabled: true
    matching: "login_code"
    pushers:
      - id: "my_webhook"
        varMap:
          mappings: []
```

## 配置说明

### 匹配规则 (matching_rules)

支持两种匹配类型：

| 类型 | 说明 | 示例 |
|------|------|------|
| `content-regex` | 按短信内容正则匹配 | `验证码.*(\d{4,8})` |
| `phone-regex` | 按发件人号码正则匹配 | `^1[0-9]{10}$` |
| `phone-prefix` | 按发件人号码前缀匹配 | `+86` |

### 推送规则 (push_rules)

#### WebHook (CURL) 推送

```yaml
push_rules:
  - id: "webhook_example"
    type: "CURL"
    config:
      url: "https://api.example.com/sms"
      method: "POST"
      body_template: '{"from":"${_phone}","content":"${_content}","code":"${code}"}'
      headers: "Content-Type: application/json\nAuthorization: Bearer TOKEN"
```

**内置变量**：
- `${_phone}` - 发件人号码
- `${_content}` - 短信完整内容
- `${_timestamp}` - 时间戳（毫秒）
- `${code}` 等规则提取的自定义变量

#### SMS Agent 推送

详见 [SMS Agent 推送](#sms-agent-推送)

### 工作流 (workflows)

将匹配规则和推送规则关联：

```yaml
workflows:
  - id: "workflow_id"
    name: "工作流名称"
    enabled: true              # 启用/禁用
    matching: "rule_id"        # 关联的匹配规则ID
    pushers:                   # 可配置多个推送器
      - id: "pusher_id"
        varMap:
          mappings: []         # 高级：变量映射
```

## SMS Agent 推送

SMSForwarder 集成了 [SMS Agent](https://github.com/spectop/sms_agent) 用于安全可靠的短信验证码转发。

### 配置示例

```yaml
push_rules:
  - id: "sms_agent"
    name: "SMS Agent"
    enabled: true
    type: "SMS_AGENT"
    config:
      endpoint: "http://your-sms-agent-server"
      tag: "your_tag"
      token: "your_auth_token"
```

### 请求格式

自动发送 `POST /codes` 请求：

```json
{
  "tag": "your_service",
  "code": "123456",
  "ttl": 300,
  "metadata": {
    "phone": "+8613800000000",
    "content": "验证码：123456"
  }
}
```

更多详情请参考 [SMS Agent 项目](https://github.com/spectop/sms_agent)。

## 架构设计

### 核心流程

```
SMS 接收 → 规则匹配 → 变量提取 → 推送适配器 → 外部服务
```

### 关键组件

- **SmsMonitorService** - 后台 Foreground Service，监听短信并启动轮询机制
- **SmsProcessingEngine** - 规则匹配和工作流执行引擎
- **PusherFactory** - 推送适配器工厂和注册表
- **ConfigManager** - 配置加载、缓存和验证
- **EventLog** - 实时日志 StateFlow

### 后台轮询机制

由于部分 Android 系统（如 MIUI）对第三方应用的 SMS 广播进行了限制，应用实现了数据库轮询机制作为补充：

- 每 3 秒检查一次 `content://sms` 数据库
- 只处理新的短信（基于时间戳过滤）
- 自动与广播接收器配合工作

## 权限说明

应用需要以下权限：

- `READ_SMS` - 读取短信库（轮询机制）
- `RECEIVE_SMS` - 接收短信广播
- `POST_NOTIFICATIONS` - 显示前台服务通知
- `INTERNET` - HTTP 推送请求
- `RECEIVE_BOOT_COMPLETED` - 设备启动时自启服务

## 开发指南

### 项目结构

```
app/src/main/java/com/example/smsforwarder/
├── core/              # 核心模块
│   ├── config/        # 配置加载和管理
│   ├── engine/        # 规则匹配和执行引擎
│   └── sms/           # SMS 处理
├── pusher/            # 推送适配器
│   ├── base/          # 基类
│   ├── curl/          # WebHook 推送
│   └── sms_agent/     # SMS Agent 推送
├── storage/           # 数据存储
└── MainActivity.kt    # UI 界面
```

### 添加新的推送适配器

1. 创建继承自 `BasePusher` 的类
2. 实现 `suspend fun push()` 方法
3. 在 `PusherFactory.create()` 中注册

```kotlin
class MyCustomPusher(ruleId: String, config: Map<String, String>) : BasePusher() {
    override suspend fun push(variables: Map<String, String>): PusherResult {
        // 实现推送逻辑
        return PusherResult.Success("...")
    }
}
```

## 故障排查

### 短信未被接收

1. **检查权限** - 确保应用已获得 READ_SMS 和 RECEIVE_SMS 权限
2. **检查通知** - 确保 Foreground Service 通知可见
3. **检查规则** - 在"短信测试"页面验证规则是否匹配
4. **检查轮询** - 检查应用日志中是否有"轮询发现新短信"的日志

### 推送失败

1. **检查网络** - 确保设备可以访问推送服务器
2. **检查配置** - 验证 endpoint、token 等配置是否正确
3. **检查日志** - 在"状态"页面的日志中查看详细错误信息
4. **重新加载配置** - 修改配置后需要重新加载或重启应用

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化 UI 框架
- [kaml](https://github.com/charleskorn/kaml) - YAML 解析库
- [OkHttp](https://github.com/square/okhttp) - HTTP 客户端
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) - 异步编程库

## 相关项目

- [SMS Agent](https://github.com/spectop/sms_agent) - 安全的短信验证码转发服务

## 支持

如有问题或建议，欢迎提交 Issue 或 Pull Request。
