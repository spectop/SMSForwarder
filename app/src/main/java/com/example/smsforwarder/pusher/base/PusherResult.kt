package com.example.smsforwarder.pusher.base

/**
 * 推送结果
 */
sealed class PusherResult {
    data class Success(val message: String = "推送成功") : PusherResult()
    data class Failure(val error: String, val cause: Throwable? = null) : PusherResult()

    val isSuccess: Boolean get() = this is Success
}
