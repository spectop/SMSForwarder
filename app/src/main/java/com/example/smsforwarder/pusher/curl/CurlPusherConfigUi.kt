package com.example.smsforwarder.pusher.curl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smsforwarder.core.config.model.PusherType
import com.example.smsforwarder.pusher.base.PusherConfigUiProvider

/**
 * Curl 推送器的配置 UI 实现。
 */
object CurlPusherConfigUiProvider : PusherConfigUiProvider {
    override val type: PusherType = PusherType.CURL
    override val displayName: String = "WebHook / CURL"
    override val description: String = "按 URL、Method、Headers 和模板发送 HTTP 请求"
    override val icon: String = "🌐"

    override fun defaultConfig(): Map<String, String> = mapOf(
        CurlConfig.KEY_URL to "",
        CurlConfig.KEY_METHOD to "POST",
        CurlConfig.KEY_BODY_TEMPLATE to "",
        CurlConfig.KEY_HEADERS to "Content-Type: application/json"
    )

    @Composable
    override fun RenderConfigEditor(
        config: Map<String, String>,
        onConfigChange: (Map<String, String>) -> Unit
    ) {
        val url = config[CurlConfig.KEY_URL] ?: ""
        val method = config[CurlConfig.KEY_METHOD] ?: "POST"
        val bodyTemplate = config[CurlConfig.KEY_BODY_TEMPLATE] ?: ""
        val headers = config[CurlConfig.KEY_HEADERS] ?: "Content-Type: application/json"

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { onConfigChange(config + (CurlConfig.KEY_URL to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                singleLine = true
            )
            OutlinedTextField(
                value = method,
                onValueChange = { onConfigChange(config + (CurlConfig.KEY_METHOD to it.uppercase())) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Method") },
                singleLine = true
            )
            OutlinedTextField(
                value = bodyTemplate,
                onValueChange = { onConfigChange(config + (CurlConfig.KEY_BODY_TEMPLATE to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Body Template") }
            )
            OutlinedTextField(
                value = headers,
                onValueChange = { onConfigChange(config + (CurlConfig.KEY_HEADERS to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Headers(每行 Key: Value)") }
            )
        }
    }
}
