package com.example.smsforwarder.core.sms.processor

import android.content.Intent
import com.example.smsforwarder.core.sms.model.SmsMessage

/**
 * 从 SMS 广播 Intent 中提取短信数据
 */
object SmsIntentProcessor {

    const val EXTRA_SMS_BODY = "sms_body"
    const val EXTRA_SENDER = "sender"
    const val EXTRA_SMS_TIMESTAMP = "sms_timestamp"

    /**
     * 将短信内容封装到 Intent 中（由 SmsReceiver 调用）
     */
    fun putSmsToIntent(intent: Intent, sender: String, body: String, timestamp: Long = System.currentTimeMillis()) {
        intent.putExtra(EXTRA_SENDER, sender)
        intent.putExtra(EXTRA_SMS_BODY, body)
        intent.putExtra(EXTRA_SMS_TIMESTAMP, timestamp)
    }

    /**
     * 从 Intent 中提取 SmsMessage（由 Service 调用）
     */
    fun extractFromIntent(intent: Intent): SmsMessage? {
        val body = intent.getStringExtra(EXTRA_SMS_BODY) ?: return null
        val sender = intent.getStringExtra(EXTRA_SENDER) ?: return null
        val timestamp = intent.getLongExtra(EXTRA_SMS_TIMESTAMP, System.currentTimeMillis())
        if (body.isEmpty() && sender.isEmpty()) return null
        return SmsMessage(
            sender = sender,
            content = body,
            timestamp = timestamp
        )
    }
}
