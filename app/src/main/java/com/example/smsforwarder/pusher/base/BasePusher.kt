package com.example.smsforwarder.pusher.base

/**
 * 推送器抽象基类
 */
abstract class BasePusher {

    /**
     * 执行推送
     * @param variables 已解析的变量，供推送配置中使用
     * @return 推送结果
     */
    abstract suspend fun push(variables: Map<String, String>): PusherResult

    /**
     * 推送器名称，用于日志标识
     */
    abstract val name: String
}
