@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.smsforwarder.core.config.ConfigManager
import com.example.smsforwarder.core.config.loader.AssetConfigLoader
import com.example.smsforwarder.core.config.loader.YamlConfigLoader
import com.example.smsforwarder.core.config.model.AppConfig
import com.example.smsforwarder.core.config.model.MatchingRule
import com.example.smsforwarder.core.config.model.MatchingRuleType
import com.example.smsforwarder.core.config.model.PusherType
import com.example.smsforwarder.core.config.model.PushRule
import com.example.smsforwarder.core.config.model.VariableMapping
import com.example.smsforwarder.core.config.model.VariableMappingItem
import com.example.smsforwarder.core.config.model.Workflow
import com.example.smsforwarder.core.config.model.WorkflowPusher
import com.example.smsforwarder.core.config.validator.ConfigValidator
import com.example.smsforwarder.pusher.base.PusherConfigUiRegistry
import com.example.smsforwarder.storage.EventLog
import com.example.smsforwarder.ui.theme.SMSForwarderTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TEST_TAG = "SmsInspector"

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startMonitorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (allPermissionsGranted()) {
            startMonitorService()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            SMSForwarderTheme {
                MainScreen()
            }
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startMonitorService() {
        val intent = Intent(this, SmsMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

// ─────────────────────────────────────────────────────────────
// 主界面（Tab 导航）
// ─────────────────────────────────────────────────────────────

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("状态", "配置", "短信测试")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Forwarder") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab])
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> StatusScreen()
                1 -> ConfigScreen()
                2 -> SmsTestScreen()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 状态页
// ─────────────────────────────────────────────────────────────

@Composable
fun StatusScreen() {
    val context = LocalContext.current
    val logs by EventLog.entries.collectAsState()
    val listState = rememberLazyListState()

    // 有新日志时自动滚到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // 服务状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val running = SmsMonitorService.isRunning
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (running) Color(0xFF4CAF50) else Color(0xFFF44336),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (SmsMonitorService.isRunning) "监控服务运行中" else "监控服务已停止",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, SmsMonitorService::class.java)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !SmsMonitorService.isRunning
                    ) { Text("启动服务") }

                    OutlinedButton(
                        onClick = {
                            context.stopService(Intent(context, SmsMonitorService::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = SmsMonitorService.isRunning
                    ) { Text("停止服务") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 日志区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("事件日志", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            TextButton(onClick = { EventLog.clear() }) { Text("清空", fontSize = 12.sp) }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无事件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { entry ->
                        Text(
                            text = entry,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 配置页
// ─────────────────────────────────────────────────────────────

private enum class ConfigEditMode { UI, YAML }

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ConfigEditMode.UI) }
    var configText by remember { mutableStateOf(AssetConfigLoader.readConfigText(context)) }
    val initialParsed = remember(configText) { YamlConfigLoader.load(configText) }
    var uiConfig by remember { mutableStateOf(initialParsed ?: AppConfig()) }
    var uiLoadError by remember { mutableStateOf<String?>(if (initialParsed == null) "当前 YAML 无法解析，已使用空配置草稿" else null) }
    var saveResult by remember { mutableStateOf<String?>(null) }
    var validationMsg by remember { mutableStateOf<String?>(null) }

    fun validateAppConfig(config: AppConfig) {
        val result = ConfigValidator.validate(config)
        validationMsg = if (result.isValid) {
            "✓ 配置有效" + (if (result.warnings.isNotEmpty()) "\n警告: ${result.warnings.joinToString()}" else "")
        } else {
            "✗ 错误:\n${result.errors.joinToString("\n")}" 
        }
        saveResult = null
    }

    fun saveUiConfig() {
        val yaml = YamlConfigLoader.dump(uiConfig)
        if (yaml == null) {
            saveResult = "✗ 保存失败：UI 配置无法序列化为 YAML"
            validationMsg = null
            EventLog.add("配置保存失败：UI 序列化失败")
            return
        }
        val cm = ConfigManager(context)
        val ok = cm.saveAndReload(yaml)
        if (ok) {
            configText = yaml
        }
        saveResult = if (ok) "✓ 保存成功，配置已重新加载" else "✗ 保存失败"
        validationMsg = null
        EventLog.add(if (ok) "配置已保存并重新加载（UI模式）" else "配置保存失败（UI模式）")
    }

    fun saveYamlConfig() {
        val parsed = YamlConfigLoader.load(configText)
        if (parsed == null) {
            saveResult = "✗ 保存失败：YAML 语法错误"
            validationMsg = null
            EventLog.add("配置保存失败：YAML 语法错误")
            return
        }
        val cm = ConfigManager(context)
        val ok = cm.saveAndReload(configText)
        if (ok) {
            uiConfig = parsed
            uiLoadError = null
        }
        saveResult = if (ok) "✓ 保存成功，配置已重新加载" else "✗ 保存失败"
        validationMsg = null
        EventLog.add(if (ok) "配置已保存并重新加载（YAML模式）" else "配置保存失败（YAML模式）")
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(text = "配置", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text(
            text = "配置文件路径：${AssetConfigLoader.getUserConfigFile(context).absolutePath}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            FilterChip(
                selected = mode == ConfigEditMode.UI,
                onClick = { mode = ConfigEditMode.UI },
                label = { Text("可视化配置（默认）") }
            )
            FilterChip(
                selected = mode == ConfigEditMode.YAML,
                onClick = { mode = ConfigEditMode.YAML },
                label = { Text("YAML 编辑") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    if (mode == ConfigEditMode.UI) {
                        validateAppConfig(uiConfig)
                    } else {
                        val loaded = YamlConfigLoader.load(configText)
                        if (loaded == null) {
                            validationMsg = "YAML 语法错误，请检查格式"
                            saveResult = null
                        } else {
                            uiConfig = loaded
                            uiLoadError = null
                            validateAppConfig(loaded)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("校验") }

            Button(
                onClick = {
                    if (mode == ConfigEditMode.UI) saveUiConfig() else saveYamlConfig()
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
        }

        (validationMsg ?: saveResult)?.let { msg ->
            Text(
                text = msg,
                fontSize = 12.sp,
                color = if (msg.startsWith("✓")) Color(0xFF388E3C) else Color(0xFFD32F2F),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        uiLoadError?.let { msg ->
            Text(
                text = msg,
                fontSize = 12.sp,
                color = Color(0xFFD32F2F),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        if (mode == ConfigEditMode.UI) {
            VisualConfigEditor(
                config = uiConfig,
                onConfigChange = {
                    uiConfig = it
                    validationMsg = null
                    saveResult = null
                }
            )
        } else {
            OutlinedTextField(
                value = configText,
                onValueChange = { configText = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                label = { Text("config.yaml") }
            )
        }
    }
}

@Composable
private fun VisualConfigEditor(config: AppConfig, onConfigChange: (AppConfig) -> Unit) {
    var selectedPage by remember { mutableIntStateOf(0) }
    var showPusherTypeDialog by remember { mutableStateOf(false) }
    var detailEditor by remember { mutableStateOf<ConfigDetailEditor?>(null) }
    val pages = listOf("Workflow", "匹配规则", "Pusher")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedPage) {
            pages.forEachIndexed { index, title ->
                Tab(
                    selected = selectedPage == index,
                    onClick = {
                        // 详情页中切换 Tab 时，先回到列表页再切换，避免“Tab 已切换但内容仍停留在详情页”的错觉。
                        detailEditor = null
                        selectedPage = index
                    },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (detailEditor == null) {
            when (selectedPage) {
                0 -> WorkflowListPage(
                    config = config,
                    onAdd = { detailEditor = ConfigDetailEditor.WorkflowEditor(null) },
                    onEdit = { detailEditor = ConfigDetailEditor.WorkflowEditor(it) }
                )
                1 -> MatchingRulesListPage(
                    config = config,
                    onAdd = { detailEditor = ConfigDetailEditor.MatchingEditor(null) },
                    onEdit = { detailEditor = ConfigDetailEditor.MatchingEditor(it) }
                )
                2 -> PushRulesListPage(
                    config = config,
                    onAddPusher = { showPusherTypeDialog = true },
                    onEdit = { detailEditor = ConfigDetailEditor.PushEditor(it) }
                )
            }
        } else {
            when (val editor = detailEditor) {
                is ConfigDetailEditor.MatchingEditor -> {
                    val idx = editor.index
                    val rule = if (idx != null) config.matching_rules.getOrNull(idx) else null
                    MatchingRuleDetailPage(
                        rule = rule,
                        onBack = { detailEditor = null },
                        onSave = { updated ->
                            val list = if (idx == null) {
                                config.matching_rules + updated
                            } else {
                                config.matching_rules.updated(idx, updated)
                            }
                            onConfigChange(config.copy(matching_rules = list))
                            detailEditor = null
                        },
                        onDelete = {
                            if (idx != null) {
                                onConfigChange(config.copy(matching_rules = config.matching_rules.filterIndexed { i, _ -> i != idx }))
                            }
                            detailEditor = null
                        }
                    )
                }
                is ConfigDetailEditor.PushEditor -> {
                    val idx = editor.index
                    val rule = config.push_rules.getOrNull(idx)
                    if (rule != null) {
                        PushRuleDetailPage(
                            rule = rule,
                            onBack = { detailEditor = null },
                            onSave = { updated ->
                                onConfigChange(config.copy(push_rules = config.push_rules.updated(idx, updated)))
                                detailEditor = null
                            },
                            onDelete = {
                                onConfigChange(config.copy(push_rules = config.push_rules.filterIndexed { i, _ -> i != idx }))
                                detailEditor = null
                            }
                        )
                    }
                }
                is ConfigDetailEditor.WorkflowEditor -> {
                    val idx = editor.index
                    val wf = if (idx != null) config.workflows.getOrNull(idx) else null
                    WorkflowDetailPage(
                        workflow = wf,
                        matchingRules = config.matching_rules,
                        pushRules = config.push_rules,
                        onBack = { detailEditor = null },
                        onSave = { updated ->
                            val list = if (idx == null) {
                                config.workflows + updated
                            } else {
                                config.workflows.updated(idx, updated)
                            }
                            onConfigChange(config.copy(workflows = list))
                            detailEditor = null
                        },
                        onDelete = {
                            if (idx != null) {
                                onConfigChange(config.copy(workflows = config.workflows.filterIndexed { i, _ -> i != idx }))
                            }
                            detailEditor = null
                        }
                    )
                }
                null -> Unit
            }
        }
    }

    if (showPusherTypeDialog) {
        AlertDialog(
            onDismissRequest = { showPusherTypeDialog = false },
            title = { Text("选择 Pusher 类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PusherConfigUiRegistry.allProviders().forEach { provider ->
                        Card(onClick = {
                            val newRule = PushRule(
                                id = "push_${config.push_rules.size + 1}",
                                name = "新推送规则",
                                enabled = true,
                                type = provider.type,
                                config = provider.defaultConfig()
                            )
                            onConfigChange(config.copy(push_rules = config.push_rules + newRule))
                            detailEditor = ConfigDetailEditor.PushEditor(config.push_rules.size)
                            showPusherTypeDialog = false
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(provider.icon, fontSize = 20.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        provider.description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPusherTypeDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MatchingRulesListPage(
    config: AppConfig,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AddHeader("匹配规则") { onAdd() }

        config.matching_rules.forEachIndexed { index, rule ->
            Card(onClick = { onEdit(index) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name.ifBlank { "未命名匹配规则" }, fontWeight = FontWeight.SemiBold)
                    Text("ID: ${rule.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("类型: ${rule.type.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PushRulesListPage(
    config: AppConfig,
    onAddPusher: () -> Unit,
    onEdit: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AddHeader("Pusher") { onAddPusher() }

        config.push_rules.forEachIndexed { index, rule ->
            val provider = PusherConfigUiRegistry.get(rule.type)
            Card(onClick = { onEdit(index) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name.ifBlank { "未命名推送规则" }, fontWeight = FontWeight.SemiBold)
                    Text("ID: ${rule.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "类型: ${provider?.displayName ?: rule.type.name} ${if (rule.enabled) "(启用)" else "(禁用)"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowListPage(
    config: AppConfig,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AddHeader("Workflow") { onAdd() }

        config.workflows.forEachIndexed { index, wf ->
            Card(onClick = { onEdit(index) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(wf.name.ifBlank { "未命名工作流" }, fontWeight = FontWeight.SemiBold)
                    Text("ID: ${wf.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "匹配数: ${wf.matchingIds().size}  推送器数: ${wf.pushers.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchingRuleDetailPage(
    rule: MatchingRule?,
    onBack: () -> Unit,
    onSave: (MatchingRule) -> Unit,
    onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    var id by remember(rule) { mutableStateOf(rule?.id ?: "matching_new") }
    var name by remember(rule) { mutableStateOf(rule?.name ?: "新匹配规则") }
    var type by remember(rule) { mutableStateOf(rule?.type ?: MatchingRuleType.CONTENT_REGEX) }
    var phone by remember(rule) { mutableStateOf(rule?.phone ?: "") }
    var content by remember(rule) { mutableStateOf(rule?.content ?: "") }
    var variablesText by remember(rule) { mutableStateOf(mapToEditorText(rule?.variables ?: mapOf("code" to "$1"))) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailHeader(title = "匹配规则详情", onBack = onBack, onDelete = if (rule != null) onDelete else null)
        OutlinedTextField(value = id, onValueChange = { id = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ID") }, singleLine = true)
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
        RuleTypePicker(selected = type, onSelect = { type = it })
        if (type == MatchingRuleType.PHONE_PREFIX || type == MatchingRuleType.PHONE_REGEX) {
            OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("号码规则") }, singleLine = true)
        }
        if (type == MatchingRuleType.CONTENT_REGEX) {
            OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("内容正则") })
        }
        OutlinedTextField(
            value = variablesText,
            onValueChange = { variablesText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("变量(每行 key=value)") }
        )
        Button(onClick = {
            onSave(
                MatchingRule(
                    id = id,
                    name = name,
                    type = type,
                    phone = phone,
                    content = content,
                    variables = parseMapEditorText(variablesText)
                )
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }
}

@Composable
private fun PushRuleDetailPage(
    rule: PushRule,
    onBack: () -> Unit,
    onSave: (PushRule) -> Unit,
    onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    var id by remember(rule) { mutableStateOf(rule.id) }
    var name by remember(rule) { mutableStateOf(rule.name) }
    var enabled by remember(rule) { mutableStateOf(rule.enabled) }
    var type by remember(rule) { mutableStateOf(rule.type) }
    var configMap by remember(rule) { mutableStateOf(rule.config) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailHeader(title = "Pusher 详情", onBack = onBack, onDelete = onDelete)
        OutlinedTextField(value = id, onValueChange = { id = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ID") }, singleLine = true)
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = { enabled = it })
            Text("启用")
        }
        PusherTypePicker(selected = type, onSelect = {
            type = it
            val provider = PusherConfigUiRegistry.get(it)
            configMap = provider?.defaultConfig() ?: emptyMap()
        })

        val provider = PusherConfigUiRegistry.get(type)
        if (provider != null) {
            provider.RenderConfigEditor(config = configMap, onConfigChange = { configMap = it })
        } else {
            OutlinedTextField(
                value = mapToEditorText(configMap),
                onValueChange = { configMap = parseMapEditorText(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("配置(每行 key=value)") }
            )
        }

        Button(onClick = {
            onSave(
                PushRule(
                    id = id,
                    name = name,
                    enabled = enabled,
                    type = type,
                    config = configMap
                )
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }
}

@Composable
private fun WorkflowDetailPage(
    workflow: Workflow?,
    matchingRules: List<MatchingRule>,
    pushRules: List<PushRule>,
    onBack: () -> Unit,
    onSave: (Workflow) -> Unit,
    onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    var id by remember(workflow) { mutableStateOf(workflow?.id ?: "workflow_new") }
    var name by remember(workflow) { mutableStateOf(workflow?.name ?: "新工作流") }
    var enabled by remember(workflow) { mutableStateOf(workflow?.enabled ?: true) }
    var showMatchingPicker by remember { mutableStateOf(false) }
    var showPusherPicker by remember { mutableStateOf(false) }
    var selectedMatchingIds by remember(workflow) {
        mutableStateOf(workflow?.matchingIds()?.toSet() ?: emptySet())
    }
    var selectedPushIds by remember(workflow) {
        mutableStateOf(workflow?.pushers?.map { it.id }?.toSet() ?: emptySet())
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailHeader(title = "Workflow 详情", onBack = onBack, onDelete = if (workflow != null) onDelete else null)
        OutlinedTextField(value = id, onValueChange = { id = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ID") }, singleLine = true)
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = { enabled = it })
            Text("启用")
        }

        Text("匹配规则", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { showMatchingPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("选择匹配规则")
        }
        if (matchingRules.isEmpty()) {
            Text("暂无可选匹配规则，请先在匹配规则页创建", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                selectedMatchingIds.forEach { idItem ->
                    val rule = matchingRules.find { it.id == idItem }
                    AssistChip(
                        onClick = { selectedMatchingIds = selectedMatchingIds - idItem },
                        label = { Text(rule?.name ?: idItem) },
                        trailingIcon = { Text("×") }
                    )
                }
            }
        }

        Text("关联 Pusher", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { showPusherPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("选择 Pusher")
        }
        if (pushRules.isEmpty()) {
            Text("暂无可选 Pusher，请先在 Pusher 页创建", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                selectedPushIds.forEach { idItem ->
                    val pushRule = pushRules.find { it.id == idItem }
                    AssistChip(
                        onClick = { selectedPushIds = selectedPushIds - idItem },
                        label = { Text(pushRule?.name ?: idItem) },
                        trailingIcon = { Text("×") }
                    )
                }
            }
        }

        Button(onClick = {
            onSave(
                Workflow(
                    id = id,
                    name = name,
                    enabled = enabled,
                    matching = "",
                    matchings = selectedMatchingIds.toList(),
                    pushers = selectedPushIds.map { WorkflowPusher(id = it) }
                )
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }

    if (showMatchingPicker) {
        AlertDialog(
            onDismissRequest = { showMatchingPicker = false },
            title = { Text("选择匹配规则（可多选）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    matchingRules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedMatchingIds.contains(rule.id),
                                onCheckedChange = { checked ->
                                    selectedMatchingIds = if (checked) selectedMatchingIds + rule.id else selectedMatchingIds - rule.id
                                }
                            )
                            Text("${rule.name} (${rule.id})")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMatchingPicker = false }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { showMatchingPicker = false }) { Text("取消") } }
        )
    }

    if (showPusherPicker) {
        AlertDialog(
            onDismissRequest = { showPusherPicker = false },
            title = { Text("选择 Pusher（可多选）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pushRules.forEach { push ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedPushIds.contains(push.id),
                                onCheckedChange = { checked ->
                                    selectedPushIds = if (checked) selectedPushIds + push.id else selectedPushIds - push.id
                                }
                            )
                            Text("${push.name} (${push.id})")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPusherPicker = false }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { showPusherPicker = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("删除") }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun AddHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        OutlinedButton(onClick = onAdd) { Text("+ 添加") }
    }
}

private sealed class ConfigDetailEditor {
    data class MatchingEditor(val index: Int?) : ConfigDetailEditor()
    data class PushEditor(val index: Int) : ConfigDetailEditor()
    data class WorkflowEditor(val index: Int?) : ConfigDetailEditor()
}

@Composable
private fun RuleHeader(title: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onRemove) { Text("删除") }
    }
}

@Composable
private fun RuleTypePicker(selected: MatchingRuleType, onSelect: (MatchingRuleType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("规则类型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MatchingRuleType.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    label = { Text(type.name) }
                )
            }
        }
    }
}

@Composable
private fun PusherTypePicker(selected: PusherType, onSelect: (PusherType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("推送类型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PusherType.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    label = { Text(type.name) }
                )
            }
        }
    }
}

private fun mapToEditorText(map: Map<String, String>): String {
    if (map.isEmpty()) return ""
    return map.entries.joinToString("\n") { (k, v) -> "$k=$v" }
}

private fun parseMapEditorText(text: String): Map<String, String> {
    if (text.isBlank()) return emptyMap()
    return text.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.contains("=")) return@mapNotNull null
        val idx = trimmed.indexOf('=')
        val key = trimmed.substring(0, idx).trim()
        val value = trimmed.substring(idx + 1).trim()
        if (key.isEmpty()) null else key to value
    }.toMap()
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> {
    return mapIndexed { i, old -> if (i == index) value else old }
}

data class SmsSnapshot(
    val address: String,
    val body: String,
    val date: Long,
    val type: Int
)

private fun queryInboxSms(context: android.content.Context, limit: Int = 300): List<SmsSnapshot> {
    val resolver = context.contentResolver
    val uri = Uri.parse("content://sms")
    val projection = arrayOf(
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE
    )

    val list = mutableListOf<SmsSnapshot>()
    val cursor = resolver.query(uri, projection, null, null, "date DESC")
    cursor?.use {
        val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
        val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
        val idxDate = it.getColumnIndex(Telephony.Sms.DATE)
        val idxType = it.getColumnIndex(Telephony.Sms.TYPE)

        while (it.moveToNext() && list.size < limit) {
            val body = if (idxBody >= 0) (it.getString(idxBody) ?: "") else ""
            if (body.isBlank()) continue
            list.add(
                SmsSnapshot(
                    address = if (idxAddress >= 0) (it.getString(idxAddress) ?: "") else "",
                    body = body,
                    date = if (idxDate >= 0) it.getLong(idxDate) else 0L,
                    type = if (idxType >= 0) it.getInt(idxType) else 0
                )
            )
        }
    }
    return list
}

private fun isOtpLike(body: String): Boolean {
    val keywordRegex = Regex("验证码|校验码|动态码|动态口令|verification code|otp", RegexOption.IGNORE_CASE)
    val digitRegex = Regex("\\b\\d{4,8}\\b")
    return keywordRegex.containsMatchIn(body) && digitRegex.containsMatchIn(body)
}

@Composable
fun SmsTestScreen() {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var onlyOtp by remember { mutableStateOf(true) }
    var allMessages by remember { mutableStateOf<List<SmsSnapshot>>(emptyList()) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val displayMessages = remember(allMessages, onlyOtp) {
        if (onlyOtp) allMessages.filter { isOtpLike(it.body) } else allMessages
    }

    fun reload() {
        loading = true
        errMsg = null
        try {
            allMessages = queryInboxSms(context)
            EventLog.add("短信测试：读取到 ${allMessages.size} 条短信，验证码候选 ${allMessages.count { isOtpLike(it.body) }} 条")
        } catch (e: Exception) {
            val msg = "读取短信失败: ${e.message}"
            Log.e(TEST_TAG, msg, e)
            errMsg = msg
            EventLog.add("短信测试：$msg")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("短信测试（本地落库检查）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "用于验证系统是否实际收到了短信。若此处可见但 Receiver 断点未命中，通常是系统未下发第三方广播。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { reload() }, enabled = !loading, modifier = Modifier.weight(1f)) {
                Text(if (loading) "读取中..." else "刷新短信")
            }
            FilterChip(
                selected = onlyOtp,
                onClick = { onlyOtp = !onlyOtp },
                label = { Text(if (onlyOtp) "仅验证码" else "全部短信") }
            )
        }

        errMsg?.let {
            Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "显示 ${displayMessages.size} 条（总计 ${allMessages.size} 条）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        if (displayMessages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有匹配短信", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(displayMessages) { sms ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            text = "发件人: ${sms.address.ifBlank { "(未知)" }}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "时间: ${SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sms.date))}  类型: ${sms.type}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sms.body,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
