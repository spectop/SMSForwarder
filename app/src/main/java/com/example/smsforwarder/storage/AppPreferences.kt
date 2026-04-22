package com.example.smsforwarder.storage

import android.content.Context
import android.content.SharedPreferences

private const val PREF_NAME = "sms_forwarder_prefs"
private const val KEY_SERVICE_ENABLED = "service_enabled"
private const val KEY_LAST_SMS_SCAN_TIMESTAMP = "last_sms_scan_timestamp"

/**
 * 应用偏好存储（SharedPreferences 封装）
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var lastSmsScanTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SMS_SCAN_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SMS_SCAN_TIMESTAMP, value).apply()
}
