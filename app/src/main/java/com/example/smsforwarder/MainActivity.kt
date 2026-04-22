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
import com.example.smsforwarder.core.config.validator.ConfigValidator
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

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    var configText by remember {
        mutableStateOf(AssetConfigLoader.readConfigText(context))
    }
    var saveResult by remember { mutableStateOf<String?>(null) }
    var validationMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "YAML 配置编辑",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "配置文件路径：${AssetConfigLoader.getUserConfigFile(context).absolutePath}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 校验/保存按钮行
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val cm = ConfigManager(context)
                    val loaded = com.example.smsforwarder.core.config.loader.YamlConfigLoader.load(configText)
                    if (loaded == null) {
                        validationMsg = "YAML 语法错误，请检查格式"
                        saveResult = null
                    } else {
                        val result = ConfigValidator.validate(loaded)
                        validationMsg = if (result.isValid) {
                            "✓ 配置有效" + (if (result.warnings.isNotEmpty()) "\n警告: ${result.warnings.joinToString()}" else "")
                        } else {
                            "✗ 错误:\n${result.errors.joinToString("\n")}"
                        }
                        saveResult = null
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("校验") }

            Button(
                onClick = {
                    val cm = ConfigManager(context)
                    val ok = cm.saveAndReload(configText)
                    saveResult = if (ok) "✓ 保存成功，配置已重新加载" else "✗ 保存失败"
                    validationMsg = null
                    EventLog.add(if (ok) "配置已保存并重新加载" else "配置保存失败")
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
        }

        // 校验结果
        (validationMsg ?: saveResult)?.let { msg ->
            Text(
                text = msg,
                fontSize = 12.sp,
                color = if (msg.startsWith("✓")) Color(0xFF388E3C) else Color(0xFFD32F2F),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // YAML 编辑区
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
