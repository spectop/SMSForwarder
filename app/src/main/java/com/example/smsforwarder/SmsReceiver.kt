package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smsforwarder.core.sms.processor.SmsIntentProcessor

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("SmsReceiver", "收到广播 action=$action")
        if (
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != Telephony.Sms.Intents.SMS_DELIVER_ACTION
        ) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).toList()
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: ""
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

        Log.i("SmsReceiver", "收到短信：发件人=${sender.take(3)}***")

        val serviceIntent = Intent(context, SmsMonitorService::class.java)
        SmsIntentProcessor.putSmsToIntent(serviceIntent, sender, body, timestamp)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

