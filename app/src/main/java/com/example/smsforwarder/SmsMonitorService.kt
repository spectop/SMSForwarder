package com.example.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smsforwarder.core.config.ConfigManager
import com.example.smsforwarder.core.engine.SmsProcessingEngine
import com.example.smsforwarder.core.sms.model.SmsMessage
import com.example.smsforwarder.core.sms.processor.SmsIntentProcessor
import com.example.smsforwarder.storage.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SmsMonitorService"
private const val NOTIFICATION_ID = 1
private const val CHANNEL_ID = "sms_monitor_channel"

class SmsMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var configManager: ConfigManager
    private lateinit var engine: SmsProcessingEngine
    private var lastCheckedSmsDate = 0L

    companion object {
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager(applicationContext)
        engine = SmsProcessingEngine(configManager)
        isRunning = true
        Log.i(TAG, "短信监控服务已启动")
        EventLog.add("监控服务已启动")
        startSmsPolling()
    }

    private fun startSmsPolling() {
        serviceScope.launch {
            while (true) {
                try {
                    delay(3000L)
                    checkNewSmsInDatabase()
                } catch (e: Exception) {
                    Log.w(TAG, "轮询检查异常: ${e.message}")
                }
            }
        }
    }

    private suspend fun checkNewSmsInDatabase() {
        val resolver = contentResolver
        val uri = Uri.parse("content://sms")
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        try {
            val cursor = resolver.query(
                uri,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(lastCheckedSmsDate.toString()),
                "date DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
                    val idxDate = it.getColumnIndex(Telephony.Sms.DATE)

                    do {
                        val address = if (idxAddress >= 0) it.getString(idxAddress) ?: "" else ""
                        val body = if (idxBody >= 0) it.getString(idxBody) ?: "" else ""
                        val date = if (idxDate >= 0) it.getLong(idxDate) else 0L

                        if (body.isNotBlank()) {
                            lastCheckedSmsDate = maxOf(lastCheckedSmsDate, date)
                            Log.d(TAG, "轮询发现新短信: ${address.take(3)}***")
                            val sms = SmsMessage(
                                sender = address,
                                content = body,
                                timestamp = date
                            )
                            engine.process(sms)
                        }
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询短信库异常: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 处理 SMS 数据（如果 Intent 包含的话）
        intent?.let { safeIntent ->
            val sms = SmsIntentProcessor.extractFromIntent(safeIntent)
            if (sms != null) {
                serviceScope.launch {
                    engine.process(sms)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isRunning = false
        Log.i(TAG, "短信监控服务已停止")
        EventLog.add("监控服务已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 通知 ──────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS Forwarder 后台运行通知"
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder")
            .setContentText("短信监控运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
