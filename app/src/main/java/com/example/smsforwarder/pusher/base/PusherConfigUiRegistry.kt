package com.example.smsforwarder.pusher.base

import com.example.smsforwarder.core.config.model.PusherType
import com.example.smsforwarder.pusher.curl.CurlPusherConfigUiProvider
import com.example.smsforwarder.pusher.sms_agent.SmsAgentPusherConfigUiProvider

/**
 * 推送器配置 UI 注册表，集中管理可用的配置表单实现。
 */
object PusherConfigUiRegistry {

    private val providerList: List<PusherConfigUiProvider> = listOf(
        SmsAgentPusherConfigUiProvider,
        CurlPusherConfigUiProvider
    )

    private val providers: Map<PusherType, PusherConfigUiProvider> =
        providerList.associateBy { it.type }

    fun get(type: PusherType): PusherConfigUiProvider? = providers[type]

    fun allProviders(): List<PusherConfigUiProvider> = providerList
}
