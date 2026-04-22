package com.example.smsforwarder.storage

import android.content.Context
import android.content.SharedPreferences

private const val PREF_NAME = "sms_forwarder_prefs"
private const val KEY_SERVICE_ENABLED = "service_enabled"

/**
 * 应用偏好存储（SharedPreferences 封装）
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()
}
