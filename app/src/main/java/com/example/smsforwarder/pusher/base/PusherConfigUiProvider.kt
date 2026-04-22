package com.example.smsforwarder.pusher.base

import androidx.compose.runtime.Composable
import com.example.smsforwarder.core.config.model.PusherType

/**
 * 推送器配置 UI 提供者。
 * 每个推送器自行实现，用于在配置页渲染对应的配置表单。
 */
interface PusherConfigUiProvider {
    val type: PusherType
    val displayName: String
    val description: String
    val icon: String

    fun defaultConfig(): Map<String, String>

    @Composable
    fun RenderConfigEditor(
        config: Map<String, String>,
        onConfigChange: (Map<String, String>) -> Unit
    )
}
