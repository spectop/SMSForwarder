package com.example.smsforwarder.pusher.base

import com.example.smsforwarder.core.config.model.PushRule
import com.example.smsforwarder.core.config.model.PusherType
import com.example.smsforwarder.pusher.curl.CurlPusher
import com.example.smsforwarder.pusher.sms_agent.SmsAgentPusher

/**
 * 推送器工厂，根据 PushRule 创建对应的 BasePusher 实例
 */
object PusherFactory {

    fun create(rule: PushRule): BasePusher? {
        return when (rule.type) {
            PusherType.SMS_AGENT -> SmsAgentPusher(rule.id, rule.config)
            PusherType.CURL -> CurlPusher(rule.id, rule.config)
        }
    }
}
