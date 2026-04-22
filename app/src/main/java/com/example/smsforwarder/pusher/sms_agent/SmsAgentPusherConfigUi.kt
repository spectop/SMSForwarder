package com.example.smsforwarder.pusher.sms_agent

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
 * SMS Agent 推送器的配置 UI 实现。
 */
object SmsAgentPusherConfigUiProvider : PusherConfigUiProvider {
    override val type: PusherType = PusherType.SMS_AGENT
    override val displayName: String = "SMS Agent"
    override val description: String = "将验证码推送到 SMS Agent 服务端"
    override val icon: String = "📨"

    override fun defaultConfig(): Map<String, String> = mapOf(
        SmsAgentConfig.KEY_ENDPOINT to "",
        SmsAgentConfig.KEY_TAG to "sms",
        SmsAgentConfig.KEY_TOKEN to ""
    )

    @Composable
    override fun RenderConfigEditor(
        config: Map<String, String>,
        onConfigChange: (Map<String, String>) -> Unit
    ) {
        val endpoint = config[SmsAgentConfig.KEY_ENDPOINT] ?: ""
        val tag = config[SmsAgentConfig.KEY_TAG] ?: "sms"
        val token = config[SmsAgentConfig.KEY_TOKEN] ?: ""

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { onConfigChange(config + (SmsAgentConfig.KEY_ENDPOINT to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint") },
                singleLine = true
            )
            OutlinedTextField(
                value = tag,
                onValueChange = { onConfigChange(config + (SmsAgentConfig.KEY_TAG to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tag") },
                singleLine = true
            )
            OutlinedTextField(
                value = token,
                onValueChange = { onConfigChange(config + (SmsAgentConfig.KEY_TOKEN to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token") },
                singleLine = true
            )
        }
    }
}
