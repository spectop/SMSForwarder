package com.example.smsforwarder.pusher.curl

import android.util.Log
import com.example.smsforwarder.core.engine.VariableResolver
import com.example.smsforwarder.pusher.base.BasePusher
import com.example.smsforwarder.pusher.base.PusherResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "CurlPusher"

/**
 * Curl 风格推送器
 * 根据配置构造 HTTP 请求推送数据
 *
 * 支持配置：
 * - url：目标 URL（支持 ${varName} 变量）
 * - method：HTTP 方法，默认 POST
 * - body_template：请求体模板（支持 ${varName} 变量）
 * - headers：以换行分隔的 "Key: Value" 请求头列表
 */
class CurlPusher(
    private val ruleId: String,
    private val config: Map<String, String>
) : BasePusher() {

    override val name: String = "curl[$ruleId]"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun push(variables: Map<String, String>): PusherResult {
        val urlTemplate = config["url"] ?: return PusherResult.Failure("curl 缺少 url 配置")
        val url = VariableResolver.resolve(urlTemplate, variables)
        val method = config["method"]?.uppercase() ?: "POST"
        val bodyTemplate = config["body_template"] ?: ""
        val body = VariableResolver.resolve(bodyTemplate, variables)
        val headersRaw = config["headers"] ?: "Content-Type: application/json"

        return try {
            val requestBody = if (method != "GET" && body.isNotEmpty()) {
                body.toRequestBody("application/json".toMediaType())
            } else {
                null
            }

            val requestBuilder = Request.Builder().url(url)
            // 解析并添加请求头
            headersRaw.lines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty()) requestBuilder.addHeader(key, value)
                }
            }

            when (method) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody())
                "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody())
                else -> requestBuilder.method(method, requestBody)
            }

            val response = client.newCall(requestBuilder.build()).execute()
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
