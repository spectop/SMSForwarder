package com.example.smsforwarder.pusher.sms_agent

import android.util.Log
import com.example.smsforwarder.core.engine.VariableResolver
import com.example.smsforwarder.pusher.base.BasePusher
import com.example.smsforwarder.pusher.base.PusherResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "SmsAgentPusher"

/**
 * SMS Agent 推送器
 * 将短信数据以 JSON POST 方式发送到指定端点
 *
 * 必需配置：endpoint, tag, token
 */
class SmsAgentPusher(
    private val ruleId: String,
    private val config: Map<String, String>
) : BasePusher() {

    override val name: String = "sms_agent[$ruleId]"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun push(variables: Map<String, String>): PusherResult {
        val endpoint = config["endpoint"] ?: return PusherResult.Failure("sms_agent 缺少 endpoint 配置")
        val tag = VariableResolver.resolve(config["tag"] ?: "sms", variables)
        val code = variables["code"] ?: return PusherResult.Failure("缺少 code 变量")
        val token = config["token"] ?: ""

        return try {
            // 构建 SMSCodePushRequest payload
            val payload = JSONObject().apply {
                put("tag", tag)
                put("code", code)
                // 可选字段
                variables["ttl"]?.let { put("ttl", it.toIntOrNull()) }
                if (variables.size > 1) {
                    val metadata = JSONObject()
                    variables.filterKeys { it !in listOf("code", "ttl") && !it.startsWith("_") }
                        .forEach { (k, v) -> metadata.put(k, v) }
                    if (metadata.length() > 0) {
                        put("metadata", metadata)
                    }
                }
            }.toString()

            val body = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$endpoint/codes")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .apply { if (token.isNotEmpty()) addHeader("Authorization", "Bearer $token") }
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "[$ruleId] 推送成功: ${resp.code}")
                    PusherResult.Success("HTTP ${resp.code}")
                } else {
                    val errorBody = resp.body?.string()?.take(200) ?: ""
                    Log.w(TAG, "[$ruleId] 推送失败: ${resp.code} $errorBody")
                    PusherResult.Failure("HTTP ${resp.code}: $errorBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$ruleId] 推送异常", e)
            PusherResult.Failure("推送异常: ${e.message}", e)
        }
    }
}
